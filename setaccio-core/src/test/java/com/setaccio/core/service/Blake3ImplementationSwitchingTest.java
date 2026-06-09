package com.setaccio.core.service;

import com.setaccio.core.model.Blake3Implementation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(apacheHash, bouncyCastleHash, "Apache Commons and BouncyCastle implementations should produce identical hashes");

        // All should be 64 character hex strings
        assertEquals(64, apacheHash.length());
        assertEquals(64, bouncyCastleHash.length());
    }

    @Test
    void testAllImplementationsHandleEmptyInput() {
        byte[] emptyData = new byte[0];

        String apacheHash = apacheCommonsService.hashBytes(emptyData);
        String bouncyCastleHash = bouncyCastleService.hashBytes(emptyData);

        // All implementations should produce identical hashes for empty input
        assertEquals(apacheHash, bouncyCastleHash);
    }

    @Test
    void testAllImplementationsHandleLargeData() {
        // Create large test data
        byte[] largeBytes = "Large data test for cross-implementation consistency. ".repeat(50000).getBytes(StandardCharsets.UTF_8);

        String apacheHash = apacheCommonsService.hashBytes(largeBytes);
        String bouncyCastleHash = bouncyCastleService.hashBytes(largeBytes);

        // All implementations should produce identical hashes for large data
        assertEquals(apacheHash, bouncyCastleHash);
    }

    @Test
    void testPrimaryServiceConfiguration() {
        // Test that the primary service is properly configured and working
        String testData = "Primary service test";
        String primaryHash = primaryService.hashString(testData);

        assertNotNull(primaryHash);
        assertEquals(64, primaryHash.length());

        // Primary service should default to Apache Commons Codec implementation
        String apacheHash = apacheCommonsService.hashString(testData);
        assertEquals(apacheHash, primaryHash, "Primary service should default to Apache Commons Codec implementation");
    }

    @Test
    void testImplementationEnumValues() {
        // Test that all enum values are properly configured
        assertEquals("apache-commons-codec", Blake3Implementation.APACHE_COMMONS_CODEC.getKey());
        assertEquals("bouncy-castle", Blake3Implementation.BOUNCY_CASTLE.getKey());

        // Test fromKey method
        assertEquals(Blake3Implementation.APACHE_COMMONS_CODEC, Blake3Implementation.fromKey("apache-commons-codec"));
        assertEquals(Blake3Implementation.BOUNCY_CASTLE, Blake3Implementation.fromKey("bouncy-castle"));

        // Test case insensitivity
        assertEquals(Blake3Implementation.APACHE_COMMONS_CODEC, Blake3Implementation.fromKey("APACHE-COMMONS-CODEC"));

        // Test invalid key
        assertThrows(IllegalArgumentException.class, () -> Blake3Implementation.fromKey("invalid-implementation"));
    }

    @Test
    void testHashVerificationAcrossImplementations() {
        String testData = "Cross-implementation verification test";

        // Generate hash with one implementation
        String hashFromApache = apacheCommonsService.hashString(testData);

        // Verify with both implementations
        assertTrue(apacheCommonsService.verifyHash(testData.getBytes(StandardCharsets.UTF_8), hashFromApache));
        assertTrue(bouncyCastleService.verifyHash(testData.getBytes(StandardCharsets.UTF_8), hashFromApache));
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

        assertEquals(apacheStreamHash, bouncyCastleStreamHash);
    }
}
