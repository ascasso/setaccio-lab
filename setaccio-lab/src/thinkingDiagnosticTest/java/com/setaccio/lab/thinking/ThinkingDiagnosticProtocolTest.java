package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkingDiagnosticProtocolTest {

    @Test
    void locksTheFivePreRegisteredArmsInAFixedOrder() {
        assertThat(ThinkingDiagnosticProtocol.ARMS).extracting(ThinkingDiagnosticArm::armId)
                .containsExactly(
                        "subject-thinking-enabled-64",
                        "subject-thinking-disabled-64",
                        "subject-thinking-enabled-256",
                        "subject-thinking-disabled-256",
                        "control-thinking-disabled-64");
        assertThat(ThinkingDiagnosticProtocol.ARMS)
                .allSatisfy(arm -> assertThat(arm.reasoningPolicy())
                        .isNotEqualTo(ChatReasoningPolicy.PROVIDER_DEFAULT));
    }

    @Test
    void holdsEveryNonReasoningSettingConstantInsideEachPairedIntervention() {
        for (List<String> pair : ThinkingDiagnosticProtocol.PAIRED_ARMS) {
            ThinkingDiagnosticArm enabled = ThinkingDiagnosticProtocol.requireArm(pair.get(0));
            ThinkingDiagnosticArm disabled = ThinkingDiagnosticProtocol.requireArm(pair.get(1));

            assertThat(enabled.modelRole()).isEqualTo(disabled.modelRole());
            assertThat(enabled.maxOutputTokens()).isEqualTo(disabled.maxOutputTokens());
            assertThat(enabled.reasoningPolicy()).isEqualTo(ChatReasoningPolicy.ENABLED);
            assertThat(disabled.reasoningPolicy()).isEqualTo(ChatReasoningPolicy.DISABLED);
        }
    }

    @Test
    void schedulesEveryTrackedFixtureOncePerArmInCatalogOrder() {
        LocalFactCheckFixtureCatalog catalog = ThinkingDiagnosticTestSupport.catalog();
        List<ThinkingDiagnosticScheduleEntry> schedule = ThinkingDiagnosticProtocol.schedule(catalog);

        assertThat(schedule).hasSize(ThinkingDiagnosticProtocol.ROW_COUNT);
        assertThat(ThinkingDiagnosticProtocol.ROW_COUNT)
                .isEqualTo(5 * LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(schedule).extracting(ThinkingDiagnosticScheduleEntry::sequence)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, schedule.size()).boxed().toList());
        assertThat(schedule).allSatisfy(entry ->
                assertThat(entry.seed()).isEqualTo(ThinkingDiagnosticProtocol.SEED));

        List<String> firstArmFixtures = schedule.stream()
                .filter(entry -> entry.armId().equals("subject-thinking-enabled-64"))
                .map(ThinkingDiagnosticScheduleEntry::fixtureId)
                .toList();
        assertThat(firstArmFixtures).containsExactlyElementsOf(
                catalog.fixtures().stream().map(fixture -> fixture.id()).toList());
    }

    @Test
    void refusesAnArmThatWouldInheritTheModelsOwnReasoningDefault() {
        assertThatThrownBy(() -> new ThinkingDiagnosticArm(
                "inherited", ThinkingDiagnosticModelRole.SUBJECT,
                ChatReasoningPolicy.PROVIDER_DEFAULT, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit reasoning policy");
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
