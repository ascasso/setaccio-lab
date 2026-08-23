package com.setaccio.lab.toolcompat;

import java.nio.file.Path;
import java.util.List;

/** Owner-approved T3.1 cohort identity and deployed-metadata lock. */
final class ToolCompatibilityCohortLock {

    static final String APPROVAL_DATE = "2026-08-23";
    static final String OLLAMA_RUNTIME_VERSION = "0.32.15";

    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final List<ApprovedModel> ORDERED_MODELS = List.of(
            approvedPeer(
                    1,
                    "hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0",
                    "2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed",
                    metadata(
                            2_874_774_591L,
                            "ollama-show architecture=lfm2",
                            "GGUF via Ollama",
                            "Q8_0",
                            "62fbfd9ed093d6e5ac83190c86eec5369317919f4b149598d2dbb38900e9faef",
                            EMPTY_SHA256,
                            "tools-not-advertised",
                            "capability-not-advertised; default/effective-mode=unavailable")),
            approvedPeer(
                    2,
                    "granite4.1:3b",
                    "6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb",
                    metadata(
                            2_099_520_281L,
                            "ollama-show architecture=granite",
                            "GGUF via Ollama",
                            "Q4_K_M",
                            "89a0ab46e638b17149f5a596060e815cb019117e9c7f745aa8861a02d63d66ef",
                            EMPTY_SHA256,
                            "tools-advertised",
                            "capability-not-advertised; default/effective-mode=unavailable")),
            approvedPeer(
                    3,
                    "ministral-3:3b",
                    "a48e77f25d7933c64552d810c3ca5c7fc8cce4ad7e1ff1432fe24574c8e146e0",
                    metadata(
                            2_953_828_889L,
                            "ollama-show architecture=mistral3",
                            "GGUF via Ollama",
                            "Q4_K_M",
                            "6db27cd4e277c91264572b9c899c1980daa8dea11e902f0070a6f4763f3d13c8",
                            "3d8ba0a186b58f4b249902c3610c731fd52ec3007b297f7adf46b7cc45c3b3d6",
                            "tools-advertised",
                            "capability-not-advertised; default/effective-mode=unavailable")),
            approvedPeer(
                    4,
                    "gemma4:e2b",
                    "7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e",
                    metadata(
                            7_162_405_886L,
                            "ollama-show architecture=gemma4",
                            "GGUF via Ollama",
                            "Q4_K_M",
                            "b507b9c2f6ca642bffcd06665ea7c91f235fd32daeefdf875a0f938db05fb315",
                            EMPTY_SHA256,
                            "tools-advertised",
                            "capability-advertised; default/effective-mode=unavailable")),
            approvedPeer(
                    5,
                    "qwen3.5:0.8b",
                    "f3817196d142eaf72ce79dfebe53dcb20bd21da87ce13e138a8f8e10a866b3a4",
                    metadata(
                            1_036_046_583L,
                            "ollama-show architecture=qwen35",
                            "GGUF via Ollama",
                            "Q8_0",
                            "b507b9c2f6ca642bffcd06665ea7c91f235fd32daeefdf875a0f938db05fb315",
                            EMPTY_SHA256,
                            "tools-advertised",
                            "capability-advertised; default/effective-mode=unavailable")),
            new ApprovedModel(
                    6,
                    ToolCompatibilityCohortModelIdentity.Role.REFERENCE,
                    "qwen3.8:27b-mlx",
                    "5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e",
                    metadata(
                            18_174_721_847L,
                            "ollama-show architecture=qwen3_5",
                            "safetensors/MLX via Ollama",
                            "nvfp4",
                            "b507b9c2f6ca642bffcd06665ea7c91f235fd32daeefdf875a0f938db05fb315",
                            EMPTY_SHA256,
                            "tools-advertised",
                            "capability-advertised; default/effective-mode=unavailable")));

    private ToolCompatibilityCohortLock() {}

    static ToolCompatibilityCohortPreflight.Input input(
            Path projectDirectory,
            String ollamaBaseUrl,
            String outputDirectory
    ) {
        return new ToolCompatibilityCohortPreflight.Input(
                projectDirectory,
                ollamaBaseUrl,
                peerTags(),
                reference().installedTag(),
                outputDirectory);
    }

    static List<String> peerTags() {
        return ORDERED_MODELS.stream()
                .filter(model -> model.role() == ToolCompatibilityCohortModelIdentity.Role.PEER)
                .map(ApprovedModel::installedTag)
                .toList();
    }

    static ApprovedModel reference() {
        return ORDERED_MODELS.getLast();
    }

    static List<ApprovedModel> orderedModels() {
        return ORDERED_MODELS;
    }

    static void requireMatches(ToolCompatibilityCohortPreflight.Prepared prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared cohort is required");
        }
        if (!OLLAMA_RUNTIME_VERSION.equals(prepared.ollamaRuntimeVersion())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Ollama runtime version does not match the owner-approved T3.1 lock");
        }

        List<ToolCompatibilityCohortModelIdentity> actual = prepared.orderedModels();
        if (actual.size() != ORDERED_MODELS.size()) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Resolved cohort size does not match the owner-approved T3.1 lock");
        }
        for (int index = 0; index < ORDERED_MODELS.size(); index++) {
            ApprovedModel expected = ORDERED_MODELS.get(index);
            ToolCompatibilityCohortModelIdentity observed = actual.get(index);
            if (observed.cohortPosition() != expected.cohortPosition()
                    || observed.role() != expected.role()
                    || !observed.requestedTag().equals(expected.installedTag())
                    || !observed.effectiveInstalledTag().equals(expected.installedTag())
                    || !observed.digest().equals(expected.digest())
                    || !observed.metadata().equals(expected.metadata())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Resolved cohort identity or metadata does not match the owner-approved "
                                + "T3.1 lock at position " + expected.cohortPosition());
            }
        }
    }

    private static ApprovedModel approvedPeer(
            int position,
            String installedTag,
            String digest,
            ToolCompatibilityCohortModelMetadata metadata
    ) {
        return new ApprovedModel(
                position,
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                installedTag,
                digest,
                metadata);
    }

    private static ToolCompatibilityCohortModelMetadata metadata(
            long sizeBytes,
            String familyProvenance,
            String artifactRuntimeFormat,
            String quantizationOrPrecision,
            String templateSha256,
            String defaultSystemPromptSha256,
            String toolCapability,
            String thinkingMode
    ) {
        return new ToolCompatibilityCohortModelMetadata(
                available(Long.toString(sizeBytes)),
                available(familyProvenance),
                available(artifactRuntimeFormat),
                available(quantizationOrPrecision),
                available("sha256:" + templateSha256),
                available("sha256:" + defaultSystemPromptSha256),
                available(toolCapability),
                available(thinkingMode));
    }

    private static ToolCompatibilityMetadataField available(String value) {
        return ToolCompatibilityMetadataField.available(value);
    }

    record ApprovedModel(
            int cohortPosition,
            ToolCompatibilityCohortModelIdentity.Role role,
            String installedTag,
            String digest,
            ToolCompatibilityCohortModelMetadata metadata
    ) {

        ApprovedModel {
            if (cohortPosition <= 0
                    || role == null
                    || installedTag == null
                    || installedTag.isBlank()
                    || installedTag.endsWith(":latest")
                    || digest == null
                    || !digest.matches("[0-9a-f]{64}")
                    || metadata == null) {
                throw new IllegalArgumentException("approved cohort model lock is invalid");
            }
        }
    }
}
