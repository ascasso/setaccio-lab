package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSearchMatrixEvidenceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ToolSearchMatrixEvidence evidence = new ToolSearchMatrixEvidence(objectMapper);

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndDeterministicallyReanalyzesVersionOneEvidence() throws Exception {
        Fixture fixture = writeVersionOneFixture("2026-07-24-v1");
        EvidenceManifest manifest = new EvidenceManifestStore(objectMapper).read(fixture.runDirectory());

        assertThat(manifest.manifestVersion()).isEqualTo(1);
        assertThat(manifest.suite()).isEqualTo(ToolSearchMatrixProtocol.SUITE);
        assertThat(manifest.executionEngine()).isEqualTo(ToolSearchMatrixProtocol.EXECUTION_ENGINE);
        assertThat(manifest.artifacts())
                .extracting(artifact -> artifact.role())
                .containsExactly(ToolSearchMatrixEvidence.RAW_ROLE, ToolSearchMatrixEvidence.SUMMARY_ROLE);
        assertThat(evidence.verify(fixture.runDirectory()).valid()).isTrue();

        Path summary = fixture.runDirectory().resolve(ToolSearchMatrixEvidence.SUMMARY_FILENAME);
        String expected = Files.readString(summary, StandardCharsets.UTF_8);
        Files.delete(summary);

        ToolSearchMatrixEvidence.OfflineResult first = evidence.reanalyze(fixture.runDirectory());
        String firstRegeneration = Files.readString(summary, StandardCharsets.UTF_8);
        ToolSearchMatrixEvidence.OfflineResult second = evidence.reanalyze(fixture.runDirectory());

        assertThat(first.valid()).isTrue();
        assertThat(second.valid()).isTrue();
        assertThat(first.manifestFormat()).isEqualTo(ToolSearchMatrixEvidence.ManifestFormat.VERSION_1);
        assertThat(firstRegeneration).isEqualTo(expected);
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(firstRegeneration);
    }

    @Test
    void rejectsTamperedRawEvidenceWithoutChangingTheSummary() throws Exception {
        Fixture fixture = writeVersionOneFixture("2026-07-24-tampered");
        Path summary = fixture.runDirectory().resolve(ToolSearchMatrixEvidence.SUMMARY_FILENAME);
        String originalSummary = Files.readString(summary, StandardCharsets.UTF_8);
        Files.writeString(fixture.rawJson(), "\n{\"tampered\":true}\n", StandardCharsets.UTF_8);

        ToolSearchMatrixEvidence.OfflineResult verification = evidence.verify(fixture.runDirectory());
        ToolSearchMatrixEvidence.OfflineResult reanalysis = evidence.reanalyze(fixture.runDirectory());

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failures()).anyMatch(failure -> failure.contains("SHA-256"));
        assertThat(reanalysis.valid()).isFalse();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(originalSummary);
    }

    @Test
    void rejectsMissingRawEvidence() throws Exception {
        Fixture fixture = writeVersionOneFixture("2026-07-24-missing");
        Files.delete(fixture.rawJson());

        ToolSearchMatrixEvidence.OfflineResult verification = evidence.verify(fixture.runDirectory());
        ToolSearchMatrixEvidence.OfflineResult reanalysis = evidence.reanalyze(fixture.runDirectory());

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failures()).anyMatch(failure -> failure.contains("missing"));
        assertThat(reanalysis.valid()).isFalse();
    }

    @Test
    void rejectsLockedProtocolDriftInTheManifest() throws Exception {
        Fixture fixture = writeVersionOneFixture("2026-07-24-drift");
        Path manifestPath = fixture.runDirectory().resolve(EvidenceManifestStore.MANIFEST_FILENAME);
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(manifestPath.toFile());
        ArrayNode models = (ArrayNode) manifest.path("settings").path("models");
        models.set(0, objectMapper.getNodeFactory().textNode("drifted:model"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);

        ToolSearchMatrixEvidence.OfflineResult verification = evidence.verify(fixture.runDirectory());

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failures())
                .contains("Tool Search manifest setting drifted: models.");
    }

    @Test
    void readsLegacyVersionZeroAndRegeneratesItsSummaryOffline() throws Exception {
        Path runDirectory = Files.createDirectory(temporaryDirectory.resolve("2026-07-12-legacy"));
        ToolBenchmarkComparisonResult result = fixtureResult();
        Path rawJson = writeRaw(runDirectory, result);
        String rawSha256 = EvidenceIntegrity.sha256(rawJson);
        writeLegacyManifest(runDirectory, rawJson, rawSha256, result);

        ToolSearchMatrixAnalyzer.MatrixAnalysis analysis = analyze(result);
        String expectedSummary = new ToolSearchMatrixReport(objectMapper)
                .render(analysis, rawJson.getFileName().toString(), rawSha256);

        assertThat(evidence.verify(runDirectory).valid()).isFalse();

        ToolSearchMatrixEvidence.OfflineResult reanalysis = evidence.reanalyze(runDirectory);

        assertThat(reanalysis.valid()).isTrue();
        assertThat(reanalysis.manifestFormat()).isEqualTo(ToolSearchMatrixEvidence.ManifestFormat.LEGACY_V0);
        assertThat(Files.readString(
                runDirectory.resolve(ToolSearchMatrixEvidence.SUMMARY_FILENAME),
                StandardCharsets.UTF_8)).isEqualTo(expectedSummary);
        assertThat(evidence.verify(runDirectory).valid()).isTrue();
    }

    private Fixture writeVersionOneFixture(String runId) throws Exception {
        Path runDirectory = Files.createDirectory(temporaryDirectory.resolve(runId));
        ToolBenchmarkComparisonResult result = fixtureResult();
        Path rawJson = writeRaw(runDirectory, result);
        evidence.writeVersionOne(runDirectory, rawJson, result, analyze(result));
        return new Fixture(runDirectory, rawJson);
    }

    private Path writeRaw(Path runDirectory, ToolBenchmarkComparisonResult result) throws Exception {
        Path rawJson = runDirectory.resolve("fixture-tool-calling-comparison.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(rawJson.toFile(), result);
        return rawJson;
    }

    private ToolSearchMatrixAnalyzer.MatrixAnalysis analyze(ToolBenchmarkComparisonResult result) {
        return new ToolSearchMatrixAnalyzer(objectMapper).analyze(
                result,
                ToolSearchMatrixProtocol.MODELS,
                ToolSearchMatrixProtocol.canonicalPrompts(),
                ToolSearchMatrixProtocol.toolNames());
    }

    private void writeLegacyManifest(
            Path runDirectory,
            Path rawJson,
            String rawSha256,
            ToolBenchmarkComparisonResult result) throws Exception {
        List<ToolBenchmarkPrompt> prompts = ToolSearchMatrixProtocol.canonicalPrompts();
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", Instant.EPOCH.toString());
        manifest.put("gitCommit", "fixture");
        manifest.put("workingTreeDirty", false);
        manifest.put("springAiVersion", "2.0.0");
        manifest.putAll(ToolSearchMatrixProtocol.manifestSettings(
                objectMapper, prompts, ToolSearchMatrixProtocol.toolNames(), result));
        manifest.put("rawJson", rawJson.getFileName().toString());
        manifest.put("rawJsonSha256", rawSha256);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                runDirectory.resolve(EvidenceManifestStore.MANIFEST_FILENAME).toFile(),
                manifest);
    }

    private ToolBenchmarkComparisonResult fixtureResult() {
        Instant startedAt = Instant.parse("2026-07-24T00:00:00Z");
        Instant finishedAt = startedAt.plusSeconds(1);
        List<String> tools = ToolSearchMatrixProtocol.toolNames();
        List<ToolBenchmarkRow> standardRows = rows(AdvisorMode.STANDARD, tools);
        List<ToolBenchmarkRow> toolSearchRows = rows(AdvisorMode.TOOL_SEARCH, tools);
        return new ToolBenchmarkComparisonResult(
                "tool-calling-comparison",
                "ollama",
                ToolSearchMatrixProtocol.INDEX_TYPE,
                startedAt,
                finishedAt,
                "offline-fixture",
                "http://localhost:11434",
                ToolSearchMatrixProtocol.SETTINGS,
                ToolSearchMatrixProtocol.EXECUTION_STRATEGY,
                tools,
                tools,
                result(AdvisorMode.STANDARD, standardRows, startedAt, finishedAt, tools),
                result(AdvisorMode.TOOL_SEARCH, toolSearchRows, startedAt, finishedAt, tools));
    }

    private ToolBenchmarkResult result(
            AdvisorMode mode,
            List<ToolBenchmarkRow> rows,
            Instant startedAt,
            Instant finishedAt,
            List<String> tools) {
        return new ToolBenchmarkResult(
                "tool-calling",
                "ollama",
                mode,
                startedAt,
                finishedAt,
                "offline-fixture",
                "http://localhost:11434",
                ToolSearchMatrixProtocol.SETTINGS,
                ToolSearchMatrixProtocol.EXECUTION_STRATEGY,
                tools,
                tools,
                rows);
    }

    private List<ToolBenchmarkRow> rows(AdvisorMode mode, List<String> tools) {
        List<ToolBenchmarkRow> rows = new ArrayList<>();
        for (String model : ToolSearchMatrixProtocol.MODELS) {
            for (ToolBenchmarkPrompt prompt : ToolSearchMatrixProtocol.canonicalPrompts()) {
                for (int repetition = 1; repetition <= ToolSearchMatrixProtocol.SETTINGS.repetitions(); repetition++) {
                    List<AdvisorMode> order = ToolSearchMatrixProtocol.SETTINGS
                            .comparisonOrder()
                            .modesFor(repetition);
                    rows.add(new ToolBenchmarkRow(
                            "ollama",
                            model,
                            prompt.id(),
                            prompt.text(),
                            prompt.expectation(),
                            mode,
                            repetition,
                            order.indexOf(mode) + 1,
                            model + "/" + prompt.id() + "/" + repetition,
                            ToolSearchMatrixProtocol.SETTINGS.seedFor(repetition),
                            tools,
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            true,
                            1L,
                            null,
                            null,
                            "offline fixture",
                            true,
                            null));
                }
            }
        }
        return List.copyOf(rows);
    }

    private record Fixture(Path runDirectory, Path rawJson) {}
}
