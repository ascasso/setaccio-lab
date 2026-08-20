package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Validates raw tool-call arguments against the deliberately bounded schema subset
 * used by the canonical public fixture tools. This validator observes raw model JSON
 * before Spring AI callback binding; it never changes callback coercion behavior.
 */
final class ToolCompatibilitySchemaValidator {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of(
            "type", "properties", "required", "additionalProperties",
            "description", "title", "$schema", "default");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "object", "array", "string", "number", "integer", "boolean", "null");

    void requireSupportedDefinition(ToolDefinition definition) {
        if (definition == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA,
                    "Tool definition must not be null");
        }
        JsonNode schema = parseSchema(definition.inputSchema(), definition.name());
        requireSupportedSchema(schema, definition.name(), true);
    }

    RawArgumentValidation validate(ToolDefinition definition, String rawArguments) {
        requireSupportedDefinition(definition);
        JsonNode raw = parseRaw(rawArguments);
        if (raw == null) {
            return new RawArgumentValidation(false, null, ToolCompatibilitySchemaIssue.MALFORMED_JSON, null);
        }
        ValidationResult result = validateValue(
                parseSchema(definition.inputSchema(), definition.name()), raw, definition.name());
        return new RawArgumentValidation(true, result.valid(), result.issue(), raw);
    }

    RawArgumentValidation validateJsonOnly(String rawArguments) {
        JsonNode raw = parseRaw(rawArguments);
        return raw == null
                ? new RawArgumentValidation(false, null, ToolCompatibilitySchemaIssue.MALFORMED_JSON, null)
                : new RawArgumentValidation(true, null, null, raw);
    }

    private JsonNode parseSchema(String inputSchema, String toolName) {
        if (inputSchema == null || inputSchema.isBlank()) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA,
                    "Tool " + toolName + " has no input schema");
        }
        try {
            JsonNode schema = JSON.readTree(inputSchema);
            if (schema == null || !schema.isObject()) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA,
                        "Tool " + toolName + " schema must be a JSON object");
            }
            return schema;
        } catch (ToolCompatibilityProtocolIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA,
                    "Tool " + toolName + " schema is not valid JSON", exception);
        }
    }

    private void requireSupportedSchema(JsonNode schema, String toolName, boolean root) {
        if (!schema.isObject()) {
            unsupported(toolName, "schema nodes must be JSON objects");
        }
        Iterator<String> names = schema.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!SUPPORTED_KEYWORDS.contains(name)) {
                unsupported(toolName, "uses unsupported schema keyword " + name);
            }
        }

        JsonNode type = schema.get("type");
        if (type == null || !type.isTextual() || !SUPPORTED_TYPES.contains(type.textValue())) {
            unsupported(toolName, "must declare one supported string type");
        }
        if (root && !"object".equals(type.textValue())) {
            unsupported(toolName, "root schema type must be object");
        }

        JsonNode properties = schema.get("properties");
        if (properties != null) {
            if (!"object".equals(type.textValue()) || !properties.isObject()) {
                unsupported(toolName, "properties is valid only as an object map");
            }
            Iterator<JsonNode> values = properties.elements();
            while (values.hasNext()) {
                requireSupportedSchema(values.next(), toolName, false);
            }
        }
        JsonNode required = schema.get("required");
        if (required != null) {
            if (!"object".equals(type.textValue()) || !required.isArray()) {
                unsupported(toolName, "required is valid only as an array for object schemas");
            }
            Set<String> propertyNames = properties == null ? Set.of() : names(properties);
            for (JsonNode requiredName : required) {
                if (!requiredName.isTextual() || !propertyNames.contains(requiredName.textValue())) {
                    unsupported(toolName, "required must contain declared property names");
                }
            }
        }
        JsonNode additionalProperties = schema.get("additionalProperties");
        if (additionalProperties != null
                && (!"object".equals(type.textValue()) || !additionalProperties.isBoolean())) {
            unsupported(toolName, "additionalProperties must be a boolean for object schemas");
        }
    }

    private ValidationResult validateValue(JsonNode schema, JsonNode value, String toolName) {
        String type = schema.path("type").textValue();
        if (!matchesType(type, value)) {
            return ValidationResult.invalid(ToolCompatibilitySchemaIssue.SCHEMA_TYPE_MISMATCH);
        }
        if (!"object".equals(type)) {
            return ValidationResult.passing();
        }

        JsonNode properties = schema.path("properties");
        Set<String> propertyNames = properties.isObject() ? names(properties) : Set.of();
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode requiredName : required) {
                if (!value.has(requiredName.textValue())) {
                    return ValidationResult.invalid(ToolCompatibilitySchemaIssue.MISSING_REQUIRED_ARGUMENT);
                }
            }
        }
        boolean allowAdditional = !schema.has("additionalProperties") || schema.path("additionalProperties").booleanValue();
        Iterator<String> argumentNames = value.fieldNames();
        while (argumentNames.hasNext()) {
            String argumentName = argumentNames.next();
            JsonNode propertySchema = properties.get(argumentName);
            if (propertySchema == null) {
                if (!allowAdditional) {
                    return ValidationResult.invalid(ToolCompatibilitySchemaIssue.UNKNOWN_ARGUMENT);
                }
                continue;
            }
            ValidationResult nested = validateValue(propertySchema, value.get(argumentName), toolName);
            if (!nested.valid()) {
                return nested;
            }
        }
        return ValidationResult.passing();
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private JsonNode parseRaw(String rawArguments) {
        if (rawArguments == null) {
            return null;
        }
        try {
            return JSON.readTree(rawArguments);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Set<String> names(JsonNode objectNode) {
        Set<String> names = new LinkedHashSet<>();
        objectNode.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static void unsupported(String toolName, String detail) {
        throw new ToolCompatibilityProtocolIntegrityException(
                ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA,
                "Tool " + toolName + " " + detail);
    }

    record RawArgumentValidation(
            boolean jsonValid,
            Boolean schemaValid,
            ToolCompatibilitySchemaIssue issue,
            JsonNode parsedArguments
    ) {}

    private record ValidationResult(boolean valid, ToolCompatibilitySchemaIssue issue) {
        private static ValidationResult passing() {
            return new ValidationResult(true, null);
        }

        private static ValidationResult invalid(ToolCompatibilitySchemaIssue issue) {
            return new ValidationResult(false, issue);
        }
    }
}
