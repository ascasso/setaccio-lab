package com.setaccio.core.service;

import com.setaccio.core.exception.HashingException;
import org.bouncycastle.crypto.digests.Blake3Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class BouncyCastleBlake3HashingServiceImpl implements Blake3HashingService {

    private static final Logger logger = LoggerFactory.getLogger(BouncyCastleBlake3HashingServiceImpl.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int BLAKE3_HASH_SIZE = 32; // 32 bytes = 256 bits

    @Override
    public String hashBytes(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        logger.debug("Hashing {} bytes with BouncyCastle BLAKE3", data.length);
        try {
            Blake3Digest digest = new Blake3Digest();
            digest.update(data, 0, data.length);
            byte[] hash = new byte[BLAKE3_HASH_SIZE];
            digest.doFinal(hash, 0);
            String result = HexFormat.of().formatHex(hash);
            logger.debug("Successfully hashed {} bytes, result: {}", data.length, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to hash {} bytes", data.length, e);
            throw e;
        }
    }

    @Override
    public String hashString(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }

        logger.debug("Hashing string of length {} with BouncyCastle BLAKE3", input.length());
        return hashBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String hashInputStream(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        logger.debug("Hashing InputStream with BouncyCastle BLAKE3");
        try {
            Blake3Digest digest = new Blake3Digest();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            byte[] hash = new byte[BLAKE3_HASH_SIZE];
            digest.doFinal(hash, 0);
            String result = HexFormat.of().formatHex(hash);
            logger.debug("Successfully hashed {} bytes from InputStream, result: {}", totalBytes, result);
            return result;
        } catch (IOException e) {
            logger.error("Error reading from InputStream during hashing", e);
            throw new HashingException("Error reading from InputStream", e);
        }
    }

    @Override
    public boolean verifyHash(byte[] data, String expectedHash) {
        if (expectedHash == null) {
            throw new IllegalArgumentException("Expected hash cannot be null");
        }

        logger.debug("Verifying hash for {} bytes", data.length);
        String actualHash = hashBytes(data);
        boolean matches = actualHash.equalsIgnoreCase(expectedHash);
        logger.debug("Hash verification result: {}", matches);
        return matches;
    }

    @Override
    public boolean verifyHash(InputStream inputStream, String expectedHash) {
        if (expectedHash == null) {
            throw new IllegalArgumentException("Expected hash cannot be null");
        }

        logger.debug("Verifying hash for InputStream");
        String actualHash = hashInputStream(inputStream);
        boolean matches = actualHash.equalsIgnoreCase(expectedHash);
        logger.debug("Hash verification result: {}", matches);
        return matches;
    }
}
