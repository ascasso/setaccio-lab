package com.setaccio.lab.model;

import java.util.List;

public record EvaluationBenchmarkRequest(
        List<String> fixtureIds
) {}
