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

public abstract class LocalEvaluationOfflineTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String runDir;

    @Inject
    public LocalEvaluationOfflineTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    public abstract Property<String> getMode();

    @Input
    @Optional
    public String getRunDir() {
        return runDir;
    }

    @Option(option = "run-dir", description = "Required saved directory under local/evidence/evaluation-matrix/.")
    public void setRunDir(String runDir) {
        this.runDir = runDir;
    }

    @TaskAction
    public void inspectEvidence() {
        if (runDir == null || runDir.isBlank()) {
            throw new GradleException(getName() + " requires --run-dir=<saved-evidence-directory>");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of("--mode", getMode().get(), "--run-dir", runDir.trim()));
        });
    }
}
