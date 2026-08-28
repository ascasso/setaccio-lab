package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Compares two already-verified provider-free retrieval evaluations. */
final class RetrievalEvaluationComparison {

    private final ObjectMapper objectMapper;
    private final RetrievalEvaluationEvidence evidence;
    private final RetrievalEvaluationAnalyzer analyzer;

    RetrievalEvaluationComparison(
            ObjectMapper objectMapper,
            RetrievalCorpus corpus,
            RetrievalQueryCatalog catalog
    ) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        evidence = new RetrievalEvaluationEvidence(objectMapper, corpus, catalog);
        analyzer = new RetrievalEvaluationAnalyzer(corpus, catalog, new DeterministicLexicalRetriever());
    }

    ComparisonResult compare(Path baselineDirectory, Path candidateDirectory) {
        ComparableRun baseline = loadVerified(baselineDirectory, "baseline");
        ComparableRun candidate = loadVerified(candidateDirectory, "candidate");
        validateComparable(baseline, candidate);
        return new ComparisonResult(render(baseline, candidate));
    }

    private ComparableRun loadVerified(Path directory, String label) {
        RetrievalEvaluationEvidence.OfflineResult verification = evidence.verify(directory);
        if (!verification.valid()) {
            throw new IllegalArgumentException("The " + label + " run did not verify: "
                    + String.join(" ", verification.failures()));
        }
        try {
            EvidenceManifest manifest = new EvidenceManifestStore(objectMapper).read(directory);
            RetrievalEvaluationResult result = objectMapper.readerFor(RetrievalEvaluationResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(directory.resolve(RetrievalEvaluationProtocol.RAW_FILENAME).toFile());
            return new ComparableRun(manifest, result, analyzer.analyze(result));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not load verified " + label + " retrieval run", exception);
        }
    }

    private static void validateComparable(ComparableRun baseline, ComparableRun candidate) {
        List<String> failures = new ArrayList<>();
        if (!Objects.equals(baseline.manifest().executionEngine(), candidate.manifest().executionEngine())) {
            failures.add("execution engine differs");
        }
        if (!protocolIdentity(baseline.result()).equals(protocolIdentity(candidate.result()))) {
            failures.add("locked protocol, corpus, query catalog, or lexical parameters differ");
        }
        if (!fixtureIdentities(baseline.result()).equals(fixtureIdentities(candidate.result()))) {
            failures.add("fixture order or human-confirmed fixture labels differ");
        }
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException("Retrieval runs are not comparable: " + String.join("; ", failures));
        }
    }

    private static ProtocolIdentity protocolIdentity(RetrievalEvaluationResult result) {
        return new ProtocolIdentity(
                result.protocolVersion(),
                result.suite(),
                result.executionEngine(),
                result.executionStrategy(),
                result.corpusCatalogId(),
                result.corpusCatalogVersion(),
                result.corpusCatalogSha256(),
                result.queryCatalogId(),
                result.queryCatalogVersion(),
                result.queryCatalogSha256(),
                result.lexicalParameters());
    }

    private static List<FixtureIdentity> fixtureIdentities(RetrievalEvaluationResult result) {
        return result.rows().stream().map(row -> new FixtureIdentity(
                row.sequence(),
                row.caseId(),
                row.query(),
                row.expectedSupportingDocumentIds(),
                row.allowedSupportingDocumentIds(),
                row.forbiddenDocumentIds(),
                row.expectedNoMatch())).toList();
    }

    private static String render(ComparableRun baseline, ComparableRun candidate) {
        StringBuilder out = new StringBuilder("# Offline Retrieval Evaluation Comparison\n\n");
        out.append("- Baseline run: `").append(baseline.manifest().runId()).append("`\n");
        out.append("- Candidate run: `").append(candidate.manifest().runId()).append("`\n");
        out.append("- Corpus: `").append(baseline.result().corpusCatalogId()).append("` version `")
                .append(baseline.result().corpusCatalogVersion()).append("` (`")
                .append(baseline.result().corpusCatalogSha256()).append("`)\n");
        out.append("- Query catalog: `").append(baseline.result().queryCatalogId()).append("` version `")
                .append(baseline.result().queryCatalogVersion()).append("` (`")
                .append(baseline.result().queryCatalogSha256()).append("`)\n");
        out.append("- Retrieval method: `").append(baseline.result().lexicalParameters().methodId())
                .append("` version `").append(baseline.result().lexicalParameters().methodVersion()).append("`\n");

        out.append("\n## Metric deltas\n\n");
        out.append("| Measure | Baseline | Candidate | Delta |\n");
        out.append("| --- | ---: | ---: | ---: |\n");
        metric(out, "Expected supporting document retrieved",
                baseline.analysis().expectedSupportingDocumentsRetrieved(),
                candidate.analysis().expectedSupportingDocumentsRetrieved());
        metric(out, "Expected supporting document in top 1",
                baseline.analysis().expectedSupportingDocumentsInTop1(),
                candidate.analysis().expectedSupportingDocumentsInTop1());
        metric(out, "Expected supporting document in top 3",
                baseline.analysis().expectedSupportingDocumentsInTop3(),
                candidate.analysis().expectedSupportingDocumentsInTop3());
        metric(out, "Fixtures retrieving a forbidden document",
                baseline.analysis().forbiddenDocumentRetrievedFixtures(),
                candidate.analysis().forbiddenDocumentRetrievedFixtures());
        metric(out, "Correct no-match",
                baseline.analysis().correctNoMatchFixtures(),
                candidate.analysis().correctNoMatchFixtures());
        metric(out, "Rows stable across immediate repeat",
                baseline.analysis().stableRows(),
                candidate.analysis().stableRows());

        out.append("\n## Retrieved-document deltas\n\n");
        out.append("| Case | Baseline document IDs | Candidate document IDs |\n");
        out.append("| --- | --- | --- |\n");
        Map<String, RetrievalEvaluationRow> candidateRows = candidate.result().rows().stream()
                .collect(Collectors.toMap(RetrievalEvaluationRow::caseId, row -> row, (first, ignored) -> first,
                        LinkedHashMap::new));
        for (RetrievalEvaluationRow baselineRow : baseline.result().rows()) {
            RetrievalEvaluationRow candidateRow = candidateRows.get(baselineRow.caseId());
            out.append("| `").append(baselineRow.caseId()).append("` | ")
                    .append(documentIds(baselineRow)).append(" | ")
                    .append(documentIds(candidateRow)).append(" |\n");
        }

        out.append("\nThis deterministic report compares only verified retrieval evidence. It does not compare ")
                .append("generated answers, embedding behavior, semantic relevance beyond the locked labels, or model quality.\n");
        return out.toString();
    }

    private static void metric(StringBuilder out, String label, int baseline, int candidate) {
        out.append("| ").append(label).append(" | ").append(baseline).append(" | ")
                .append(candidate).append(" | ").append(signed(candidate - baseline)).append(" |\n");
    }

    private static String documentIds(RetrievalEvaluationRow row) {
        if (row == null || row.retrievedDocuments().isEmpty()) {
            return "none";
        }
        return row.retrievedDocuments().stream()
                .map(RetrievalEvaluationRetrievedDocument::documentId)
                .map(documentId -> "`" + documentId + "`")
                .collect(Collectors.joining(", "));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    record ComparisonResult(String report) {}

    private record ComparableRun(
            EvidenceManifest manifest,
            RetrievalEvaluationResult result,
            RetrievalEvaluationAnalyzer.Analysis analysis
    ) {}

    private record ProtocolIdentity(
            int protocolVersion,
            String suite,
            String executionEngine,
            String executionStrategy,
            String corpusCatalogId,
            int corpusCatalogVersion,
            String corpusCatalogSha256,
            String queryCatalogId,
            int queryCatalogVersion,
            String queryCatalogSha256,
            RetrievalLexicalParameters lexicalParameters
    ) {}

    private record FixtureIdentity(
            int sequence,
            String caseId,
            String query,
            List<String> expectedSupportingDocumentIds,
            List<String> allowedSupportingDocumentIds,
            List<String> forbiddenDocumentIds,
            boolean expectedNoMatch
    ) {}
}
