package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.lab.model.VisionInvocationResult;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.model.VisionStructuralCheck;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

final class VisionMatrixTestFixtures {

    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    static final VisionPromptDefinition PROMPT = new VisionPromptDefinition();
    static final byte[] JPEG_BYTES = new byte[] {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0,
            0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01
    };
    static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC);

    private VisionMatrixTestFixtures() {}

    static LoadedVisionCorpus writeAndLoadCorpus(Path root, List<String> caseIds) throws Exception {
        Files.createDirectories(root.resolve("images"));
        List<VisionCorpusCase> cases = new ArrayList<>();
        ApacheCommonsBlake3HashingServiceImpl hashing = new ApacheCommonsBlake3HashingServiceImpl();
        for (int index = 0; index < caseIds.size(); index++) {
            String caseId = caseIds.get(index);
            byte[] bytes = JPEG_BYTES.clone();
            bytes[bytes.length - 1] = (byte) (index + 1);
            Path image = root.resolve("images/" + caseId + ".jpg");
            Files.write(image, bytes);
            cases.add(new VisionCorpusCase(
                    caseId,
                    "images/" + caseId + ".jpg",
                    "image/jpeg",
                    hashing.hashBytes(bytes),
                    "Private fixture observation " + index,
                    List.of("fixture concept " + index),
                    List.of("unsupported fixture detail " + index),
                    List.of("fixture limitation " + index),
                    new VisionPrivacyReview(true, false, false)));
        }
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                root.resolve(VisionCorpusReader.CATALOG_FILENAME).toFile(),
                new VisionCorpusCatalog(VisionCorpusReader.CURRENT_VERSION, cases));
        return new VisionCorpusReader(OBJECT_MAPPER, hashing).read(root);
    }

    static VisionInvocationResult successfulInvocation(
            VisionInvocationSettings settings,
            String mimeType,
            long latency,
            String output) {
        List<VisionStructuralCheck> checks = PROMPT.requiredSections().stream()
                .map(section -> new VisionStructuralCheck(section, true))
                .toList();
        return new VisionInvocationResult(
                settings,
                mimeType,
                PROMPT.id(),
                PROMPT.version(),
                PROMPT.sha256(),
                latency,
                11,
                7,
                output,
                checks,
                true,
                true,
                null,
                null);
    }

    static VisionMatrixResult successfulMatrix(
            LoadedVisionCorpus corpus,
            List<String> models,
            Integer maxTokens) {
        VisionMatrixRunSettings settings = VisionMatrixProtocol.settings(models, maxTokens);
        return new VisionMatrixExecutor(
                (image, invocationSettings) -> successfulInvocation(
                        invocationSettings,
                        image.contentType(),
                        invocationSettings.seed() == 42 ? 10 : 20,
                        "fixture output " + image.originalFilename() + " seed " + invocationSettings.seed()),
                PROMPT,
                FIXED_CLOCK)
                .execute(corpus, settings, modelIdentities(models));
    }

    static List<VisionMatrixModelIdentity> modelIdentities(List<String> models) {
        List<VisionMatrixModelIdentity> identities = new ArrayList<>();
        for (int index = 0; index < models.size(); index++) {
            String model = models.get(index);
            identities.add(new VisionMatrixModelIdentity(
                    model,
                    VisionMatrixRunner.normalizeModelTag(model),
                    "%064x".formatted(index + 1)));
        }
        return List.copyOf(identities);
    }
}
