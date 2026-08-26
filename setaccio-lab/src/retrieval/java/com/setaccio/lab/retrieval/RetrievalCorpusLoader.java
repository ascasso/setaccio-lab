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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads the locked version-one public retrieval corpus without contacting a
 * model provider or a network service.
 *
 * <p>The loader rejects symbolic links, unsafe paths, non-repository-authored
 * sources, duplicate identities, unexpected corpus files, and catalog or
 * document digest drift. A successfully loaded corpus may still be pending
 * the human approval required before formal retrieval work.</p>
 */
public final class RetrievalCorpusLoader {

    public static final int SCHEMA_VERSION = 1;
    public static final String CATALOG_ID = "public-safe-retrieval-corpus";
    public static final int CATALOG_VERSION = 1;
    public static final int MINIMUM_DOCUMENT_COUNT = 12;
    public static final int MAXIMUM_DOCUMENT_COUNT = 20;
    public static final String CATALOG_FILENAME = "catalog.json";
    public static final String CATALOG_SHA256_FILENAME = "catalog.sha256";

    private static final String DOCUMENT_DIRECTORY = "documents";
    private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern SAFE_TOPIC = Pattern.compile("[a-z][a-z0-9-]{2,47}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAXIMUM_DOCUMENT_BYTES = 16 * 1024;

    /**
     * Loads and validates a corpus that may still be pending human approval.
     *
     * @param corpusDirectory directory containing {@code catalog.json},
     *                        {@code catalog.sha256}, and {@code documents/}
     * @return the validated catalog and its exact document text
     */
    public RetrievalCorpus load(Path corpusDirectory) {
        Path root = requireRoot(corpusDirectory);
        rejectSymbolicLinks(root);

        Path catalogPath = root.resolve(CATALOG_FILENAME);
        Path catalogSha256Path = root.resolve(CATALOG_SHA256_FILENAME);
        requireRegularFile(catalogPath, "Retrieval catalog");
        requireRegularFile(catalogSha256Path, "Retrieval catalog digest");

        byte[] catalogBytes = readBytes(catalogPath, "Retrieval catalog");
        validateCatalogDigest(catalogBytes, catalogSha256Path);
        CatalogDocument catalog = parseCatalog(catalogBytes);
        validateCatalogIdentity(catalog);

        List<CatalogEntry> entries = catalog.documents() == null ? List.of() : List.copyOf(catalog.documents());
        if (entries.size() < MINIMUM_DOCUMENT_COUNT || entries.size() > MAXIMUM_DOCUMENT_COUNT) {
            throw new IllegalArgumentException("Retrieval corpus must contain between %d and %d documents"
                    .formatted(MINIMUM_DOCUMENT_COUNT, MAXIMUM_DOCUMENT_COUNT));
        }

        Set<String> documentIds = new LinkedHashSet<>();
        Set<String> relativePaths = new LinkedHashSet<>();
        Set<String> contentDigests = new HashSet<>();
        List<RetrievalDocument> documents = entries.stream()
                .map(entry -> loadDocument(root, catalog.privacyReviewState(), entry, documentIds, relativePaths, contentDigests))
                .toList();
        rejectUnexpectedEntries(root, relativePaths);

        return new RetrievalCorpus(
                catalog.catalogId(),
                catalog.catalogVersion(),
                EvidenceIntegrity.sha256(catalogBytes),
                catalog.privacyReviewState(),
                documents);
    }

    /**
     * Loads a corpus only when a human has marked the catalog and all documents
     * {@link RetrievalPrivacyReviewState#APPROVED_PUBLIC_SAFE}.
     *
     * @param corpusDirectory corpus root
     * @return the approved corpus
     */
    public RetrievalCorpus loadApproved(Path corpusDirectory) {
        return load(corpusDirectory).requireApprovedPublicSafe();
    }

    private static Path requireRoot(Path corpusDirectory) {
        if (corpusDirectory == null) {
            throw new IllegalArgumentException("Retrieval corpus directory must not be null");
        }
        Path root = corpusDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Retrieval corpus directory is missing or unsafe");
        }
        return root;
    }

