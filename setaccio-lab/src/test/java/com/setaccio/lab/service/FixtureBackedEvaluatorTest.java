package com.setaccio.lab.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureBackedEvaluatorTest {

    private final FixtureBackedEvaluator evaluator = new FixtureBackedEvaluator();

    @Test
    void evaluatePassesWhenResponseContainsEveryRequiredFixtureTerm() {
        EvaluationResponse response = evaluator.evaluate(request("JSON files are written under build/lab-results/."));

        assertThat(response.isPass()).isTrue();
        assertThat(response.getScore()).isEqualTo(1.0f);
        assertThat(response.getFeedback()).contains("every required fixture term");
        assertThat(response.getMetadata()).containsEntry("missingTerms", List.of());
    }

    @Test
    void evaluateReturnsPartialFailureWhenResponseMissesFixtureTerms() {
        EvaluationResponse response = evaluator.evaluate(request("JSON files are written locally."));

        assertThat(response.isPass()).isFalse();
        assertThat(response.getScore()).isEqualTo(0.5f);
        assertThat(response.getFeedback()).contains("build/lab-results");
        assertThat(response.getMetadata()).containsEntry("missingTerms", List.of("build/lab-results"));
    }

    @Test
    void evaluateFailsClearlyWhenTheFixtureDoesNotDeclareRequiredTerms() {
        EvaluationResponse response = evaluator.evaluate(new EvaluationRequest(
                "Question",
                List.of(new Document("Context")),
                "Response"
        ));

        assertThat(response.isPass()).isFalse();
        assertThat(response.getScore()).isZero();
        assertThat(response.getFeedback()).contains("requires at least one required term");
    }

    private EvaluationRequest request(String responseText) {
        return new EvaluationRequest(
                "Where are results written?",
                List.of(new Document(
                        "Results are JSON files under build/lab-results/.",
                        Map.of(FixtureBackedEvaluator.REQUIRED_TERMS_METADATA_KEY,
                                List.of("json", "build/lab-results"))
                )),
                responseText
        );
    }
}
