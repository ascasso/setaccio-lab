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

/** Opt-in wrapper for the five-arm Phase 4 output-budget breakpoint study. */
public abstract class LocalEvaluationBreakpointTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String judgeModel;
    private String outputDir64;
    private String outputDir96;
    private String outputDir128;
    private String outputDir192;
    private String outputDir256;

    @Inject
    public LocalEvaluationBreakpointTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the opt-in five-arm 64/96/128/192/256-token fact-check breakpoint study.");
    }

    @Classpath public abstract ConfigurableFileCollection getClasspath();
    @Input public abstract Property<String> getMainClass();
    @Input public abstract Property<String> getJavaExecutable();
    @Input @Optional public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama URL; never persisted.")
    public void setOllamaBaseUrl(String value) { ollamaBaseUrl = value; }
    @Input @Optional public String getJudgeModel() { return judgeModel; }
    @Option(option = "judge-model", description = "Required already-installed Ollama judge tag; no pull.")
    public void setJudgeModel(String value) { judgeModel = value; }
    @Input @Optional public String getOutputDir64() { return outputDir64; }
    @Option(option = "output-dir-64", description = "Required new dated directory for the 64-token arm.")
    public void setOutputDir64(String value) { outputDir64 = value; }
    @Input @Optional public String getOutputDir96() { return outputDir96; }
    @Option(option = "output-dir-96", description = "Required new dated directory for the 96-token arm.")
    public void setOutputDir96(String value) { outputDir96 = value; }
    @Input @Optional public String getOutputDir128() { return outputDir128; }
    @Option(option = "output-dir-128", description = "Required new dated directory for the 128-token arm.")
    public void setOutputDir128(String value) { outputDir128 = value; }
    @Input @Optional public String getOutputDir192() { return outputDir192; }
    @Option(option = "output-dir-192", description = "Required new dated directory for the 192-token arm.")
    public void setOutputDir192(String value) { outputDir192 = value; }
    @Input @Optional public String getOutputDir256() { return outputDir256; }
    @Option(option = "output-dir-256", description = "Required new dated directory for the 256-token arm.")
    public void setOutputDir256(String value) { outputDir256 = value; }

    @TaskAction
    public void runStudy() {
        require(ollamaBaseUrl, "--ollama-base-url");
        require(judgeModel, "--judge-model");
        require(outputDir64, "--output-dir-64");
        require(outputDir96, "--output-dir-96");
        require(outputDir128, "--output-dir-128");
        require(outputDir192, "--output-dir-192");
        require(outputDir256, "--output-dir-256");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of("--ollama-base-url", ollamaBaseUrl.trim(), "--judge-model", judgeModel.trim(),
                    "--output-dir-64", outputDir64.trim(), "--output-dir-96", outputDir96.trim(),
                    "--output-dir-128", outputDir128.trim(), "--output-dir-192", outputDir192.trim(),
                    "--output-dir-256", outputDir256.trim()));
        });
    }

    private void require(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("localEvaluationBreakpoint requires " + option + "=<value>");
        }
    }
}
