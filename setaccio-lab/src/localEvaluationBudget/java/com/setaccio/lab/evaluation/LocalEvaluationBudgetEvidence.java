package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Pair-level lifecycle around the existing immutable per-arm evidence format. */
final class LocalEvaluationBudgetEvidence {

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final LocalEvaluationEvidence armEvidence;

    LocalEvaluationBudgetEvidence(
            ObjectMapper objectMapper,
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        manifestStore = new EvidenceManifestStore(objectMapper);
        armEvidence = new LocalEvaluationEvidence(objectMapper, prompt, catalog, review);
    }

    void writePair(
            Path budget64,
            LocalEvaluationResult result64,
            Path budget256,
            LocalEvaluationResult result256,
            EvidenceCodeBaseline codeBaseline
    ) {
        requireDistinctDirectories(budget64, budget256);
        requirePairResults(result64, result256);
        armEvidence.write(budget64, result64, codeBaseline);
        armEvidence.write(budget256, result256, codeBaseline);
    }

    void writeArm(
            Path runDirectory,
            LocalEvaluationResult result,
            EvidenceCodeBaseline codeBaseline
    ) {
        armEvidence.write(runDirectory, result, codeBaseline);
    }

    OfflinePairResult verifyPair(Path budget64, Path budget256) {
        List<String> failures = new ArrayList<>();
        requireDistinctDirectories(budget64, budget256, failures);
        LocalEvaluationEvidence.OfflineResult left = verifyArm(
                budget64,
                "64-token arm",
                failures);
        LocalEvaluationEvidence.OfflineResult right = verifyArm(
                budget256,
                "256-token arm",
                failures);
        if (left.valid() && right.valid()) {
            compareSnapshots(budget64, budget256, failures);
        }
        return new OfflinePairResult(List.copyOf(new LinkedHashSet<>(failures)));
    }

    OfflinePairResult reanalyzePair(Path budget64, Path budget256) {
        List<String> failures = new ArrayList<>();
        requireDistinctDirectories(budget64, budget256, failures);
        LocalEvaluationEvidence.OfflineResult left = reanalyzeArm(
                budget64,
                "64-token arm",
                failures);
        LocalEvaluationEvidence.OfflineResult right = reanalyzeArm(
                budget256,
                "256-token arm",
                failures);
        if (left.valid() && right.valid()) {
            compareSnapshots(budget64, budget256, failures);
        }
        return new OfflinePairResult(List.copyOf(new LinkedHashSet<>(failures)));
    }

    private LocalEvaluationEvidence.OfflineResult verifyArm(
            Path runDirectory,
            String label,
            List<String> failures
    ) {
        try {
            LocalEvaluationEvidence.OfflineResult result = armEvidence.verify(runDirectory);
            addFailures(failures, label, result.failures());
            return result;
        } catch (RuntimeException exception) {
            failures.add(label + ": " + safeMessage(exception));
            return new LocalEvaluationEvidence.OfflineResult(List.of(safeMessage(exception)));
        }
    }

    private LocalEvaluationEvidence.OfflineResult reanalyzeArm(
            Path runDirectory,
            String label,
            List<String> failures
    ) {
        try {
            LocalEvaluationEvidence.OfflineResult result = armEvidence.reanalyze(runDirectory);
            addFailures(failures, label, result.failures());
            return result;
        } catch (RuntimeException exception) {
            failures.add(label + ": " + safeMessage(exception));
            return new LocalEvaluationEvidence.OfflineResult(List.of(safeMessage(exception)));
        }
    }

    private void compareSnapshots(Path budget64, Path budget256, List<String> failures) {
        Snapshot left = readSnapshot(budget64, "64-token arm", failures);
        Snapshot right = readSnapshot(budget256, "256-token arm", failures);
        if (left == null || right == null) {
            return;
        }
        compareManifests(left.manifest(), right.manifest(), failures);
        compareResults(left.result(), right.result(), failures);
    }

