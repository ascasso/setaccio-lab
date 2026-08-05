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

/** Explicit opt-in task for the bounded remote portability proof. */
public abstract class AnthropicChatMatrixTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String maxTokens;
    private String timeout;
    private String maxCostUsd;
    private String outputDir;
    private String ollamaRunDir;

    @Inject
    public AnthropicChatMatrixTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the explicit six-call Anthropic chat portability proof; never part of the default lifecycle.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input @Optional
    public String getMaxTokens() { return maxTokens; }

    @Option(option = "max-tokens", description = "Required locked Anthropic output-token cap: 128.")
    public void setMaxTokens(String maxTokens) { this.maxTokens = maxTokens; }

    @Input @Optional
    public String getRequestTimeout() { return timeout; }

    @Option(option = "timeout", description = "Required locked Anthropic timeout: PT2M.")
    public void setRequestTimeout(String timeout) { this.timeout = timeout; }

    @Input @Optional
    public String getMaxCostUsd() { return maxCostUsd; }

    @Option(option = "max-cost-usd", description = "Required explicit authorization ceiling in USD; it must cover the preflight estimate.")
    public void setMaxCostUsd(String maxCostUsd) { this.maxCostUsd = maxCostUsd; }

    @Input @Optional
    public String getOutputDir() { return outputDir; }

    @Option(option = "output-dir", description = "Required new dated directory directly under build/anthropic-chat-matrix/.")
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    @Input @Optional
    public String getOllamaRunDir() { return ollamaRunDir; }

    @Option(option = "ollama-run-dir", description = "Required verified matching saved Ollama chat-matrix run for the offline portability report.")
    public void setOllamaRunDir(String ollamaRunDir) { this.ollamaRunDir = ollamaRunDir; }

    @TaskAction
    public void runMatrix() {
        require(maxTokens, "--max-tokens");
        require(timeout, "--timeout");
        require(maxCostUsd, "--max-cost-usd");
        require(outputDir, "--output-dir");
        require(ollamaRunDir, "--ollama-run-dir");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--max-tokens", maxTokens.trim(),
                    "--timeout", timeout.trim(),
                    "--max-cost-usd", maxCostUsd.trim(),
                    "--output-dir", outputDir.trim(),
                    "--ollama-run-dir", ollamaRunDir.trim()));
        });
    }

    private static void require(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("anthropicChatMatrix requires " + option + "=<value>");
        }
    }
}
