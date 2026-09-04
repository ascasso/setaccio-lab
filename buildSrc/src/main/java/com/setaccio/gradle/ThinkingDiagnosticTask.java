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

/** Runs the explicit opt-in local reasoning and empty-content diagnostic. */
public abstract class ThinkingDiagnosticTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String subjectModel;
    private String controlModel;
    private String ollamaVersion;
    private String outputDir;

    @Inject
    public ThinkingDiagnosticTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the opt-in local-only, no-pull reasoning and empty-content diagnostic.");
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
    public String getSubjectModel() {
        return subjectModel;
    }

    @Option(option = "subject-model", description = "Required already-installed subject tag with no pull or substitution.")
    public void setSubjectModel(String subjectModel) {
        this.subjectModel = subjectModel;
    }

    @Input
    @Optional
    public String getControlModel() {
        return controlModel;
    }

    @Option(option = "control-model", description = "Required already-installed non-thinking control tag.")
    public void setControlModel(String controlModel) {
        this.controlModel = controlModel;
    }

    @Input
    @Optional
    public String getOllamaVersion() {
        return ollamaVersion;
    }

    @Option(option = "ollama-version", description = "Required observed local Ollama runtime version.")
    public void setOllamaVersion(String ollamaVersion) {
        this.ollamaVersion = ollamaVersion;
    }

    @Input
    @Optional
    public String getOutputDir() {
        return outputDir;
    }

    @Option(option = "output-dir", description = "Required new dated directory directly under local/evidence/thinking-diagnostic/.")
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void runDiagnostic() {
        requireOption(ollamaBaseUrl, "--ollama-base-url");
        requireOption(subjectModel, "--subject-model");
        requireOption(controlModel, "--control-model");
        requireOption(ollamaVersion, "--ollama-version");
        requireOption(outputDir, "--output-dir");
        if (!outputDir.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new GradleException("thinkingDiagnostic output directory must contain a YYYY-MM-DD date");
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--ollama-base-url", ollamaBaseUrl.trim(),
                    "--subject-model", subjectModel.trim(),
                    "--control-model", controlModel.trim(),
                    "--ollama-version", ollamaVersion.trim(),
                    "--output-dir", outputDir.trim()));
        });
    }

    private static void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("thinkingDiagnostic requires " + option + "=<value>");
        }
    }
}
