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

/** Opt-in Gradle wrapper for the paired F1 runner. */
public abstract class LocalEvaluationBudgetTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String judgeModel;
    private String outputDir64;
    private String outputDir256;

    @Inject
    public LocalEvaluationBudgetTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription(
                "Runs the explicit fresh 64/256-token local fact-check pair; never part of the default lifecycle.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama URL; never persisted.")
    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    @Input
    @Optional
    public String getJudgeModel() {
        return judgeModel;
    }

    @Option(option = "judge-model", description = "Required already-installed Ollama judge tag; no default or pull.")
    public void setJudgeModel(String judgeModel) {
        this.judgeModel = judgeModel;
    }

    @Input
    @Optional
    public String getOutputDir64() {
        return outputDir64;
    }

    @Option(option = "output-dir-64", description = "Required new dated directory for the 64-token arm.")
    public void setOutputDir64(String outputDir64) {
        this.outputDir64 = outputDir64;
    }

    @Input
    @Optional
    public String getOutputDir256() {
        return outputDir256;
    }

    @Option(option = "output-dir-256", description = "Required new dated directory for the 256-token arm.")
    public void setOutputDir256(String outputDir256) {
        this.outputDir256 = outputDir256;
    }

    @TaskAction
    public void runBudgetPair() {
        requireOption(ollamaBaseUrl, "--ollama-base-url");
        requireOption(judgeModel, "--judge-model");
        requireOption(outputDir64, "--output-dir-64");
        requireOption(outputDir256, "--output-dir-256");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--ollama-base-url", ollamaBaseUrl.trim(),
                    "--judge-model", judgeModel.trim(),
                    "--output-dir-64", outputDir64.trim(),
                    "--output-dir-256", outputDir256.trim()));
        });
    }

    private void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("localEvaluationBudget requires " + option + "=<value>");
        }
    }
}
