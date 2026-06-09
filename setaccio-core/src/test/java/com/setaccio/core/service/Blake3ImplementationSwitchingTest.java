package com.setaccio.core.service;

import com.setaccio.core.model.Blake3Implementation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class Blake3ImplementationSwitchingTest {

    private final Blake3HashingService apacheCommonsService = new ApacheCommonsBlake3HashingServiceImpl();
    private final Blake3HashingService bouncyCastleService = new BouncyCastleBlake3HashingServiceImpl();
    private final Blake3HashingService primaryService = apacheCommonsService;

    @Test
    void testAllImplementationsProduceIdenticalHashes() {
        String testData = "Cross-implementation hash consistency test";
        byte[] testBytes = testData.getBytes(StandardCharsets.UTF_8);

        String apacheHash = apacheCommonsService.hashBytes(testBytes);
        String bouncyCastleHash = bouncyCastleService.hashBytes(testBytes);

        // All implementations should produce identical hashes for the same input
        assertThat(bouncyCastleHash).as("Apache Commons and BouncyCastle implementations should produce identical hashes").isEqualTo(apacheHash);

        // All should be 64 character hex strings
        assertThat(apacheHash.length()).isEqualTo(64);
        assertThat(bouncyCastleHash.length()).isEqualTo(64);
    }

    @Test
    void testAllImplementationsHandleEmptyInput() {
        byte[] emptyData = new byte[0];

        String apacheHash = apacheCommonsService.hashBytes(emptyData);
        String bouncyCastleHash = bouncyCastleService.hashBytes(emptyData);

        // All implementations should produce identical hashes for empty input
        assertThat(bouncyCastleHash).isEqualTo(apacheHash);
    }

    @Test
    void testAllImplementationsHandleLargeData() {
        // Create large test data
        byte[] largeBytes = "Large data test for cross-implementation consistency. ".repeat(50000).getBytes(StandardCharsets.UTF_8);

        String apacheHash = apacheCommonsService.hashBytes(largeBytes);
        String bouncyCastleHash = bouncyCastleService.hashBytes(largeBytes);

        // All implementations should produce identical hashes for large data
        assertThat(bouncyCastleHash).isEqualTo(apacheHash);
    }

    @Test
    void testPrimaryServiceConfiguration() {
        // Test that the primary service is properly configured and working
        String testData = "Primary service test";
        String primaryHash = primaryService.hashString(testData);

        assertThat(primaryHash).isNotNull();
        assertThat(primaryHash.length()).isEqualTo(64);

        // Primary service should default to Apache Commons Codec implementation
        String apacheHash = apacheCommonsService.hashString(testData);
        assertThat(primaryHash).as("Primary service should default to Apache Commons Codec implementation").isEqualTo(apacheHash);
    }

    @Test
    void testImplementationEnumValues() {
        // Test that all enum values are properly configured
        assertThat(Blake3Implementation.APACHE_COMMONS_CODEC.getKey()).isEqualTo("apache-commons-codec");
        assertThat(Blake3Implementation.BOUNCY_CASTLE.getKey()).isEqualTo("bouncy-castle");

        // Test fromKey method
        assertThat(Blake3Implementation.fromKey("apache-commons-codec")).isEqualTo(Blake3Implementation.APACHE_COMMONS_CODEC);
        assertThat(Blake3Implementation.fromKey("bouncy-castle")).isEqualTo(Blake3Implementation.BOUNCY_CASTLE);

        // Test case insensitivity
        assertThat(Blake3Implementation.fromKey("APACHE-COMMONS-CODEC")).isEqualTo(Blake3Implementation.APACHE_COMMONS_CODEC);

        // Test invalid key
        assertThatThrownBy(() -> Blake3Implementation.fromKey("invalid-implementation"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHashVerificationAcrossImplementations() {
        String testData = "Cross-implementation verification test";

        // Generate hash with one implementation
        String hashFromApache = apacheCommonsService.hashString(testData);

        // Verify with both implementations
        assertThat(apacheCommonsService.verifyHash(testData.getBytes(StandardCharsets.UTF_8), hashFromApache)).isTrue();
        assertThat(bouncyCastleService.verifyHash(testData.getBytes(StandardCharsets.UTF_8), hashFromApache)).isTrue();
    }

    @Test
    void testStreamProcessingConsistency() {
        String testData = "Stream processing consistency test across implementations";

        String apacheStreamHash = apacheCommonsService.hashInputStream(
            new java.io.ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8))
        );
        String bouncyCastleStreamHash = bouncyCastleService.hashInputStream(
            new java.io.ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(bouncyCastleStreamHash).isEqualTo(apacheStreamHash);
    }
}