    private static void rejectSymbolicLinks(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                        throw new IllegalArgumentException("Retrieval corpus contains an unsafe symbolic link: "
                                + root.relativize(directory));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                        throw new IllegalArgumentException("Retrieval corpus contains an unsafe symbolic link: "
                                + root.relativize(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalArgumentException("Retrieval corpus could not be inspected safely", exception);
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

    private static void validateCatalogDigest(byte[] catalogBytes, Path catalogSha256Path) {
        String declaredDigest = decodeUtf8(readBytes(catalogSha256Path, "Retrieval catalog digest"),
                "Retrieval catalog digest");
        if (!declaredDigest.endsWith("\n") || declaredDigest.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Retrieval catalog digest must use one LF-terminated line");
        }
        String expected = declaredDigest.substring(0, declaredDigest.length() - 1);
        if (!SHA256.matcher(expected).matches()) {
            throw new IllegalArgumentException("Retrieval catalog digest must be a lowercase SHA-256 value");
        }
        if (!expected.equals(EvidenceIntegrity.sha256(catalogBytes))) {
            throw new IllegalArgumentException("Retrieval catalog digest does not match catalog.json");
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
            throw new IllegalArgumentException("Failed to parse retrieval catalog", exception);
        }
    }

    private static void validateCatalogIdentity(CatalogDocument catalog) {
        if (catalog == null
                || !CATALOG_ID.equals(catalog.catalogId())
                || catalog.schemaVersion() != SCHEMA_VERSION
                || catalog.catalogVersion() != CATALOG_VERSION) {
            throw new IllegalArgumentException("Retrieval catalog identity or schema version is unsupported");
        }
        if (catalog.privacyReviewState() == null) {
            throw new IllegalArgumentException("Retrieval catalog privacy review state is required");
        }
    }

    private static RetrievalDocument loadDocument(
            Path root,
            RetrievalPrivacyReviewState catalogReviewState,
            CatalogEntry entry,
            Set<String> documentIds,
            Set<String> relativePaths,
            Set<String> contentDigests) {
        if (entry == null) {
            throw new IllegalArgumentException("Retrieval catalog must not contain null documents");
        }
        String documentId = requireMatching(entry.documentId(), SAFE_ID, "documentId");
        if (!documentIds.add(documentId)) {
            throw new IllegalArgumentException("Duplicate retrieval documentId: " + documentId);
        }
        String relativePath = requireText(entry.relativePath(), "relativePath");
        String expectedRelativePath = DOCUMENT_DIRECTORY + "/" + documentId + ".md";
        if (!expectedRelativePath.equals(relativePath)) {
            throw new IllegalArgumentException("Retrieval document relativePath must be " + expectedRelativePath);
        }
        if (!relativePaths.add(relativePath)) {
            throw new IllegalArgumentException("Duplicate retrieval document relativePath: " + relativePath);
        }
        String contentSha256 = requireMatching(entry.contentSha256(), SHA256, "contentSha256");
        if (!contentDigests.add(contentSha256)) {
            throw new IllegalArgumentException("Duplicate retrieval document content SHA-256: " + documentId);
        }
        String title = requireText(entry.title(), "title");
        if (title.length() > 160) {
            throw new IllegalArgumentException("Retrieval document title exceeds 160 characters: " + documentId);
        }
        String topic = requireMatching(entry.topic(), SAFE_TOPIC, "topic");
        if (entry.sourceType() != RetrievalDocumentSource.REPOSITORY_AUTHORED) {
            throw new IllegalArgumentException("Retrieval document must be repository-authored: " + documentId);
        }
        if (entry.privacyReviewState() == null || entry.privacyReviewState() != catalogReviewState) {
            throw new IllegalArgumentException("Retrieval document privacy review state must match the catalog: " + documentId);
        }

        Path documentPath = root.resolve(relativePath).normalize();
        if (!documentPath.startsWith(root)) {
            throw new IllegalArgumentException("Retrieval document escapes the corpus directory: " + documentId);
        }
        requireRegularFile(documentPath, "Retrieval document " + documentId);
        byte[] contentBytes = readBytes(documentPath, "Retrieval document " + documentId);
        if (contentBytes.length > MAXIMUM_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Retrieval document exceeds 16 KiB: " + documentId);
        }
        String content = decodeUtf8(contentBytes, "Retrieval document " + documentId);
        if (content.isBlank() || !content.endsWith("\n") || content.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Retrieval document must be non-blank UTF-8 with LF line endings: " + documentId);
        }
        if (!contentSha256.equals(EvidenceIntegrity.sha256(contentBytes))) {
            throw new IllegalArgumentException("Retrieval document SHA-256 does not match content: " + documentId);
        }
        return new RetrievalDocument(
                documentId,
                relativePath,
                contentSha256,
                title,
                topic,
                entry.sourceType(),
                entry.privacyReviewState(),
                content);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Retrieval document " + field + " must not be blank");
        }
        return value;
    }

    private static String requireMatching(String value, Pattern pattern, String field) {
        String text = requireText(value, field);
        if (!pattern.matcher(text).matches()) {
            throw new IllegalArgumentException("Retrieval document " + field + " is unsafe or malformed: " + text);
        }
        return text;
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

    private static void rejectUnexpectedEntries(Path root, Set<String> documentPaths) {
        Set<String> expectedFiles = new HashSet<>(documentPaths);
        expectedFiles.add(CATALOG_FILENAME);
        expectedFiles.add(CATALOG_SHA256_FILENAME);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    String relative = root.relativize(directory).toString().replace('\\', '/');
                    if (!relative.isEmpty() && !DOCUMENT_DIRECTORY.equals(relative)) {
                        throw new IllegalArgumentException("Retrieval corpus contains an unexpected directory: " + relative);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    if (!attributes.isRegularFile() || !expectedFiles.contains(relative)) {
                        throw new IllegalArgumentException("Retrieval corpus contains an unexpected file: " + relative);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalArgumentException("Retrieval corpus entries could not be inspected", exception);
        }
    }

    private record CatalogDocument(
            int schemaVersion,
            String catalogId,
            int catalogVersion,
            RetrievalPrivacyReviewState privacyReviewState,
            List<CatalogEntry> documents
    ) {}

    private record CatalogEntry(
            String documentId,
            String relativePath,
            String contentSha256,
            String title,
            String topic,
            RetrievalDocumentSource sourceType,
            RetrievalPrivacyReviewState privacyReviewState
    ) {}
}
