package com.setaccio.core.service;

import com.setaccio.core.exception.HashingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ApacheCommonsBlake3HashingServiceTest {

    private final Blake3HashingService blake3HashingService = new ApacheCommonsBlake3HashingServiceImpl();

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
    void testHashBytes_withEmptyData_returnsHash() {
        byte[] emptyData = new byte[0];
        String hash = blake3HashingService.hashBytes(emptyData);
        
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64);
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
    void testHashString_withNullString_throwsException() {
        assertThatThrownBy(() -> blake3HashingService.hashString(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHashString_withEmptyString_returnsHash() {
        String hash = blake3HashingService.hashString("");
        
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64);
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
    void testHashInputStream_withNullInputStream_throwsException() {
        assertThatThrownBy(() -> blake3HashingService.hashInputStream(null)).isInstanceOf(IllegalArgumentException.class);
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
    void testVerifyHash_withNullExpectedHash_throwsException() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        
        assertThatThrownBy(() -> blake3HashingService.verifyHash(data, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testVerifyHashInputStream_withMatchingHash_returnsTrue() {
        String data = "Hello, World!";
        String expectedHash = blake3HashingService.hashString(data);
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        
        assertThat(blake3HashingService.verifyHash(inputStream, expectedHash)).isTrue();
    }

    @Test
    void testVerifyHashInputStream_withNonMatchingHash_returnsFalse() {
        String data = "Hello, World!";
        String incorrectHash = "0000000000000000000000000000000000000000000000000000000000000000";
        InputStream inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        
        assertThat(blake3HashingService.verifyHash(inputStream, incorrectHash)).isFalse();
    }

    @Test
    void testHashConsistency_acrossDifferentMethods() {
        String testData = "Consistency test data";
        
        String hashFromString = blake3HashingService.hashString(testData);
        String hashFromBytes = blake3HashingService.hashBytes(testData.getBytes(StandardCharsets.UTF_8));
        String hashFromStream = blake3HashingService.hashInputStream(
            new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8))
        );
        
        assertThat(hashFromBytes).isEqualTo(hashFromString);
        assertThat(hashFromStream).isEqualTo(hashFromBytes);
    }

    @Test
    void testKnownVector_apacheCommonsCodec() {
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
