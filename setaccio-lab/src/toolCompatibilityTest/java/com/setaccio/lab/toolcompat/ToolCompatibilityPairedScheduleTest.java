package com.setaccio.lab.toolcompat;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class ToolCompatibilityPairedScheduleTest {

    @Test
    void locksTheExactCaseMajorAlternatingThirtyTwoRowOrder() {
        ToolCompatibilityPairedSchedule schedule = ToolCompatibilityPairedSchedule.locked();

        assertThat(schedule.id()).isEqualTo(ToolCompatibilityPairedSchedule.ID);
        assertThat(schedule.version()).isEqualTo(ToolCompatibilityPairedSchedule.VERSION);
        assertThat(schedule.sha256()).isEqualTo(ToolCompatibilityPairedSchedule.SHA256);
        assertThat(schedule.entries()).hasSize(32);
        assertThat(schedule.entries())
                .extracting(ToolCompatibilityPairedSchedule.Entry::globalPairSequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 32).boxed().toList());

        for (int caseIndex = 0; caseIndex < ToolCompatibilityProtocol.CASE_IDS.size(); caseIndex++) {
            String caseId = ToolCompatibilityProtocol.CASE_IDS.get(caseIndex);
            int firstSequence = caseIndex + 1;
            int secondSequence = firstSequence + ToolCompatibilityProtocol.CASE_IDS.size();
            assertThat(schedule.entries().subList(caseIndex * 4, caseIndex * 4 + 4))
                    .extracting(
                            ToolCompatibilityPairedSchedule.Entry::conditionSequence,
                            ToolCompatibilityPairedSchedule.Entry::caseId,
                            ToolCompatibilityPairedSchedule.Entry::repetition,
                            ToolCompatibilityPairedSchedule.Entry::seed,
                            ToolCompatibilityPairedSchedule.Entry::condition,
                            ToolCompatibilityPairedSchedule.Entry::conditionExecutionPosition)
                    .containsExactly(
                            tuple(firstSequence, caseId, 1, 42,
                                    ToolCompatibilityPromptCondition.UNTREATED,
                                    ToolCompatibilityConditionExecutionPosition.FIRST),
                            tuple(firstSequence, caseId, 1, 42,
                                    ToolCompatibilityPromptCondition.PROMPTED,
                                    ToolCompatibilityConditionExecutionPosition.SECOND),
                            tuple(secondSequence, caseId, 2, 43,
                                    ToolCompatibilityPromptCondition.PROMPTED,
                                    ToolCompatibilityConditionExecutionPosition.FIRST),
                            tuple(secondSequence, caseId, 2, 43,
                                    ToolCompatibilityPromptCondition.UNTREATED,
                                    ToolCompatibilityConditionExecutionPosition.SECOND));
        }

        assertThat(schedule.entriesFor(ToolCompatibilityPromptCondition.UNTREATED))
                .extracting(ToolCompatibilityPairedSchedule.Entry::conditionSequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
        assertThat(schedule.entriesFor(ToolCompatibilityPromptCondition.PROMPTED))
                .extracting(ToolCompatibilityPairedSchedule.Entry::conditionSequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
    }

    @Test
    void rejectsScheduleContentDriftEvenWhenItsDigestIsRecomputed() {
        ToolCompatibilityPairedSchedule locked = ToolCompatibilityPairedSchedule.locked();
        ArrayList<ToolCompatibilityPairedSchedule.Entry> drifted = new ArrayList<>(locked.entries());
        ToolCompatibilityPairedSchedule.Entry original = drifted.getFirst();
        drifted.set(0, new ToolCompatibilityPairedSchedule.Entry(
                original.globalPairSequence(),
                original.conditionSequence(),
                original.caseId(),
                original.repetition(),
                original.seed(),
                ToolCompatibilityPromptCondition.PROMPTED,
                original.conditionExecutionPosition()));

        assertThatThrownBy(() -> new ToolCompatibilityPairedSchedule(
                locked.id(),
                locked.version(),
                locked.promptCatalogId(),
                locked.promptCatalogVersion(),
                locked.promptCatalogSha256(),
                drifted,
                ToolCompatibilityPairedSchedule.canonicalSha256(
                        locked.id(),
                        locked.version(),
                        locked.promptCatalogId(),
                        locked.promptCatalogVersion(),
                        locked.promptCatalogSha256(),
                drifted)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity drifted");
    }
}
