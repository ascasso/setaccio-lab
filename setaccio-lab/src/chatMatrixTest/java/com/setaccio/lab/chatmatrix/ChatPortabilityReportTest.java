package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatEvidenceModelIdentity;
import com.setaccio.lab.chat.ChatGenerationOption;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatModelIdentifierKind;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceFrameworkVersions;
import com.setaccio.lab.evidence.EvidenceManifest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatPortabilityReportTest {

    @Test
    void reportsProviderEvidenceWithoutRankingOrRawOutputAndMakesSeedDifferenceExplicit() {
        ChatPortabilitySnapshot ollama = snapshot("ollama-evidence", ollamaIdentity(), settings(ChatProviderOptionSupport.supportsAll(), List.of(42, 43)), false);
        ChatPortabilitySnapshot anthropic = snapshot("anthropic-evidence", anthropicIdentity(),
                settings(anthropicOptionSupport(), List.of()), true);

        ChatPortabilityReport report = new ChatPortabilityReport();
        ChatPortabilityReport.Comparison comparison = report.compare(ollama, anthropic);
        String rendered = report.render(ollama, anthropic);

        assertThat(comparison.architectureCompatible()).isTrue();
        assertThat(comparison.reasons()).anyMatch(reason -> reason.contains("seed as unsupported"));
        assertThat(rendered)
                .contains("# Chat Provider Portability Report")
                .contains("HOSTED_VERSIONED")
                .contains("not applicable to hosted model")
                .contains("Semantic/performance comparison: `not performed`")
                .contains("Token usage and estimated cost are reported separately")
                .doesNotContain("private response text");
    }

    @Test
    void refusesArchitectureComparisonWhenInputsOrCommonSettingsDrift() {
        ChatPortabilitySnapshot baseline = snapshot("baseline", ollamaIdentity(), settings(ChatProviderOptionSupport.supportsAll(), List.of(42, 43)), false);
        ChatPortabilityRunSettings changedSettings = new ChatPortabilityRunSettings(
                "different-catalog", "1", "b".repeat(64), ChatMatrixTestFixtures.CATALOG.identities(),
                2, 6, 0.0, 256, 30_000, 1, List.of(), anthropicOptionSupport());
        ChatPortabilitySnapshot candidate = snapshot("candidate", anthropicIdentity(), changedSettings, true);

        ChatPortabilityReport.Comparison comparison = new ChatPortabilityReport().compare(baseline, candidate);

        assertThat(comparison.architectureCompatible()).isFalse();
        assertThat(comparison.reasons()).anyMatch(reason -> reason.contains("Prompt catalog identity"));
        assertThat(comparison.reasons()).anyMatch(reason -> reason.contains("output-token cap"));
    }

    @Test
    void rejectsFabricatedHostedDigestsAndSimulatedUnsupportedSeeds() {
        assertThatThrownBy(() -> new ChatEvidenceModelIdentity(
                "anthropic", "claude-haiku-4-5-20251001", "claude-haiku-4-5-20251001",
                ChatModelIdentifierKind.HOSTED_VERSIONED, "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hosted model identity must not claim a local digest");

        assertThatThrownBy(() -> settings(anthropicOptionSupport(), List.of(42, 43)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported seed settings must not record simulated seed values");
    }

    @Test
    void projectsVerifiedOllamaEvidenceWithoutCopyingRawResponses() {
        ChatMatrixResult result = ChatMatrixTestFixtures.successfulResult();
        ChatPortabilitySnapshot snapshot = ChatPortabilitySnapshotFactory.fromVerifiedOllama(
                "ollama-v1",
                result,
                new EvidenceManifest(
                        1, ChatMatrixProtocol.SUITE, "2026-08-05-ollama", Instant.parse("2026-08-05T12:00:00Z"),
                        new EvidenceCodeBaseline("clean", false), new EvidenceFrameworkVersions("4.1.0", "2.0.0"),
                        ChatMatrixProtocol.EXECUTION_ENGINE, Map.of(), List.of()),
                new ChatEstimatedCost("USD", 1_536, 768, BigDecimal.ONE, new BigDecimal("5"),
                        Instant.parse("2026-08-05T12:00:00Z"), "https://platform.claude.com/docs/en/about-claude/pricing"));

        assertThat(snapshot.requestedModelIdentity().identifierKind()).isEqualTo(ChatModelIdentifierKind.LOCAL_DIGEST);
        assertThat(snapshot.rows()).hasSize(6);
        assertThat(snapshot.rows().getFirst().structuralOutputPresent()).isTrue();
    }

    private static ChatPortabilitySnapshot snapshot(
            String evidenceId,
            ChatEvidenceModelIdentity identity,
            ChatPortabilityRunSettings settings,
            boolean candidate
    ) {
        List<ChatPortabilityRow> rows = new ArrayList<>();
        for (int index = 0; index < settings.plannedCallCount(); index++) {
            ChatPromptIdentity prompt = settings.orderedPromptIdentities().get(index % settings.orderedPromptIdentities().size());
            int repetition = index / settings.orderedPromptIdentities().size() + 1;
            boolean empty = candidate && index == 0;
            rows.add(new ChatPortabilityRow(
                    index + 1,
                    repetition,
                    settings.seeds().isEmpty() ? null : settings.seeds().get(repetition - 1),
                    prompt.id(),
                    prompt.sha256(),
                    identity,
                    true,
                    !empty,
                    70,
                    empty ? 0 : 4,
                    empty ? 70 : 74,
                    10L + index,
                    1,
                    empty ? ChatInvocationFailureCategory.EMPTY_RESPONSE : ChatInvocationFailureCategory.NONE));
        }
        return new ChatPortabilitySnapshot(
                evidenceId,
                identity,
                settings,
                new EvidenceFrameworkVersions("4.1.0", "2.0.0"),
                new EvidenceCodeBaseline(candidate ? "candidate" : "baseline", false),
                rows,
                new ChatEstimatedCost(
                        "USD", 1_536, (long) settings.plannedCallCount() * settings.maxOutputTokens(), new BigDecimal("1"), new BigDecimal("5"),
                        Instant.parse("2026-08-05T12:00:00Z"), "https://platform.claude.com/docs/en/about-claude/pricing"));
    }

    private static ChatPortabilityRunSettings settings(ChatProviderOptionSupport support, List<Integer> seeds) {
        return new ChatPortabilityRunSettings(
                ChatMatrixTestFixtures.CATALOG.id(),
                ChatMatrixTestFixtures.CATALOG.version(),
                ChatMatrixTestFixtures.CATALOG.sha256(),
                ChatMatrixTestFixtures.CATALOG.identities(),
                2, 6, 0.0, 128, 30_000, 1, seeds, support);
    }

    private static ChatProviderOptionSupport anthropicOptionSupport() {
        return new ChatProviderOptionSupport(
                EnumSet.complementOf(EnumSet.of(ChatGenerationOption.SEED)),
                Map.of(ChatGenerationOption.SEED, "Anthropic Messages API does not expose a seed option"));
    }

    private static ChatEvidenceModelIdentity ollamaIdentity() {
        return new ChatEvidenceModelIdentity(
                "ollama", "gemma4:e2b", "gemma4:e2b", ChatModelIdentifierKind.LOCAL_DIGEST, "a".repeat(64));
    }

    private static ChatEvidenceModelIdentity anthropicIdentity() {
        return new ChatEvidenceModelIdentity(
                "anthropic", "claude-haiku-4-5-20251001", "claude-haiku-4-5-20251001",
                ChatModelIdentifierKind.HOSTED_VERSIONED, null);
    }
}
