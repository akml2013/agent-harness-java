package com.example.core.management;

import java.io.InputStream;

public interface ObjectDataManager extends DataManager {

    String upload(String key, byte[] data, String contentType);

    String upload(String key, InputStream inputStream, long size, String contentType);

    byte[] downloadBytes(String key);

    InputStream downloadStream(String key);

    void delete(String key);

    boolean exists(String key);

    String copy(String sourceKey, String destinationKey);

    String generatePresignedUrl(String key, int expiryMinutes);

    String generatePresignedUrl(String key, int expiryMinutes, String downloadFileName);

    String generateKey(Long userId, String sessionId, String fileName);
}
