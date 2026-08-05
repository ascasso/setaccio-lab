package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationOption;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ChatMatrixAnalyzer {

    private static final int MAX_TOKENS = 32768;
    private static final long MAX_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();

    private final ChatPromptCatalog catalog;
    private final List<ChatMatrixScheduleEntry> expectedSchedule;

    ChatMatrixAnalyzer(ChatPromptCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        catalog.requireLocked();
        expectedSchedule = ChatMatrixProtocol.schedule(catalog);
    }

    MatrixAnalysis analyze(ChatMatrixResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Chat matrix result must not be null");
        }
        List<String> failures = new ArrayList<>();
        validateTopLevel(result, failures);
        List<ChatMatrixRow> rows = safe(result.rows());
        int comparable = Math.min(rows.size(), expectedSchedule.size());
        for (int index = 0; index < comparable; index++) {
            validateRow(rows.get(index), expectedSchedule.get(index), result, failures);
        }
        return metrics(rows, List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateTopLevel(ChatMatrixResult result, List<String> failures) {
        if (result.protocolVersion() != ChatMatrixProtocol.VERSION) {
            failures.add("Raw chat matrix protocol version drifted from version 1.");
        }
        if (!ChatMatrixProtocol.SUITE.equals(result.suite())) {
            failures.add("Raw chat matrix suite is not ollama-chat-matrix.");
        }
        if (!ChatMatrixProtocol.PROVIDER.equals(result.provider())) {
            failures.add("Raw chat matrix provider is not ollama.");
        }
        if (!ChatMatrixProtocol.ENDPOINT_CATEGORY.equals(result.endpointCategory())) {
            failures.add("Raw chat matrix endpoint category is not the neutral local value.");
        }
        if (result.startedAt() == null || result.finishedAt() == null
                || result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Raw chat matrix timestamps are missing or invalid.");
        }
        if (!ChatMatrixProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Raw chat matrix execution strategy is not sequential.");
        }
        if (!ChatMatrixProtocol.PULL_MODEL_STRATEGY.equals(result.pullModelStrategy())) {
            failures.add("Raw chat matrix pull strategy is not never.");
        }
        if (!catalog.id().equals(result.promptCatalogId())
                || !catalog.version().equals(result.promptCatalogVersion())
                || !catalog.sha256().equals(result.promptCatalogSha256())
                || !catalog.identities().equals(safe(result.orderedPromptIdentities()))) {
            failures.add("Raw chat matrix prompt catalog identity drifted from the tracked contract.");
        }
        validateSettings(result.runSettings(), failures);
        validateModelIdentity(result.runSettings(), result.modelIdentity(), failures);
        if (!expectedSchedule.equals(safe(result.orderedSchedule()))) {
            failures.add("Raw chat matrix ordered schedule drifted from the locked protocol.");
        }
        if (safe(result.rows()).size() != ChatMatrixProtocol.ROW_COUNT) {
            failures.add("Raw chat matrix must contain exactly six rows.");
        }
    }

    private static void validateSettings(ChatMatrixRunSettings settings, List<String> failures) {
        if (settings == null
                || settings.repetitions() != ChatMatrixProtocol.REPETITIONS
                || Double.compare(settings.temperature(), ChatMatrixProtocol.TEMPERATURE) != 0
                || !ChatMatrixProtocol.SEEDS.equals(settings.seeds())
                || settings.maxOutputTokens() < 1
                || settings.maxOutputTokens() > MAX_TOKENS
                || settings.timeoutMillis() < 1
                || settings.timeoutMillis() > MAX_TIMEOUT_MILLIS
                || settings.maxAttempts() != ChatMatrixProtocol.MAX_ATTEMPTS) {
            failures.add("Raw chat matrix run settings drifted from the locked protocol.");
        }
    }

    private static void validateModelIdentity(
            ChatMatrixRunSettings settings,
            OllamaChatModelIdentity identity,
            List<String> failures
    ) {
        if (settings == null || identity == null) {
            failures.add("Raw chat matrix model identity is missing.");
            return;
        }
        if (!OllamaChatModelIdentity.OLLAMA_PROVIDER_ID.equals(identity.providerId())
                || !settings.requestedModel().equals(identity.requestedModel())
                || !ChatMatrixProtocol.normalizeModelTag(settings.requestedModel())
                        .equals(identity.effectiveModel())) {
            failures.add("Raw chat matrix model identity does not match the requested installed model.");
        }
    }

    private static void validateRow(
            ChatMatrixRow row,
            ChatMatrixScheduleEntry expected,
            ChatMatrixResult result,
            List<String> failures
    ) {
        String prefix = "Raw chat matrix row " + expected.sequence() + " ";
        if (row == null) {
            failures.add(prefix + "is missing.");
            return;
        }
        if (row.sequence() != expected.sequence()
                || row.repetition() != expected.repetition()
                || row.seed() != expected.seed()
                || !Objects.equals(row.promptId(), expected.promptId())
                || !Objects.equals(row.promptSha256(), expected.promptSha256())) {
            failures.add(prefix + "does not match the locked schedule.");
        }
        ChatGenerationSettings expectedSettings = result.runSettings() == null
                ? null
                : result.runSettings().generationSettingsFor(expected.repetition());
        if (!Objects.equals(expectedSettings, row.generationSettings())) {
            failures.add(prefix + "generation settings drifted from the locked protocol.");
        }
        if (row.optionSupport() == null
                || !row.optionSupport().supported().equals(java.util.Set.of(ChatGenerationOption.values()))
                || !row.optionSupport().unsupportedReasons().isEmpty()) {
            failures.add(prefix + "Ollama option support metadata drifted.");
        }
        if (row.latencyMillis() < 0) {
            failures.add(prefix + "latency must not be negative.");
        }
        if (row.attemptCount() != ChatMatrixProtocol.MAX_ATTEMPTS) {
            failures.add(prefix + "must record exactly one attempt.");
        }
        validateUsage(row, prefix, failures);
        validateOutcome(row, prefix, failures);
    }

    private static void validateUsage(ChatMatrixRow row, String prefix, List<String> failures) {
        boolean allMissing = row.promptTokens() == null
                && row.completionTokens() == null
                && row.totalTokens() == null;
        boolean allPresent = row.promptTokens() != null
                && row.completionTokens() != null
                && row.totalTokens() != null;
        if (!allMissing && !allPresent) {
            failures.add(prefix + "usage metadata must be complete or absent.");
        } else if (allPresent && (row.promptTokens() < 0
                || row.completionTokens() < 0
                || row.totalTokens() < 0)) {
            failures.add(prefix + "usage metadata must not be negative.");
        }
    }

    private static void validateOutcome(ChatMatrixRow row, String prefix, List<String> failures) {
        if (row.failureCategory() == null) {
            failures.add(prefix + "failure category is missing.");
            return;
        }
        if (row.invocationSucceeded()) {
            if (row.failureCategory() == ChatInvocationFailureCategory.NONE) {
                if (row.rawResponse() == null || row.rawResponse().isBlank() || row.error() != null) {
                    failures.add(prefix + "successful response fields are inconsistent.");
                }
            } else if (row.failureCategory() == ChatInvocationFailureCategory.EMPTY_RESPONSE) {
                if ((row.rawResponse() != null && !row.rawResponse().isBlank()) || row.error() != null) {
                    failures.add(prefix + "empty response fields are inconsistent.");
                }
            } else {
                failures.add(prefix + "completed invocation has an infrastructure failure category.");
            }
        } else {
            String expectedError = switch (row.failureCategory()) {
                case MODEL_UNAVAILABLE -> "Ollama chat model was unavailable";
                case TIMEOUT -> "Ollama chat invocation timed out";
                case AUTHENTICATION, RATE_LIMIT, PROVIDER_FAILURE -> "Ollama chat provider invocation failed";
                case NONE, EMPTY_RESPONSE -> null;
            };
            if (expectedError == null
                    || row.rawResponse() != null
                    || !Objects.equals(expectedError, row.error())) {
                failures.add(prefix + "failed invocation fields are inconsistent.");
            }
        }
    }

    private MatrixAnalysis metrics(List<ChatMatrixRow> rows, List<String> failures) {
        EnumMap<ChatInvocationFailureCategory, Integer> categories =
                new EnumMap<>(ChatInvocationFailureCategory.class);
        for (ChatInvocationFailureCategory category : ChatInvocationFailureCategory.values()) {
            categories.put(category, 0);
        }
        LinkedHashMap<String, PromptMetrics> byPrompt = new LinkedHashMap<>();
        for (ChatPromptCase prompt : catalog.prompts()) {
            List<ChatMatrixRow> promptRows = rows.stream()
                    .filter(row -> row != null && prompt.id().equals(row.promptId()))
                    .toList();
            byPrompt.put(prompt.id(), new PromptMetrics(
                    promptRows.size(),
                    (int) promptRows.stream().filter(ChatMatrixRow::successful).count(),
                    (int) promptRows.stream().filter(row -> row.failureCategory()
                            == ChatInvocationFailureCategory.EMPTY_RESPONSE).count()));
        }
        for (ChatMatrixRow row : rows) {
            if (row != null && row.failureCategory() != null) {
                categories.compute(row.failureCategory(), (ignored, count) -> count + 1);
            }
        }
        List<Long> successfulLatencies = rows.stream()
                .filter(Objects::nonNull)
                .filter(ChatMatrixRow::successful)
                .map(ChatMatrixRow::latencyMillis)
                .toList();
        return new MatrixAnalysis(
                rows.size(),
                (int) rows.stream().filter(Objects::nonNull).filter(ChatMatrixRow::successful).count(),
                (int) rows.stream().filter(Objects::nonNull).filter(ChatMatrixRow::invocationSucceeded).count(),
                (int) rows.stream().filter(Objects::nonNull)
                        .filter(row -> row.promptTokens() != null).count(),
                successfulLatencies.stream().mapToLong(Long::longValue).min().stream().boxed().findFirst().orElse(null),
                successfulLatencies.stream().mapToLong(Long::longValue).max().stream().boxed().findFirst().orElse(null),
                Map.copyOf(categories),
                Map.copyOf(byPrompt),
                failures);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    record MatrixAnalysis(
            int totalRows,
            int successfulResponses,
            int completedInvocations,
            int rowsWithUsage,
            Long minimumSuccessfulLatencyMillis,
            Long maximumSuccessfulLatencyMillis,
            Map<ChatInvocationFailureCategory, Integer> categories,
            Map<String, PromptMetrics> byPrompt,
            List<String> integrityFailures
    ) {
        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }

    record PromptMetrics(int totalRows, int successfulResponses, int emptyResponses) {}
}
