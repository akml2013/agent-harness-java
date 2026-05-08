package com.example.core.management.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.core.management.DataManager;
import com.example.core.management.ObjectDataManager;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class MinioDataManager implements DataManager, ObjectDataManager {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket:agent-files}")
    private String bucket;

    private MinioClient minioClient;

    public MinioDataManager() {
    }

    public MinioDataManager(String endpoint, String accessKey, String secretKey, String bucket) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    @PostConstruct
    @Override
    public void initialize() {
        if (endpoint == null) {
            endpoint = "http://localhost:9000";
        }
        if (accessKey == null) {
            accessKey = "minioadmin";
        }
        if (secretKey == null) {
            secretKey = "minioadmin";
        }
        if (bucket == null) {
            bucket = "agent-files";
        }
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        log.info("MinIO数据管理器初始化完成: endpoint={}, bucket={}", endpoint, bucket);
    }

    @Override
    public void destroy() {
        log.info("MinIO数据管理器已关闭");
    }

    @Override
    public String getType() {
        return "MINIO";
    }

    @Override
    public boolean isAvailable() {
        try {
            minioClient.listBuckets();
            return true;
        } catch (Exception e) {
            log.warn("MinIO不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        return upload(key, new ByteArrayInputStream(data), data.length, contentType);
    }

    @Override
    public String upload(String key, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            log.debug("MinIO上传成功: {}", key);
            return key;
        } catch (Exception e) {
            log.error("MinIO上传失败: {}, 错误: {}", key, e.getMessage());
            throw new RuntimeException("MinIO上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadBytes(String key) {
        try (InputStream is = downloadStream(key);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("MinIO下载字节失败: {}, 错误: {}", key, e.getMessage());
            throw new RuntimeException("MinIO下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadStream(String key) {
        try {
            return minioClient.getObject(io.minio.GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.error("MinIO下载失败: {}, 错误: {}", key, e.getMessage());
            throw new RuntimeException("MinIO下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.debug("MinIO删除成功: {}", key);
        } catch (Exception e) {
            log.error("MinIO删除失败: {}, 错误: {}", key, e.getMessage());
            throw new RuntimeException("MinIO删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String copy(String sourceKey, String destinationKey) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destinationKey)
                    .source(CopySource.builder()
                            .bucket(bucket)
                            .object(sourceKey)
                            .build())
                    .build());
            log.debug("MinIO复制成功: {} -> {}", sourceKey, destinationKey);
            return destinationKey;
        } catch (Exception e) {
            log.error("MinIO复制失败: {} -> {}, 错误: {}", sourceKey, destinationKey, e.getMessage());
            throw new RuntimeException("MinIO复制失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expiryMinutes) {
        return generatePresignedUrl(key, expiryMinutes, null);
    }

    @Override
    public String generatePresignedUrl(String key, int expiryMinutes, String downloadFileName) {
        try {
            GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expiryMinutes, TimeUnit.MINUTES);
            if (downloadFileName != null && !downloadFileName.isBlank()) {
                builder.extraQueryParams(Map.of("response-content-disposition",
                        "attachment; filename=\"" + downloadFileName + "\""));
            }
            return minioClient.getPresignedObjectUrl(builder.build());
        } catch (Exception e) {
            log.error("MinIO生成预签名URL失败: {}, 错误: {}", key, e.getMessage());
            throw new RuntimeException("MinIO生成预签名URL失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateKey(Long userId, String sessionId, String fileName) {
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = fileName.substring(dotIndex);
        }
        return String.format("users/%d/sessions/%s/%s%s",
                userId, sessionId, UUID.randomUUID().toString().substring(0, 8), ext);
    }

    public String getBucket() {
        return bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
