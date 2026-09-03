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

/** Runs the explicit opt-in, local-only R6 relevance-evaluation matrix. */
public abstract class RetrievalRelevancyTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String evaluatorModel;
    private String maxOutputTokens;
    private String seed;
    private String timeout;
    private String sourceAnswerRunDir;
    private String outputDir;

    @Inject
    public RetrievalRelevancyTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the opt-in local-only, no-pull Phase 5 retrieval relevancy matrix.");
    }

    @Classpath public abstract ConfigurableFileCollection getClasspath();
    @Input public abstract Property<String> getMainClass();
    @Input public abstract Property<String> getJavaExecutable();

    @Input @Optional public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama HTTP(S) URL; never persisted.")
    public void setOllamaBaseUrl(String value) { ollamaBaseUrl = value; }

    @Input @Optional public String getEvaluatorModel() { return evaluatorModel; }
    @Option(option = "evaluator-model", description = "Required already-installed Ollama evaluator model tag; no pull or substitution.")
    public void setEvaluatorModel(String value) { evaluatorModel = value; }

    @Input @Optional public String getMaxOutputTokens() { return maxOutputTokens; }
    @Option(option = "max-output-tokens", description = "Required explicit positive evaluator-token limit.")
    public void setMaxOutputTokens(String value) { maxOutputTokens = value; }

    @Input @Optional public String getSeed() { return seed; }
    @Option(option = "seed", description = "Required explicit non-negative local generation seed.")
    public void setSeed(String value) { seed = value; }

    @Input @Optional public String getRequestTimeout() { return timeout; }
    @Option(option = "timeout", description = "Required explicit positive ISO-8601 request timeout.")
    public void setTimeout(String value) { timeout = value; }

    @Input @Optional public String getSourceAnswerRunDir() { return sourceAnswerRunDir; }
    @Option(option = "source-answer-run-dir", description = "Required verified R5 run directly under local/evidence/retrieval-answer/.")
    public void setSourceAnswerRunDir(String value) { sourceAnswerRunDir = value; }

    @Input @Optional public String getOutputDir() { return outputDir; }
    @Option(option = "output-dir", description = "Required fresh dated directory directly under local/evidence/retrieval-relevancy/.")
    public void setOutputDir(String value) { outputDir = value; }

    @TaskAction
    public void runRelevancyEvaluation() {
        require(ollamaBaseUrl, "--ollama-base-url");
        require(evaluatorModel, "--evaluator-model");
        require(maxOutputTokens, "--max-output-tokens");
        require(seed, "--seed");
        require(timeout, "--timeout");
        require(sourceAnswerRunDir, "--source-answer-run-dir");
        require(outputDir, "--output-dir");
        if (!outputDir.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new GradleException("retrievalRelevancyMatrix output directory must contain a YYYY-MM-DD date");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--ollama-base-url", ollamaBaseUrl.trim(),
                    "--evaluator-model", evaluatorModel.trim(),
                    "--max-output-tokens", maxOutputTokens.trim(),
                    "--seed", seed.trim(),
                    "--timeout", timeout.trim(),
                    "--source-answer-run-dir", sourceAnswerRunDir.trim(),
                    "--output-dir", outputDir.trim()));
        });
    }

    private static void require(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("retrievalRelevancyMatrix requires " + option + "=<value>");
        }
    }
}
