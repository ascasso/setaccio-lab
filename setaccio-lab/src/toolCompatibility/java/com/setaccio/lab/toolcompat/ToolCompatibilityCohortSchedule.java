package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable model-major sequential schedule for one explicitly resolved cohort. */
record ToolCompatibilityCohortSchedule(
        String id,
        int version,
        String ollamaRuntimeVersion,
        List<ToolCompatibilityCohortModelIdentity> orderedModels,
        List<Entry> entries,
        String sha256
) {

    static final String ID = "tool-compatibility-cohort-execution";
    static final int VERSION = 1;

    ToolCompatibilityCohortSchedule {
        if (!ID.equals(id) || version != VERSION) {
            throw new IllegalArgumentException("cohort schedule identity drifted");
        }
        if (ollamaRuntimeVersion == null
                || ollamaRuntimeVersion.isBlank()
                || !ollamaRuntimeVersion.equals(ollamaRuntimeVersion.strip())) {
            throw new IllegalArgumentException("cohort schedule requires one Ollama runtime version");
        }
        orderedModels = List.copyOf(orderedModels == null ? List.of() : orderedModels);
        entries = List.copyOf(entries == null ? List.of() : entries);
        requireOrderedModels(orderedModels);
        if (!expectedEntries(orderedModels).equals(entries)) {
            throw new IllegalArgumentException("cohort schedule content or order drifted");
        }
        String expectedSha256 = canonicalSha256(
                id, version, ollamaRuntimeVersion, orderedModels, entries);
        if (!expectedSha256.equals(sha256)) {
            throw new IllegalArgumentException("cohort schedule digest does not match its contents");
        }
    }

    static ToolCompatibilityCohortSchedule create(
            String ollamaRuntimeVersion,
            List<ToolCompatibilityCohortModelIdentity> orderedModels
    ) {
        List<ToolCompatibilityCohortModelIdentity> models = List.copyOf(orderedModels);
        List<Entry> entries = expectedEntries(models);
        return new ToolCompatibilityCohortSchedule(
                ID,
                VERSION,
                ollamaRuntimeVersion,
                models,
                entries,
                canonicalSha256(ID, VERSION, ollamaRuntimeVersion, models, entries));
    }

    List<Entry> entriesFor(ToolCompatibilityCohortModelIdentity modelIdentity) {
        if (modelIdentity == null || !orderedModels.contains(modelIdentity)) {
            throw new IllegalArgumentException("model identity is not part of the cohort schedule");
        }
        return entries.stream()
                .filter(entry -> entry.modelPosition() == modelIdentity.cohortPosition())
                .toList();
    }

    void requireBoundTo(ToolCompatibilityCohortPreflight.Prepared prepared) {
        if (prepared == null
                || !ollamaRuntimeVersion.equals(prepared.ollamaRuntimeVersion())
                || !orderedModels.equals(prepared.orderedModels())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Cohort schedule drifted from the resolved preflight");
        }
    }

    static void requireOrderedModels(
            List<ToolCompatibilityCohortModelIdentity> orderedModels
    ) {
        List<ToolCompatibilityCohortModelIdentity> models =
                List.copyOf(orderedModels == null ? List.of() : orderedModels);
        if (models.size() < 2) {
            throw new IllegalArgumentException(
                    "cohort requires at least one peer and one reference");
        }
        Set<String> tags = new HashSet<>();
        Set<String> digests = new HashSet<>();
        for (int index = 0; index < models.size(); index++) {
            ToolCompatibilityCohortModelIdentity model = models.get(index);
            ToolCompatibilityCohortModelIdentity.Role expectedRole =
                    index == models.size() - 1
                            ? ToolCompatibilityCohortModelIdentity.Role.REFERENCE
                            : ToolCompatibilityCohortModelIdentity.Role.PEER;
            if (model.cohortPosition() != index + 1 || model.role() != expectedRole) {
                throw new IllegalArgumentException(
                        "cohort model positions and peer/reference roles must be ordered");
            }
            if (!tags.add(model.effectiveInstalledTag())) {
                throw new IllegalArgumentException(
                        "cohort model identities contain a duplicate installed tag");
            }
            if (!digests.add(model.digest())) {
                throw new IllegalArgumentException(
                        "cohort model identities contain duplicate model bytes");
            }
        }
    }

    private static List<Entry> expectedEntries(
            List<ToolCompatibilityCohortModelIdentity> orderedModels
    ) {
        List<ToolCompatibilityCohortModelIdentity> models =
                List.copyOf(orderedModels == null ? List.of() : orderedModels);
        List<ToolCompatibilityCaseSelection.ScheduledCase> perModel =
                ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(),
                        ToolCompatibilityProtocol.runSettings());
        List<Entry> expected = new ArrayList<>(models.size() * perModel.size());
        int globalSequence = 1;
        for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
            ToolCompatibilityCohortModelIdentity model = models.get(modelIndex);
            int expectedPosition = modelIndex + 1;
            if (model.cohortPosition() != expectedPosition) {
                throw new IllegalArgumentException(
                        "cohort model positions must be complete and ordered");
            }
            for (ToolCompatibilityCaseSelection.ScheduledCase scheduled : perModel) {
                Integer effectiveSeed = model.seedSemantics()
                        == ToolCompatibilityCohortSeedSemantics.SUPPORTED
                        ? scheduled.seed()
                        : null;
                expected.add(new Entry(
                        globalSequence++,
                        model.cohortPosition(),
                        model.role(),
                        model.digest(),
                        scheduled.sequence(),
                        scheduled.caseId(),
                        scheduled.repetition(),
                        scheduled.seed(),
                        effectiveSeed));
            }
        }
        return List.copyOf(expected);
    }

    private static String canonicalSha256(
            String id,
            int version,
            String runtimeVersion,
            List<ToolCompatibilityCohortModelIdentity> models,
            List<Entry> entries
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(id).append('\n')
                .append(version).append('\n')
                .append(runtimeVersion).append('\n');
        for (ToolCompatibilityCohortModelIdentity model : models) {
            canonical.append(model.cohortPosition()).append('\t')
                    .append(model.role()).append('\t')
                    .append(model.requestedTag()).append('\t')
                    .append(model.effectiveInstalledTag()).append('\t')
                    .append(model.digest()).append('\t')
                    .append(model.seedSemantics()).append('\n');
            appendMetadata(canonical, model.metadata());
        }
        for (Entry entry : entries) {
            canonical.append(entry.globalSequence()).append('\t')
                    .append(entry.modelPosition()).append('\t')
                    .append(entry.modelRole()).append('\t')
                    .append(entry.modelDigest()).append('\t')
                    .append(entry.modelSequence()).append('\t')
                    .append(entry.caseId()).append('\t')
                    .append(entry.repetition()).append('\t')
                    .append(entry.requestedSeed()).append('\t')
                    .append(entry.effectiveSeed() == null ? "unavailable" : entry.effectiveSeed())
                    .append('\n');
        }
        return EvidenceIntegrity.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendMetadata(
            StringBuilder canonical,
            ToolCompatibilityCohortModelMetadata metadata
    ) {
        appendMetadataField(canonical, metadata.sizeBytes());
        appendMetadataField(canonical, metadata.familyProvenance());
        appendMetadataField(canonical, metadata.artifactRuntimeFormat());
        appendMetadataField(canonical, metadata.quantizationOrPrecision());
        appendMetadataField(canonical, metadata.templateFingerprint());
        appendMetadataField(canonical, metadata.defaultSystemPromptFingerprint());
        appendMetadataField(canonical, metadata.toolCapability());
        appendMetadataField(canonical, metadata.thinkingMode());
    }

    private static void appendMetadataField(
            StringBuilder canonical,
            ToolCompatibilityMetadataField field
    ) {
        canonical.append(field.availability()).append('\t')
                .append(field.value() == null ? "unavailable" : field.value())
                .append('\n');
    }

    record Entry(
            int globalSequence,
            int modelPosition,
            ToolCompatibilityCohortModelIdentity.Role modelRole,
            String modelDigest,
            int modelSequence,
            String caseId,
            int repetition,
            int requestedSeed,
            Integer effectiveSeed
    ) {

        Entry {
            if (globalSequence < 1
                    || modelPosition < 1
                    || modelSequence < 1
                    || modelSequence > ToolCompatibilityProtocol.ROW_COUNT) {
                throw new IllegalArgumentException("cohort schedule sequences must be positive");
            }
            if (modelRole == null
                    || modelDigest == null
                    || !modelDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("cohort schedule model identity is incomplete");
            }
            ToolCompatibilityCaseSelection.ScheduledCase scheduled =
                    ToolCompatibilityProtocol.schedule(
                                    ToolCompatibilityProtocol.caseSelection(),
                                    ToolCompatibilityProtocol.runSettings())
                            .get(modelSequence - 1);
            if (!scheduled.caseId().equals(caseId)
                    || scheduled.repetition() != repetition
                    || scheduled.seed() != requestedSeed
                    || (effectiveSeed != null && !Objects.equals(effectiveSeed, requestedSeed))) {
                throw new IllegalArgumentException(
                        "cohort schedule entry drifted from the per-model protocol");
            }
        }

        ToolCompatibilityCaseSelection.ScheduledCase scheduledCase() {
            return ToolCompatibilityProtocol.schedule(
                            ToolCompatibilityProtocol.caseSelection(),
                            ToolCompatibilityProtocol.runSettings())
                    .get(modelSequence - 1);
        }
    }
}
