package com.setaccio.lab.model;

import java.util.List;

public record ToolSearchObservation(
        String callId,
        String query,
        boolean completed,
        List<String> discoveredTools
) {
    public ToolSearchObservation {
        discoveredTools = discoveredTools == null ? List.of() : List.copyOf(discoveredTools);
    }
}
