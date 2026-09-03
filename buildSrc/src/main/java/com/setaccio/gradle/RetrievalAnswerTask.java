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

/** Runs the explicit opt-in, local-only R5 answer-generation matrix. */
public abstract class RetrievalAnswerTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String answerModel;
    private String maxOutputTokens;
    private String seed;
    private String timeout;
    private String sourceRetrievalRunDir;
    private String outputDir;

    @Inject
    public RetrievalAnswerTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the opt-in local-only, no-pull Phase 5 retrieval answer matrix.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input @Optional public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama HTTP(S) URL; never persisted.")
    public void setOllamaBaseUrl(String value) { ollamaBaseUrl = value; }

    @Input @Optional public String getAnswerModel() { return answerModel; }
    @Option(option = "answer-model", description = "Required already-installed Ollama answer model tag; no pull or substitution.")
    public void setAnswerModel(String value) { answerModel = value; }

    @Input @Optional public String getMaxOutputTokens() { return maxOutputTokens; }
    @Option(option = "max-output-tokens", description = "Required explicit positive answer-token limit.")
    public void setMaxOutputTokens(String value) { maxOutputTokens = value; }

    @Input @Optional public String getSeed() { return seed; }
    @Option(option = "seed", description = "Required explicit non-negative local generation seed.")
    public void setSeed(String value) { seed = value; }

    @Input @Optional public String getRequestTimeout() { return timeout; }
    @Option(option = "timeout", description = "Required explicit positive ISO-8601 request timeout.")
    public void setTimeout(String value) { timeout = value; }

    @Input @Optional public String getSourceRetrievalRunDir() { return sourceRetrievalRunDir; }
    @Option(option = "source-retrieval-run-dir", description = "Required verified R3 run directly under local/evidence/retrieval-evaluation/.")
    public void setSourceRetrievalRunDir(String value) { sourceRetrievalRunDir = value; }

    @Input @Optional public String getOutputDir() { return outputDir; }
    @Option(option = "output-dir", description = "Required fresh dated directory directly under local/evidence/retrieval-answer/.")
    public void setOutputDir(String value) { outputDir = value; }

    @TaskAction
    public void runAnswerGeneration() {
        require(ollamaBaseUrl, "--ollama-base-url");
        require(answerModel, "--answer-model");
        require(maxOutputTokens, "--max-output-tokens");
        require(seed, "--seed");
        require(timeout, "--timeout");
        require(sourceRetrievalRunDir, "--source-retrieval-run-dir");
        require(outputDir, "--output-dir");
        if (!outputDir.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new GradleException("retrievalAnswerMatrix output directory must contain a YYYY-MM-DD date");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--ollama-base-url", ollamaBaseUrl.trim(),
                    "--answer-model", answerModel.trim(),
                    "--max-output-tokens", maxOutputTokens.trim(),
                    "--seed", seed.trim(),
                    "--timeout", timeout.trim(),
                    "--source-retrieval-run-dir", sourceRetrievalRunDir.trim(),
                    "--output-dir", outputDir.trim()));
        });
    }

    private static void require(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("retrievalAnswerMatrix requires " + option + "=<value>");
        }
    }
}
