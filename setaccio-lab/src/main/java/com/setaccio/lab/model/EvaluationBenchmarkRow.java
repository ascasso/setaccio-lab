package com.setaccio.lab.model;

import java.util.Map;
import org.springframework.ai.evaluation.EvaluationResponse;

public record EvaluationBenchmarkRow(
        String fixtureId,
        String userText,
        String contextText,
        String responseText,
        String evaluatorProvider,
        String evaluatorModel,
        Boolean passed,
        Float score,
        String feedback,
        Map<String, Object> evaluatorMetadata,
        boolean success,
        String error
) {
    public static EvaluationBenchmarkRow completed(EvaluationBenchmarkFixture fixture,
                                                    String evaluatorProvider,
                                                    String evaluatorModel,
                                                    EvaluationResponse response) {
        return new EvaluationBenchmarkRow(
                fixture.id(),
                fixture.userText(),
                fixture.contextText(),
                fixture.responseText(),
                evaluatorProvider,
                evaluatorModel,
                response.isPass(),
                response.getScore(),
                response.getFeedback(),
                response.getMetadata(),
                true,
                null
        );
    }

    public static EvaluationBenchmarkRow failed(EvaluationBenchmarkFixture fixture,
                                                 String evaluatorProvider,
                                                 String evaluatorModel,
                                                 String error) {
        return new EvaluationBenchmarkRow(
                fixture.id(),
                fixture.userText(),
                fixture.contextText(),
                fixture.responseText(),
                evaluatorProvider,
                evaluatorModel,
                null,
                null,
                null,
                Map.of(),
                false,
                error
        );
    }
}
