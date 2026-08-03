package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class LocalFactCheckFixtureCatalog {

    public static final String ID = "local-fact-check-fixtures";
    public static final String VERSION = "1";
    public static final String RESOURCE_PATH = "evaluation/local-fact-check-fixtures-v1.json";
    public static final int FIXTURE_COUNT = 6;
    public static final int PAIR_COUNT = 3;

    private final List<LocalFactCheckFixture> fixtures;
    private final Map<String, LocalFactCheckFixture> fixturesById;
    private final String sha256;

    public LocalFactCheckFixtureCatalog(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        byte[] bytes = loadBytes();
        CatalogDocument catalog = parse(objectMapper, bytes);
        if (!ID.equals(catalog.catalogId())) {
            throw new IllegalStateException("Unexpected fact-check fixture catalog ID: " + catalog.catalogId());
        }
        if (!VERSION.equals(catalog.catalogVersion())) {
            throw new IllegalStateException("Unsupported fact-check fixture catalog version: " + catalog.catalogVersion());
        }
        fixtures = validate(catalog.fixtures());
        fixturesById = fixtures.stream().collect(Collectors.toUnmodifiableMap(
                LocalFactCheckFixture::id,
                Function.identity()));
        sha256 = EvidenceIntegrity.sha256(bytes);
    }

    public String id() {
        return ID;
    }

    public String version() {
        return VERSION;
    }

    public String sha256() {
        return sha256;
    }

    public List<LocalFactCheckFixture> fixtures() {
        return fixtures;
    }

    public LocalFactCheckFixture require(String fixtureId) {
        if (fixtureId == null || fixtureId.isBlank()) {
            throw new IllegalArgumentException("fixtureId must not be blank");
        }
        LocalFactCheckFixture fixture = fixturesById.get(fixtureId);
        if (fixture == null) {
            throw new IllegalArgumentException("Unknown fact-check fixture ID: " + fixtureId);
        }
        return fixture;
    }

    private static List<LocalFactCheckFixture> validate(List<LocalFactCheckFixture> candidates) {
        List<LocalFactCheckFixture> fixed = candidates == null ? List.of() : List.copyOf(candidates);
        if (fixed.size() != FIXTURE_COUNT) {
            throw new IllegalStateException("Fact-check fixture catalog must contain exactly six fixtures");
        }
        if (fixed.stream().map(LocalFactCheckFixture::id).distinct().count() != FIXTURE_COUNT) {
            throw new IllegalStateException("Fact-check fixture IDs must be unique");
        }
        long supported = fixed.stream()
                .filter(fixture -> fixture.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED)
                .count();
        long unsupported = fixed.stream()
                .filter(fixture -> fixture.expectedVerdict() == LocalFactCheckExpectedVerdict.UNSUPPORTED)
                .count();
        if (supported != PAIR_COUNT || unsupported != PAIR_COUNT) {
            throw new IllegalStateException("Fact-check fixture catalog must contain three supported and three unsupported fixtures");
        }

        Map<String, List<LocalFactCheckFixture>> pairs = fixed.stream().collect(Collectors.groupingBy(
                LocalFactCheckFixture::pairId,
                LinkedHashMap::new,
                Collectors.toList()));
        if (pairs.size() != PAIR_COUNT) {
            throw new IllegalStateException("Fact-check fixture catalog must contain exactly three pairs");
        }
        pairs.forEach(LocalFactCheckFixtureCatalog::validatePair);
        return fixed;
    }

    private static void validatePair(String pairId, List<LocalFactCheckFixture> pair) {
        if (pair.size() != 2) {
            throw new IllegalStateException("Fact-check pair must contain exactly two fixtures: " + pairId);
        }
        if (!pair.getFirst().document().equals(pair.getLast().document())) {
            throw new IllegalStateException("Fact-check pair must share exact document text: " + pairId);
        }
        if (pair.stream().map(LocalFactCheckFixture::expectedVerdict).distinct().count() != 2) {
            throw new IllegalStateException("Fact-check pair must contain one supported and one unsupported fixture: " + pairId);
        }
    }

    private static byte[] loadBytes() {
        try (var input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load tracked fact-check fixture catalog " + RESOURCE_PATH, exception);
        }
    }

    private static CatalogDocument parse(ObjectMapper objectMapper, byte[] bytes) {
        try {
            return objectMapper.readerFor(CatalogDocument.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse tracked fact-check fixture catalog", exception);
        }
    }

    private record CatalogDocument(
            String catalogId,
            String catalogVersion,
            List<LocalFactCheckFixture> fixtures
    ) {}
}
