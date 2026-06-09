package com.setaccio.core.service;

import java.io.InputStream;

public interface Blake3HashingService {

    String hashBytes(byte[] data);

    String hashString(String input);

    String hashInputStream(InputStream inputStream);

    boolean verifyHash(byte[] data, String expectedHash);

    boolean verifyHash(InputStream inputStream, String expectedHash);
}