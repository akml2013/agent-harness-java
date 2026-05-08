package com.example.core.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.AgentKnowledgeBase;
import com.example.core.entity.AgentKnowledgeChunk;
import com.example.core.management.VectorCollectionSchema;
import com.example.core.management.VectorDataManager;
import com.example.core.management.VectorFieldSchema;
import com.example.core.management.VectorIndexSchema;
import com.example.core.management.VectorSearchRequest;
import com.example.core.management.VectorSearchResult;
import com.example.core.mapper.AgentKnowledgeBaseMapper;
import com.example.core.mapper.AgentKnowledgeChunkMapper;
import com.example.core.memory.service.EmbeddingService;
import com.example.core.service.KnowledgeBaseService;
import com.example.core.service.KnowledgeIndexService;
import com.example.core.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final String COLLECTION_NAME = "knowledge_chunks";

    private final AgentKnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentKnowledgeChunkMapper knowledgeChunkMapper;
    private final FileStorageService fileStorageService;
    private final VectorDataManager vectorDataManager;
    private final EmbeddingService embeddingService;
    private final KnowledgeIndexService knowledgeIndexService;

    @Override
    @Transactional
    public AgentKnowledgeBase uploadAndCreate(Long userId, InputStream fileStream, String fileName,
            String contentType, long fileSize,
            String name, String description,
            String chunkStrategy, Integer chunkSize, Integer chunkOverlap) {
        String kbId = UUID.randomUUID().toString().replace("-", "");

        String storageKey = fileStorageService.generateUploadKey(userId, "knowledge", fileName);
        fileStorageService.upload(storageKey, fileStream, fileSize, contentType);

        String fileType = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (name == null || name.isBlank()) {
            name = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        if (chunkStrategy == null || chunkStrategy.isBlank()) {
            chunkStrategy = AgentKnowledgeBase.STRATEGY_PARAGRAPH;
        }
        if (chunkSize == null || chunkSize <= 0) {
            chunkSize = 500;
        }
        if (chunkOverlap == null || chunkOverlap < 0) {
            chunkOverlap = 50;
        }

        AgentKnowledgeBase kb = new AgentKnowledgeBase();
        kb.setKbId(kbId);
        kb.setUserId(userId);
        kb.setName(name);
        kb.setDescription(description);
        kb.setFileName(fileName);
        kb.setFileType(fileType);
        kb.setFileSize(fileSize);
        kb.setStorageKey(storageKey);
        kb.setChunkCount(0);
        kb.setChunkStrategy(chunkStrategy);
        kb.setChunkSize(chunkSize);
        kb.setChunkOverlap(chunkOverlap);
        kb.setStatus(AgentKnowledgeBase.STATUS_UPLOADING);
        kb.setProgress(0);
        kb.setDeleted(0);
        knowledgeBaseMapper.insert(kb);

        ensureCollectionExists();

        knowledgeIndexService.indexKnowledgeBase(kbId);

        return kb;
    }

    @Override
    @Transactional
    public boolean deleteKnowledgeBase(Long userId, String kbId) {
        AgentKnowledgeBase kb = getKnowledgeBase(userId, kbId);
        if (kb == null) {
            return false;
        }

        knowledgeChunkMapper.delete(new LambdaQueryWrapper<AgentKnowledgeChunk>()
                .eq(AgentKnowledgeChunk::getKbId, kbId));

        try {
            vectorDataManager.deleteByFilter(COLLECTION_NAME, "kbId == \"" + kbId + "\"");
        } catch (Exception e) {
            log.warn("删除Milvus向量数据失败(继续删除): kbId={}, error={}", kbId, e.getMessage());
        }

        try {
            fileStorageService.delete(kb.getStorageKey());
        } catch (Exception e) {
            log.warn("删除MinIO文件失败(继续删除DB记录): storageKey={}, error={}", kb.getStorageKey(), e.getMessage());
        }

        knowledgeBaseMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getKbId, kbId)
                .eq(AgentKnowledgeBase::getUserId, userId));

        return true;
    }

    @Override
    public List<AgentKnowledgeBase> listKnowledgeBases(Long userId) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getUserId, userId)
                .eq(AgentKnowledgeBase::getDeleted, 0)
                .orderByDesc(AgentKnowledgeBase::getCreateTime));
    }

    @Override
    public AgentKnowledgeBase getKnowledgeBase(Long userId, String kbId) {
        return knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getKbId, kbId)
                .eq(AgentKnowledgeBase::getUserId, userId)
                .eq(AgentKnowledgeBase::getDeleted, 0));
    }

    @Override
    public AgentKnowledgeBase updateKnowledgeBase(Long userId, String kbId, String name, String description) {
        AgentKnowledgeBase kb = getKnowledgeBase(userId, kbId);
        if (kb == null) {
            return null;
        }

        LambdaUpdateWrapper<AgentKnowledgeBase> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                .eq(AgentKnowledgeBase::getUserId, userId);

        if (name != null && !name.isBlank()) {
            updateWrapper.set(AgentKnowledgeBase::getName, name);
        }
        if (description != null) {
            updateWrapper.set(AgentKnowledgeBase::getDescription, description);
        }

        knowledgeBaseMapper.update(null, updateWrapper);
        return getKnowledgeBase(userId, kbId);
    }

    @Override
    public boolean retryIndex(Long userId, String kbId) {
        AgentKnowledgeBase kb = getKnowledgeBase(userId, kbId);
        if (kb == null) {
            return false;
        }
        if (!AgentKnowledgeBase.STATUS_ERROR.equals(kb.getStatus())) {
            return false;
        }

        knowledgeChunkMapper.delete(new LambdaQueryWrapper<AgentKnowledgeChunk>()
                .eq(AgentKnowledgeChunk::getKbId, kbId));

        try {
            vectorDataManager.deleteByFilter(COLLECTION_NAME, "kbId == \"" + kbId + "\"");
        } catch (Exception e) {
            log.warn("清理Milvus旧数据失败(继续重试): kbId={}, error={}", kbId, e.getMessage());
        }

        LambdaUpdateWrapper<AgentKnowledgeBase> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                .set(AgentKnowledgeBase::getStatus, AgentKnowledgeBase.STATUS_UPLOADING)
                .set(AgentKnowledgeBase::getProgress, 0)
                .set(AgentKnowledgeBase::getErrorMessage, null)
                .set(AgentKnowledgeBase::getChunkCount, 0);
        knowledgeBaseMapper.update(null, updateWrapper);

        knowledgeIndexService.indexKnowledgeBase(kbId);
        return true;
    }

    @Override
    public Map<String, String> getKnowledgeBaseMap(Long userId) {
        List<AgentKnowledgeBase> kbs = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getUserId, userId)
                .eq(AgentKnowledgeBase::getStatus, AgentKnowledgeBase.STATUS_ACTIVE)
                .eq(AgentKnowledgeBase::getDeleted, 0)
                .select(AgentKnowledgeBase::getKbId, AgentKnowledgeBase::getName, AgentKnowledgeBase::getDescription));

        Map<String, String> map = new LinkedHashMap<>();
        for (AgentKnowledgeBase kb : kbs) {
            String desc = kb.getDescription() != null && !kb.getDescription().isBlank()
                    ? kb.getDescription()
                    : kb.getName();
            map.put(kb.getKbId(), desc);
        }
        return map;
    }

    @Override
    public List<Map<String, Object>> searchKnowledge(Long userId, String query, int topK) {
        return doSearch(userId, null, query, topK);
    }

    @Override
    public List<Map<String, Object>> searchKnowledgeByKb(Long userId, List<String> kbIds, String query, int topK) {
        return doSearch(userId, kbIds, query, topK);
    }

    private List<Map<String, Object>> doSearch(Long userId, List<String> kbIds, String query, int topK) {
        float[] queryVector = embeddingService.embed(query);
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vectorList.add(v);
        }

        StringBuilder filter = new StringBuilder("userId == ").append(userId);
        if (kbIds != null && !kbIds.isEmpty()) {
            String kbFilter = kbIds.stream()
                    .map(id -> "\"" + id + "\"")
                    .collect(Collectors.joining(", "));
            filter.append(" and kbId in [").append(kbFilter).append("]");
        }

        VectorSearchRequest searchRequest = VectorSearchRequest.of(
                vectorList, topK, filter.toString(),
                List.of("chunkId", "kbId", "content"));

        List<VectorSearchResult> results = vectorDataManager.search(COLLECTION_NAME, searchRequest);

        List<Map<String, Object>> searchResults = new ArrayList<>();
        for (VectorSearchResult result : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("chunk_id", result.fields().get("chunkId"));
            item.put("kb_id", result.fields().get("kbId"));
            item.put("content", result.fields().get("content"));
            item.put("score", result.score());

            String kbId = result.fields().get("kbId") != null ? result.fields().get("kbId").toString() : null;
            if (kbId != null) {
                AgentKnowledgeBase kb = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeBase>()
                        .eq(AgentKnowledgeBase::getKbId, kbId)
                        .eq(AgentKnowledgeBase::getDeleted, 0)
                        .select(AgentKnowledgeBase::getName));
                if (kb != null) {
                    item.put("kb_name", kb.getName());
                }
            }
            searchResults.add(item);
        }

        return searchResults;
    }

    private void ensureCollectionExists() {
        try {
            if (!vectorDataManager.collectionExists(COLLECTION_NAME)) {
                VectorCollectionSchema schema = new VectorCollectionSchema(
                        List.of(
                                VectorFieldSchema.primaryKey("id", "INT64", true),
                                VectorFieldSchema.varChar("chunkId", 64),
                                VectorFieldSchema.varChar("kbId", 64),
                                VectorFieldSchema.int64("userId"),
                                VectorFieldSchema.varChar("content", 4096),
                                VectorFieldSchema.vector("embedding", embeddingService.getDimension())),
                        List.of(
                                VectorIndexSchema.cosineAutoIndex("embedding")));
                vectorDataManager.createCollection(COLLECTION_NAME, schema);
                log.info("Milvus知识库集合创建成功: {}", COLLECTION_NAME);
            }
        } catch (Exception e) {
            log.error("确保Milvus知识库集合存在时出错: {}", e.getMessage());
        }
    }
}
