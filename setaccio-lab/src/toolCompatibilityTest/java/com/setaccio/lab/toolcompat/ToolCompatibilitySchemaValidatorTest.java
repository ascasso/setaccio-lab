package com.setaccio.lab.toolcompat;

import com.setaccio.lab.fixture.ToolBenchmarkCases;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilitySchemaValidatorTest {

    private final ToolCompatibilitySchemaValidator validator = new ToolCompatibilitySchemaValidator();

    @Test
    void acceptsTheCurrentCanonicalFixtureDefinitionsWithinTheBoundedSubset() {
        assertThat(ToolCompatibilityCallbackCatalog.canonicalCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyElementsOf(ToolBenchmarkCases.toolNames());
        ToolCompatibilityCallbackCatalog.canonicalCallbacks().forEach(callback ->
                validator.requireSupportedDefinition(callback.getToolDefinition()));
    }

    @Test
    void distinguishesMalformedMissingUnknownAndTypeMismatchesBeforeCallbackBinding() {
        ToolDefinition definition = definition("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "title": "Probe",
                  "description": "ignored metadata",
                  "type": "object",
                  "properties": {
                    "count": { "type": "integer", "default": 1 },
                    "label": { "type": "string" }
                  },
                  "required": ["count"],
                  "additionalProperties": false
                }
                """);

        assertThat(validator.validate(definition, "{not-json}").issue())
                .isEqualTo(ToolCompatibilitySchemaIssue.MALFORMED_JSON);
        assertThat(validator.validate(definition, "{}").issue())
                .isEqualTo(ToolCompatibilitySchemaIssue.MISSING_REQUIRED_ARGUMENT);
        assertThat(validator.validate(definition, "{\"count\":\"5\"}").issue())
                .isEqualTo(ToolCompatibilitySchemaIssue.SCHEMA_TYPE_MISMATCH);
        assertThat(validator.validate(definition, "{\"count\":5,\"extra\":true}").issue())
                .isEqualTo(ToolCompatibilitySchemaIssue.UNKNOWN_ARGUMENT);
        assertThat(validator.validate(definition, "{\"count\":5,\"label\":\"ok\"}"))
                .extracting(
                        ToolCompatibilitySchemaValidator.RawArgumentValidation::jsonValid,
                        ToolCompatibilitySchemaValidator.RawArgumentValidation::schemaValid,
                        ToolCompatibilitySchemaValidator.RawArgumentValidation::issue)
                .containsExactly(true, true, null);
    }

    @Test
    void rejectsSchemaKeywordsOutsideTheLockedSubsetInsteadOfApproximatingThem() {
        ToolDefinition pattern = definition("""
                {"type":"object","properties":{"value":{"type":"string","pattern":"[A-Z]+"}}}
                """);
        ToolDefinition items = definition("""
                {"type":"object","properties":{"values":{"type":"array","items":{"type":"string"}}}}
                """);

        assertThatThrownBy(() -> validator.requireSupportedDefinition(pattern))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .satisfies(exception -> assertThat(((ToolCompatibilityProtocolIntegrityException) exception).issue())
                        .isEqualTo(ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA));
        assertThatThrownBy(() -> validator.requireSupportedDefinition(items))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .satisfies(exception -> assertThat(((ToolCompatibilityProtocolIntegrityException) exception).issue())
                        .isEqualTo(ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA));
    }

    private static ToolDefinition definition(String inputSchema) {
        return DefaultToolDefinition.builder()
                .name("probe")
                .description("Probe")
                .inputSchema(inputSchema)
                .build();
    }
}
