package com.setaccio.gradle;

import java.util.ArrayList;
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

public abstract class VisionMatrixTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String corpusDir;
    private String models;
    private String maxTokens;
    private String outputDir;

    @Inject
    public VisionMatrixTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the opt-in sequential local vision matrix.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getCorpusDir() {
        return corpusDir;
    }

    @Option(option = "corpus-dir", description = "Required fixed local corpus directory.")
    public void setCorpusDir(String corpusDir) {
        this.corpusDir = corpusDir;
    }

    @Input
    @Optional
    public String getModels() {
        return models;
    }

    @Option(option = "models", description = "Required comma-separated already-installed Ollama model tags.")
    public void setModels(String models) {
        this.models = models;
    }

    @Input
    @Optional
    public String getMaxTokens() {
        return maxTokens;
    }

    @Option(option = "max-tokens", description = "Required locked token policy: none or an integer from 1 to 32768.")
    public void setMaxTokens(String maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Input
    @Optional
    public String getOutputDir() {
        return outputDir;
    }

    @Option(option = "output-dir", description = "Required new dated directory under build/vision-matrix/.")
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void runMatrix() {
        requireOption(corpusDir, "--corpus-dir");
        requireOption(models, "--models");
        requireOption(maxTokens, "--max-tokens");
        requireOption(outputDir, "--output-dir");
        if (!outputDir.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new GradleException("visionMatrix output directory must contain a YYYY-MM-DD date");
        }

        List<String> args = new ArrayList<>();
        args.addAll(List.of("--corpus-dir", corpusDir.trim()));
        args.addAll(List.of("--models", models.trim()));
        args.addAll(List.of("--max-tokens", maxTokens.trim()));
        args.addAll(List.of("--output-dir", outputDir.trim()));
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(args);
        });
    }

    private void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("visionMatrix requires " + option + "=<value>");
        }
    }
}
