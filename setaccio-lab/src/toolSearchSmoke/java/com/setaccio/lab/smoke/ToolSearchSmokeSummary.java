package com.setaccio.lab.smoke;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ToolSearchSmokeSummary {

    static final String DIAGNOSTIC_WARNING =
            "Model behavior categories are for diagnosis only ��� never block merges on them unless a specific hypothesis was stated.";

    enum Bucket {
        NO_TOOL_SEARCH_CALL("No Tool Search call"),
        ZERO_MATCHES("Search completed with zero matches"),
        NON_EMPTY_DISCOVERY("Non-empty discovery"),
        DISCOVERY_MISMATCH("Discovery mismatch (raw vs. normalized)"),
        REQUIRED_DISCOVERED_NOT_EXECUTED("Required tool discovered but not executed"),
        REQUIRED_EXECUTED_OUTPUT_FAILED("Required tool executed but output contract failed");

        private final String label;

        Bucket(String label) {
            this.label = label;
        }
    }

    private final Map<Bucket, List<String>> casesByBucket = new EnumMap<>(Bucket.class);
    private final List<String> hardFailures = new ArrayList<>();

    ToolSearchSmokeSummary() {
        for (Bucket bucket : Bucket.values()) {
            casesByBucket.put(bucket, new ArrayList<>());
        }
    }

    void add(Bucket bucket, String caseId) {
        casesByBucket.get(bucket).add(caseId);
    }

    void hardFailure(String detail) {
        hardFailures.add(detail);
    }

    List<String> cases(Bucket bucket) {
        return List.copyOf(casesByBucket.get(bucket));
    }

    List<String> hardFailures() {
        return List.copyOf(hardFailures);
    }

    boolean hasHardFailures() {
        return !hardFailures.isEmpty();
    }

    void printTo(PrintStream out) {
        out.println();
        out.println("Tool Search smoke summary");
        for (Bucket bucket : Bucket.values()) {
            List<String> caseIds = casesByBucket.get(bucket);
            String details = caseIds.isEmpty() ? "-" : String.join(", ", caseIds);
            out.printf("  %-57s %2d  %s%n", bucket.label + ":", caseIds.size(), details);
        }
        out.println("  Hard failures: " + hardFailures.size());
        for (String failure : hardFailures) {
            out.println("    - " + failure);
        }
        out.println();
        out.println(DIAGNOSTIC_WARNING);
    }
}
