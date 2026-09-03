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

/** Prints the provider-free F3 comparison for one already-verified F1 evidence pair. */
public abstract class LocalEvaluationBudgetComparisonTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String budget64RunDir;
    private String budget256RunDir;

    @Inject
    public LocalEvaluationBudgetComparisonTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription(
                "Compares one verified F1 64/256-token evidence pair without starting Spring or calling a provider.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getBudget64RunDir() {
        return budget64RunDir;
    }

    @Option(option = "budget-64-run-dir", description = "Required saved 64-token arm under local/evidence/evaluation-matrix/.")
    public void setBudget64RunDir(String budget64RunDir) {
        this.budget64RunDir = budget64RunDir;
    }

    @Input
    @Optional
    public String getBudget256RunDir() {
        return budget256RunDir;
    }

    @Option(option = "budget-256-run-dir", description = "Required saved 256-token arm under local/evidence/evaluation-matrix/.")
    public void setBudget256RunDir(String budget256RunDir) {
        this.budget256RunDir = budget256RunDir;
    }

    @TaskAction
    public void comparePair() {
        requireOption(budget64RunDir, "--budget-64-run-dir");
        requireOption(budget256RunDir, "--budget-256-run-dir");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--budget-64-run-dir", budget64RunDir.trim(),
                    "--budget-256-run-dir", budget256RunDir.trim()));
        });
    }

    private void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException(getName() + " requires " + option + "=<saved-evidence-directory>");
        }
    }
}
