package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityProtocolTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void selectsTheExactCanonicalCasesAndToolsWithoutDuplicatingThem() {
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();

        assertThat(selection.cases()).containsExactlyElementsOf(ToolBenchmarkCases.defaults());
        assertThat(selection.caseIds()).containsExactlyElementsOf(ToolCompatibilityProtocol.CASE_IDS);
        assertThat(selection.toolNames()).containsExactlyElementsOf(ToolBenchmarkCases.toolNames());
        assertThat(selection.canonicalCasesSha256()).matches("[0-9a-f]{64}");
        assertThat(selection.toolNamesSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void locksTheRunSettingsAndBuildsTheSixteenRowSequentialSchedule() {
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule = ToolCompatibilityProtocol.schedule(selection, settings);

        assertThat(settings.requestedModel()).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
        assertThat(settings.repetitions()).isEqualTo(2);
        assertThat(settings.temperature()).isZero();
        assertThat(settings.seeds()).containsExactly(42, 43);
        assertThat(settings.maxOutputTokensPerProviderTurn()).isEqualTo(512);
        assertThat(settings.rowTimeoutMillis()).isEqualTo(120_000L);
        assertThat(settings.logicalRowAttempts()).isOne();
        assertThat(schedule).hasSize(16);
        assertThat(schedule).extracting(ToolCompatibilityCaseSelection.ScheduledCase::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
        assertThat(schedule.subList(0, 8))
                .extracting(ToolCompatibilityCaseSelection.ScheduledCase::caseId)
                .containsExactlyElementsOf(ToolCompatibilityProtocol.CASE_IDS);
        assertThat(schedule.subList(8, 16))
                .extracting(ToolCompatibilityCaseSelection.ScheduledCase::caseId)
                .containsExactlyElementsOf(ToolCompatibilityProtocol.CASE_IDS);
        assertThat(schedule.subList(0, 8))
                .extracting(ToolCompatibilityCaseSelection.ScheduledCase::seed)
                .containsOnly(42);
        assertThat(schedule.subList(8, 16))
                .extracting(ToolCompatibilityCaseSelection.ScheduledCase::seed)
                .containsOnly(43);
    }

    @Test
    void rejectsRunSettingAndModelIdentityDrift() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilityRunSettings(
                ToolCompatibilityProtocol.INITIAL_MODEL,
                2,
                0.0,
                List.of(42, 43),
                256,
                120_000L,
                1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilityModelIdentity(
                "different:model",
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "a".repeat(64)));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilityModelIdentity(
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "resolved:model",
                "not-a-digest"));
    }

    @Test
    void loadsTheExactCaseOracleAndProtectsExpectedArgumentsFromMutation() throws Exception {
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();

        assertThat(oracle.id()).isEqualTo("tool-case-oracle");
        assertThat(oracle.version()).isOne();
        assertThat(oracle.sha256()).isEqualTo("9ccd612ace107b37a6f5a5c30e0cc77e80236a54afcb728566750c18035c1af4");
        assertThat(oracle.caseIds()).containsExactlyElementsOf(ToolCompatibilityProtocol.CASE_IDS);
        assertThat(oracle.requireCase("catalog-multi-step").calls())
                .extracting(ToolCompatibilityExpectedCall::toolName)
                .containsExactly("lab_lookup_catalog_item", "lab_list_catalog_items");
        assertThat(oracle.requireCase("no-applicable-domain-tool").calls()).isEmpty();

        ToolCompatibilityExpectedCall arithmetic = oracle.requireCase("arithmetic-add").calls().getFirst();
        JsonNode expectedArguments = objectMapper.readTree("{\"left\":17.25,\"right\":4.75}");
        assertThat(arithmetic.arguments()).isEqualTo(expectedArguments);

        ObjectNode mutableCopy = (ObjectNode) arithmetic.arguments();
        mutableCopy.put("left", 99);
        assertThat(arithmetic.arguments()).isEqualTo(expectedArguments);
    }

    @Test
    void rejectsCaseOracleByteAndDigestDrift() throws Exception {
        byte[] locked = lockedOracleBytes();
        byte[] byteDrift = Arrays.copyOf(locked, locked.length + 1);
        byteDrift[byteDrift.length - 1] = ' ';

        assertThat(EvidenceIntegrity.sha256(locked)).isEqualTo(ToolCompatibilityCaseOracle.SHA256);
        assertThat(ToolCompatibilityCaseOracle.parseLocked(locked).sha256())
                .isEqualTo(ToolCompatibilityCaseOracle.SHA256);
        assertThatThrownBy(() -> ToolCompatibilityCaseOracle.parseLocked(byteDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");
    }

    @Test
    void rejectsCaseOracleCaseIdToolNameAndCountDrift()
            throws Exception {
        byte[] caseIdDrift = mutatedOracle(root ->
                ((ObjectNode) root.path("cases").get(0)).put("caseId", "changed-case"));
        assertThatThrownBy(() -> ToolCompatibilityCaseOracle.parseLocked(caseIdDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");

        byte[] toolNameDrift = mutatedOracle(root ->
                ((ObjectNode) root.path("cases").get(0).path("calls").get(0))
                        .put("tool", "lab_unknown_tool"));
        assertThatThrownBy(() -> ToolCompatibilityCaseOracle.parseLocked(toolNameDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");

        byte[] countDrift = mutatedOracle(root -> {
            ArrayNode cases = (ArrayNode) root.path("cases");
            cases.remove(cases.size() - 1);
        });
        assertThatThrownBy(() -> ToolCompatibilityCaseOracle.parseLocked(countDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");
    }

    private byte[] mutatedOracle(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(lockedOracleBytes());
        mutation.accept(root);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private static byte[] lockedOracleBytes() throws Exception {
        try (InputStream input = ToolCompatibilityProtocolTest.class.getClassLoader()
                .getResourceAsStream(ToolCompatibilityCaseOracle.RESOURCE)) {
            if (input == null) {
                throw new AssertionError("Locked tool compatibility oracle resource is missing");
            }
            return input.readAllBytes();
        }
    }
}