    private Snapshot readSnapshot(Path runDirectory, String label, List<String> failures) {
        if (runDirectory == null) {
            failures.add(label + ": run directory must not be null.");
            return null;
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        Path raw = root.resolve(LocalEvaluationProtocol.RAW_FILENAME);
        try {
            EvidenceManifest manifest = manifestStore.read(root);
            if (Files.isSymbolicLink(raw) || !Files.isRegularFile(raw, LinkOption.NOFOLLOW_LINKS)) {
                failures.add(label + ": raw result is missing or unsafe.");
                return null;
            }
            LocalEvaluationResult result = objectMapper.readerFor(LocalEvaluationResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(raw.toFile());
            return new Snapshot(manifest, result);
        } catch (Exception exception) {
            failures.add(label + ": saved pair inputs could not be loaded: " + safeMessage(exception));
            return null;
        }
    }

    private static void compareManifests(
            EvidenceManifest left,
            EvidenceManifest right,
            List<String> failures
    ) {
        if (!Objects.equals(left.suite(), right.suite())) {
            failures.add("Pair suite identity differs between the two arms.");
        }
        if (!Objects.equals(left.executionEngine(), right.executionEngine())) {
            failures.add("Pair execution engine differs between the two arms.");
        }
        if (!Objects.equals(left.codeBaseline(), right.codeBaseline())) {
            failures.add("Pair Git code baseline differs between the two arms.");
        }
        if (!Objects.equals(left.frameworkVersions(), right.frameworkVersions())) {
            failures.add("Pair framework versions differ between the two arms.");
        }
    }

    private static void compareResults(
            LocalEvaluationResult left,
            LocalEvaluationResult right,
            List<String> failures
    ) {
        try {
            LocalEvaluationBudgetProtocol.requirePairSettings(left.runSettings(), right.runSettings());
        } catch (RuntimeException exception) {
            failures.add("Pair maximum-token or shared run settings differ: " + safeMessage(exception));
        }
        compare("protocol version", left.protocolVersion(), right.protocolVersion(), failures);
        compare("suite", left.suite(), right.suite(), failures);
        compare("provider", left.provider(), right.provider(), failures);
        compare("endpoint category", left.endpointCategory(), right.endpointCategory(), failures);
        compare("execution strategy", left.executionStrategy(), right.executionStrategy(), failures);
        compare("pull strategy", left.pullModelStrategy(), right.pullModelStrategy(), failures);
        if (!Objects.equals(left.judgeModelIdentity(), right.judgeModelIdentity())) {
            failures.add("Pair judge model identity differs between the two arms.");
        }
        compare("prompt identity", identity(left.promptId(), left.promptVersion(), left.promptSha256()),
                identity(right.promptId(), right.promptVersion(), right.promptSha256()), failures);
        compare("fixture catalog identity",
                identity(left.fixtureCatalogId(), left.fixtureCatalogVersion(), left.fixtureCatalogSha256()),
                identity(right.fixtureCatalogId(), right.fixtureCatalogVersion(), right.fixtureCatalogSha256()),
                failures);
        compare("fixture review identity",
                identity(left.fixtureReviewId(), left.fixtureReviewVersion(), left.fixtureReviewSha256()),
                identity(right.fixtureReviewId(), right.fixtureReviewVersion(), right.fixtureReviewSha256()),
                failures);
        if (!Objects.equals(left.orderedSchedule(), right.orderedSchedule())) {
            failures.add("Pair ordered schedule differs between the two arms.");
        }
        compareSharedSettings(left.runSettings(), right.runSettings(), failures);
        compareRowProtocol(left.rows(), right.rows(), failures);
    }

    private static void compareSharedSettings(
            LocalEvaluationRunSettings left,
            LocalEvaluationRunSettings right,
            List<String> failures
    ) {
        if (left == null || right == null) {
            return;
        }
        if (!Objects.equals(left.requestedModel(), right.requestedModel())
                || left.repetitions() != right.repetitions()
                || Double.compare(left.temperature(), right.temperature()) != 0
                || !Objects.equals(left.seeds(), right.seeds())
                || left.timeoutMillis() != right.timeoutMillis()
                || left.maxAttempts() != right.maxAttempts()) {
            failures.add("Pair shared generation settings differ outside maximum output tokens.");
        }
    }

    private static void compareRowProtocol(
            List<LocalEvaluationRow> left,
            List<LocalEvaluationRow> right,
            List<String> failures
    ) {
        if (left.size() != right.size()) {
            failures.add("Pair row counts differ between the two arms.");
            return;
        }
        for (int index = 0; index < left.size(); index++) {
            LocalEvaluationRow leftRow = left.get(index);
            LocalEvaluationRow rightRow = right.get(index);
            if (leftRow == null || rightRow == null) {
                failures.add("Pair row protocol contains a null row at index " + index + ".");
                continue;
            }
            if (leftRow.sequence() != rightRow.sequence()
                    || leftRow.repetition() != rightRow.repetition()
                    || leftRow.seed() != rightRow.seed()
                    || !Objects.equals(leftRow.fixtureId(), rightRow.fixtureId())
                    || !Objects.equals(leftRow.pairId(), rightRow.pairId())
                    || !Objects.equals(leftRow.documentBlake3(), rightRow.documentBlake3())
                    || !Objects.equals(leftRow.claimBlake3(), rightRow.claimBlake3())
                    || leftRow.expectedVerdict() != rightRow.expectedVerdict()
                    || !sameNonBudgetJudgeSettings(leftRow.judgeSettings(), rightRow.judgeSettings())) {
                failures.add("Pair row protocol differs at sequence " + (index + 1) + ".");
            }
        }
    }

    private static boolean sameNonBudgetJudgeSettings(
            LocalFactCheckJudgeSettings left,
            LocalFactCheckJudgeSettings right
    ) {
        return left != null
                && right != null
                && Objects.equals(left.model(), right.model())
                && Double.compare(left.temperature(), right.temperature()) == 0
                && left.seed() == right.seed()
                && left.timeout().equals(right.timeout())
                && left.maxAttempts() == right.maxAttempts();
    }

    private static void requirePairResults(
            LocalEvaluationResult result64,
            LocalEvaluationResult result256
    ) {
        if (result64 == null || result256 == null) {
            throw new IllegalArgumentException("F1 pair results must not be null");
        }
        List<String> failures = new ArrayList<>();
        compareResults(result64, result256, failures);
        if (!failures.isEmpty()) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    String.join(" ", failures));
        }
    }

    private static void requireDistinctDirectories(Path left, Path right) {
        if (left == null || right == null || left.toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize())) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 pair requires two distinct arm directories");
        }
    }

    private static void requireDistinctDirectories(Path left, Path right, List<String> failures) {
        if (left == null || right == null || left.toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize())) {
            failures.add("Pair arm directories must be two distinct paths.");
        }
    }

    private static String identity(String id, String version, String sha256) {
        return id + "/" + version + "/" + sha256;
    }

    private static void compare(String field, Object left, Object right, List<String> failures) {
        if (!Objects.equals(left, right)) {
            failures.add("Pair " + field + " differs between the two arms.");
        }
    }

    private static void addFailures(List<String> target, String label, List<String> failures) {
        failures.forEach(failure -> target.add(label + ": " + failure));
    }

    private static String safeMessage(Throwable throwable) {
        String message = null;
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private record Snapshot(EvidenceManifest manifest, LocalEvaluationResult result) {}

    record OfflinePairResult(List<String> failures) {

        OfflinePairResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        boolean valid() {
            return failures.isEmpty();
        }
    }
}
