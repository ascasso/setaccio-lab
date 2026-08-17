package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ToolCompatibilityCaseOracle {

    static final String RESOURCE = "tool-compatibility/case-oracle-v1.json";
    static final String ID = "tool-case-oracle";
    static final int VERSION = 1;
    static final String SHA256 = "9ccd612ace107b37a6f5a5c30e0cc77e80236a54afcb728566750c18035c1af4";

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final String id;
    private final int version;
    private final String sha256;
    private final List<CaseExpectation> cases;

    private ToolCompatibilityCaseOracle(String id, int version, String sha256, List<CaseExpectation> cases) {
        this.id = requireText(id, "id");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be a full lowercase SHA-256 digest");
        }
        this.sha256 = sha256;
        this.cases = List.copyOf(cases == null ? List.of() : cases);
        if (this.cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }
        Set<String> caseIds = new HashSet<>();
        for (CaseExpectation caseExpectation : this.cases) {
            if (!caseIds.add(caseExpectation.caseId())) {
                throw new IllegalArgumentException("case oracle must not contain duplicate case IDs");
            }
        }
    }

    static ToolCompatibilityCaseOracle loadLocked() {
        byte[] bytes = readResource();
        String sha256 = EvidenceIntegrity.sha256(bytes);
        if (!SHA256.equals(sha256)) {
            throw new IllegalArgumentException("Tracked tool compatibility case oracle digest drifted");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(bytes);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Tracked tool compatibility case oracle must be a JSON object");
            }
            String id = requiredText(root.path("id"), "id");
            int version = requiredPositiveInteger(root.path("version"), "version");
            if (!ID.equals(id) || VERSION != version) {
                throw new IllegalArgumentException("Tracked tool compatibility case oracle identity drifted");
            }
            JsonNode caseNodes = root.path("cases");
            if (!caseNodes.isArray()) {
                throw new IllegalArgumentException("Tracked tool compatibility case oracle cases must be an array");
            }
            List<CaseExpectation> cases = new ArrayList<>();
            for (JsonNode caseNode : caseNodes) {
                String caseId = requiredText(caseNode.path("caseId"), "caseId");
                JsonNode callNodes = caseNode.path("calls");
                if (!callNodes.isArray()) {
                    throw new IllegalArgumentException("Tracked tool compatibility case oracle calls must be an array");
                }
                List<ToolCompatibilityExpectedCall> calls = new ArrayList<>();
                for (JsonNode callNode : callNodes) {
                    calls.add(new ToolCompatibilityExpectedCall(
                            requiredText(callNode.path("tool"), "tool"),
                            callNode.get("arguments")));
                }
                cases.add(new CaseExpectation(caseId, calls));
            }
            return new ToolCompatibilityCaseOracle(id, version, sha256, cases);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tracked tool compatibility case oracle is invalid", exception);
        }
    }

    String id() {
        return id;
    }

    int version() {
        return version;
    }

    String sha256() {
        return sha256;
    }

    List<CaseExpectation> cases() {
        return cases;
    }

    List<String> caseIds() {
        return cases.stream().map(CaseExpectation::caseId).toList();
    }

    CaseExpectation requireCase(String caseId) {
        return cases.stream()
                .filter(caseExpectation -> caseExpectation.caseId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool compatibility oracle case: " + caseId));
    }

    private static byte[] readResource() {
        try (InputStream input = ToolCompatibilityCaseOracle.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalArgumentException("Tracked tool compatibility case oracle is missing");
            }
            return input.readAllBytes();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tracked tool compatibility case oracle could not be read", exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("Tracked tool compatibility case oracle " + field + " must be text");
        }
        return requireText(node.textValue(), field);
    }

    private static int requiredPositiveInteger(JsonNode node, String field) {
        if (node == null || !node.canConvertToInt() || !node.isIntegralNumber() || node.intValue() < 1) {
            throw new IllegalArgumentException("Tracked tool compatibility case oracle " + field + " must be positive");
        }
        return node.intValue();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }

    record CaseExpectation(String caseId, List<ToolCompatibilityExpectedCall> calls) {
        CaseExpectation {
            caseId = requireText(caseId, "caseId");
            calls = List.copyOf(calls == null ? List.of() : calls);
        }
    }
}
