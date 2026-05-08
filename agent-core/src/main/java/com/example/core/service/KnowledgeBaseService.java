package com.example.core.service;

import java.util.List;
import java.util.Map;

import com.example.core.entity.AgentKnowledgeBase;

public interface KnowledgeBaseService {

    AgentKnowledgeBase uploadAndCreate(Long userId, java.io.InputStream fileStream, String fileName,
                                       String contentType, long fileSize,
                                       String name, String description,
                                       String chunkStrategy, Integer chunkSize, Integer chunkOverlap);

    boolean deleteKnowledgeBase(Long userId, String kbId);

    List<AgentKnowledgeBase> listKnowledgeBases(Long userId);

    AgentKnowledgeBase getKnowledgeBase(Long userId, String kbId);

    AgentKnowledgeBase updateKnowledgeBase(Long userId, String kbId, String name, String description);

    boolean retryIndex(Long userId, String kbId);

    Map<String, String> getKnowledgeBaseMap(Long userId);

    List<Map<String, Object>> searchKnowledge(Long userId, String query, int topK);

    List<Map<String, Object>> searchKnowledgeByKb(Long userId, List<String> kbIds, String query, int topK);
}
