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

public abstract class ToolSearchSmokeTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String model;
    private String caseIds;

    @Inject
    public ToolSearchSmokeTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the explicitly opt-in local Tool Search smoke diagnostic.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getModel() {
        return model;
    }

    @Option(option = "model", description = "Required already-installed Ollama model ID/version.")
    public void setModel(String model) {
        this.model = model;
    }

    @Input
    @Optional
    public String getCaseIds() {
        return caseIds;
    }

    @Option(option = "case-ids", description = "Optional comma-separated semantic case IDs or 1-based ordinals.")
    public void setCaseIds(String caseIds) {
        this.caseIds = caseIds;
    }

    @TaskAction
    public void runSmoke() {
        if (model == null || model.isBlank()) {
            throw new GradleException("toolSearchSmoke requires --model=<already-installed-ollama-model>");
        }
        List<String> runnerArguments = new ArrayList<>();
        runnerArguments.add("--model");
        runnerArguments.add(model.trim());
        if (caseIds != null) {
            runnerArguments.add("--case-ids");
            runnerArguments.add(caseIds);
        }
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(runnerArguments);
        });
    }
}
