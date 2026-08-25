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

/** Reads one verified cohort and prints its deterministic T3.6 frontier report. */
@DisableCachingByDefault(because = "Inspects explicitly selected saved cohort evidence")
public abstract class ToolCompatibilityCohortFrontierTask extends DefaultTask {

    private final ExecOperations execOperations;

    @Inject
    public ToolCompatibilityCohortFrontierTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription(
                "Evaluates the narrow capability frontier in one verified saved cohort without contacting a provider.");
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
    public abstract Property<String> getRunDir();

    @Option(
            option = "run-dir",
            description = "Required saved cohort directly under build/tool-compatibility/.")
    public void setRunDirOption(String value) {
        getRunDir().set(value);
    }

    @TaskAction
    public void analyzeEvidence() {
        String runDirectory = getRunDir().getOrNull();
        if (runDirectory == null || runDirectory.isBlank()) {
            throw new GradleException(
                    getName() + " requires --run-dir=<saved-build-directory>");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getWorkingDirectory().get().getAsFile());
            spec.args(List.of("--run-dir", runDirectory));
        });
    }
}
