package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import java.util.HashSet;
import java.util.List;

final class ToolCompatibilityCaseSelection {

    private static final ObjectMapper FINGERPRINT_MAPPER = JsonMapper.builder().build();

    private final List<ToolBenchmarkPrompt> cases;
    private final List<String> toolNames;
    private final String canonicalCasesSha256;
    private final String toolNamesSha256;

    private ToolCompatibilityCaseSelection(
            List<ToolBenchmarkPrompt> cases,
            List<String> toolNames,
            String canonicalCasesSha256,
            String toolNamesSha256) {
        this.cases = List.copyOf(cases);
        this.toolNames = List.copyOf(toolNames);
        this.canonicalCasesSha256 = requireDigest(canonicalCasesSha256, "canonicalCasesSha256");
        this.toolNamesSha256 = requireDigest(toolNamesSha256, "toolNamesSha256");
        requireLockedCanonicalSelection();
    }

    static ToolCompatibilityCaseSelection fromCanonicalCases() {
        List<ToolBenchmarkPrompt> cases = List.copyOf(ToolBenchmarkCases.defaults());
        List<String> toolNames = List.copyOf(ToolBenchmarkCases.toolNames());
        return new ToolCompatibilityCaseSelection(
                cases,
                toolNames,
                fingerprint(cases, "canonical cases"),
                fingerprint(toolNames, "canonical tool names"));
    }

    List<ToolBenchmarkPrompt> cases() {
        return cases;
    }

    List<String> caseIds() {
        return cases.stream().map(ToolBenchmarkPrompt::id).toList();
    }

    List<String> toolNames() {
        return toolNames;
    }

    String canonicalCasesSha256() {
        return canonicalCasesSha256;
    }

    String toolNamesSha256() {
        return toolNamesSha256;
    }

    void requireBoundTo(ToolCompatibilityCaseOracle oracle) {
        if (oracle == null) {
            throw new IllegalArgumentException("oracle must not be null");
        }
        if (!caseIds().equals(oracle.caseIds())) {
            throw new IllegalArgumentException("Case oracle IDs must match the selected canonical cases in order");
        }
        for (ToolCompatibilityCaseOracle.CaseExpectation expectation : oracle.cases()) {
            for (ToolCompatibilityExpectedCall call : expectation.calls()) {
                if (!toolNames.contains(call.toolName())) {
                    throw new IllegalArgumentException("Case oracle references a tool outside the canonical tool list: "
                            + call.toolName());
                }
            }
        }
    }

    private void requireLockedCanonicalSelection() {
        if (!ToolCompatibilityProtocol.CASE_IDS.equals(caseIds())) {
            throw new IllegalArgumentException("Canonical tool compatibility case IDs are missing, duplicated, or reordered");
        }
        if (!toolNames.equals(ToolBenchmarkCases.toolNames())) {
            throw new IllegalArgumentException("Tool compatibility tool names must match ToolBenchmarkCases.toolNames()");
        }
        if (new HashSet<>(toolNames).size() != toolNames.size()) {
            throw new IllegalArgumentException("Canonical tool names must not contain duplicates");
        }
    }

    private static String fingerprint(Object value, String label) {
        try {
            return EvidenceIntegrity.sha256(FINGERPRINT_MAPPER.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fingerprint " + label, exception);
        }
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a full lowercase SHA-256 digest");
        }
        return value;
    }

    record ScheduledCase(int sequence, int repetition, int seed, String caseId) {
        ScheduledCase {
            if (sequence < 1 || repetition < 1 || seed < 0) {
                throw new IllegalArgumentException("Tool compatibility schedule numbers must be positive");
            }
            if (caseId == null || caseId.isBlank() || !caseId.equals(caseId.strip())) {
                throw new IllegalArgumentException("Tool compatibility schedule caseId must be nonblank and trimmed");
            }
        }
    }
}
