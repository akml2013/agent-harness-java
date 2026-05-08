package com.example.core.storage;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.example.core.management.ObjectDataManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private final ObjectDataManager objectDataManager;

    @Override
    public String upload(String objectKey, InputStream inputStream, long size, String contentType) {
        return objectDataManager.upload(objectKey, inputStream, size, contentType);
    }

    @Override
    public InputStream download(String objectKey) {
        return objectDataManager.downloadStream(objectKey);
    }

    @Override
    public void delete(String objectKey) {
        objectDataManager.delete(objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        return objectDataManager.exists(objectKey);
    }

    @Override
    public String copy(String sourceKey, String destinationKey) {
        return objectDataManager.copy(sourceKey, destinationKey);
    }

    @Override
    public String generateDownloadUrl(String objectKey, int expiryMinutes) {
        return objectDataManager.generatePresignedUrl(objectKey, expiryMinutes);
    }

    @Override
    public String generateDownloadUrl(String objectKey, int expiryMinutes, String downloadFileName) {
        return objectDataManager.generatePresignedUrl(objectKey, expiryMinutes, downloadFileName);
    }

    @Override
    public String generateUploadKey(Long userId, String sessionId, String fileName) {
        return objectDataManager.generateKey(userId, sessionId, fileName);
    }
}
