package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceSuiteRoot;
import java.util.LinkedHashMap;
import java.util.Map;

/** Locked provider-free protocol metadata for Phase 5 R3 retrieval evaluation. */
final class RetrievalEvaluationProtocol {

    static final int VERSION = 1;
    static final String SUITE = "public-safe-retrieval-evaluation";
    static final EvidenceSuiteRoot EVIDENCE_ROOT = EvidenceSuiteRoot.of("retrieval-evaluation");
    static final String EXECUTION_ENGINE = "plain-java-exact-distinct-query-coverage";
    static final String EXECUTION_STRATEGY = "sequential-single-pass-with-immediate-repeatability-check";
    static final String RAW_FILENAME = "retrieval-evaluation-results.json";

    private RetrievalEvaluationProtocol() {}

    static Map<String, Object> manifestSettings(RetrievalEvaluationResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("corpusCatalogId", result.corpusCatalogId());
        settings.put("corpusCatalogVersion", result.corpusCatalogVersion());
        settings.put("corpusCatalogSha256", result.corpusCatalogSha256());
        settings.put("queryCatalogId", result.queryCatalogId());
        settings.put("queryCatalogVersion", result.queryCatalogVersion());
        settings.put("queryCatalogSha256", result.queryCatalogSha256());
        settings.put("lexicalParameters", result.lexicalParameters());
        settings.put("fixtureCount", result.rows().size());
        return Map.copyOf(settings);
    }
}
