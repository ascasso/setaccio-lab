package com.setaccio.lab.service;

import com.setaccio.lab.model.EvaluationBenchmarkFixture;
import com.setaccio.lab.model.EvaluationBenchmarkResult;
import com.setaccio.lab.model.EvaluationBenchmarkRow;
import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class EvaluationBenchmarkService {

    public static final String SUITE = "evaluation";

    private static final Logger logger = LoggerFactory.getLogger(EvaluationBenchmarkService.class);

    private static final List<EvaluationBenchmarkFixture> DEFAULT_FIXTURES = List.of(
            new EvaluationBenchmarkFixture(
                    "result-output-supported",
                    "Where are local benchmark results written?",
                    "Benchmark results are written as JSON under build/lab-results/.",
                    "The benchmark writes JSON result files under build/lab-results/.",
                    List.of("json", "build/lab-results")
            ),
            new EvaluationBenchmarkFixture(
                    "result-output-unsupported",
                    "Where are local benchmark results written?",
                    "Benchmark results are written as JSON under build/lab-results/.",
                    "The benchmark stores result rows in a database.",
                    List.of("json", "build/lab-results")
            ),
            new EvaluationBenchmarkFixture(
                    "offline-test-partial",
                    "What do default benchmark tests avoid?",
                    "Default test runs do not call live Ollama or remote providers.",
                    "They avoid remote providers.",
                    List.of("live ollama", "remote providers")
            )
    );

    private final Evaluator evaluator;
    private final LabResultWriter labResultWriter;

    public EvaluationBenchmarkService(@Qualifier("fixtureBackedEvaluator") Evaluator evaluator,
                                      LabResultWriter labResultWriter) {
        this.evaluator = evaluator;
        this.labResultWriter = labResultWriter;
    }

    public EvaluationBenchmarkResult run(List<String> fixtureIds) {
        Instant startedAt = Instant.now();
        List<EvaluationBenchmarkRow> runs = resolveFixtures(fixtureIds).stream()
                .map(this::evaluate)
                .toList();
        EvaluationBenchmarkResult result = new EvaluationBenchmarkResult(
                SUITE,
                FixtureBackedEvaluator.PROVIDER,
                FixtureBackedEvaluator.MODEL,
                startedAt,
                Instant.now(),
                hostName(),
                runs
        );
        labResultWriter.write(SUITE, result.startedAt(), result);
        return result;
    }

    public static List<EvaluationBenchmarkFixture> defaultFixtures() {
        return DEFAULT_FIXTURES;
    }

    private EvaluationBenchmarkRow evaluate(EvaluationBenchmarkFixture fixture) {
        try {
            EvaluationResponse response = evaluator.evaluate(new EvaluationRequest(
                    fixture.userText(),
                    List.of(new Document(
                            fixture.contextText() == null ? "" : fixture.contextText(),
                            Map.of(FixtureBackedEvaluator.REQUIRED_TERMS_METADATA_KEY, fixture.requiredTerms())
                    )),
                    fixture.responseText()
            ));
            return EvaluationBenchmarkRow.completed(
                    fixture,
                    FixtureBackedEvaluator.PROVIDER,
                    FixtureBackedEvaluator.MODEL,
                    response
            );
        } catch (Exception e) {
            logger.warn("Evaluation fixture failed for fixtureId={}: {}", fixture.id(), e.getMessage());
            return EvaluationBenchmarkRow.failed(
                    fixture,
                    FixtureBackedEvaluator.PROVIDER,
                    FixtureBackedEvaluator.MODEL,
                    e.getMessage()
            );
        }
    }

    private List<EvaluationBenchmarkFixture> resolveFixtures(List<String> fixtureIds) {
        if (fixtureIds == null || fixtureIds.isEmpty()) {
            return DEFAULT_FIXTURES;
        }
        Map<String, EvaluationBenchmarkFixture> fixturesById = new LinkedHashMap<>();
        for (EvaluationBenchmarkFixture fixture : DEFAULT_FIXTURES) {
            fixturesById.put(fixture.id(), fixture);
        }
        List<String> unknown = fixtureIds.stream()
                .filter(id -> !fixturesById.containsKey(id))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown evaluation fixture IDs: " + String.join(", ", unknown));
        }
        return fixtureIds.stream().map(fixturesById::get).toList();
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
