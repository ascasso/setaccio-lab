package com.setaccio.gradle;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

public abstract class VisionMatrixComparisonTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String baselineRunDir;
    private String candidateRunDir;

    @Inject
    public VisionMatrixComparisonTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Compares two verified saved vision matrices without contacting a provider.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getBaselineRunDir() {
        return baselineRunDir;
    }

    @Option(option = "baseline-run-dir", description = "Required saved baseline directory under local/evidence/vision-matrix/.")
    public void setBaselineRunDir(String baselineRunDir) {
        this.baselineRunDir = baselineRunDir;
    }

    @Input
    @Optional
    public String getCandidateRunDir() {
        return candidateRunDir;
    }

    @Option(option = "candidate-run-dir", description = "Required saved candidate directory under local/evidence/vision-matrix/.")
    public void setCandidateRunDir(String candidateRunDir) {
        this.candidateRunDir = candidateRunDir;
    }

    @TaskAction
    public void compareMatrices() {
        requireOption(baselineRunDir, "--baseline-run-dir");
        requireOption(candidateRunDir, "--candidate-run-dir");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--baseline-run-dir", baselineRunDir.trim(),
                    "--candidate-run-dir", candidateRunDir.trim()));
        });
    }

    private static void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("visionMatrixCompare requires " + option + "=<saved-evidence-directory>");
        }
    }
}
