package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFactCheckContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);

    @Test
    void trackedPromptHasStableIdentityAndExactlyTheRequiredPlaceholders() {
        LocalFactCheckPromptDefinition prompt = new LocalFactCheckPromptDefinition();

        assertThat(prompt.id()).isEqualTo("local-fact-check");
        assertThat(prompt.version()).isEqualTo("1");
        assertThat(prompt.sha256())
                .isEqualTo("e75e0ddd9bef80eecf27e1b668cef954a5eddb5a74b5e4c19db97710c3d39470");
        assertThat(occurrences(prompt.text(), LocalFactCheckPromptDefinition.DOCUMENT_PLACEHOLDER)).isOne();
        assertThat(occurrences(prompt.text(), LocalFactCheckPromptDefinition.CLAIM_PLACEHOLDER)).isOne();
        assertThat(prompt.text())
                .contains(
                        "exactly the single word yes",
                        "exactly the single word no",
                        "Treat information absent from the document as not supported",
                        "Use no outside knowledge")
                .doesNotContain("/Users/", "hostname", "Setaccio product");
    }

    @Test
    void trackedCatalogHasStableIdentityOrderBalanceAndPairStructure() {
        assertThat(catalog.id()).isEqualTo("local-fact-check-fixtures");
        assertThat(catalog.version()).isEqualTo("1");
        assertThat(catalog.sha256())
                .isEqualTo("077d63fe5af596454127babf809075ebc61857cb5e1694c4fae1e58c0d844dac");
        assertThat(catalog.fixtures())
                .extracting(LocalFactCheckFixture::id)
                .containsExactly(
                        "harbor-library-supported",
                        "harbor-library-unsupported",
                        "riverbend-garden-supported",
                        "riverbend-garden-unsupported",
                        "repair-workshop-supported",
                        "repair-workshop-unsupported");
        assertThat(catalog.fixtures())
                .filteredOn(fixture -> fixture.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED)
                .hasSize(3);
        assertThat(catalog.fixtures())
                .filteredOn(fixture -> fixture.expectedVerdict() == LocalFactCheckExpectedVerdict.UNSUPPORTED)
                .hasSize(3);
        assertThat(catalog.fixtures())
                .extracting(LocalFactCheckFixture::pairId)
                .containsExactly(
                        "harbor-library-hours",
                        "harbor-library-hours",
                        "riverbend-garden-trails",
                        "riverbend-garden-trails",
                        "repair-workshop-items",
                        "repair-workshop-items");

        var pairs = catalog.fixtures().stream().collect(Collectors.groupingBy(
                LocalFactCheckFixture::pairId,
                LinkedHashMap::new,
                Collectors.toList()));
        assertThat(pairs).hasSize(3).allSatisfy((pairId, pair) -> {
            assertThat(pair).hasSize(2);
            assertThat(pair).extracting(LocalFactCheckFixture::document).containsOnly(pair.getFirst().document());
            assertThat(pair).extracting(LocalFactCheckFixture::expectedVerdict)
                    .containsExactlyInAnyOrder(
                            LocalFactCheckExpectedVerdict.SUPPORTED,
                            LocalFactCheckExpectedVerdict.UNSUPPORTED);
        });
    }

    @Test
    void everyFixtureHasCompletePublicSafeContentAndAnExpectedVerdict() {
        assertThat(catalog.fixtures()).allSatisfy(fixture -> {
            assertThat(fixture.id()).isNotBlank();
            assertThat(fixture.pairId()).isNotBlank();
            assertThat(fixture.document()).isNotBlank();
            assertThat(fixture.claim()).isNotBlank();
            assertThat(fixture.expectedVerdict()).isNotNull();
            assertThat(fixture.document()).doesNotContain("/Users/", "hostname", "Setaccio product");
            assertThat(fixture.claim()).doesNotContain("/Users/", "hostname", "Setaccio product");
        });
    }

    @Test
    void catalogLookupIsExplicitAndRejectsUnknownIds() {
        assertThat(catalog.require("harbor-library-supported").expectedVerdict())
                .isEqualTo(LocalFactCheckExpectedVerdict.SUPPORTED);
        assertThatThrownBy(() -> catalog.require("missing-fixture"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown fact-check fixture ID: missing-fixture");
        assertThatThrownBy(() -> catalog.require(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fixtureId must not be blank");
    }

    @Test
    void humanReviewConfirmsTheExactCatalogDigestAndEveryFixture() {
        LocalFactCheckFixtureReview review = new LocalFactCheckFixtureReview(objectMapper, catalog);

        assertThat(review.id()).isEqualTo("local-fact-check-fixture-review");
        assertThat(review.version()).isEqualTo("1");
        assertThat(review.status()).isEqualTo("confirmed");
        assertThat(review.catalogId()).isEqualTo(catalog.id());
        assertThat(review.catalogVersion()).isEqualTo(catalog.version());
        assertThat(review.catalogSha256()).isEqualTo(catalog.sha256());
        assertThat(review.confirmedOn()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(review.confirmedFixtureIds())
                .containsExactlyElementsOf(catalog.fixtures().stream().map(LocalFactCheckFixture::id).toList());
        assertThat(review.sha256())
                .isEqualTo("55a5c452dd58a6dddf9d9012cdfb68e50a127226fd49abfaa30597d5e8310161");
    }

    @Test
    void humanReviewRejectsPendingIncompleteOrDigestMismatchedRecords() {
        String confirmed = reviewText();

        assertThatThrownBy(() -> review(confirmed.replace(
                "\"status\": \"confirmed\"",
                "\"status\": \"pending\"")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fact-check fixture review is not confirmed");
        assertThatThrownBy(() -> review(confirmed.replace(
                catalog.sha256(),
                "0".repeat(64))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fact-check fixture review catalog digest does not match");
        assertThatThrownBy(() -> review(confirmed.replace(
                ",\n    \"repair-workshop-unsupported\"",
                "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fact-check fixture review must confirm every fixture in catalog order");
    }

    @Test
    void fixtureMetadataRejectsUnsafeOrIncompleteValues() {
        assertThatThrownBy(() -> new LocalFactCheckFixture(
                "Unsafe ID",
                "pair-id",
                "Document",
                "Claim",
                LocalFactCheckExpectedVerdict.SUPPORTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must be a lowercase kebab-case identifier");
        assertThatThrownBy(() -> new LocalFactCheckFixture(
                "fixture-id",
                "pair-id",
                " ",
                "Claim",
                LocalFactCheckExpectedVerdict.SUPPORTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("document must not be blank");
        assertThatThrownBy(() -> new LocalFactCheckFixture(
                "fixture-id",
                "pair-id",
                "Document",
                "Claim",
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expectedVerdict must not be null");
    }

    private static int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }

    private LocalFactCheckFixtureReview review(String json) {
        return new LocalFactCheckFixtureReview(
                objectMapper,
                catalog,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static String reviewText() {
        try (var input = new ClassPathResource(LocalFactCheckFixtureReview.RESOURCE_PATH).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load fact-check fixture review test resource", exception);
        }
    }
}
