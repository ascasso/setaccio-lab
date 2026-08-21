package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Immutable, shared T2.2 interleaved execution identity for both prompt conditions. */
record ToolCompatibilityPairedSchedule(
        String id,
        int version,
        String promptCatalogId,
        int promptCatalogVersion,
        String promptCatalogSha256,
        List<Entry> entries,
        String sha256
) {

    static final String ID = "tool-compatibility-paired-execution";
    static final int VERSION = 1;
    static final String SHA256 = "e18f7d3a6c0701e4c2e84dddf92c1ff0b3824ed03bd0b2f359c8904443c434de";
    static final int ROW_COUNT = ToolCompatibilityProtocol.ROW_COUNT * 2;

    ToolCompatibilityPairedSchedule {
        id = requireText(id, "id");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        promptCatalogId = requireText(promptCatalogId, "promptCatalogId");
        if (promptCatalogVersion < 1) {
            throw new IllegalArgumentException("promptCatalogVersion must be positive");
        }
        promptCatalogSha256 = requireSha256(promptCatalogSha256, "promptCatalogSha256");
        entries = List.copyOf(entries == null ? List.of() : entries);
        sha256 = requireSha256(sha256, "sha256");
        if (!sha256.equals(canonicalSha256(
                id, version, promptCatalogId, promptCatalogVersion, promptCatalogSha256, entries))) {
            throw new IllegalArgumentException("paired execution schedule SHA-256 does not match its contents");
        }
        requireLocked(id, version, promptCatalogId, promptCatalogVersion, promptCatalogSha256, entries, sha256);
    }

    static ToolCompatibilityPairedSchedule locked() {
        ToolCompatibilitySystemPromptCatalog catalog = ToolCompatibilityProtocol.systemPromptCatalog();
        List<Entry> entries = expectedEntries();
        return new ToolCompatibilityPairedSchedule(
                ID,
                VERSION,
                catalog.id(),
                catalog.version(),
                catalog.sha256(),
                entries,
                canonicalSha256(
                        ID, VERSION, catalog.id(), catalog.version(), catalog.sha256(), entries));
    }

    List<Entry> entriesFor(ToolCompatibilityPromptCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("prompt condition must not be null");
        }
        return entries.stream()
                .filter(entry -> entry.condition() == condition)
                .sorted(java.util.Comparator.comparingInt(Entry::conditionSequence))
                .toList();
    }

    Entry requireEntry(ToolCompatibilityPromptCondition condition, int conditionSequence) {
        return entriesFor(condition).stream()
                .filter(entry -> entry.conditionSequence() == conditionSequence)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No paired schedule entry for " + condition + " row " + conditionSequence));
    }

    static String canonicalSha256(
            String id,
            int version,
            String promptCatalogId,
            int promptCatalogVersion,
            String promptCatalogSha256,
            List<Entry> entries
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(id).append('\n')
                .append(version).append('\n')
                .append(promptCatalogId).append('\n')
                .append(promptCatalogVersion).append('\n')
                .append(promptCatalogSha256).append('\n');
        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            canonical.append(entry.globalPairSequence()).append('\t')
                    .append(entry.conditionSequence()).append('\t')
                    .append(entry.caseId()).append('\t')
                    .append(entry.repetition()).append('\t')
                    .append(entry.seed()).append('\t')
                    .append(entry.condition().wireValue()).append('\t')
                    .append(entry.conditionExecutionPosition().wireValue()).append('\n');
        }
        return EvidenceIntegrity.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    void requireLocked() {
        requireLocked(id, version, promptCatalogId, promptCatalogVersion, promptCatalogSha256, entries, sha256);
    }

    private static void requireLocked(
            String id,
            int version,
            String promptCatalogId,
            int promptCatalogVersion,
            String promptCatalogSha256,
            List<Entry> entries,
            String sha256
    ) {
        ToolCompatibilitySystemPromptCatalog catalog = ToolCompatibilityProtocol.systemPromptCatalog();
        if (!ID.equals(id)
                || VERSION != version
                || !catalog.id().equals(promptCatalogId)
                || catalog.version() != promptCatalogVersion
                || !catalog.sha256().equals(promptCatalogSha256)
                || !SHA256.equals(sha256)) {
            throw new IllegalArgumentException("paired execution schedule identity drifted");
        }
        if (!expectedEntries().equals(entries) || entries.size() != ROW_COUNT) {
            throw new IllegalArgumentException("paired execution schedule content or order drifted");
        }
    }

    private static List<Entry> expectedEntries() {
        List<ToolCompatibilityCaseSelection.ScheduledCase> perCondition = ToolCompatibilityProtocol.schedule(
                ToolCompatibilityProtocol.caseSelection(), ToolCompatibilityProtocol.runSettings());
        List<Entry> expected = new ArrayList<>(ROW_COUNT);
        int globalPairSequence = 1;
        int caseCount = ToolCompatibilityProtocol.CASE_IDS.size();
        for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
            ToolCompatibilityCaseSelection.ScheduledCase firstRepetition = perCondition.get(caseIndex);
            ToolCompatibilityCaseSelection.ScheduledCase secondRepetition = perCondition.get(caseIndex + caseCount);
            expected.add(Entry.from(
                    globalPairSequence++, firstRepetition, ToolCompatibilityPromptCondition.UNTREATED,
                    ToolCompatibilityConditionExecutionPosition.FIRST));
            expected.add(Entry.from(
                    globalPairSequence++, firstRepetition, ToolCompatibilityPromptCondition.PROMPTED,
                    ToolCompatibilityConditionExecutionPosition.SECOND));
            expected.add(Entry.from(
                    globalPairSequence++, secondRepetition, ToolCompatibilityPromptCondition.PROMPTED,
                    ToolCompatibilityConditionExecutionPosition.FIRST));
            expected.add(Entry.from(
                    globalPairSequence++, secondRepetition, ToolCompatibilityPromptCondition.UNTREATED,
                    ToolCompatibilityConditionExecutionPosition.SECOND));
        }
        return List.copyOf(expected);
    }

    private static ToolCompatibilityCaseSelection.ScheduledCase scheduledCaseFor(int conditionSequence) {
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule = ToolCompatibilityProtocol.schedule(
                ToolCompatibilityProtocol.caseSelection(), ToolCompatibilityProtocol.runSettings());
        if (conditionSequence < 1 || conditionSequence > schedule.size()) {
            throw new IllegalArgumentException("condition sequence is outside the locked schedule");
        }
        return schedule.get(conditionSequence - 1);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }

    private static String requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a full lowercase SHA-256 digest");
        }
        return value;
    }

    record Entry(
            int globalPairSequence,
            int conditionSequence,
            String caseId,
            int repetition,
            int seed,
            ToolCompatibilityPromptCondition condition,
            ToolCompatibilityConditionExecutionPosition conditionExecutionPosition
    ) {

        Entry {
            if (globalPairSequence < 1) {
                throw new IllegalArgumentException("globalPairSequence must be positive");
            }
            ToolCompatibilityCaseSelection.ScheduledCase scheduled = scheduledCaseFor(conditionSequence);
            if (!scheduled.caseId().equals(caseId)
                    || scheduled.repetition() != repetition
                    || scheduled.seed() != seed) {
                throw new IllegalArgumentException("paired entry must match the locked per-condition schedule");
            }
            if (condition == null || conditionExecutionPosition == null) {
                throw new IllegalArgumentException("paired entry condition and execution position are required");
            }
        }

        static Entry from(
                int globalPairSequence,
                ToolCompatibilityCaseSelection.ScheduledCase scheduledCase,
                ToolCompatibilityPromptCondition condition,
                ToolCompatibilityConditionExecutionPosition position
        ) {
            if (scheduledCase == null) {
                throw new IllegalArgumentException("scheduled case must not be null");
            }
            return new Entry(
                    globalPairSequence,
                    scheduledCase.sequence(),
                    scheduledCase.caseId(),
                    scheduledCase.repetition(),
                    scheduledCase.seed(),
                    condition,
                    position);
        }

        ToolCompatibilityCaseSelection.ScheduledCase scheduledCase() {
            return scheduledCaseFor(conditionSequence);
        }
    }
}
