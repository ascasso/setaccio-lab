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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Five-arm immutable evidence lifecycle for the separately started Phase 4 breakpoint study. */
final class LocalEvaluationBreakpointEvidence {

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final LocalEvaluationEvidence armEvidence;

    LocalEvaluationBreakpointEvidence(
            ObjectMapper objectMapper,
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        manifestStore = new EvidenceManifestStore(objectMapper);
        armEvidence = new LocalEvaluationEvidence(objectMapper, prompt, catalog, review);
    }

    void writeArm(Path runDirectory, LocalEvaluationResult result, EvidenceCodeBaseline baseline) {
        if (result == null || result.runSettings() == null) {
            throw new IllegalArgumentException("Breakpoint arm result settings are required");
        }
        LocalEvaluationBreakpointProtocol.requireFixedSettings(
                result.runSettings(), result.runSettings().maxTokens());
        armEvidence.write(runDirectory, result, baseline);
    }

    OfflineStudyResult verifyStudy(Map<Integer, Path> runDirectories) {
        return inspectStudy(runDirectories, false);
    }

    OfflineStudyResult reanalyzeStudy(Map<Integer, Path> runDirectories) {
        return inspectStudy(runDirectories, true);
    }

    StudySnapshot loadVerifiedStudy(Map<Integer, Path> runDirectories) {
        OfflineStudyResult verification = verifyStudy(runDirectories);
        if (!verification.valid()) {
            throw new IllegalArgumentException(
                    "Breakpoint study did not verify: " + String.join("; ", verification.failures()));
        }
        Map<Integer, ArmSnapshot> arms = new LinkedHashMap<>();
        for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            arms.put(maxTokens, readSnapshot(runDirectories.get(maxTokens), maxTokens, new ArrayList<>()));
        }
        return new StudySnapshot(Map.copyOf(arms));
    }

    private OfflineStudyResult inspectStudy(Map<Integer, Path> runDirectories, boolean reanalyze) {
        List<String> failures = new ArrayList<>();
        Map<Integer, ArmSnapshot> arms = new LinkedHashMap<>();
        if (!hasExpectedDirectories(runDirectories, failures)) {
            return new OfflineStudyResult(List.copyOf(failures));
        }
        for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            Path directory = runDirectories.get(maxTokens);
            try {
                LocalEvaluationEvidence.OfflineResult arm = reanalyze
                        ? armEvidence.reanalyze(directory)
                        : armEvidence.verify(directory);
                arm.failures().forEach(failure -> failures.add(maxTokens + "-token arm: " + failure));
            } catch (RuntimeException exception) {
                failures.add(maxTokens + "-token arm: " + safeMessage(exception));
            }
            ArmSnapshot snapshot = readSnapshot(directory, maxTokens, failures);
            if (snapshot != null) {
                arms.put(maxTokens, snapshot);
            }
        }
        if (failures.isEmpty() && arms.size() == LocalEvaluationBreakpointProtocol.MAX_TOKENS.size()) {
            compareSnapshots(arms, failures);
        }
        return new OfflineStudyResult(List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static boolean hasExpectedDirectories(Map<Integer, Path> directories, List<String> failures) {
        if (directories == null || !directories.keySet().equals(new LinkedHashSet<>(
                LocalEvaluationBreakpointProtocol.MAX_TOKENS))) {
            failures.add("Breakpoint study requires exactly the locked 64, 96, 128, 192, and 256 token directories.");
            return false;
        }
        long distinct = directories.values().stream().filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize()).distinct().count();
        if (distinct != directories.size()) {
            failures.add("Breakpoint study arm directories must all be distinct.");
            return false;
        }
        return true;
    }

    private ArmSnapshot readSnapshot(Path runDirectory, int maxTokens, List<String> failures) {
        if (runDirectory == null) {
            failures.add(maxTokens + "-token arm: run directory must not be null.");
            return null;
        }
        Path raw = runDirectory.toAbsolutePath().normalize().resolve(LocalEvaluationProtocol.RAW_FILENAME);
        try {
            if (Files.isSymbolicLink(raw) || !Files.isRegularFile(raw, LinkOption.NOFOLLOW_LINKS)) {
                failures.add(maxTokens + "-token arm: raw result is missing or unsafe.");
                return null;
            }
            return new ArmSnapshot(
                    manifestStore.read(runDirectory.toAbsolutePath().normalize()),
                    objectMapper.readerFor(LocalEvaluationResult.class)
                            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(raw.toFile()));
        } catch (Exception exception) {
            failures.add(maxTokens + "-token arm: saved inputs could not be loaded: " + safeMessage(exception));
            return null;
        }
    }

    private static void compareSnapshots(Map<Integer, ArmSnapshot> arms, List<String> failures) {
        ArmSnapshot canonical = arms.get(64);
        List<LocalEvaluationRunSettings> settings = new ArrayList<>();
        for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            ArmSnapshot arm = arms.get(maxTokens);
            settings.add(arm.result().runSettings());
            if (!Objects.equals(canonical.manifest().suite(), arm.manifest().suite())) {
                failures.add("Breakpoint suite identity differs at " + maxTokens + " tokens.");
            }
            if (!Objects.equals(canonical.manifest().executionEngine(), arm.manifest().executionEngine())) {
                failures.add("Breakpoint execution engine differs at " + maxTokens + " tokens.");
            }
            if (!Objects.equals(canonical.manifest().codeBaseline(), arm.manifest().codeBaseline())) {
                failures.add("Breakpoint Git code baseline differs at " + maxTokens + " tokens.");
            }
            if (!Objects.equals(canonical.manifest().frameworkVersions(), arm.manifest().frameworkVersions())) {
                failures.add("Breakpoint framework versions differ at " + maxTokens + " tokens.");
            }
            compareResultProtocol(canonical.result(), arm.result(), maxTokens, failures);
        }
        try {
            LocalEvaluationBreakpointProtocol.requireStudySettings(settings);
        } catch (RuntimeException exception) {
            failures.add("Breakpoint maximum-token or shared settings differ: " + safeMessage(exception));
        }
    }

    private static void compareResultProtocol(
            LocalEvaluationResult canonical,
            LocalEvaluationResult arm,
            int maxTokens,
            List<String> failures
    ) {
        if (canonical == null || arm == null) {
            failures.add("Breakpoint result is absent at " + maxTokens + " tokens.");
            return;
        }
        if (!Objects.equals(canonical.protocolVersion(), arm.protocolVersion())
                || !Objects.equals(canonical.suite(), arm.suite())
                || !Objects.equals(canonical.provider(), arm.provider())
                || !Objects.equals(canonical.endpointCategory(), arm.endpointCategory())
                || !Objects.equals(canonical.executionStrategy(), arm.executionStrategy())
                || !Objects.equals(canonical.pullModelStrategy(), arm.pullModelStrategy())
                || !Objects.equals(canonical.judgeModelIdentity(), arm.judgeModelIdentity())
                || !Objects.equals(canonical.promptId(), arm.promptId())
                || !Objects.equals(canonical.promptVersion(), arm.promptVersion())
                || !Objects.equals(canonical.promptSha256(), arm.promptSha256())
                || !Objects.equals(canonical.fixtureCatalogId(), arm.fixtureCatalogId())
                || !Objects.equals(canonical.fixtureCatalogVersion(), arm.fixtureCatalogVersion())
                || !Objects.equals(canonical.fixtureCatalogSha256(), arm.fixtureCatalogSha256())
                || !Objects.equals(canonical.fixtureReviewId(), arm.fixtureReviewId())
                || !Objects.equals(canonical.fixtureReviewVersion(), arm.fixtureReviewVersion())
                || !Objects.equals(canonical.fixtureReviewSha256(), arm.fixtureReviewSha256())
                || !Objects.equals(canonical.orderedSchedule(), arm.orderedSchedule())
                || !sameRowProtocol(canonical.rows(), arm.rows())) {
            failures.add("Breakpoint non-budget result protocol differs at " + maxTokens + " tokens.");
        }
    }

    private static boolean sameRowProtocol(List<LocalEvaluationRow> left, List<LocalEvaluationRow> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            LocalEvaluationRow a = left.get(index);
            LocalEvaluationRow b = right.get(index);
            if (a == null || b == null || a.sequence() != b.sequence() || a.repetition() != b.repetition()
                    || a.seed() != b.seed() || !Objects.equals(a.fixtureId(), b.fixtureId())
                    || !Objects.equals(a.pairId(), b.pairId())
                    || !Objects.equals(a.documentBlake3(), b.documentBlake3())
                    || !Objects.equals(a.claimBlake3(), b.claimBlake3())
                    || a.expectedVerdict() != b.expectedVerdict()
                    || !sameNonBudgetJudgeSettings(a.judgeSettings(), b.judgeSettings())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameNonBudgetJudgeSettings(
            LocalFactCheckJudgeSettings left, LocalFactCheckJudgeSettings right) {
        return left != null && right != null && Objects.equals(left.model(), right.model())
                && Double.compare(left.temperature(), right.temperature()) == 0 && left.seed() == right.seed()
                && Objects.equals(left.timeout(), right.timeout()) && left.maxAttempts() == right.maxAttempts();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    record OfflineStudyResult(List<String> failures) {
        OfflineStudyResult {
            failures = failures == null ? List.of("Breakpoint verification failed without details.") : List.copyOf(failures);
        }

        boolean valid() {
            return failures.isEmpty();
        }
    }

    record ArmSnapshot(EvidenceManifest manifest, LocalEvaluationResult result) {}

    record StudySnapshot(Map<Integer, ArmSnapshot> arms) {
        StudySnapshot {
            arms = arms == null ? Map.of() : Map.copyOf(arms);
        }
    }
}
