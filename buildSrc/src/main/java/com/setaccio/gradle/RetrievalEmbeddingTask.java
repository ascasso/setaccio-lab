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

/** Runs the explicit opt-in local Ollama embedding retrieval generation. */
public abstract class RetrievalEmbeddingTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String embeddingModel;
    private String topK;
    private String outputDir;

    @Inject
    public RetrievalEmbeddingTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the opt-in local-only, no-pull Phase 5 embedding retrieval generation.");
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

    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama HTTP(S) URL; never persisted.")
    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    @Input
    @Optional
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    @Option(option = "embedding-model", description = "Required already-installed Ollama model tag with no pull or substitution.")
    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Input
    @Optional
    public String getTopK() {
        return topK;
    }

    @Option(option = "top-k", description = "Required explicit positive number of ranked document hits to retain.")
    public void setTopK(String topK) {
        this.topK = topK;
    }

    @Input
    @Optional
    public String getOutputDir() {
        return outputDir;
    }

    @Option(option = "output-dir", description = "Required new dated directory directly under local/evidence/retrieval-embedding/.")
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void runEmbeddingGeneration() {
        requireOption(ollamaBaseUrl, "--ollama-base-url");
        requireOption(embeddingModel, "--embedding-model");
        requireOption(topK, "--top-k");
        requireOption(outputDir, "--output-dir");
        if (!outputDir.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new GradleException("retrievalEmbedding output directory must contain a YYYY-MM-DD date");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--ollama-base-url", ollamaBaseUrl.trim(),
                    "--embedding-model", embeddingModel.trim(),
                    "--top-k", topK.trim(),
                    "--output-dir", outputDir.trim()));
        });
    }

    private static void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("retrievalEmbedding requires " + option + "=<value>");
        }
    }
}
