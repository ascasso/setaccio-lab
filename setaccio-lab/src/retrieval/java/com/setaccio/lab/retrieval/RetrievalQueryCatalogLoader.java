package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads the version-one query fixtures against the exact approved corpus,
 * without starting Spring or contacting a provider.
 */
public final class RetrievalQueryCatalogLoader {

    public static final int SCHEMA_VERSION = 1;
    public static final String CATALOG_ID = "public-safe-retrieval-query-fixtures";
    public static final int CATALOG_VERSION = 1;
    public static final int FIXTURE_COUNT = 14;
    public static final int NO_MATCH_FIXTURE_COUNT = 2;
    public static final String CATALOG_FILENAME = "catalog.json";
    public static final String CATALOG_SHA256_FILENAME = "catalog.sha256";

    private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAXIMUM_CATALOG_BYTES = 64 * 1024;

    /**
     * Loads and validates query fixtures that may still await human truth
     * confirmation. The bound corpus must already be approved public-safe.
     *
     * @param catalogDirectory directory containing the query catalog and digest
     * @param corpus exact approved corpus referenced by the query catalog
     * @return the validated query catalog
     */
    public RetrievalQueryCatalog load(Path catalogDirectory, RetrievalCorpus corpus) {
        if (corpus == null) {
            throw new IllegalArgumentException("Retrieval query corpus must not be null");
        }
        RetrievalCorpus approvedCorpus = corpus.requireApprovedPublicSafe();
        Path root = requireRoot(catalogDirectory);
        rejectSymbolicLinks(root);

        Path catalogPath = root.resolve(CATALOG_FILENAME);
        Path catalogSha256Path = root.resolve(CATALOG_SHA256_FILENAME);
        requireRegularFile(catalogPath, "Retrieval query catalog");
        requireRegularFile(catalogSha256Path, "Retrieval query catalog digest");
        rejectUnexpectedEntries(root);

        byte[] catalogBytes = readBytes(catalogPath, "Retrieval query catalog");
        if (catalogBytes.length > MAXIMUM_CATALOG_BYTES) {
            throw new IllegalArgumentException("Retrieval query catalog exceeds 64 KiB");
        }
        validateCatalogDigest(catalogBytes, catalogSha256Path);
        CatalogDocument catalog = parseCatalog(catalogBytes);
        validateCatalogIdentity(catalog, approvedCorpus);

        List<CatalogFixture> entries = catalog.fixtures() == null ? List.of() : List.copyOf(catalog.fixtures());
        if (entries.size() != FIXTURE_COUNT) {
            throw new IllegalArgumentException(
                    "Retrieval query catalog must contain exactly " + FIXTURE_COUNT + " fixtures");
        }

        List<String> corpusDocumentIds = approvedCorpus.documents().stream()
                .map(RetrievalDocument::documentId)
                .toList();
        Set<String> corpusDocumentIdSet = Set.copyOf(corpusDocumentIds);
        Set<String> caseIds = new LinkedHashSet<>();
        List<String> expectedCoverage = new ArrayList<>();
        List<RetrievalQueryFixture> fixtures = entries.stream()
                .map(entry -> validateFixture(
                        entry,
                        catalog.humanReviewState(),
                        corpusDocumentIds,
                        corpusDocumentIdSet,
                        caseIds,
                        expectedCoverage))
                .toList();

        long noMatchCount = fixtures.stream().filter(RetrievalQueryFixture::expectedNoMatch).count();
        if (noMatchCount != NO_MATCH_FIXTURE_COUNT) {
            throw new IllegalArgumentException(
                    "Retrieval query catalog must contain exactly " + NO_MATCH_FIXTURE_COUNT + " no-match fixtures");
        }
        if (!expectedCoverage.equals(corpusDocumentIds)) {
            throw new IllegalArgumentException(
                    "Retrieval query fixtures must cover every corpus document exactly once and in corpus order");
        }

        return new RetrievalQueryCatalog(
                catalog.catalogId(),
                catalog.catalogVersion(),
                EvidenceIntegrity.sha256(catalogBytes),
                catalog.corpusCatalogId(),
                catalog.corpusCatalogVersion(),
                catalog.corpusCatalogSha256(),
                catalog.humanReviewState(),
                fixtures);
    }

