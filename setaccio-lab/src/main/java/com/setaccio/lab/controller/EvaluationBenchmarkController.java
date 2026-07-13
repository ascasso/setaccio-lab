package com.setaccio.lab.controller;

import com.setaccio.lab.model.EvaluationBenchmarkRequest;
import com.setaccio.lab.model.EvaluationBenchmarkResult;
import com.setaccio.lab.service.EvaluationBenchmarkService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("local")
@RestController
@RequestMapping("/api/lab/evaluations")
public class EvaluationBenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationBenchmarkController.class);

    private final EvaluationBenchmarkService evaluationBenchmarkService;

    public EvaluationBenchmarkController(EvaluationBenchmarkService evaluationBenchmarkService) {
        this.evaluationBenchmarkService = evaluationBenchmarkService;
    }

    @PostMapping
    public ResponseEntity<EvaluationBenchmarkResult> run(
            @RequestBody(required = false) EvaluationBenchmarkRequest request) {
        List<String> fixtureIds = request == null ? List.of() : normalizeFixtureIds(request.fixtureIds());
        try {
            logger.info("Evaluation benchmark requested: {} fixture selection entries", fixtureIds.size());
            return ResponseEntity.ok(evaluationBenchmarkService.run(fixtureIds));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private List<String> normalizeFixtureIds(List<String> fixtureIds) {
        if (fixtureIds == null) {
            return List.of();
        }
        return fixtureIds.stream()
                .map(id -> id == null ? "" : id.trim())
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
    }
}
