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
        ThinkingDiagnosticTestSupport.PolicyAwareChatFactory chat =
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory();

        ThinkingDiagnosticResult result = execute(settings -> model, chat);

        assertThat(result.rows()).hasSize(ThinkingDiagnosticProtocol.ROW_COUNT);
        assertThat(result.rows()).extracting(ThinkingDiagnosticRow::sequence)
                .containsExactlyElementsOf(
                        result.orderedSchedule().stream()
                                .map(ThinkingDiagnosticScheduleEntry::sequence).toList());
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.attemptCount()).isEqualTo(1));
        assertThat(model.observedPolicies()).hasSize(3 * LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(chat.observedPolicies()).hasSize(4 * LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
    }

    @Test
    void sendsEachArmsExplicitReasoningPolicyAndRecordsItPerRow() {
        ThinkingDiagnosticTestSupport.PolicyAwareChatModel model =
                new ThinkingDiagnosticTestSupport.PolicyAwareChatModel();
        ThinkingDiagnosticTestSupport.PolicyAwareChatFactory chat =
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory();

        ThinkingDiagnosticResult result = execute(settings -> model, chat);

        for (ThinkingDiagnosticRow row : result.rows()) {
            ThinkingDiagnosticArm arm = ThinkingDiagnosticProtocol.requireArm(row.armId());
            assertThat(row.requestedReasoningPolicy()).isEqualTo(arm.reasoningPolicy());
            assertThat(row.reasoningPolicySupport()).isEqualTo(
                    arm.reasoningPolicy() == ChatReasoningPolicy.PROVIDER_DEFAULT
                            ? ChatReasoningSupport.NOT_REQUESTED : ChatReasoningSupport.APPLIED);
            assertThat(row.maxOutputTokens()).isEqualTo(arm.maxOutputTokens());
            assertThat(row.executionBoundary()).isEqualTo(arm.executionBoundary());
        }
        assertThat(chat.observedPolicies().stream().distinct().toList())
                .containsExactlyInAnyOrder(
                        ChatReasoningPolicy.PROVIDER_DEFAULT,
                        ChatReasoningPolicy.ENABLED,
                        ChatReasoningPolicy.DISABLED);
        assertThat(model.observedPolicies()).containsNull();
    }

    @Test
    void separatesEmptyContentWithThinkingFromEmptyContentWithoutIt() {
        ThinkingDiagnosticResult result = execute(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel(),
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory());

        List<ThinkingDiagnosticRow> enabled = rowsFor(result, "fact-check-subject-enabled-64");
        List<ThinkingDiagnosticRow> disabled = rowsFor(result, "fact-check-subject-disabled-64");

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
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel(),
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory());

        assertThat(result.rows()).allSatisfy(row -> {
            if (row.thinking() != null) {
                assertThat(row.content() == null || !row.content().contains(row.thinking())).isTrue();
            }
        });
    }

    @Test
    void classifiesContentAccompaniedByReasoningAsItsOwnOutcome() {
        ThinkingDiagnosticResult result = execute(
                settings -> fixedModel(ThinkingDiagnosticTestSupport.response(
                        "yes", "trace", "stop", 11, 3)),
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory());

        assertThat(result.rows().stream()
                .filter(row -> row.executionBoundary()
                        == ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR)
                .toList()).allSatisfy(row -> {
            assertThat(row.outcome()).isEqualTo(ThinkingDiagnosticOutcome.CONTENT_WITH_THINKING);
            assertThat(row.content()).isEqualTo("yes");
            assertThat(row.thinking()).isEqualTo("trace");
        });
    }

    @Test
    void retainsEveryFailedRowWithoutRetryingOrOmittingIt() {
        ThinkingDiagnosticResult result = execute(
                settings -> failingModel(),
                (identity, settings, policy) -> request -> {
                    throw new IllegalStateException("provider unavailable in this test");
                });

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
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel(),
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory());

        assertThat(rowsFor(result, "fact-check-subject-enabled-64"))
                .allSatisfy(row -> assertThat(row.modelAdvertisesThinking()).isTrue());
        assertThat(rowsFor(result, "chat-control-provider-default-64"))
                .allSatisfy(row -> {
                    assertThat(row.modelAdvertisesThinking()).isFalse();
                    assertThat(row.modelRole()).isEqualTo(ThinkingDiagnosticModelRole.CONTROL);
                    assertThat(row.requestedReasoningPolicy())
                            .isEqualTo(ChatReasoningPolicy.PROVIDER_DEFAULT);
                });
    }

    @Test
    void chatBoundaryUsesTheIdenticalRenderedFactCheckPromptAndCarriesNoJudgeVerdict() {
        ThinkingDiagnosticTestSupport.PolicyAwareChatFactory chat =
                new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory();
        ThinkingDiagnosticResult result = execute(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel(), chat);

        List<ThinkingDiagnosticRow> chatRows = result.rows().stream()
                .filter(row -> row.executionBoundary()
                        == ThinkingDiagnosticExecutionBoundary.CHAT_INVOCATION)
                .toList();
        assertThat(chatRows).allSatisfy(row -> {
            assertThat(row.documentBlake3()).hasSize(64);
            assertThat(row.claimBlake3()).hasSize(64);
            assertThat(row.expectedVerdict()).isNotNull();
            assertThat(row.normalizedJudgeVerdict()).isNull();
            assertThat(row.expectedVerdictMatched()).isNull();
        });
        assertThat(chat.observedPrompts()).containsExactlyElementsOf(
                chatRows.stream().map(row -> {
                    var fixture = catalog.require(row.fixtureId());
                    return ThinkingDiagnosticTestSupport.prompt()
                            .render(fixture.document(), fixture.claim());
                }).toList());
    }

    private ThinkingDiagnosticResult execute(
            ThinkingDiagnosticJudgeFactory factory,
            ThinkingDiagnosticChatFactory chatFactory
    ) {
        return new ThinkingDiagnosticExecutor(
                factory, chatFactory, ThinkingDiagnosticTestSupport.prompt())
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
