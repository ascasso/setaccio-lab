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

/** Wrapper for provider-free verification, reanalysis, and reporting of a breakpoint study. */
public abstract class LocalEvaluationBreakpointOfflineTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String runDir64;
    private String runDir96;
    private String runDir128;
    private String runDir192;
    private String runDir256;

    @Inject
    public LocalEvaluationBreakpointOfflineTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
    }

    @Classpath public abstract ConfigurableFileCollection getClasspath();
    @Input public abstract Property<String> getMainClass();
    @Input public abstract Property<String> getJavaExecutable();
    @Input @Optional public abstract Property<String> getMode();
    @Input @Optional public String getRunDir64() { return runDir64; }
    @Option(option = "run-dir-64", description = "Required saved 64-token arm under local/evidence/evaluation-matrix/.")
    public void setRunDir64(String value) { runDir64 = value; }
    @Input @Optional public String getRunDir96() { return runDir96; }
    @Option(option = "run-dir-96", description = "Required saved 96-token arm under local/evidence/evaluation-matrix/.")
    public void setRunDir96(String value) { runDir96 = value; }
    @Input @Optional public String getRunDir128() { return runDir128; }
    @Option(option = "run-dir-128", description = "Required saved 128-token arm under local/evidence/evaluation-matrix/.")
    public void setRunDir128(String value) { runDir128 = value; }
    @Input @Optional public String getRunDir192() { return runDir192; }
    @Option(option = "run-dir-192", description = "Required saved 192-token arm under local/evidence/evaluation-matrix/.")
    public void setRunDir192(String value) { runDir192 = value; }
    @Input @Optional public String getRunDir256() { return runDir256; }
    @Option(option = "run-dir-256", description = "Required saved 256-token arm under local/evidence/evaluation-matrix/.")
    public void setRunDir256(String value) { runDir256 = value; }

    @TaskAction
    public void inspectStudy() {
        require(runDir64, "--run-dir-64"); require(runDir96, "--run-dir-96");
        require(runDir128, "--run-dir-128"); require(runDir192, "--run-dir-192");
        require(runDir256, "--run-dir-256");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            java.util.ArrayList<String> args = new java.util.ArrayList<>();
            if (getMode().isPresent()) { args.add("--mode"); args.add(getMode().get()); }
            args.addAll(List.of("--run-dir-64", runDir64.trim(), "--run-dir-96", runDir96.trim(),
                    "--run-dir-128", runDir128.trim(), "--run-dir-192", runDir192.trim(),
                    "--run-dir-256", runDir256.trim()));
            spec.args(args);
        });
    }

    private void require(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException(getName() + " requires " + option + "=<saved-evidence-directory>");
        }
    }
}