    /**
     * Loads fixtures only when a human has confirmed the catalog and every
     * query's document relevance labels.
     *
     * @param catalogDirectory query catalog root
     * @param corpus exact approved corpus referenced by the query catalog
     * @return the confirmed query catalog
     */
    public RetrievalQueryCatalog loadConfirmed(Path catalogDirectory, RetrievalCorpus corpus) {
        return load(catalogDirectory, corpus).requireConfirmed();
    }

    private static RetrievalQueryFixture validateFixture(
            CatalogFixture entry,
            RetrievalQueryReviewState catalogReviewState,
            List<String> corpusDocumentIds,
            Set<String> corpusDocumentIdSet,
            Set<String> caseIds,
            List<String> expectedCoverage) {
        if (entry == null) {
            throw new IllegalArgumentException("Retrieval query catalog must not contain null fixtures");
        }
        String caseId = requireMatching(entry.caseId(), SAFE_ID, "caseId");
        if (!caseIds.add(caseId)) {
            throw new IllegalArgumentException("Duplicate retrieval query caseId: " + caseId);
        }
        String query = requireQuery(entry.query(), caseId);
        List<String> expected = requireDocumentIds(
                entry.expectedSupportingDocumentIds(), "expectedSupportingDocumentIds", caseId, corpusDocumentIdSet);
        List<String> allowed = requireDocumentIds(
                entry.allowedSupportingDocumentIds(), "allowedSupportingDocumentIds", caseId, corpusDocumentIdSet);
        List<String> forbidden = requireDocumentIds(
                entry.forbiddenDocumentIds(), "forbiddenDocumentIds", caseId, corpusDocumentIdSet);

        if (!allowed.containsAll(expected)) {
            throw new IllegalArgumentException(
                    "Retrieval query allowedSupportingDocumentIds must include every expected document: " + caseId);
        }
        if (!disjoint(allowed, forbidden)) {
            throw new IllegalArgumentException(
                    "Retrieval query allowed and forbidden document IDs must be disjoint: " + caseId);
        }
        if (entry.expectedNoMatch() == null) {
            throw new IllegalArgumentException("Retrieval query expectedNoMatch is required: " + caseId);
        }
        if (entry.expectedNoMatch()) {
            if (!expected.isEmpty() || !allowed.isEmpty() || !forbidden.equals(corpusDocumentIds)) {
                throw new IllegalArgumentException(
                        "Retrieval no-match query must expect no support and forbid every corpus document: " + caseId);
            }
        } else {
            if (expected.size() != 1 || allowed.isEmpty() || forbidden.isEmpty()) {
                throw new IllegalArgumentException(
                        "Retrieval match query must have one expected document plus allowed and forbidden labels: "
                                + caseId);
            }
            expectedCoverage.addAll(expected);
        }
        if (entry.humanReviewState() == null || entry.humanReviewState() != catalogReviewState) {
            throw new IllegalArgumentException(
                    "Retrieval query human review state must match the catalog: " + caseId);
        }

        return new RetrievalQueryFixture(
                caseId,
                query,
                expected,
                allowed,
                forbidden,
                entry.expectedNoMatch(),
                entry.humanReviewState());
    }

    private static List<String> requireDocumentIds(
            List<String> values,
            String field,
            String caseId,
            Set<String> corpusDocumentIds) {
        if (values == null) {
            throw new IllegalArgumentException("Retrieval query " + field + " is required: " + caseId);
        }
        List<String> ids = List.copyOf(values);
        Set<String> uniqueIds = new HashSet<>();
        for (String value : ids) {
            String documentId = requireMatching(value, SAFE_ID, field);
            if (!corpusDocumentIds.contains(documentId)) {
                throw new IllegalArgumentException(
                        "Retrieval query references an unknown corpus document: " + documentId);
            }
            if (!uniqueIds.add(documentId)) {
                throw new IllegalArgumentException(
                        "Retrieval query contains a duplicate document ID in " + field + ": " + caseId);
            }
        }
        return ids;
    }

    private static boolean disjoint(List<String> first, List<String> second) {
        return first.stream().noneMatch(second::contains);
    }

    private static String requireQuery(String value, String caseId) {
        if (value == null || value.isBlank() || !value.equals(value.strip())
                || value.length() > 320 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || !value.endsWith("?")) {
            throw new IllegalArgumentException(
                    "Retrieval query must be a trimmed single question of at most 320 characters: " + caseId);
        }
        return value;
    }

