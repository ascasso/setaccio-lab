package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

record LocalEvaluationContract(
        LocalFactCheckPromptDefinition prompt,
        LocalFactCheckFixtureCatalog catalog,
        LocalFactCheckFixtureReview review,
        Identity identity
) {

    LocalEvaluationContract(
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        this(prompt, catalog, review, Identity.from(prompt, catalog, review));
    }

    static LocalEvaluationContract load(ObjectMapper objectMapper) {
        LocalFactCheckPromptDefinition prompt = new LocalFactCheckPromptDefinition();
        LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);
        LocalFactCheckFixtureReview review = new LocalFactCheckFixtureReview(objectMapper, catalog);
        return new LocalEvaluationContract(prompt, catalog, review);
    }

    LocalEvaluationContract {
        if (prompt == null || catalog == null || review == null || identity == null) {
            throw new IllegalArgumentException("Local evaluation contract must be complete");
        }
    }

    void requireLockedAndConfirmed() {
        identity.requireLockedAndConfirmed();
    }

    record Identity(
            String promptId,
            String promptVersion,
            String promptSha256,
            String catalogId,
            String catalogVersion,
            String catalogSha256,
            String reviewId,
            String reviewVersion,
            String reviewStatus,
            String reviewSha256,
            String reviewedCatalogId,
            String reviewedCatalogVersion,
            String reviewedCatalogSha256,
            List<String> confirmedFixtureIds,
            List<String> catalogFixtureIds
    ) {

        Identity {
            confirmedFixtureIds = confirmedFixtureIds == null ? List.of() : List.copyOf(confirmedFixtureIds);
            catalogFixtureIds = catalogFixtureIds == null ? List.of() : List.copyOf(catalogFixtureIds);
        }

        static Identity from(
                LocalFactCheckPromptDefinition prompt,
                LocalFactCheckFixtureCatalog catalog,
                LocalFactCheckFixtureReview review
        ) {
            List<String> fixtureIds = catalog.fixtures().stream()
                    .map(LocalFactCheckFixture::id)
                    .toList();
            return new Identity(
                    prompt.id(),
                    prompt.version(),
                    prompt.sha256(),
                    catalog.id(),
                    catalog.version(),
                    catalog.sha256(),
                    review.id(),
                    review.version(),
                    review.status(),
                    review.sha256(),
                    review.catalogId(),
                    review.catalogVersion(),
                    review.catalogSha256(),
                    review.confirmedFixtureIds(),
                    fixtureIds);
        }

        void requireLockedAndConfirmed() {
            requireEquals(promptId, LocalFactCheckPromptDefinition.ID, "prompt ID drift");
            requireEquals(promptVersion, LocalFactCheckPromptDefinition.VERSION, "prompt version drift");
            requireEquals(promptSha256, LocalFactCheckPromptDefinition.SHA256, "prompt digest drift");
            requireEquals(catalogId, LocalFactCheckFixtureCatalog.ID, "fixture catalog ID drift");
            requireEquals(catalogVersion, LocalFactCheckFixtureCatalog.VERSION, "fixture catalog version drift");
            requireEquals(catalogSha256, LocalFactCheckFixtureCatalog.SHA256, "fixture catalog digest drift");
            requireEquals(reviewId, LocalFactCheckFixtureReview.ID, "fixture review ID drift");
            requireEquals(reviewVersion, LocalFactCheckFixtureReview.VERSION, "fixture review version drift");
            requireEquals(reviewStatus, LocalFactCheckFixtureReview.STATUS, "fixture review is not confirmed");
            requireEquals(reviewSha256, LocalFactCheckFixtureReview.SHA256, "fixture review digest drift");
            requireEquals(reviewedCatalogId, catalogId, "fixture review catalog ID drift");
            requireEquals(reviewedCatalogVersion, catalogVersion, "fixture review catalog version drift");
            requireEquals(reviewedCatalogSha256, catalogSha256, "fixture review catalog digest drift");
            if (!catalogFixtureIds.equals(confirmedFixtureIds)
                    || catalogFixtureIds.size() != LocalFactCheckFixtureCatalog.FIXTURE_COUNT) {
                throw new IllegalArgumentException(
                        "Local evaluation preflight failed: fixture review does not confirm the locked catalog");
            }
        }

        private static void requireEquals(String actual, String expected, String failure) {
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("Local evaluation preflight failed: " + failure);
            }
        }
    }
}
