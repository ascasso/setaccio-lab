package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThat;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatThinkingPresence;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;

class ThinkingDiagnosticExecutorTest {

    private final LocalFactCheckFixtureCatalog catalog = ThinkingDiagnosticTestSupport.catalog();

    @Test
    void runsTheWholeLockedScheduleWithoutAnyProviderService() {
        ThinkingDiagnosticTestSupport.PolicyAwareChatModel model =
                new ThinkingDiagnosticTestSupport.PolicyAwareChatModel();

        ThinkingDiagnosticResult result = execute(settings -> model);

        assertThat(result.rows()).hasSize(ThinkingDiagnosticProtocol.ROW_COUNT);
        assertThat(result.rows()).extracting(ThinkingDiagnosticRow::sequence)
                .containsExactlyElementsOf(
                        result.orderedSchedule().stream()
                                .map(ThinkingDiagnosticScheduleEntry::sequence).toList());
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.attemptCount()).isEqualTo(1));
        assertThat(model.observedPolicies()).hasSize(ThinkingDiagnosticProtocol.ROW_COUNT);
    }

    @Test
    void sendsEachArmsExplicitReasoningPolicyAndRecordsItPerRow() {
        ThinkingDiagnosticTestSupport.PolicyAwareChatModel model =
                new ThinkingDiagnosticTestSupport.PolicyAwareChatModel();

        ThinkingDiagnosticResult result = execute(settings -> model);

        assertThat(model.observedPolicies()).doesNotContainNull();
        for (ThinkingDiagnosticRow row : result.rows()) {
            ThinkingDiagnosticArm arm = ThinkingDiagnosticProtocol.requireArm(row.armId());
            assertThat(row.requestedReasoningPolicy()).isEqualTo(arm.reasoningPolicy());
            assertThat(row.reasoningPolicySupport()).isEqualTo(ChatReasoningSupport.APPLIED);
            assertThat(row.maxOutputTokens()).isEqualTo(arm.maxOutputTokens());
        }
        assertThat(model.observedPolicies().stream().distinct().toList())
                .containsExactlyInAnyOrder(
                        ThinkOption.ThinkBoolean.ENABLED, ThinkOption.ThinkBoolean.DISABLED);
    }

    @Test
    void separatesEmptyContentWithThinkingFromEmptyContentWithoutIt() {
        ThinkingDiagnosticResult result = execute(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel());

        List<ThinkingDiagnosticRow> enabled = rowsFor(result, "subject-thinking-enabled-64");
        List<ThinkingDiagnosticRow> disabled = rowsFor(result, "subject-thinking-disabled-64");

        assertThat(enabled).allSatisfy(row -> {
            assertThat(row.outcome()).isEqualTo(ThinkingDiagnosticOutcome.EMPTY_CONTENT_WITH_THINKING);
            assertThat(row.thinkingPresence()).isEqualTo(ChatThinkingPresence.PRESENT);
            assertThat(row.thinking()).isEqualTo("reasoning trace");
            assertThat(row.contentPresent()).isFalse();
            assertThat(row.budgetSaturated()).isTrue();
            assertThat(row.finishReason()).isEqualTo("length");
            assertThat(row.evaluatedOutputTokens()).isEqualTo(64);
        });
        assertThat(disabled).allSatisfy(row -> {
            assertThat(row.outcome()).isEqualTo(ThinkingDiagnosticOutcome.CONTENT_WITHOUT_THINKING);
            assertThat(row.thinkingPresence()).isEqualTo(ChatThinkingPresence.ABSENT);
            assertThat(row.thinking()).isNull();
            assertThat(row.content()).isEqualTo("no");
            assertThat(row.budgetSaturated()).isFalse();
        });
    }

    @Test
    void neverMergesReasoningIntoTheRecordedAssistantContent() {
        ThinkingDiagnosticResult result = execute(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel());

        assertThat(result.rows()).allSatisfy(row -> {
            if (row.thinking() != null) {
                assertThat(row.content() == null || !row.content().contains(row.thinking())).isTrue();
            }
        });
    }

    @Test
    void classifiesContentAccompaniedByReasoningAsItsOwnOutcome() {
        ThinkingDiagnosticResult result = execute(settings -> fixedModel(
                ThinkingDiagnosticTestSupport.response("yes", "trace", "stop", 11, 3)));

        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.outcome()).isEqualTo(ThinkingDiagnosticOutcome.CONTENT_WITH_THINKING);
            assertThat(row.content()).isEqualTo("yes");
            assertThat(row.thinking()).isEqualTo("trace");
        });
    }

    @Test
    void retainsEveryFailedRowWithoutRetryingOrOmittingIt() {
        ThinkingDiagnosticResult result = execute(settings -> failingModel());

        assertThat(result.rows()).hasSize(ThinkingDiagnosticProtocol.ROW_COUNT);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.invocationSucceeded()).isFalse();
            assertThat(row.outcome()).isEqualTo(ThinkingDiagnosticOutcome.PROVIDER_FAILURE);
            assertThat(row.error()).isNotBlank();
            assertThat(row.content()).isNull();
            assertThat(row.thinking()).isNull();
            assertThat(row.thinkingPresence()).isEqualTo(ChatThinkingPresence.UNAVAILABLE);
            assertThat(row.attemptCount()).isEqualTo(1);
        });
    }

    @Test
    void recordsTheAdvertisedThinkingCapabilityPerModelRole() {
        ThinkingDiagnosticResult result = execute(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel());

        assertThat(rowsFor(result, "subject-thinking-enabled-64"))
                .allSatisfy(row -> assertThat(row.modelAdvertisesThinking()).isTrue());
        assertThat(rowsFor(result, "control-thinking-disabled-64"))
                .allSatisfy(row -> {
                    assertThat(row.modelAdvertisesThinking()).isFalse();
                    assertThat(row.modelRole()).isEqualTo(ThinkingDiagnosticModelRole.CONTROL);
                    assertThat(row.requestedReasoningPolicy()).isEqualTo(ChatReasoningPolicy.DISABLED);
                });
    }

    private ThinkingDiagnosticResult execute(ThinkingDiagnosticJudgeFactory factory) {
        return new ThinkingDiagnosticExecutor(factory, ThinkingDiagnosticTestSupport.prompt())
                .execute(catalog, ThinkingDiagnosticTestSupport.identities(), "0.33.2");
    }

    private static List<ThinkingDiagnosticRow> rowsFor(ThinkingDiagnosticResult result, String armId) {
        return result.rows().stream().filter(row -> row.armId().equals(armId)).toList();
    }

    private static ChatModel fixedModel(org.springframework.ai.chat.model.ChatResponse response) {
        return new ChatModel() {
            @Override
            public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
                return response;
            }

            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return OllamaChatOptions.builder().model("subject:model").build();
            }
        };
    }

    private static ChatModel failingModel() {
        return new ChatModel() {
            @Override
            public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("provider unavailable in this test");
            }

            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return OllamaChatOptions.builder().model("subject:model").build();
            }
        };
    }
}
