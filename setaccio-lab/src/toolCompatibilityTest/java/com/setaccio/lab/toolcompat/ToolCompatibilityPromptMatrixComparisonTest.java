package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceArtifact;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceFrameworkVersions;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPromptMatrixComparisonTest {

    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityPromptMatrixEvidence evidence =
            new ToolCompatibilityPromptMatrixEvidence(OBJECT_MAPPER);
    private final ToolCompatibilityPromptMatrixComparison comparison =
            new ToolCompatibilityPromptMatrixComparison(OBJECT_MAPPER);

    @Test
    void acceptsOneVerifiedPairWithExpectedDifferentPositionsAndObservedOutcomes() throws Exception {
        Pair pair = writePair(
                "valid",
                ToolCompatibilityPromptMatrixTestFixtures.result(
                        ToolCompatibilityPromptCondition.UNTREATED),
                promptedResultWithAnObservedProviderFailure(),
                CLEAN_BASELINE,
                CLEAN_BASELINE);

        ToolCompatibilityRow untreatedFirst = pair.baseline().result().rows().getFirst();
        ToolCompatibilityRow promptedFirst = pair.candidate().result().rows().getFirst();
        ToolCompatibilityRow untreatedSecondRepetition = pair.baseline().result().rows().get(8);
        ToolCompatibilityRow promptedSecondRepetition = pair.candidate().result().rows().get(8);
        assertThat(untreatedFirst.globalPairSequence()).isEqualTo(1);
        assertThat(untreatedFirst.conditionExecutionPosition())
                .isEqualTo(ToolCompatibilityConditionExecutionPosition.FIRST);
        assertThat(promptedFirst.globalPairSequence()).isEqualTo(2);
        assertThat(promptedFirst.conditionExecutionPosition())
                .isEqualTo(ToolCompatibilityConditionExecutionPosition.SECOND);
        assertThat(untreatedSecondRepetition.globalPairSequence()).isEqualTo(4);
        assertThat(untreatedSecondRepetition.conditionExecutionPosition())
                .isEqualTo(ToolCompatibilityConditionExecutionPosition.SECOND);
        assertThat(promptedSecondRepetition.globalPairSequence()).isEqualTo(3);
        assertThat(promptedSecondRepetition.conditionExecutionPosition())
                .isEqualTo(ToolCompatibilityConditionExecutionPosition.FIRST);
        assertThat(pair.baseline().result().rows().getFirst().providerTurns())
                .isNotEqualTo(pair.candidate().result().rows().getFirst().providerTurns());

        ToolCompatibilityPromptMatrixComparison.ComparisonResult result = comparison.compare(
                pair.baseline().directory(), pair.candidate().directory());

        assertThat(result.baselineRunId()).isEqualTo(pair.baseline().directory().getFileName().toString());
        assertThat(result.candidateRunId()).isEqualTo(pair.candidate().directory().getFileName().toString());
        assertThat(result.pairedScheduleSha256()).isEqualTo(ToolCompatibilityPairedSchedule.SHA256);
        assertThat(result.pairedRowCount()).isEqualTo(ToolCompatibilityProtocol.ROW_COUNT);
    }

    @Test
    void rejectsValidModelFrameworkAndGitParityMismatches() throws Exception {
        ToolCompatibilityModelIdentity differentDigest = new ToolCompatibilityModelIdentity(
                ToolCompatibilityProtocol.INITIAL_MODEL,
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "d".repeat(64));
        Pair differentModel = writePair(
                "different-model",
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                ToolCompatibilityPromptMatrixTestFixtures.result(
                        ToolCompatibilityPromptCondition.PROMPTED, differentDigest),
                CLEAN_BASELINE,
                CLEAN_BASELINE);
        assertThatThrownBy(() -> comparison.compare(
                differentModel.baseline().directory(), differentModel.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model digest or ordered model identity differs");

        ToolCompatibilityModelIdentity differentEffectiveModel = new ToolCompatibilityModelIdentity(
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "same-digest-different-effective-model",
                ToolCompatibilityAnalysisTestFixtures.MODEL_IDENTITY.digest());
        Pair differentEffectiveIdentity = writePair(
                "different-effective-model",
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                ToolCompatibilityPromptMatrixTestFixtures.result(
                        ToolCompatibilityPromptCondition.PROMPTED, differentEffectiveModel),
                CLEAN_BASELINE,
                CLEAN_BASELINE);
        assertThatThrownBy(() -> comparison.compare(
                differentEffectiveIdentity.baseline().directory(),
                differentEffectiveIdentity.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model digest or ordered model identity differs");

        Pair differentFramework = writePair("different-framework");
        EvidenceManifest frameworkManifest = readManifest(differentFramework.candidate());
        rewriteManifest(
                differentFramework.candidate(),
                new EvidenceManifest(
                        frameworkManifest.manifestVersion(),
                        frameworkManifest.suite(),
                        frameworkManifest.runId(),
                        frameworkManifest.generatedAt(),
                        frameworkManifest.codeBaseline(),
                        new EvidenceFrameworkVersions(
                                "different-spring-boot",
                                frameworkManifest.frameworkVersions().springAi()),
                        frameworkManifest.executionEngine(),
                        frameworkManifest.settings(),
                        frameworkManifest.artifacts()));
        assertThat(evidence.verify(differentFramework.candidate().directory()).valid()).isTrue();
        assertThatThrownBy(() -> comparison.compare(
                differentFramework.baseline().directory(), differentFramework.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Spring Boot or Spring AI framework versions differ");

        Pair differentCommit = writePair(
                "different-commit",
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.PROMPTED),
                CLEAN_BASELINE,
                new EvidenceCodeBaseline("b".repeat(40), false));
        assertThatThrownBy(() -> comparison.compare(
                differentCommit.baseline().directory(), differentCommit.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Git commits differ");

        Pair dirtyWorktree = writePair(
                "dirty-worktree",
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.PROMPTED),
                CLEAN_BASELINE,
                new EvidenceCodeBaseline(CLEAN_BASELINE.gitCommit(), true));
        assertThatThrownBy(() -> comparison.compare(
                dirtyWorktree.baseline().directory(), dirtyWorktree.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate run records a dirty Git worktree");

        EvidenceCodeBaseline incompleteCommit = new EvidenceCodeBaseline("unknown", false);
        Pair incompleteCommitPair = writePair(
                "incomplete-commit",
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.PROMPTED),
                incompleteCommit,
                incompleteCommit);
        assertThatThrownBy(() -> comparison.compare(
                incompleteCommitPair.baseline().directory(), incompleteCommitPair.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseline run does not record a full Git commit")
                .hasMessageContaining("candidate run does not record a full Git commit");
    }

    @Test
    void rejectsReversedPromptRolesBeforeAnySemanticComparison() throws Exception {
        Pair reversed = writePair(
                "reversed",
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.PROMPTED),
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                CLEAN_BASELINE,
                CLEAN_BASELINE);

        assertThatThrownBy(() -> comparison.compare(
                reversed.baseline().directory(), reversed.candidate().directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseline run must carry the untreated prompt condition")
                .hasMessageContaining("candidate run must carry the prompted prompt condition");
    }

    @Test
    void rejectsEveryLockedRawProtocolTamperBeforeComparison() throws Exception {
        List<RawTamper> tampers = List.of(
                new RawTamper("model digest", raw -> object(raw, "modelIdentity")
                        .put("digest", "e".repeat(64))),
                new RawTamper("case IDs", raw -> array(raw, "orderedCaseIds")
                        .set(0, TextNode.valueOf("different-case-id"))),
                new RawTamper("case order", raw -> swap(array(raw, "orderedCaseIds"), 0, 1)),
                new RawTamper("repetition count", raw -> object(raw, "runSettings")
                        .put("repetitions", 3)),
                new RawTamper("seeds", raw -> object(raw, "runSettings")
                        .withArray("seeds").set(1, OBJECT_MAPPER.getNodeFactory().numberNode(44))),
                new RawTamper("temperature", raw -> object(raw, "runSettings")
                        .put("temperature", 0.25)),
                new RawTamper("output tokens", raw -> object(raw, "runSettings")
                        .put("maxOutputTokensPerProviderTurn", 256)),
                new RawTamper("row timeout", raw -> object(raw, "runSettings")
                        .put("rowTimeoutMillis", 60_000)),
                new RawTamper("logical attempts", raw -> object(raw, "runSettings")
                        .put("logicalRowAttempts", 2)),
                new RawTamper("advisor mode", raw -> raw
                        .put("executionEngine", "different-advisor-mode")),
                new RawTamper("execution strategy", raw -> raw
                        .put("executionStrategy", "parallel")),
                new RawTamper("tool catalog identity", raw -> raw
                        .put("toolDefinitionsSha256", "f".repeat(64))),
                new RawTamper("semantic call oracle identity", raw -> raw
                        .put("caseOracleSha256", "f".repeat(64))),
                new RawTamper("per-row provider-turn output policy", raw -> firstRow(raw)
                        .put("maxOutputTokensPerProviderTurn", 256)),
                new RawTamper("per-row deadline policy", raw -> firstRow(raw)
                        .put("rowAttemptDeadline", "PT1M")),
                new RawTamper("per-row attempt policy", raw -> firstRow(raw)
                        .put("attemptCount", 2)),
                new RawTamper("paired schedule identity", raw -> object(raw, "pairedExecutionSchedule")
                        .put("sha256", "f".repeat(64))),
                new RawTamper("missing paired position", raw -> firstRow(raw)
                        .remove("globalPairSequence")),
                new RawTamper("equal paired positions", raw -> {
                    ObjectNode row = firstRow(raw);
                    row.put("globalPairSequence", 1);
                    row.put("conditionExecutionPosition", "first");
                }),
                new RawTamper("schedule-inconsistent paired position", raw -> firstRow(raw)
                        .put("conditionExecutionPosition", "first")));

        for (int index = 0; index < tampers.size(); index++) {
            RawTamper tamper = tampers.get(index);
            Pair pair = writePair("raw-tamper-" + index);
            tamperRawResult(pair.candidate(), tamper.mutation());

            assertThatThrownBy(() -> comparison.compare(
                    pair.baseline().directory(), pair.candidate().directory()))
                    .as(tamper.name())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("The candidate run did not verify");
        }
    }

    private Pair writePair(String name) throws Exception {
        return writePair(
                name,
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.PROMPTED),
                CLEAN_BASELINE,
                CLEAN_BASELINE);
    }

    private Pair writePair(
            String name,
            ToolCompatibilityPromptMatrixResult baselineResult,
            ToolCompatibilityPromptMatrixResult candidateResult,
            EvidenceCodeBaseline baselineCodeBaseline,
            EvidenceCodeBaseline candidateCodeBaseline
    ) throws Exception {
        Run baseline = writeRun(
                name + "-baseline", baselineResult, baselineCodeBaseline);
        Run candidate = writeRun(
                name + "-candidate", candidateResult, candidateCodeBaseline);
        return new Pair(baseline, candidate);
    }

    private Run writeRun(
            String runId,
            ToolCompatibilityPromptMatrixResult result,
            EvidenceCodeBaseline codeBaseline
    ) throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(runId));
        evidence.write(directory, result, codeBaseline);
        return new Run(directory, result);
    }

    private ToolCompatibilityPromptMatrixResult promptedResultWithAnObservedProviderFailure() {
        ToolCompatibilityPairedSchedule pairedSchedule = ToolCompatibilityPairedSchedule.locked();
        ToolCompatibilitySystemPromptIdentity prompt = ToolCompatibilityPromptCondition.PROMPTED.prompt(
                ToolCompatibilityProtocol.systemPromptCatalog());
        List<ToolCompatibilityRow> rows = ToolCompatibilityAnalysisTestFixtures.schedule().stream()
                .map(scheduled -> ToolCompatibilityPromptMatrixTestFixtures.withPromptAndPair(
                        scheduled.sequence() == 1
                                ? ToolCompatibilityAnalysisTestFixtures.providerFailureRow(scheduled, 35)
                                : ToolCompatibilityAnalysisTestFixtures.successfulRow(
                                        scheduled, scheduled.sequence() * 10L),
                        prompt,
                        pairedSchedule.requireEntry(
                                ToolCompatibilityPromptCondition.PROMPTED, scheduled.sequence())))
                .toList();
        return ToolCompatibilityPromptMatrixResult.create(
                Instant.parse("2026-08-21T12:00:00Z"),
                Instant.parse("2026-08-21T13:00:00Z"),
                ToolCompatibilityAnalysisTestFixtures.MODEL_IDENTITY,
                ToolCompatibilityPromptCondition.PROMPTED,
                pairedSchedule,
                rows);
    }

    private EvidenceManifest readManifest(Run run) {
        return new EvidenceManifestStore(OBJECT_MAPPER).read(run.directory());
    }

    private void rewriteManifest(Run run, EvidenceManifest manifest) throws Exception {
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                run.directory().resolve(EvidenceManifestStore.MANIFEST_FILENAME).toFile(), manifest);
    }

    private void tamperRawResult(Run run, Consumer<ObjectNode> mutation) throws Exception {
        Path raw = run.directory().resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME);
        ObjectNode json = (ObjectNode) OBJECT_MAPPER.readTree(raw.toFile());
        mutation.accept(json);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(raw.toFile(), json);
        EvidenceManifest manifest = readManifest(run);
        List<EvidenceArtifact> artifacts = manifest.artifacts().stream()
                .map(artifact -> ToolCompatibilityPromptMatrixEvidence.RAW_ROLE.equals(artifact.role())
                        ? EvidenceIntegrity.describe(run.directory(), raw, artifact.role())
                        : artifact)
                .toList();
        rewriteManifest(run, new EvidenceManifest(
                manifest.manifestVersion(),
                manifest.suite(),
                manifest.runId(),
                manifest.generatedAt(),
                manifest.codeBaseline(),
                manifest.frameworkVersions(),
                manifest.executionEngine(),
                manifest.settings(),
                artifacts));
    }

    private static ObjectNode object(ObjectNode parent, String field) {
        return (ObjectNode) parent.get(field);
    }

    private static ArrayNode array(ObjectNode parent, String field) {
        return (ArrayNode) parent.get(field);
    }

    private static ObjectNode firstRow(ObjectNode raw) {
        return (ObjectNode) array(raw, "rows").get(0);
    }

    private static void swap(ArrayNode values, int left, int right) {
        JsonNode replacement = values.get(left);
        values.set(left, values.get(right));
        values.set(right, replacement);
    }

    private record Pair(Run baseline, Run candidate) {}

    private record Run(Path directory, ToolCompatibilityPromptMatrixResult result) {}

    private record RawTamper(String name, Consumer<ObjectNode> mutation) {}
}
