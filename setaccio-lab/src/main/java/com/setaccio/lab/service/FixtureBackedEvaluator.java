package com.setaccio.lab.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.stereotype.Service;

@Service
public class FixtureBackedEvaluator implements Evaluator {

    public static final String PROVIDER = "fixture";
    public static final String MODEL = "term-containment-v1";
    public static final String REQUIRED_TERMS_METADATA_KEY = "requiredTerms";

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        List<String> requiredTerms = requiredTerms(request);
        if (requiredTerms.isEmpty()) {
            return new EvaluationResponse(
                    false,
                    0.0f,
                    "Fixture evaluator requires at least one required term.",
                    Map.of(REQUIRED_TERMS_METADATA_KEY, List.of(), "missingTerms", List.of(), "evaluationType", MODEL)
            );
        }
        List<String> missingTerms = requiredTerms.stream()
                .filter(term -> !normalize(request.getResponseContent()).contains(normalize(term)))
                .toList();
        float score = (requiredTerms.size() - missingTerms.size()) / (float) requiredTerms.size();
        boolean passed = missingTerms.isEmpty();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(REQUIRED_TERMS_METADATA_KEY, requiredTerms);
        metadata.put("missingTerms", missingTerms);
        metadata.put("evaluationType", MODEL);

        String feedback = passed
                ? "Response contains every required fixture term."
                : "Response is missing required fixture terms: " + String.join(", ", missingTerms);
        return new EvaluationResponse(passed, score, feedback, Map.copyOf(metadata));
    }

    private List<String> requiredTerms(EvaluationRequest request) {
        return request.getDataList().stream()
                .map(Document::getMetadata)
                .map(metadata -> metadata.get(REQUIRED_TERMS_METADATA_KEY))
                .filter(List.class::isInstance)
                .map(value -> (List<?>) value)
                .flatMap(List::stream)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
