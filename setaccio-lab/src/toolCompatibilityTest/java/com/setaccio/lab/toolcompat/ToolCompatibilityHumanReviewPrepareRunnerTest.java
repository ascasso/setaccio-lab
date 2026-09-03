package com.setaccio.lab.toolcompat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityHumanReviewPrepareRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresEveryExplicitWorksheetBindingOptionExactlyOnce() {
        assertThat(ToolCompatibilityHumanReviewPrepareRunner.Arguments.parse(new String[] {
                "--output-root", "local/evidence/tool-compatibility-human-review",
                "--review-date", "2026-08-21",
                "--candidate-run", "local/evidence/tool-compatibility/2026-08-21-prompted",
                "--baseline-run", "local/evidence/tool-compatibility/2026-08-21-baseline"
        })).isEqualTo(new ToolCompatibilityHumanReviewPrepareRunner.Arguments(
                "local/evidence/tool-compatibility/2026-08-21-baseline",
                "local/evidence/tool-compatibility/2026-08-21-prompted",
                "2026-08-21",
                "local/evidence/tool-compatibility-human-review"));

        assertThatThrownBy(() -> ToolCompatibilityHumanReviewPrepareRunner.Arguments.parse(new String[] {
                "--baseline-run", "local/evidence/tool-compatibility/2026-08-21-baseline",
                "--candidate-run", "local/evidence/tool-compatibility/2026-08-21-prompted",
                "--review-date", "2026-08-21",
                "--review-date", "2026-08-22"
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityHumanReviewPrepareRunner.Arguments.parse(new String[] {
                "--baseline-run", "local/evidence/tool-compatibility/2026-08-21-baseline",
                "--candidate-run", " local/evidence/tool-compatibility/2026-08-21-prompted ",
                "--review-date", "2026-08-21",
                "--output-root", "local/evidence/tool-compatibility-human-review"
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixesTheIgnoredReviewRootAndValidatesTheOwnerSuppliedDate() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));

        assertThat(ToolCompatibilityHumanReviewPrepareRunner.resolveReviewRoot(
                project, "local/evidence/tool-compatibility-human-review"))
                .isEqualTo(project.resolve("local/evidence/tool-compatibility-human-review"));
        assertThatThrownBy(() -> ToolCompatibilityHumanReviewPrepareRunner.resolveReviewRoot(
                project, "build/other-review-root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed local/evidence/tool-compatibility-human-review");
        assertThat(ToolCompatibilityHumanReviewPrepareRunner.parseReviewDate("2026-08-21"))
                .isEqualTo(LocalDate.parse("2026-08-21"));
        assertThatThrownBy(() -> ToolCompatibilityHumanReviewPrepareRunner.parseReviewDate("2026-02-30"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid ISO-8601 date");
    }

    @Test
    void derivesOneSafeNonOverwritingWorksheetIdentityFromBothRunsAndTheDate() {
        assertThat(ToolCompatibilityHumanReviewPrepareRunner.reviewId(
                Path.of("local/evidence/tool-compatibility/2026-08-21-baseline"),
                Path.of("local/evidence/tool-compatibility/2026-08-21-prompted"),
                LocalDate.parse("2026-08-21")))
                .isEqualTo("2026-08-21-baseline--vs--2026-08-21-prompted--review-2026-08-21");
        assertThatThrownBy(() -> ToolCompatibilityHumanReviewPrepareRunner.reviewId(
                Path.of("local/evidence/tool-compatibility/.unsafe"),
                Path.of("local/evidence/tool-compatibility/2026-08-21-prompted"),
                LocalDate.parse("2026-08-21")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe path segments");
    }
}
