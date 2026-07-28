package com.setaccio.gradle;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Creates a private non-overwriting local review worksheet")
public abstract class VisionHumanReviewPrepareTask extends DefaultTask {

    private final ExecOperations execOperations;

    @Inject
    public VisionHumanReviewPrepareTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Prepares an ignored private worksheet for human review of two verified vision runs.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    @Internal
    public abstract DirectoryProperty getReviewRoot();

    @Input
    @Optional
    public abstract Property<String> getBaselineRunDir();

    @Option(option = "baseline-run-dir", description = "Required saved baseline directory under build/vision-matrix/.")
    public void setBaselineRunDirOption(String value) {
        getBaselineRunDir().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getCandidateRunDir();

    @Option(option = "candidate-run-dir", description = "Required saved candidate directory under build/vision-matrix/.")
    public void setCandidateRunDirOption(String value) {
        getCandidateRunDir().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getCorpusDir();

    @Option(option = "corpus-dir", description = "Required fixed ignored local vision corpus directory.")
    public void setCorpusDirOption(String value) {
        getCorpusDir().set(value);
    }

    @TaskAction
    public void prepareReview() {
        requireOptions();
        String baseline = getBaselineRunDir().get().trim();
        String candidate = getCandidateRunDir().get().trim();
        String corpus = getCorpusDir().get().trim();
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getWorkingDirectory().get().getAsFile());
            spec.args(List.of(
                    "--baseline-run-dir", baseline,
                    "--candidate-run-dir", candidate,
                    "--corpus-dir", corpus,
                    "--output-root", getReviewRoot().get().getAsFile().getAbsolutePath()));
        });
    }

    private void requireOptions() {
        if (isMissing(getBaselineRunDir())
                || isMissing(getCandidateRunDir())
                || isMissing(getCorpusDir())) {
            throw new GradleException("""
                    visionHumanReviewPrepare requires three explicit options:
                      --baseline-run-dir=build/vision-matrix/2026-07-25-controlled-four-case
                      --candidate-run-dir=build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case
                      --corpus-dir=local/vision-corpus
                    See docs/VISION-HUMAN-REVIEW.md for the three-step preparation workflow.
                    """.strip());
        }
    }

    private static boolean isMissing(Property<String> property) {
        String value = property.getOrNull();
        return value == null || value.isBlank();
    }
}
