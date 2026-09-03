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

/** Inspects two explicit saved prompt-matrix runs without starting Spring or contacting Ollama. */
@DisableCachingByDefault(because = "Inspects explicitly selected saved paired evidence")
public abstract class ToolCompatibilityComparisonTask extends DefaultTask {

    private final ExecOperations execOperations;

    @Inject
    public ToolCompatibilityComparisonTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Strictly compares two verified saved prompt-matrix runs without starting Spring or contacting a provider.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    @Input
    @Optional
    public abstract Property<String> getBaselineRun();

    @Option(option = "baseline-run", description = "Required saved untreated directory directly under local/evidence/tool-compatibility/.")
    public void setBaselineRunOption(String value) {
        getBaselineRun().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getCandidateRun();

    @Option(option = "candidate-run", description = "Required saved prompted directory directly under local/evidence/tool-compatibility/.")
    public void setCandidateRunOption(String value) {
        getCandidateRun().set(value);
    }

    @TaskAction
    public void compareEvidence() {
        requireOption(getBaselineRun(), "--baseline-run");
        requireOption(getCandidateRun(), "--candidate-run");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getWorkingDirectory().get().getAsFile());
            spec.args(List.of(
                    "--baseline-run", getBaselineRun().get(),
                    "--candidate-run", getCandidateRun().get()));
        });
    }

    private void requireOption(Property<String> property, String option) {
        String value = property.getOrNull();
        if (value == null || value.isBlank()) {
            throw new GradleException(getName() + " requires " + option + "=<saved-evidence-directory>");
        }
    }
}
