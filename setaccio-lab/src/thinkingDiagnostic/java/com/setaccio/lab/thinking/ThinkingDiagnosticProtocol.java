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

/**
 * The locked, pre-registered protocol for the reasoning/empty-content diagnostic.
 *
 * <p>This is a new diagnostic protocol. It reuses the tracked public-safe fact-check fixture
 * catalog, its tracked ordering, and the tracked fact-check prompt, but it is not a rerun,
 * repair, replacement, or reanalysis of the Phase 4 fact-check evidence, and it does not write
 * into the Phase 4 suite. Its schedule, arms, budgets, and recorded dimensions are its own.
 *
 * <p>The reasoning policy is a first-class protocol setting here. Existing suites deliberately
 * keep sending no policy, so their protocol identity and retained evidence are untouched.
 */
public final class ThinkingDiagnosticProtocol {

    public static final int VERSION = 1;
    public static final String SUITE = "ollama-thinking-diagnostic";
    public static final EvidenceSuiteRoot EVIDENCE_ROOT = EvidenceSuiteRoot.of("thinking-diagnostic");
    public static final String PROVIDER = "ollama";
    public static final String ENDPOINT_CATEGORY = "loopback-local";
    public static final String EXECUTION_ENGINE = "spring-ai-fact-checking-evaluator-recording-boundary";
    public static final String EXECUTION_STRATEGY = "sequential-one-attempt-per-arm-fixture";
    public static final String PULL_MODEL_STRATEGY = "never";
    public static final String RAW_FILENAME = "thinking-diagnostic-results.json";

    public static final double TEMPERATURE = 0.0;
    public static final int SEED = 42;
    public static final int MAX_ATTEMPTS = 1;
    public static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    public static final int SUBJECT_LOW_BUDGET = 64;
    public static final int SUBJECT_HIGH_BUDGET = 256;
    public static final int CONTROL_BUDGET = 64;

    /**
     * Fixed arm order. The two subject pairs hold every non-reasoning setting constant within a
     * pair, so the only difference inside a pair is the explicit reasoning policy.
     */
    public static final List<ThinkingDiagnosticArm> ARMS = List.of(
            new ThinkingDiagnosticArm("subject-thinking-enabled-64",
                    ThinkingDiagnosticModelRole.SUBJECT, ChatReasoningPolicy.ENABLED, SUBJECT_LOW_BUDGET),
            new ThinkingDiagnosticArm("subject-thinking-disabled-64",
                    ThinkingDiagnosticModelRole.SUBJECT, ChatReasoningPolicy.DISABLED, SUBJECT_LOW_BUDGET),
            new ThinkingDiagnosticArm("subject-thinking-enabled-256",
                    ThinkingDiagnosticModelRole.SUBJECT, ChatReasoningPolicy.ENABLED, SUBJECT_HIGH_BUDGET),
            new ThinkingDiagnosticArm("subject-thinking-disabled-256",
                    ThinkingDiagnosticModelRole.SUBJECT, ChatReasoningPolicy.DISABLED, SUBJECT_HIGH_BUDGET),
            new ThinkingDiagnosticArm("control-thinking-disabled-64",
                    ThinkingDiagnosticModelRole.CONTROL, ChatReasoningPolicy.DISABLED, CONTROL_BUDGET));

    /** The two paired interventions, by arm id. Each pair differs only in reasoning policy. */
    public static final List<List<String>> PAIRED_ARMS = List.of(
            List.of("subject-thinking-enabled-64", "subject-thinking-disabled-64"),
            List.of("subject-thinking-enabled-256", "subject-thinking-disabled-256"));

    public static final int ROW_COUNT = ARMS.size() * LocalFactCheckFixtureCatalog.FIXTURE_COUNT;

    private ThinkingDiagnosticProtocol() {}

    /** The locked schedule: arms in fixed order, fixtures in tracked catalog order, no repetition. */
    public static List<ThinkingDiagnosticScheduleEntry> schedule(LocalFactCheckFixtureCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        List<LocalFactCheckFixture> fixtures = catalog.fixtures();
        if (fixtures.size() != LocalFactCheckFixtureCatalog.FIXTURE_COUNT) {
            throw new IllegalStateException("Fact-check fixture catalog does not hold the confirmed fixture count");
        }
        List<ThinkingDiagnosticScheduleEntry> schedule = new ArrayList<>(ROW_COUNT);
        int sequence = 0;
        for (ThinkingDiagnosticArm arm : ARMS) {
            for (LocalFactCheckFixture fixture : fixtures) {
                schedule.add(new ThinkingDiagnosticScheduleEntry(
                        sequence++,
                        arm.armId(),
                        fixture.id(),
                        fixture.pairId(),
                        fixture.expectedVerdict(),
                        SEED));
            }
        }
        return Collections.unmodifiableList(schedule);
    }

    public static ThinkingDiagnosticArm requireArm(String armId) {
        return ARMS.stream()
                .filter(arm -> arm.armId().equals(armId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown diagnostic arm: " + armId));
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
        settings.put("reasoningPolicySource", "explicit-per-arm");
        settings.put("arms", result.arms());
        settings.put("pairedArms", PAIRED_ARMS);
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