    private static String requireMatching(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Retrieval query " + field + " is unsafe or malformed: " + value);
        }
        return value;
    }

    private static void validateCatalogIdentity(CatalogDocument catalog, RetrievalCorpus corpus) {
        if (catalog == null
                || !CATALOG_ID.equals(catalog.catalogId())
                || catalog.schemaVersion() != SCHEMA_VERSION
                || catalog.catalogVersion() != CATALOG_VERSION) {
            throw new IllegalArgumentException("Retrieval query catalog identity or schema version is unsupported");
        }
        if (catalog.humanReviewState() == null) {
            throw new IllegalArgumentException("Retrieval query catalog human review state is required");
        }
        if (!corpus.catalogId().equals(catalog.corpusCatalogId())
                || corpus.catalogVersion() != catalog.corpusCatalogVersion()
                || !corpus.catalogSha256().equals(catalog.corpusCatalogSha256())) {
            throw new IllegalArgumentException("Retrieval query catalog does not match the exact approved corpus");
        }
    }

    private static CatalogDocument parseCatalog(byte[] catalogBytes) {
        try {
            return JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
                    .readerFor(CatalogDocument.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(catalogBytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse retrieval query catalog", exception);
        }
    }

    private static void validateCatalogDigest(byte[] catalogBytes, Path digestPath) {
        String declaredDigest = decodeUtf8(
                readBytes(digestPath, "Retrieval query catalog digest"),
                "Retrieval query catalog digest");
        if (!declaredDigest.endsWith("\n") || declaredDigest.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Retrieval query catalog digest must use one LF-terminated line");
        }
        String expected = declaredDigest.substring(0, declaredDigest.length() - 1);
        if (!SHA256.matcher(expected).matches()) {
            throw new IllegalArgumentException("Retrieval query catalog digest must be a lowercase SHA-256 value");
        }
        if (!expected.equals(EvidenceIntegrity.sha256(catalogBytes))) {
            throw new IllegalArgumentException("Retrieval query catalog digest does not match catalog.json");
        }
    }

    private static Path requireRoot(Path catalogDirectory) {
        if (catalogDirectory == null) {
            throw new IllegalArgumentException("Retrieval query catalog directory must not be null");
        }
        Path root = catalogDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Retrieval query catalog directory is missing or unsafe");
        }
        return root;
    }

    private static void rejectSymbolicLinks(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                        throw new IllegalArgumentException(
                                "Retrieval query catalog contains an unsafe symbolic link: "
                                        + root.relativize(directory));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                        throw new IllegalArgumentException(
                                "Retrieval query catalog contains an unsafe symbolic link: " + root.relativize(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalArgumentException("Retrieval query catalog could not be inspected safely", exception);
        }
    }

    private static void rejectUnexpectedEntries(Path root) {
        Set<String> expectedFiles = Set.of(CATALOG_FILENAME, CATALOG_SHA256_FILENAME);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!root.equals(directory)) {
                        throw new IllegalArgumentException(
                                "Retrieval query catalog contains an unexpected directory: "
                                        + root.relativize(directory));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    if (!attributes.isRegularFile() || !expectedFiles.contains(relative)) {
                        throw new IllegalArgumentException(
                                "Retrieval query catalog contains an unexpected file: " + relative);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalArgumentException("Retrieval query catalog entries could not be inspected", exception);
        }
    }

    private static void requireRegularFile(Path path, String label) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " is missing or unsafe");
        }
    }

    private static byte[] readBytes(Path path, String label) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                throw new IllegalArgumentException(label + " must not be empty");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException(label + " could not be read", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes, String label) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(label + " must be valid UTF-8", exception);
        }
    }

    private record CatalogDocument(
            int schemaVersion,
            String catalogId,
            int catalogVersion,
            String corpusCatalogId,
            int corpusCatalogVersion,
            String corpusCatalogSha256,
            RetrievalQueryReviewState humanReviewState,
            List<CatalogFixture> fixtures
    ) {}

    private record CatalogFixture(
            String caseId,
            String query,
            List<String> expectedSupportingDocumentIds,
            List<String> allowedSupportingDocumentIds,
            List<String> forbiddenDocumentIds,
            Boolean expectedNoMatch,
            RetrievalQueryReviewState humanReviewState
    ) {}
}
