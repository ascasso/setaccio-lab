package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class LocalFactCheckFixtureReview {

    public static final String ID = "local-fact-check-fixture-review";
    public static final String VERSION = "1";
    public static final String STATUS = "confirmed";
    public static final String RESOURCE_PATH = "evaluation/local-fact-check-fixture-review-v1.json";

    private final String catalogId;
    private final String catalogVersion;
    private final String catalogSha256;
    private final LocalDate confirmedOn;
    private final List<String> confirmedFixtureIds;
    private final String sha256;

    @Autowired
    public LocalFactCheckFixtureReview(ObjectMapper objectMapper, LocalFactCheckFixtureCatalog catalog) {
        this(objectMapper, catalog, loadBytes());
    }

    LocalFactCheckFixtureReview(
            ObjectMapper objectMapper,
            LocalFactCheckFixtureCatalog catalog,
            byte[] bytes
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("review bytes must not be empty");
        }

        ReviewDocument review = parse(objectMapper, bytes);
        if (!ID.equals(review.reviewId())) {
            throw new IllegalStateException("Unexpected fact-check fixture review ID: " + review.reviewId());
        }
        if (!VERSION.equals(review.reviewVersion())) {
            throw new IllegalStateException("Unsupported fact-check fixture review version: " + review.reviewVersion());
        }
        if (!STATUS.equals(review.status())) {
            throw new IllegalStateException("Fact-check fixture review is not confirmed");
        }
        if (!catalog.id().equals(review.catalogId()) || !catalog.version().equals(review.catalogVersion())) {
            throw new IllegalStateException("Fact-check fixture review catalog identity does not match");
        }
        if (!catalog.sha256().equals(review.catalogSha256())) {
            throw new IllegalStateException("Fact-check fixture review catalog digest does not match");
        }

        List<String> expectedFixtureIds = catalog.fixtures().stream()
                .map(LocalFactCheckFixture::id)
                .toList();
        List<String> reviewedFixtureIds = review.confirmedFixtureIds() == null
                ? List.of()
                : List.copyOf(review.confirmedFixtureIds());
        if (!expectedFixtureIds.equals(reviewedFixtureIds)) {
            throw new IllegalStateException("Fact-check fixture review must confirm every fixture in catalog order");
        }

        catalogId = review.catalogId();
        catalogVersion = review.catalogVersion();
        catalogSha256 = review.catalogSha256();
        confirmedOn = parseDate(review.confirmedOn());
        confirmedFixtureIds = reviewedFixtureIds;
        sha256 = EvidenceIntegrity.sha256(bytes);
    }

    public String id() {
        return ID;
    }

    public String version() {
        return VERSION;
    }

    public String status() {
        return STATUS;
    }

    public String catalogId() {
        return catalogId;
    }

    public String catalogVersion() {
        return catalogVersion;
    }

    public String catalogSha256() {
        return catalogSha256;
    }

    public LocalDate confirmedOn() {
        return confirmedOn;
    }

    public List<String> confirmedFixtureIds() {
        return confirmedFixtureIds;
    }

    public String sha256() {
        return sha256;
    }

    private static byte[] loadBytes() {
        try (var input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load tracked fact-check fixture review " + RESOURCE_PATH, exception);
        }
    }

    private static ReviewDocument parse(ObjectMapper objectMapper, byte[] bytes) {
        try {
            return objectMapper.readerFor(ReviewDocument.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse tracked fact-check fixture review", exception);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalStateException("Fact-check fixture review confirmedOn must be an ISO date", exception);
        }
    }

    private record ReviewDocument(
            String reviewId,
            String reviewVersion,
            String status,
            String catalogId,
            String catalogVersion,
            String catalogSha256,
            String confirmedOn,
            List<String> confirmedFixtureIds
    ) {}
}
