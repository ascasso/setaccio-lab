package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkingDiagnosticProtocolTest {

    @Test
    void locksTheSevenPreRegisteredArmsInAFixedOrder() {
        assertThat(ThinkingDiagnosticProtocol.ARMS).extracting(ThinkingDiagnosticArm::armId)
                .containsExactly(
                        "fact-check-subject-provider-default-64",
                        "fact-check-subject-enabled-64",
                        "fact-check-subject-disabled-64",
                        "chat-subject-provider-default-64",
                        "chat-subject-enabled-64",
                        "chat-subject-disabled-64",
                        "chat-control-provider-default-64");
        assertThat(ThinkingDiagnosticProtocol.ARMS)
                .filteredOn(arm -> arm.reasoningPolicy() == ChatReasoningPolicy.PROVIDER_DEFAULT)
                .allSatisfy(arm -> assertThat(arm.measuredProviderDefault()).isTrue());
    }

    @Test
    void holdsEveryNonReasoningSettingConstantInsideEachPairedIntervention() {
        assertThat(ThinkingDiagnosticProtocol.PAIRED_ARMS).hasSize(6);
        for (List<String> pair : ThinkingDiagnosticProtocol.PAIRED_ARMS) {
            ThinkingDiagnosticArm first = ThinkingDiagnosticProtocol.requireArm(pair.get(0));
            ThinkingDiagnosticArm second = ThinkingDiagnosticProtocol.requireArm(pair.get(1));

            assertThat(first.modelRole()).isEqualTo(second.modelRole());
            assertThat(first.maxOutputTokens()).isEqualTo(second.maxOutputTokens());
            assertThat(first.executionBoundary()).isEqualTo(second.executionBoundary());
            assertThat(first.reasoningPolicy()).isNotEqualTo(second.reasoningPolicy());
        }
        assertThat(ThinkingDiagnosticProtocol.BOUNDARY_PAIRS).hasSize(3);
    }

    @Test
    void schedulesEveryTrackedFixtureOncePerArmInCatalogOrder() {
        LocalFactCheckFixtureCatalog catalog = ThinkingDiagnosticTestSupport.catalog();
        List<ThinkingDiagnosticScheduleEntry> schedule = ThinkingDiagnosticProtocol.schedule(catalog);

        assertThat(schedule).hasSize(ThinkingDiagnosticProtocol.ROW_COUNT);
        assertThat(ThinkingDiagnosticProtocol.ROW_COUNT)
                .isEqualTo(7 * LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(schedule).extracting(ThinkingDiagnosticScheduleEntry::sequence)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, schedule.size()).boxed().toList());
        assertThat(schedule).allSatisfy(entry ->
                assertThat(entry.seed()).isEqualTo(ThinkingDiagnosticProtocol.SEED));

        List<String> firstArmFixtures = schedule.stream()
                .filter(entry -> entry.armId().equals("fact-check-subject-provider-default-64"))
                .map(ThinkingDiagnosticScheduleEntry::fixtureId)
                .toList();
        assertThat(firstArmFixtures).containsExactlyElementsOf(
                catalog.fixtures().stream().map(fixture -> fixture.id()).toList());
    }

    @Test
    void refusesAnImplicitOrUnregisteredProviderDefaultPolicy() {
        assertThatThrownBy(() -> new ThinkingDiagnosticArm(
                "inherited", ThinkingDiagnosticModelRole.SUBJECT,
                ChatReasoningPolicy.PROVIDER_DEFAULT,
                ThinkingDiagnosticExecutionBoundary.CHAT_INVOCATION, 64, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicitly measured pre-registered condition");
        assertThatThrownBy(() -> ThinkingDiagnosticProtocol.requireArm("unregistered-default"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown diagnostic arm");
    }

    @Test
    void restrictsNewDiagnosticEvidenceToTheDurableSuiteRoot() {
        assertThat(ThinkingDiagnosticRunner.resolveNewOutputDirectory(
                "local/evidence/thinking-diagnostic/2026-09-03-thinking").getFileName().toString())
                .isEqualTo("2026-09-03-thinking");
        assertThatThrownBy(() -> ThinkingDiagnosticRunner.resolveNewOutputDirectory(
                "build/thinking-diagnostic/2026-09-03-thinking"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/thinking-diagnostic");
        assertThatThrownBy(() -> ThinkingDiagnosticRunner.resolveNewOutputDirectory(
                "local/evidence/thinking-diagnostic/not-dated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }
}
