package com.setaccio.lab.thinking;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.evaluation.LocalFactCheckFixture;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import com.setaccio.lab.evaluation.LocalFactCheckPromptDefinition;
import com.setaccio.lab.evidence.EvidenceSuiteRoot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The locked, version-aware protocol for the reasoning/default/boundary diagnostic. */
public final class ThinkingDiagnosticProtocol {

    public static final int LEGACY_VERSION = 1;
    public static final int VERSION = 2;
    public static final String SUITE = "ollama-thinking-diagnostic";
    public static final EvidenceSuiteRoot EVIDENCE_ROOT = EvidenceSuiteRoot.of("thinking-diagnostic");
    public static final String PROVIDER = "ollama";
    public static final String ENDPOINT_CATEGORY = "loopback-local";
    public static final String LEGACY_MANIFEST_EXECUTION_ENGINE =
            "spring-ai-fact-checking-evaluator-recording-boundary";
    public static final String MANIFEST_EXECUTION_ENGINE = "mixed-pre-registered-execution-boundaries";
    public static final String EXECUTION_STRATEGY = "sequential-one-attempt-per-arm-fixture";
    public static final String PULL_MODEL_STRATEGY = "never";
    public static final String RAW_FILENAME = "thinking-diagnostic-results.json";

    public static final String PROMPT_DELIVERY = "identical-rendered-fact-check-prompt";
    public static final String POLICY_COMPARISON = "controlled-within-execution-boundary";
    public static final String BOUNDARY_COMPARISON = "controlled-identical-rendered-prompt";

    public static final double TEMPERATURE = 0.0;
    public static final int SEED = 42;
    public static final int MAX_ATTEMPTS = 1;
    public static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    public static final int OUTPUT_BUDGET = 64;

    /**
     * Fixed v2 arm order. PROVIDER_DEFAULT is present only where the arm explicitly records that
     * it is a measured condition; it can therefore never enter the schedule through omission.
     */
    public static final List<ThinkingDiagnosticArm> ARMS = List.of(
            arm("fact-check-subject-provider-default-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.PROVIDER_DEFAULT,
                    ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR, true),
            arm("fact-check-subject-enabled-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.ENABLED,
                    ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR, false),
            arm("fact-check-subject-disabled-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.DISABLED,
                    ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR, false),
            arm("chat-subject-provider-default-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.PROVIDER_DEFAULT,
                    ThinkingDiagnosticExecutionBoundary.CHAT_INVOCATION, true),
            arm("chat-subject-enabled-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.ENABLED,
                    ThinkingDiagnosticExecutionBoundary.CHAT_INVOCATION, false),
            arm("chat-subject-disabled-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.DISABLED,
                    ThinkingDiagnosticExecutionBoundary.CHAT_INVOCATION, false),
            arm("chat-control-provider-default-64", ThinkingDiagnosticModelRole.CONTROL,
                    ChatReasoningPolicy.PROVIDER_DEFAULT,
                    ThinkingDiagnosticExecutionBoundary.CHAT_INVOCATION, true));

    /** Policy contrasts are derived from the recorded arm list, not injected into evidence. */
    public static final List<List<String>> PAIRED_ARMS = policyPairs(ARMS);

    /** Matching-policy boundary contrasts are likewise derived from the recorded arm list. */
    public static final List<List<String>> BOUNDARY_PAIRS = boundaryPairs(ARMS);

    public static final int ROW_COUNT = ARMS.size() * LocalFactCheckFixtureCatalog.FIXTURE_COUNT;

