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

public abstract class ToolSearchMatrixBaselineTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String outputDir;

    @Inject
    public ToolSearchMatrixBaselineTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the locked post-fix Tool Search diagnostic matrix baseline.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getOutputDir() {
        return outputDir;
    }

    @Option(option = "output-dir", description = "Required new dated directory under build/tool-search-matrix/.")
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void runMatrix() {
        if (outputDir == null || outputDir.isBlank()) {
            throw new GradleException("toolSearchMatrixBaseline requires --output-dir=<dated-build-directory>");
        }
        if (!outputDir.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new GradleException("toolSearchMatrixBaseline output directory must contain a YYYY-MM-DD date");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of("--output-dir", outputDir.trim()));
        });
    }
}
