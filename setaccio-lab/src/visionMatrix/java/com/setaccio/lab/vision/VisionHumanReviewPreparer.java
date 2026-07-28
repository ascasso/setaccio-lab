package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VisionHumanReviewPreparer {

    static final String WORKSHEET_FILENAME = "HUMAN-REVIEW.md";

    private final ObjectMapper objectMapper;
    private final VisionCorpusReader corpusReader;

    VisionHumanReviewPreparer(ObjectMapper objectMapper, Blake3HashingService hashingService) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        if (hashingService == null) {
            throw new IllegalArgumentException("hashingService must not be null");
        }
        this.objectMapper = objectMapper;
        this.corpusReader = new VisionCorpusReader(objectMapper, hashingService);
    }

    PreparationResult prepare(
            Path baselineDirectory,
            Path candidateDirectory,
            Path corpusDirectory,
            Path outputRoot,
            String reviewId) {
        new VisionMatrixComparison(objectMapper).compare(baselineDirectory, candidateDirectory);
        VisionMatrixResult baseline = readResult(baselineDirectory, "baseline");
        VisionMatrixResult candidate = readResult(candidateDirectory, "candidate");
        LoadedVisionCorpus corpus = corpusReader.read(corpusDirectory);
        Map<String, LoadedVisionCorpus.LoadedVisionCase> cases = requireMatchingCases(baseline, corpus);

        String worksheet = render(
                baselineDirectory,
                candidateDirectory,
                baseline,
                candidate,
                cases,
                outputRoot.resolve(reviewId));
        Path outputDirectory = EvidenceRunDirectory.createNamed(outputRoot, reviewId);
        Path worksheetPath = outputDirectory.resolve(WORKSHEET_FILENAME);
        try {
            Files.writeString(worksheetPath, worksheet, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write private human-review worksheet", e);
        }
        return new PreparationResult(worksheetPath);
    }

    private VisionMatrixResult readResult(Path runDirectory, String label) {
        try {
            return objectMapper.readerFor(VisionMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(runDirectory.resolve(VisionMatrixProtocol.RAW_FILENAME).toFile());
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read verified " + label + " vision run", e);
        }
    }

    private static Map<String, LoadedVisionCorpus.LoadedVisionCase> requireMatchingCases(
            VisionMatrixResult baseline,
            LoadedVisionCorpus corpus) {
        Map<String, LoadedVisionCorpus.LoadedVisionCase> cases = new LinkedHashMap<>();
        for (LoadedVisionCorpus.LoadedVisionCase loadedCase : corpus.cases()) {
            cases.put(loadedCase.metadata().caseId(), loadedCase);
        }
        for (VisionMatrixInput input : baseline.inputs()) {
            LoadedVisionCorpus.LoadedVisionCase loadedCase = cases.get(input.caseId());
            if (loadedCase == null) {
                throw new IllegalArgumentException(
                        "Private corpus does not contain saved case " + input.caseId());
            }
            VisionCorpusCase metadata = loadedCase.metadata();
            if (!input.blake3().equals(metadata.blake3())
                    || !input.mimeType().equals(metadata.mimeType())) {
                throw new IllegalArgumentException(
                        "Private corpus case does not match saved input identity: " + input.caseId());
            }
        }
        return Map.copyOf(cases);
    }

    private static String render(
            Path baselineDirectory,
            Path candidateDirectory,
            VisionMatrixResult baseline,
            VisionMatrixResult candidate,
            Map<String, LoadedVisionCorpus.LoadedVisionCase> cases,
            Path outputDirectory) {
        StringBuilder out = new StringBuilder("# Private Vision Human-Review Worksheet\n\n");
        out.append("> Private local artifact: contains corpus reference metadata and raw model responses. ")
                .append("Keep this worksheet ignored and do not commit or publish it.\n\n");
        out.append("- Baseline run: `").append(inline(baselineDirectory.getFileName().toString())).append("`\n");
        out.append("- Candidate run: `").append(inline(candidateDirectory.getFileName().toString())).append("`\n");
        out.append("- Baseline prompt: `").append(inline(baseline.promptId())).append("` version `")
                .append(inline(baseline.promptVersion())).append("` (`")
                .append(inline(baseline.promptSha256())).append("`)\n");
        out.append("- Candidate prompt: `").append(inline(candidate.promptId())).append("` version `")
                .append(inline(candidate.promptVersion())).append("` (`")
                .append(inline(candidate.promptSha256())).append("`)\n");
        out.append("- Protocol: ").append(baseline.runSettings().models().size())
                .append(" model(s) × ").append(baseline.inputs().size())
                .append(" case(s) × ").append(baseline.runSettings().repetitions())
                .append(" repetitions\n\n");
        out.append("Both saved runs passed offline verification and the deterministic comparability gate before ")
                .append("this worksheet was written. Apply `docs/VISION-HUMAN-REVIEW.md`; this worksheet does not ")
                .append("perform semantic scoring or make a prompt decision.\n\n");

        for (String model : baseline.runSettings().models()) {
            for (VisionMatrixInput input : baseline.inputs()) {
                LoadedVisionCorpus.LoadedVisionCase loadedCase = cases.get(input.caseId());
                out.append("## `").append(inline(model)).append("` / `")
                        .append(inline(input.caseId())).append("`\n\n");
                renderCaseReference(out, loadedCase, outputDirectory);
                renderRun(out, "Baseline", baseline, rowsFor(baseline, model, input.caseId()));
                renderRun(out, "Candidate", candidate, rowsFor(candidate, model, input.caseId()));
                out.append("### Pair-level human comparison\n\n");
                out.append("- Did the candidate retain the primary expected concepts? \n");
                out.append("- Did the candidate reduce unsupported specificity? \n");
                out.append("- Did `unknown` suppress useful visible detail? \n");
                out.append("- Did repetition consistency change materially? \n");
                out.append("- Human notes: \n\n");
            }
        }

        out.append("## Final human decision\n\n");
        out.append("- [ ] Adopt the candidate prompt\n");
        out.append("- [ ] Revise the candidate prompt\n");
        out.append("- [ ] Reject the candidate prompt\n");
        out.append("- Evidence-backed rationale: \n");
        out.append("- Next bounded hypothesis: \n");
        return out.toString();
    }

    private static void renderCaseReference(
            StringBuilder out,
            LoadedVisionCorpus.LoadedVisionCase loadedCase,
            Path outputDirectory) {
        VisionCorpusCase metadata = loadedCase.metadata();
        String relativeImage = outputDirectory.relativize(loadedCase.imagePath())
                .toString()
                .replace(File.separatorChar, '/');
        out.append("### Private case reference\n\n");
        out.append("![Private source image](").append(relativeImage).append(")\n\n");
        out.append("- Reference observation: ").append(inline(metadata.referenceObservation())).append("\n");
        renderList(out, "Expected concepts", metadata.expectedConcepts());
        renderList(out, "Unsupported details", metadata.unsupportedDetails());
        renderList(out, "Limitations", metadata.limitations());
        out.append('\n');
    }

    private static void renderList(StringBuilder out, String label, List<String> values) {
        out.append("- ").append(label).append(":\n");
        for (String value : values) {
            out.append("  - ").append(inline(value)).append("\n");
        }
    }

    private static void renderRun(
            StringBuilder out,
            String label,
            VisionMatrixResult result,
            List<VisionMatrixRow> rows) {
        out.append("### ").append(label).append(" prompt v")
                .append(inline(result.promptVersion())).append(" responses\n\n");
        if (repetitionsMatch(rows)) {
            out.append("Repetitions matched exactly; review the shared successful response once.\n\n");
            renderResponse(out, "Shared response", rows.getFirst());
        } else {
            for (VisionMatrixRow row : rows) {
                renderResponse(out, "Repetition " + row.repetition(), row);
            }
        }
        out.append("#### Human judgment for ").append(label.toLowerCase()).append("\n\n");
        out.append("- Primary-concept retention: [ ] retained  [ ] partially retained  [ ] not retained\n");
        out.append("- Unsupported specificity (`location`, `identity`, `event`, `time`, or `other`; ")
                .append("record `avoided`, `unknown`, or `claimed`): \n");
        out.append("- Excessive `unknown`: [ ] no  [ ] yes — suppressed visible concept: \n");
        out.append("- Repetition finding: \n");
        out.append("- Notes: \n\n");
    }

    private static void renderResponse(StringBuilder out, String heading, VisionMatrixRow row) {
        out.append("#### ").append(heading).append("\n\n");
        out.append("- Invocation success: `").append(row.invocationSuccess()).append("`\n");
        out.append("- Structure complete: `").append(row.structureComplete()).append("`\n");
        if (row.errorCategory() != null) {
            out.append("- Error category: `").append(row.errorCategory().name().toLowerCase()).append("`\n");
        }
        out.append('\n').append(fenced(row.outputText())).append("\n\n");
    }

    private static List<VisionMatrixRow> rowsFor(VisionMatrixResult result, String model, String caseId) {
        return result.rows().stream()
                .filter(row -> model.equals(row.model()) && caseId.equals(row.caseId()))
                .sorted(java.util.Comparator.comparingInt(VisionMatrixRow::repetition))
                .toList();
    }

    private static boolean repetitionsMatch(List<VisionMatrixRow> rows) {
        return rows.size() > 1
                && rows.stream().allMatch(VisionMatrixRow::invocationSuccess)
                && rows.stream().map(VisionMatrixRow::outputText).distinct().count() == 1;
    }

    private static String fenced(String value) {
        String text = value == null || value.isBlank() ? "(no model output)" : value.strip();
        int longest = 0;
        int current = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '`') {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        return fence + "text\n" + text + "\n" + fence;
    }

    private static String inline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "<br>");
    }

    record PreparationResult(Path worksheet) {}
}
