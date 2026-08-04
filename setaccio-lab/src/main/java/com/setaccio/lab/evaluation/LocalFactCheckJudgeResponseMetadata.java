package com.setaccio.lab.evaluation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record LocalFactCheckJudgeResponseMetadata(
        String responseId,
        String responseModel,
        Map<String, Object> attributes
) {
    public LocalFactCheckJudgeResponseMetadata {
        responseId = responseId == null ? "" : responseId;
        responseModel = responseModel == null ? "" : responseModel;
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
