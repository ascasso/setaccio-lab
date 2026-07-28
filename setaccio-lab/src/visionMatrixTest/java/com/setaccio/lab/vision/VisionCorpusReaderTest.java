package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionCorpusReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsOnlyDeclaredCaseIdBasedImagesWithMatchingMimeAndBlake3() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("corpus"),
                List.of("vision-one", "vision-two"));

        assertThat(corpus.corpusVersion()).isEqualTo(1);
        assertThat(corpus.cases())
                .extracting(loaded -> loaded.metadata().caseId())
                .containsExactly("vision-one", "vision-two");
        assertThat(corpus.cases())
                .allSatisfy(loaded -> {
                    assertThat(loaded.imagePath()).isRegularFile();
                    assertThat(loaded.metadata().privacyReview().sensitiveContentReviewed()).isTrue();
                });
    }

    @Test
    void rejectsUnknownFieldsThatCouldLeakOriginalFilenames() throws Exception {
        Path root = temporaryDirectory.resolve("unknown-field");
        VisionMatrixTestFixtures.writeAndLoadCorpus(root, List.of("vision-one"));
        Path catalog = root.resolve(VisionCorpusReader.CATALOG_FILENAME);
        ObjectNode node = (ObjectNode) VisionMatrixTestFixtures.OBJECT_MAPPER.readTree(catalog.toFile());
        ((ObjectNode) node.path("cases").get(0)).put("originalFilename", "private-name.jpg");
        VisionMatrixTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(catalog.toFile(), node);

        VisionCorpusReader reader = reader();

        assertThatThrownBy(() -> reader.read(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized field");
    }

    @Test
    void rejectsIncompletePrivacyReviewBeforeReadingTheImage() throws Exception {
        Path root = temporaryDirectory.resolve("privacy");
        VisionMatrixTestFixtures.writeAndLoadCorpus(root, List.of("vision-one"));
        Path catalog = root.resolve(VisionCorpusReader.CATALOG_FILENAME);
        ObjectNode node = (ObjectNode) VisionMatrixTestFixtures.OBJECT_MAPPER.readTree(catalog.toFile());
        ((ObjectNode) node.path("cases").get(0).path("privacyReview"))
                .put("sensitiveContentReviewed", false);
        VisionMatrixTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(catalog.toFile(), node);

        assertThatThrownBy(() -> reader().read(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensitive-content review is incomplete");
    }

    @Test
    void rejectsHashDriftAndUnsafeImagePaths() throws Exception {
        Path hashRoot = temporaryDirectory.resolve("hash");
        VisionMatrixTestFixtures.writeAndLoadCorpus(hashRoot, List.of("vision-one"));
        byte[] changedBytes = VisionMatrixTestFixtures.JPEG_BYTES.clone();
        changedBytes[changedBytes.length - 1] = 99;
        Files.write(hashRoot.resolve("images/vision-one.jpg"), changedBytes);

        assertThatThrownBy(() -> reader().read(hashRoot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLAKE3 does not match");

        Path pathRoot = temporaryDirectory.resolve("path");
        VisionMatrixTestFixtures.writeAndLoadCorpus(pathRoot, List.of("vision-one"));
        Path catalog = pathRoot.resolve(VisionCorpusReader.CATALOG_FILENAME);
        ObjectNode node = (ObjectNode) VisionMatrixTestFixtures.OBJECT_MAPPER.readTree(catalog.toFile());
        ((ObjectNode) node.path("cases").get(0)).put("imageFile", "../private.jpg");
        VisionMatrixTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(catalog.toFile(), node);

        assertThatThrownBy(() -> reader().read(pathRoot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("images/<caseId>");
    }

    @Test
    void rejectsUnknownImageBytesInsteadOfDefaultingThemToJpeg() throws Exception {
        Path root = temporaryDirectory.resolve("unknown-bytes");
        VisionMatrixTestFixtures.writeAndLoadCorpus(root, List.of("vision-one"));
        Files.write(root.resolve("images/vision-one.jpg"), new byte[] {0, 1, 2, 3, 4, 5});

        assertThatThrownBy(() -> reader().read(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported MIME type");
    }

    private VisionCorpusReader reader() {
        return new VisionCorpusReader(
                VisionMatrixTestFixtures.OBJECT_MAPPER,
                new ApacheCommonsBlake3HashingServiceImpl());
    }
}
