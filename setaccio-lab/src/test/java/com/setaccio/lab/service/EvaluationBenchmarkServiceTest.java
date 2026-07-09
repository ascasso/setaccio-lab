package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.EvaluationBenchmarkResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationBenchmarkServiceTest {

    @Test
    void runProducesDeterministicFixtureVerdictsAndWritesJson() throws Exception {
        Path outputDir = Files.createTempDirectory("evaluation-results-");
        EvaluationBenchmarkService service = newService(outputDir);

        EvaluationBenchmarkResult result = service.run(List.of());

        assertThat(result.suite()).isEqualTo("evaluation");
        assertThat(result.evaluatorProvider()).isEqualTo("fixture");
        assertThat(result.evaluatorModel()).isEqualTo("term-containment-v1");
        assertThat(result.runs()).extracting("fixtureId")
                .containsExactly("result-output-supported", "result-output-unsupported", "offline-test-partial");
        assertThat(result.runs()).extracting("passed")
                .containsExactly(true, false, false);
        assertThat(result.runs()).extracting("score")
                .containsExactly(1.0f, 0.0f, 0.5f);
        assertThat(result.runs()).allSatisfy(row -> {
            assertThat(row.success()).isTrue();
            assertThat(row.error()).isNull();
            assertThat(row.evaluatorMetadata()).containsKey("requiredTerms");
        });
        assertThat(Files.list(outputDir))
                .anySatisfy(path -> assertThat(path.getFileName().toString()).endsWith("-evaluation.json"));
    }

    @Test
    void runSelectsRequestedFixturesOnly() throws Exception {
        EvaluationBenchmarkResult result = newService(Files.createTempDirectory("evaluation-results-"))
                .run(List.of("offline-test-partial"));

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.fixtureId()).isEqualTo("offline-test-partial");
            assertThat(row.passed()).isFalse();
            assertThat(row.score()).isEqualTo(0.5f);
        });
    }

    @Test
    void runRejectsUnknownFixtureIds() throws Exception {
        EvaluationBenchmarkService service = newService(Files.createTempDirectory("evaluation-results-"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.run(List.of("missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown evaluation fixture IDs: missing");
    }

    private EvaluationBenchmarkService newService(Path outputDir) {
        return new EvaluationBenchmarkService(
                new FixtureBackedEvaluator(),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString())
        );
    }
}
