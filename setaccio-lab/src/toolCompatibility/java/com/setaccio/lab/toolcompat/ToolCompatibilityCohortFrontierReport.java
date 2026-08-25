package com.setaccio.lab.toolcompat;

/** Renders the bounded, deterministic T3.6 capability-frontier report. */
final class ToolCompatibilityCohortFrontierReport {

    String render(ToolCompatibilityCohortFrontier.FrontierData data) {
        if (data == null) {
            throw new IllegalArgumentException("cohort frontier data is required");
        }
        int rowsPerModel = Math.multiplyExact(
                data.orderedCaseIds().size(), data.runSettings().repetitions());
        StringBuilder out = new StringBuilder(
                "# Offline Tool Compatibility Capability Frontier\n\n");
        out.append("## Verified protocol\n\n");
        out.append("- Run: `").append(data.runId()).append("`\n");
        out.append("- Git commit: `")
                .append(data.codeBaseline().gitCommit())
                .append("`\n");
        out.append("- Working tree dirty at execution: `")
                .append(data.codeBaseline().workingTreeDirty())
                .append("`\n");
        out.append("- Ollama runtime version: `")
                .append(data.ollamaRuntimeVersion())
                .append("`\n");
        out.append("- Tested installed models: `")
                .append(data.models().size())
                .append("`\n");
        out.append("- Locked rows per model: `")
                .append(data.orderedCaseIds().size())
                .append("` cases × `")
                .append(data.runSettings().repetitions())
                .append("` repetitions = `")
                .append(rowsPerModel)
                .append("`\n");

        out.append("\n## Qualification by tested installed artifact\n\n");
        out.append("| Position | Role | Installed tag | Digest | Recorded artifact size (bytes) | Passed rows | Passed every locked row |\n");
        out.append("| ---: | --- | --- | --- | ---: | ---: | --- |\n");
        for (ToolCompatibilityCohortFrontier.ModelObservation model : data.models()) {
            ToolCompatibilityCohortModelIdentity identity = model.identity();
            out.append("| ").append(identity.cohortPosition())
                    .append(" | ").append(role(identity.role()))
                    .append(" | `").append(identity.effectiveInstalledTag())
                    .append("` | `").append(identity.digest())
                    .append("` | ").append(size(model.sizeBytes()))
                    .append(" | ").append(model.passedRows())
                    .append(" / ").append(model.totalRows())
                    .append(" | ").append(model.passedEveryLockedRow() ? "yes" : "no")
                    .append(" |\n");
        }

        out.append("\n## Frontier\n\n");
        ToolCompatibilityCohortFrontier.Measurement measurement = data.measurement();
        out.append("- Status: `")
                .append(measurement.status() == ToolCompatibilityCohortFrontier.Status.MEASURABLE
                        ? "measurable" : "not measurable")
                .append("`\n");
        if (measurement.status() == ToolCompatibilityCohortFrontier.Status.MEASURABLE) {
            ToolCompatibilityCohortFrontier.ModelObservation frontier = measurement.frontier();
            ToolCompatibilityCohortModelIdentity identity = frontier.identity();
            out.append("- Qualifying models: `")
                    .append(measurement.qualifyingModels())
                    .append("`\n");
            out.append("- Frontier tag: `")
                    .append(identity.effectiveInstalledTag())
                    .append("`\n");
            out.append("- Frontier role: `")
                    .append(role(identity.role()))
                    .append("`\n");
            out.append("- Frontier digest: `")
                    .append(identity.digest())
                    .append("`\n");
            out.append("- Frontier recorded artifact size: `")
                    .append(frontier.sizeBytes())
                    .append("` bytes\n\n");
            out.append("> Among the tested installed models, `")
                    .append(identity.effectiveInstalledTag())
                    .append("` was the smallest model by recorded installed-artifact size that ")
                    .append("passed all locked cases in both repetitions under this exact protocol.\n");
        } else {
            out.append("- Reason: ")
                    .append(measurement.reason())
                    .append(".\n");
        }

        out.append("\n## Interpretation boundary\n\n")
                .append("`Recorded artifact size` is the byte size retained from the installed ")
                .append("Ollama artifact metadata; it is not parameter count or a normalized ")
                .append("measure across GGUF and MLX deployments. This frontier is limited to ")
                .append("the tested tags, full digests, recorded artifact/runtime formats, Ollama ")
                .append("version, eight locked cases, two repetitions, and untreated prompt policy ")
                .append("in this verified run. It is not a claim about the smallest model capable ")
                .append("of tool calling, a ranking, semantic ground truth, a production selection, ")
                .append("or general capability.\n");
        return out.toString();
    }

    private static String role(ToolCompatibilityCohortModelIdentity.Role role) {
        return role == ToolCompatibilityCohortModelIdentity.Role.REFERENCE
                ? "reference"
                : "peer";
    }

    private static String size(Long sizeBytes) {
        return sizeBytes == null ? "unavailable" : Long.toString(sizeBytes);
    }
}
