package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityHumanReviewPreparerTest {

    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);
    private static final LocalDate REVIEW_DATE = LocalDate.parse("2026-08-21");

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesOneDeterministicBlankWorksheetBoundToVerifiedEvidence() throws Exception {
        Pair pair = writePair("baseline", "candidate");
        ToolCompatibilityHumanReviewPreparer preparer = new ToolCompatibilityHumanReviewPreparer(OBJECT_MAPPER);
        String reviewId = ToolCompatibilityHumanReviewPrepareRunner.reviewId(
                pair.baseline(), pair.candidate(), REVIEW_DATE);

        ToolCompatibilityHumanReviewPreparer.PreparationResult first = preparer.prepare(
                pair.baseline(),
                pair.candidate(),
                temporaryDirectory.resolve("review-one"),
                REVIEW_DATE,
                reviewId);
        ToolCompatibilityHumanReviewPreparer.PreparationResult second = preparer.prepare(
                pair.baseline(),
                pair.candidate(),
                temporaryDirectory.resolve("review-two"),
                REVIEW_DATE,
                reviewId);

        String worksheet = Files.readString(first.worksheet(), StandardCharsets.UTF_8);
        String repeatedWorksheet = Files.readString(second.worksheet(), StandardCharsets.UTF_8);
        String report = new ToolCompatibilityPromptMatrixComparison(OBJECT_MAPPER)
                .compare(pair.baseline(), pair.candidate())
                .report();
        String expectedReportSha256 = EvidenceIntegrity.sha256(report.getBytes(StandardCharsets.UTF_8));

        assertThat(first.worksheet().getFileName().toString())
                .isEqualTo(ToolCompatibilityHumanReviewPreparer.WORKSHEET_FILENAME);
        assertThat(first.worksheet().getParent().getFileName().toString()).isEqualTo(reviewId);
        assertThat(first.comparisonReportSha256()).isEqualTo(expectedReportSha256);
        assertThat(second.comparisonReportSha256()).isEqualTo(expectedReportSha256);
        assertThat(worksheet).isEqualTo(repeatedWorksheet);
        assertThat(worksheet)
                .contains("# Private Tool Compatibility Human-Review Worksheet")
                .contains("Private ignored artifact")
                .contains("- Baseline run: `baseline`")
                .contains("- Candidate run: `candidate`")
                .contains("- Prompt catalog: `" + ToolCompatibilitySystemPromptCatalog.ID + "` version `"
                        + ToolCompatibilitySystemPromptCatalog.VERSION + "` (`"
                        + ToolCompatibilitySystemPromptCatalog.SHA256 + "`)")
                .contains("- Comparison report SHA-256: `" + expectedReportSha256 + "`")
                .contains("- Review date: `2026-08-21`")
                .contains("## `arithmetic-add` / repetition `1`")
                .contains("### Baseline recorded evidence")
                .contains("### Candidate recorded evidence")
                .contains("Thinking... selecting the required tool")
                .contains("- [ ] `adopt`")
                .contains("- [ ] `revise`")
                .contains("- [ ] `reject`")
                .contains("- [ ] `inconclusive`")
                .contains("an agent or LLM must not select it")
                .doesNotContain(temporaryDirectory.toString(), "- [x]", "overall winner");

        assertThatThrownBy(() -> preparer.prepare(
                pair.baseline(),
                pair.candidate(),
                temporaryDirectory.resolve("review-one"),
                REVIEW_DATE,
                reviewId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsIncompleteEvidenceBeforeAllocatingThePrivateWorksheet() throws Exception {
        Pair pair = writePair("complete", "complete-candidate");
        Path incompleteCandidate = Files.createDirectory(temporaryDirectory.resolve("incomplete"));
        Path outputRoot = temporaryDirectory.resolve("unallocated-review-root");

        assertThatThrownBy(() -> new ToolCompatibilityHumanReviewPreparer(OBJECT_MAPPER).prepare(
                pair.baseline(),
                incompleteCandidate,
                outputRoot,
                REVIEW_DATE,
                "complete--vs--incomplete--review-2026-08-21"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The candidate run did not verify");
        assertThat(outputRoot).doesNotExist();
    }

    private Pair writePair(String baselineId, String candidateId) throws Exception {
        ToolCompatibilityPromptMatrixEvidence evidence = new ToolCompatibilityPromptMatrixEvidence(OBJECT_MAPPER);
        Path baseline = Files.createDirectory(temporaryDirectory.resolve(baselineId));
        Path candidate = Files.createDirectory(temporaryDirectory.resolve(candidateId));
        evidence.write(
                baseline,
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.UNTREATED),
                CLEAN_BASELINE);
        evidence.write(
                candidate,
                ToolCompatibilityPromptMatrixTestFixtures.result(ToolCompatibilityPromptCondition.PROMPTED),
                CLEAN_BASELINE);
        return new Pair(baseline, candidate);
    }

    private record Pair(Path baseline, Path candidate) {}
}
