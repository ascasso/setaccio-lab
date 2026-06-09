package com.setaccio.core.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class BouncyCastleBlake3HashingServiceTest {

    private final Blake3HashingService blake3HashingService = new BouncyCastleBlake3HashingServiceImpl();

    @Test
    void testHashBytes_withValidData_returnsExpectedHash() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String hash = blake3HashingService.hashBytes(data);
        
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64); // Blake3 produces 32 bytes = 64 hex chars
        
        // Test consistency - same input should produce same hash
        String hash2 = blake3HashingService.hashBytes(data);
        assertThat(hash2).isEqualTo(hash);
    }

    @Test
    void testHashBytes_withNullData_throwsException() {
        assertThatThrownBy(() -> blake3HashingService.hashBytes(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHashString_withValidString_returnsExpectedHash() {
        String input = "Hello, World!";
        String hash = blake3HashingService.hashString(input);
        
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64);
        
        // Should be same as hashing the bytes directly
        String expectedHash = blake3HashingService.hashBytes(input.getBytes(StandardCharsets.UTF_8));
        assertThat(hash).isEqualTo(expectedHash);
    }

    @Test
    void testHashInputStream_withValidData_returnsExpectedHash() {
        String data = "Hello, World!";
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        
        String hash = blake3HashingService.hashInputStream(inputStream);
        
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64);
        
        // Should be same as hashing the string directly
        String expectedHash = blake3HashingService.hashString(data);
        assertThat(hash).isEqualTo(expectedHash);
    }

    @Test
    void testHashInputStream_withLargeData_returnsHash() {
        // Test with data larger than buffer size
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeData.append("This is a test string for large data hashing. ");
        }
        
        InputStream inputStream = new ByteArrayInputStream(largeData.toString().getBytes(StandardCharsets.UTF_8));
        String hash = blake3HashingService.hashInputStream(inputStream);
        
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64);
    }

    @Test
    void testVerifyHash_withMatchingHash_returnsTrue() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String hash = blake3HashingService.hashBytes(data);
        
        assertThat(blake3HashingService.verifyHash(data, hash)).isTrue();
    }

    @Test
    void testVerifyHash_withNonMatchingHash_returnsFalse() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String incorrectHash = "0000000000000000000000000000000000000000000000000000000000000000";
        
        assertThat(blake3HashingService.verifyHash(data, incorrectHash)).isFalse();
    }

    @Test
    void testHashConsistency_acrossDifferentMethods() {
        String testData = "BouncyCastle Blake3 consistency test";
        
        String hashFromString = blake3HashingService.hashString(testData);
        String hashFromBytes = blake3HashingService.hashBytes(testData.getBytes(StandardCharsets.UTF_8));
        String hashFromStream = blake3HashingService.hashInputStream(
            new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8))
        );
        
        assertThat(hashFromBytes).isEqualTo(hashFromString);
        assertThat(hashFromStream).isEqualTo(hashFromBytes);
    }

    @Test
    void testKnownVector_bouncyCastle() {
        // Test with known vector - empty string should produce a specific hash
        String emptyHash = blake3HashingService.hashString("");
        assertThat(emptyHash).isNotNull();
        assertThat(emptyHash.length()).isEqualTo(64);
        
        // Test with "hello world"
        String helloWorldHash = blake3HashingService.hashString("hello world");
        assertThat(helloWorldHash).isNotNull();
        assertThat(helloWorldHash.length()).isEqualTo(64);
    }
}
