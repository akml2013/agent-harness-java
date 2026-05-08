package com.example.core.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.core.entity.AgentChatMessage;

public interface ChatMessageService {

        AgentChatMessage saveMessage(String sessionId, Long userId, String messageType,
                        String role, String content, String metadata, Long parentId,
                        Integer roundIndex, Integer durationMs);

        AgentChatMessage saveUserMessage(String sessionId, Long userId, String content);

        AgentChatMessage saveAiText(String sessionId, Long userId, String content,
                        Integer roundIndex, Integer durationMs, String model);

        AgentChatMessage saveAiThought(String sessionId, Long userId, String content, Integer roundIndex);

        AgentChatMessage saveAiThoughtBrief(String sessionId, Long userId, String content, Integer roundIndex);

        AgentChatMessage saveMcpAction(String sessionId, Long userId, String toolName,
                        Map<String, Object> actionInput, Integer roundIndex);

        AgentChatMessage saveMcpResult(String sessionId, Long userId, String content,
                        Map<String, Object> data, Long parentId, Integer roundIndex);

        AgentChatMessage saveAskUser(String sessionId, Long userId, String question,
                        List<String> options, Integer roundIndex);

        AgentChatMessage saveUserInput(String sessionId, Long userId,
                        String selectedOption, String userInput, Integer roundIndex);

        AgentChatMessage saveFileGenerated(String sessionId, Long userId,
                        Long fileId, String fileKey, String fileName, String fileType, String source);

        AgentChatMessage saveFileDeleted(String sessionId, Long userId,
                        List<Map<String, Object>> deletedFiles);

        AgentChatMessage saveTaskUpdate(String sessionId, Long userId,
                        List<Map<String, Object>> taskList, Integer roundIndex);

        AgentChatMessage saveError(String sessionId, Long userId, String errorMessage,
                        String errorType, Integer roundIndex);

        AgentChatMessage saveUserEvent(String sessionId, Long userId, String content,
                        Map<String, Object> metadata);

        AgentChatMessage saveSystemAction(String sessionId, Long userId, String toolName,
                        Map<String, Object> actionInput, Integer roundIndex);

        AgentChatMessage saveSystemResult(String sessionId, Long userId, String content,
                        Map<String, Object> data, Long parentId, Integer roundIndex);

        AgentChatMessage saveAgentProactiveMessage(String sessionId, Long userId, String content,
                        Integer roundIndex);

        Page<AgentChatMessage> getMessages(String sessionId, String messageType,
                        int page, int size);

        List<AgentChatMessage> getRecentMessages(String sessionId, int limit);

        int countBySession(String sessionId);

        int countBySessionAndTypes(String sessionId, List<String> types);

        Map<String, Object> getMessagesByRounds(String sessionId, int rounds, Integer beforeSortOrder);

        int getNextSortOrder(String sessionId);

        void updateContent(Long id, String content);
}
