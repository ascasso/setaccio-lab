package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;

/** Exact JSON-typed comparison with mathematical equality for JSON numbers. */
final class ToolCompatibilityJsonSemantics {

    private ToolCompatibilityJsonSemantics() {}

    static boolean equals(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return false;
        }
        if (expected.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!actual.has(field.getKey()) || !equals(field.getValue(), actual.get(field.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int index = 0; index < expected.size(); index++) {
                if (!equals(expected.get(index), actual.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }
}