    private static final List<ThinkingDiagnosticArm> LEGACY_ARMS = List.of(
            legacyArm("subject-thinking-enabled-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.ENABLED, 64),
            legacyArm("subject-thinking-disabled-64", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.DISABLED, 64),
            legacyArm("subject-thinking-enabled-256", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.ENABLED, 256),
            legacyArm("subject-thinking-disabled-256", ThinkingDiagnosticModelRole.SUBJECT,
                    ChatReasoningPolicy.DISABLED, 256),
            legacyArm("control-thinking-disabled-64", ThinkingDiagnosticModelRole.CONTROL,
                    ChatReasoningPolicy.DISABLED, 64));

    private ThinkingDiagnosticProtocol() {}

    public static List<ThinkingDiagnosticScheduleEntry> schedule(LocalFactCheckFixtureCatalog catalog) {
        return schedule(catalog, VERSION);
    }

    static List<ThinkingDiagnosticScheduleEntry> schedule(
            LocalFactCheckFixtureCatalog catalog,
            int protocolVersion
    ) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        List<LocalFactCheckFixture> fixtures = catalog.fixtures();
        if (fixtures.size() != LocalFactCheckFixtureCatalog.FIXTURE_COUNT) {
            throw new IllegalStateException("Fact-check fixture catalog does not hold the confirmed fixture count");
        }
        List<ThinkingDiagnosticArm> arms = arms(protocolVersion);
        List<ThinkingDiagnosticScheduleEntry> schedule =
                new ArrayList<>(arms.size() * LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        int sequence = 0;
        for (ThinkingDiagnosticArm arm : arms) {
            for (LocalFactCheckFixture fixture : fixtures) {
                schedule.add(new ThinkingDiagnosticScheduleEntry(
                        sequence++, arm.armId(), fixture.id(), fixture.pairId(),
                        fixture.expectedVerdict(), SEED));
            }
        }
        return Collections.unmodifiableList(schedule);
    }

    public static ThinkingDiagnosticArm requireArm(String armId) {
        return requireArm(VERSION, armId);
    }

    static ThinkingDiagnosticArm requireArm(int protocolVersion, String armId) {
        return arms(protocolVersion).stream()
                .filter(arm -> arm.armId().equals(armId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown diagnostic arm: " + armId));
    }

    static List<ThinkingDiagnosticArm> arms(int protocolVersion) {
        return switch (protocolVersion) {
            case LEGACY_VERSION -> LEGACY_ARMS;
            case VERSION -> ARMS;
            default -> throw new IllegalArgumentException(
                    "Unsupported thinking diagnostic protocol version: " + protocolVersion);
        };
    }

    static int rowCount(int protocolVersion) {
        return arms(protocolVersion).size() * LocalFactCheckFixtureCatalog.FIXTURE_COUNT;
    }

    static boolean supportsVersion(int protocolVersion) {
        return protocolVersion == LEGACY_VERSION || protocolVersion == VERSION;
    }

    static String manifestExecutionEngine(int protocolVersion) {
        return switch (protocolVersion) {
            case LEGACY_VERSION -> LEGACY_MANIFEST_EXECUTION_ENGINE;
            case VERSION -> MANIFEST_EXECUTION_ENGINE;
            default -> throw new IllegalArgumentException(
                    "Unsupported thinking diagnostic protocol version: " + protocolVersion);
        };
    }

    public static Map<String, Object> manifestSettings(ThinkingDiagnosticResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("endpointCategory", result.endpointCategory());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("temperature", result.temperature());
        settings.put("seed", result.seed());
        settings.put("maxAttempts", result.maxAttempts());
        settings.put("requestTimeoutMillis", result.requestTimeoutMillis());
        settings.put("reasoningPolicySource", reasoningPolicySource(result.arms()));
        settings.put("arms", result.protocolVersion() == LEGACY_VERSION
                ? legacyArmSettings(result.arms()) : result.arms());
        settings.put("pairedArms", policyPairs(result.arms()));
        if (result.protocolVersion() == VERSION) {
            settings.put("boundaryPairs", boundaryPairs(result.arms()));
            settings.put("promptDelivery", result.promptDelivery());
            settings.put("policyComparison", result.policyComparison());
            settings.put("boundaryComparison", result.boundaryComparison());
        }
        settings.put("modelIdentities", result.modelIdentities());
        settings.put("ollamaVersion", result.ollamaVersion());
        settings.put("promptId", result.promptId());
        settings.put("promptVersion", result.promptVersion());
        settings.put("promptSha256", result.promptSha256());
        settings.put("fixtureCatalogId", result.fixtureCatalogId());
        settings.put("fixtureCatalogVersion", result.fixtureCatalogVersion());
        settings.put("fixtureCatalogSha256", result.fixtureCatalogSha256());
        settings.put("orderedSchedule", result.orderedSchedule());
        return Collections.unmodifiableMap(settings);
    }

    static String reasoningPolicySource(List<ThinkingDiagnosticArm> arms) {
        boolean measuresDefault = arms.stream()
                .anyMatch(arm -> arm.reasoningPolicy() == ChatReasoningPolicy.PROVIDER_DEFAULT);
        return measuresDefault
                ? "pre-registered-per-arm-including-provider-default"
                : "explicit-per-arm";
    }

    static List<List<String>> policyPairs(List<ThinkingDiagnosticArm> arms) {
        return pairs(arms, true);
    }

    static List<List<String>> boundaryPairs(List<ThinkingDiagnosticArm> arms) {
        return pairs(arms, false);
    }

    private static List<List<String>> pairs(List<ThinkingDiagnosticArm> arms, boolean policyAxis) {
        List<List<String>> pairs = new ArrayList<>();
        for (int left = 0; left < arms.size(); left++) {
            ThinkingDiagnosticArm first = arms.get(left);
            for (int right = left + 1; right < arms.size(); right++) {
                ThinkingDiagnosticArm second = arms.get(right);
                boolean common = first.modelRole() == second.modelRole()
                        && first.maxOutputTokens() == second.maxOutputTokens();
                boolean matches = policyAxis
                        ? common && first.executionBoundary() == second.executionBoundary()
                                && first.reasoningPolicy() != second.reasoningPolicy()
                        : common && first.reasoningPolicy() == second.reasoningPolicy()
                                && first.executionBoundary() != second.executionBoundary();
                if (matches) {
                    pairs.add(List.of(first.armId(), second.armId()));
                }
            }
        }
        return List.copyOf(pairs);
    }

    private static List<Map<String, Object>> legacyArmSettings(List<ThinkingDiagnosticArm> arms) {
        return arms.stream().map(arm -> {
            LinkedHashMap<String, Object> setting = new LinkedHashMap<>();
            setting.put("armId", arm.armId());
            setting.put("modelRole", arm.modelRole());
            setting.put("reasoningPolicy", arm.reasoningPolicy());
            setting.put("maxOutputTokens", arm.maxOutputTokens());
            return Collections.unmodifiableMap(setting);
        }).toList();
    }

    private static ThinkingDiagnosticArm arm(
            String id,
            ThinkingDiagnosticModelRole role,
            ChatReasoningPolicy policy,
            ThinkingDiagnosticExecutionBoundary boundary,
            boolean measuredProviderDefault
    ) {
        return new ThinkingDiagnosticArm(
                id, role, policy, boundary, OUTPUT_BUDGET, measuredProviderDefault);
    }

    private static ThinkingDiagnosticArm legacyArm(
            String id,
            ThinkingDiagnosticModelRole role,
            ChatReasoningPolicy policy,
            int maxOutputTokens
    ) {
        return new ThinkingDiagnosticArm(
                id, role, policy, ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR,
                maxOutputTokens, false);
    }

    public static String promptId() {
        return LocalFactCheckPromptDefinition.ID;
    }

    public static String normalizeModelTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("model tag must not be blank");
        }
        String trimmed = tag.strip();
        return trimmed.contains(":") ? trimmed : trimmed + ":latest";
    }
}
