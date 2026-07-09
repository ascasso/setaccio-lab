package com.setaccio.lab.controller;

import com.setaccio.lab.model.EvaluationBenchmarkResult;
import com.setaccio.lab.model.EvaluationBenchmarkRow;
import com.setaccio.lab.service.EvaluationBenchmarkService;
import com.setaccio.lab.service.FixtureBackedEvaluator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvaluationBenchmarkControllerTest {

    private MockMvc mockMvc;

    private EvaluationBenchmarkService evaluationBenchmarkService;

    @BeforeEach
    void setUp() {
        evaluationBenchmarkService = mock(EvaluationBenchmarkService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EvaluationBenchmarkController(evaluationBenchmarkService)).build();
    }

    @Test
    void runUsesAllFixturesWhenTheRequestBodyIsAbsent() throws Exception {
        when(evaluationBenchmarkService.run(anyList())).thenAnswer(invocation -> {
            assertThat(invocation.<List<String>>getArgument(0)).isEmpty();
            return result();
        });

        mockMvc.perform(post("/api/lab/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suite").value("evaluation"))
                .andExpect(jsonPath("$.runs[0].passed").value(true));
    }

    @Test
    void runNormalizesAndDelegatesExplicitFixtureIds() throws Exception {
        when(evaluationBenchmarkService.run(anyList())).thenAnswer(invocation -> {
            assertThat(invocation.<List<String>>getArgument(0)).containsExactly("offline-test-partial");
            return result();
        });

        mockMvc.perform(post("/api/lab/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fixtureIds": [" offline-test-partial ", "offline-test-partial", " "]}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void runReturnsBadRequestForUnknownFixtureIds() throws Exception {
        when(evaluationBenchmarkService.run(anyList()))
                .thenThrow(new IllegalArgumentException("Unknown evaluation fixture IDs: missing"));

        mockMvc.perform(post("/api/lab/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fixtureIds": ["missing"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private EvaluationBenchmarkResult result() {
        return new EvaluationBenchmarkResult(
                "evaluation",
                FixtureBackedEvaluator.PROVIDER,
                FixtureBackedEvaluator.MODEL,
                Instant.parse("2026-07-09T00:00:00Z"),
                Instant.parse("2026-07-09T00:00:01Z"),
                "test-host",
                List.of(new EvaluationBenchmarkRow(
                        "fixture",
                        "question",
                        "context",
                        "response",
                        FixtureBackedEvaluator.PROVIDER,
                        FixtureBackedEvaluator.MODEL,
                        true,
                        1.0f,
                        "passed",
                        Map.of(),
                        true,
                        null
                ))
        );
    }
}
