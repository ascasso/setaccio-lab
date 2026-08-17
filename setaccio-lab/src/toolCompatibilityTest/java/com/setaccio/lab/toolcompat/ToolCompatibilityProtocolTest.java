package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
}
