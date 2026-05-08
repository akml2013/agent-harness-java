package com.example.core.storage;

import java.io.InputStream;

public interface FileStorageService {

    String upload(String objectKey, InputStream inputStream, long size, String contentType);

    InputStream download(String objectKey);

    void delete(String objectKey);

    boolean exists(String objectKey);

    String copy(String sourceKey, String destinationKey);

    String generateDownloadUrl(String objectKey, int expiryMinutes);

    String generateDownloadUrl(String objectKey, int expiryMinutes, String downloadFileName);

    String generateUploadKey(Long userId, String sessionId, String fileName);
}
