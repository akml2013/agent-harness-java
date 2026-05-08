package com.example.core.memory.store.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.core.entity.AgentChatMessage;
import com.example.core.entity.AgentOperationDetail;
import com.example.core.mapper.AgentChatMessageMapper;
import com.example.core.mapper.AgentOperationDetailMapper;
import com.example.core.memory.config.MemoryProperties;
import com.example.core.memory.model.MemoryFragment;
import com.example.core.memory.model.MemoryMessage;
import com.example.core.memory.model.MemoryPriority;
import com.example.core.memory.model.MemoryType;
import com.example.core.memory.store.MemoryStore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShortTermMemoryStore implements MemoryStore {

    private static final String KEY_PREFIX = "memory:short:";

    private static final Set<String> INTERACTION_TYPES = Set.of(
            AgentChatMessage.TYPE_USER_INPUT,
            AgentChatMessage.TYPE_USER_EVENT,
            AgentChatMessage.TYPE_ASK_USER);

    private static final Set<String> THOUGHT_TYPES = Set.of(
            AgentChatMessage.TYPE_AI_THOUGHT,
            AgentChatMessage.TYPE_AI_THOUGHT_BRIEF);

    private static final Set<String> WRITE_OPERATION_TYPES = Set.of(
            AgentChatMessage.TYPE_MCP_ACTION,
            AgentChatMessage.TYPE_MCP_RESULT,
            AgentChatMessage.TYPE_SYSTEM_ACTION,
            AgentChatMessage.TYPE_SYSTEM_RESULT);

    private static final Set<String> AOF_TYPES = new HashSet<>();
    static {
        AOF_TYPES.addAll(INTERACTION_TYPES);
        AOF_TYPES.addAll(THOUGHT_TYPES);
        AOF_TYPES.addAll(WRITE_OPERATION_TYPES);
    }

    private static final Set<String> FILE_WRITE_TOOLS = Set.of(
            "write_file", "create_document", "edit_document", "delete_file",
            "create_workflow", "update_workflow", "delete_workflow",
            "create_scheduled_task", "update_scheduled_task", "cancel_scheduled_task");

    private static final Set<String> FILE_READ_TOOLS = Set.of(
            "read_file", "list_files", "search_files", "get_file_content");

    private final Object redisTemplate;
    private final AgentChatMessageMapper chatMessageMapper;
    private final AgentOperationDetailMapper operationDetailMapper;
    private final MemoryProperties properties;

    public ShortTermMemoryStore(Object redisTemplate, AgentChatMessageMapper chatMessageMapper,
            AgentOperationDetailMapper operationDetailMapper, MemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.chatMessageMapper = chatMessageMapper;
        this.operationDetailMapper = operationDetailMapper;
        this.properties = properties;
    }

    @Override
    public void store(MemoryMessage message) {
        writeToRedis(message);
    }

    @Override
    public void storeBatch(List<MemoryMessage> messages) {
        batchWriteToRedis(messages);
    }

    @Override
    public List<MemoryFragment> retrieve(String sessionId, int limit) {
        return performAofRewrite(sessionId, limit);
    }

    @Override
    public void delete(String sessionId) {
        deleteFromRedis(sessionId);
    }

    @Override
    public boolean exists(String sessionId) {
        return existsInRedis(sessionId) || existsInChatMessages(sessionId);
    }

    public List<MemoryFragment> performAofRewrite(String sessionId, int limit) {
        List<MemoryFragment> rewritten = new ArrayList<>();

        try {
            LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                    .in(AgentChatMessage::getMessageType, AOF_TYPES)
                    .orderByAsc(AgentChatMessage::getCreateTime);

            List<AgentChatMessage> allMessages = chatMessageMapper.selectList(wrapper);

            Map<Long, AgentOperationDetail> detailMap = loadOperationDetails(sessionId);

            List<AgentChatMessage> interactionMessages = allMessages.stream()
                    .filter(m -> INTERACTION_TYPES.contains(m.getMessageType()))
                    .collect(Collectors.toList());

            List<AgentChatMessage> thoughtMessages = allMessages.stream()
                    .filter(m -> THOUGHT_TYPES.contains(m.getMessageType()))
                    .collect(Collectors.toList());

            List<AgentChatMessage> actionMessages = allMessages.stream()
                    .filter(m -> WRITE_OPERATION_TYPES.contains(m.getMessageType()))
                    .collect(Collectors.toList());

            for (AgentChatMessage msg : interactionMessages) {
                MemoryFragment fragment = buildFragmentFromMessage(msg, detailMap);
                rewritten.add(fragment);
            }

            Map<Integer, List<AgentChatMessage>> thoughtsByRound = thoughtMessages.stream()
                    .filter(m -> m.getRoundIndex() != null)
                    .collect(Collectors.groupingBy(AgentChatMessage::getRoundIndex));

            for (Map.Entry<Integer, List<AgentChatMessage>> entry : thoughtsByRound.entrySet()) {
                List<AgentChatMessage> roundThoughts = entry.getValue();

                AgentChatMessage fullThought = roundThoughts.stream()
                        .filter(m -> AgentChatMessage.TYPE_AI_THOUGHT.equals(m.getMessageType()))
                        .findFirst().orElse(null);

                if (fullThought != null) {
                    MemoryFragment fragment = buildFragmentFromMessage(fullThought, detailMap);
                    rewritten.add(fragment);
                } else {
                    AgentChatMessage briefThought = roundThoughts.stream()
                            .filter(m -> AgentChatMessage.TYPE_AI_THOUGHT_BRIEF.equals(m.getMessageType()))
                            .findFirst().orElse(null);
                    if (briefThought != null) {
                        MemoryFragment fragment = buildFragmentFromMessage(briefThought, detailMap);
                        rewritten.add(fragment);
                    }
                }
            }

            Map<Integer, List<AgentChatMessage>> actionsByRound = actionMessages.stream()
                    .filter(m -> m.getRoundIndex() != null)
                    .collect(Collectors.groupingBy(AgentChatMessage::getRoundIndex));

            for (Map.Entry<Integer, List<AgentChatMessage>> entry : actionsByRound.entrySet()) {
                List<AgentChatMessage> roundActions = entry.getValue();

                AgentChatMessage actionMsg = roundActions.stream()
                        .filter(m -> AgentChatMessage.TYPE_MCP_ACTION.equals(m.getMessageType())
                                || AgentChatMessage.TYPE_SYSTEM_ACTION.equals(m.getMessageType()))
                        .findFirst().orElse(null);

                AgentChatMessage resultMsg = roundActions.stream()
                        .filter(m -> AgentChatMessage.TYPE_MCP_RESULT.equals(m.getMessageType())
                                || AgentChatMessage.TYPE_SYSTEM_RESULT.equals(m.getMessageType()))
                        .findFirst().orElse(null);

                if (actionMsg != null) {
                    String operationType = AgentChatMessage.TYPE_SYSTEM_ACTION.equals(actionMsg.getMessageType())
                            ? AgentOperationDetail.TYPE_SYSTEM
                            : AgentOperationDetail.TYPE_MCP;

                    StringBuilder content = new StringBuilder();
                    content.append("[").append(operationType).append("] ");
                    content.append(actionMsg.getContent());

                    if (resultMsg != null) {
                        String resultContent = resultMsg.getContent();
                        if (resultContent != null
                                && resultContent.length() > properties.getShortTerm().getResultTruncateLength()) {
                            resultContent = resultContent.substring(0,
                                    properties.getShortTerm().getResultTruncateLength()) + "...";
                        }
                        content.append(" → ").append(resultContent);
                    }

                    AgentOperationDetail detail = actionMsg.getId() != null
                            ? detailMap.get(actionMsg.getId())
                            : null;

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("round_index", entry.getKey());
                    metadata.put("operation_type", operationType);
                    if (detail != null) {
                        metadata.put("tool_name", detail.getToolName());
                        metadata.put("file_key", detail.getFileKey());
                        metadata.put("duration_ms", detail.getDurationMs());
                    }

                    MemoryFragment fragment = MemoryFragment.builder()
                            .id(String.valueOf(actionMsg.getId()))
                            .content(content.toString())
                            .sourceType(MemoryType.SHORT_TERM)
                            .priority(MemoryPriority.P2)
                            .relevanceScore(1.0)
                            .timestamp(actionMsg.getCreateTime())
                            .metadata(metadata)
                            .build();
                    rewritten.add(fragment);
                }
            }

            rewritten.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

            rewritten = deduplicateFileOperations(rewritten);

            if (rewritten.size() > limit) {
                rewritten = new ArrayList<>(rewritten.subList(rewritten.size() - limit, rewritten.size()));
            }

            log.debug("短期记忆AOF重写完成: sessionId={}, 原始消息={}, 重写后={}",
                    sessionId, allMessages.size(), rewritten.size());
        } catch (Exception e) {
            log.error("短期记忆AOF重写失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return rewritten;
    }

    List<MemoryFragment> deduplicateFileOperations(List<MemoryFragment> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return fragments;
        }

        Map<String, FileInfo> fileOperationMap = new LinkedHashMap<>();

        for (int i = 0; i < fragments.size(); i++) {
            MemoryFragment fragment = fragments.get(i);
            Map<String, Object> meta = fragment.getMetadata();
            if (meta == null) {
                continue;
            }

            String fileKey = (String) meta.get("file_key");
            String toolName = (String) meta.get("tool_name");

            if (fileKey == null || fileKey.isEmpty() || toolName == null) {
                continue;
            }

            boolean isWrite = FILE_WRITE_TOOLS.contains(toolName);
            boolean isRead = FILE_READ_TOOLS.contains(toolName);

            if (!isWrite && !isRead) {
                continue;
            }

            FileInfo info = fileOperationMap.computeIfAbsent(fileKey, k -> new FileInfo());
            if (isWrite) {
                info.lastWriteIndex = i;
                info.hasWrite = true;
            }
            if (isRead) {
                info.lastReadIndex = i;
                info.hasRead = true;
            }
        }

        if (fileOperationMap.isEmpty()) {
            return fragments;
        }

        Set<Integer> indicesToRemove = new HashSet<>();

        for (Map.Entry<String, FileInfo> entry : fileOperationMap.entrySet()) {
            FileInfo info = entry.getValue();

            for (int i = 0; i < fragments.size(); i++) {
                if (indicesToRemove.contains(i)) {
                    continue;
                }

                MemoryFragment fragment = fragments.get(i);
                Map<String, Object> meta = fragment.getMetadata();
                if (meta == null) {
                    continue;
                }

                String fileKey = (String) meta.get("file_key");
                String toolName = (String) meta.get("tool_name");

                if (!entry.getKey().equals(fileKey) || toolName == null) {
                    continue;
                }

                boolean isWrite = FILE_WRITE_TOOLS.contains(toolName);
                boolean isRead = FILE_READ_TOOLS.contains(toolName);

                if (isWrite && i != info.lastWriteIndex) {
                    indicesToRemove.add(i);
                } else if (isRead) {
                    if (i != info.lastReadIndex) {
                        indicesToRemove.add(i);
                    } else {
                        if (info.hasWrite && info.lastWriteIndex > i) {
                            indicesToRemove.add(i);
                        }
                    }
                }
            }
        }

        if (indicesToRemove.isEmpty()) {
            return fragments;
        }

        List<MemoryFragment> result = new ArrayList<>(fragments.size() - indicesToRemove.size());
        for (int i = 0; i < fragments.size(); i++) {
            if (!indicesToRemove.contains(i)) {
                result.add(fragments.get(i));
            }
        }

        log.debug("文件操作去重: 原始={}, 去重后={}, 移除={}",
                fragments.size(), result.size(), indicesToRemove.size());

        return result;
    }

    private static class FileInfo {
        int lastReadIndex = -1;
        int lastWriteIndex = -1;
        boolean hasRead = false;
        boolean hasWrite = false;
    }

    private Map<Long, AgentOperationDetail> loadOperationDetails(String sessionId) {
        Map<Long, AgentOperationDetail> detailMap = new HashMap<>();
        try {
            if (operationDetailMapper == null) {
                return detailMap;
            }
            List<AgentOperationDetail> details = operationDetailMapper.selectBySessionId(sessionId);
            for (AgentOperationDetail detail : details) {
                if (detail.getMessageId() != null) {
                    detailMap.put(detail.getMessageId(), detail);
                }
            }
        } catch (Exception e) {
            log.warn("加载操作详情失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
        }
        return detailMap;
    }

    private MemoryFragment buildFragmentFromMessage(AgentChatMessage msg,
            Map<Long, AgentOperationDetail> detailMap) {
        String role = mapRole(msg.getMessageType(), msg.getRole());
        MemoryPriority priority = mapMessageTypeToPriority(msg.getMessageType());

        Map<String, Object> metadata = parseMetadata(msg.getMetadata());
        if (msg.getRoundIndex() != null) {
            metadata.put("round_index", msg.getRoundIndex());
        }

        return MemoryFragment.builder()
                .id(String.valueOf(msg.getId()))
                .content(role + ": " + msg.getContent())
                .sourceType(MemoryType.SHORT_TERM)
                .priority(priority)
                .relevanceScore(1.0)
                .timestamp(msg.getCreateTime())
                .metadata(metadata)
                .build();
    }

    private void writeToRedis(MemoryMessage message) {
        String key = KEY_PREFIX + message.getSessionId();
        String field = message.getId();
        String value = JSON.toJSONString(message);

        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;
            template.opsForHash().put(key, field, value);
            template.expire(key, properties.getShortTerm().getTtl(), TimeUnit.SECONDS);
            trimToMaxRounds(key, template);
            log.debug("短期记忆Redis缓存成功: sessionId={}", message.getSessionId());
        } catch (Exception e) {
            log.error("短期记忆Redis缓存失败: {}", e.getMessage());
        }
    }

    private void batchWriteToRedis(List<MemoryMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }

        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;

            String sessionId = messages.get(0).getSessionId();
            String key = KEY_PREFIX + sessionId;

            template.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (MemoryMessage message : messages) {
                            String field = message.getId();
                            String value = JSON.toJSONString(message);
                            connection.hSet(key.getBytes(), field.getBytes(), value.getBytes());
                        }
                        return null;
                    });
            template.expire(key, properties.getShortTerm().getTtl(), TimeUnit.SECONDS);
            trimToMaxRounds(key, template);
            log.debug("短期记忆Redis批量缓存成功: sessionId={}, count={}", sessionId, messages.size());
        } catch (Exception e) {
            log.error("短期记忆Redis批量缓存失败: {}", e.getMessage());
        }
    }

    private void deleteFromRedis(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;
            template.delete(key);
            log.debug("短期记忆Redis缓存已删除: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("短期记忆Redis缓存删除失败: {}", e.getMessage());
        }
    }

    private boolean existsInRedis(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;
            return Boolean.TRUE.equals(template.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean existsInChatMessages(String sessionId) {
        try {
            LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                    .in(AgentChatMessage::getMessageType, AOF_TYPES)
                    .last("LIMIT 1");
            return chatMessageMapper.selectCount(wrapper) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void trimToMaxRounds(String key,
            org.springframework.data.redis.core.StringRedisTemplate template) {
        Set<Object> fields = template.opsForHash().keys(key);
        if (fields != null && fields.size() > properties.getShortTerm().getMaxRounds()) {
            List<MemoryMessage> all = new ArrayList<>();
            for (Object field : fields) {
                Object value = template.opsForHash().get(key, field);
                if (value != null) {
                    try {
                        all.add(JSON.parseObject(value.toString(), MemoryMessage.class));
                    } catch (Exception ignored) {
                    }
                }
            }
            all.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

            int toRemove = all.size() - properties.getShortTerm().getMaxRounds();
            for (int i = 0; i < toRemove; i++) {
                template.opsForHash().delete(key, all.get(i).getId());
            }
        }
    }

    private String mapRole(String messageType, String role) {
        if (AgentChatMessage.TYPE_USER_INPUT.equals(messageType)
                || AgentChatMessage.TYPE_USER_EVENT.equals(messageType)) {
            return "user";
        }
        if (AgentChatMessage.TYPE_AI_THOUGHT.equals(messageType)
                || AgentChatMessage.TYPE_AI_THOUGHT_BRIEF.equals(messageType)
                || AgentChatMessage.TYPE_ASK_USER.equals(messageType)
                || AgentChatMessage.TYPE_TASK_UPDATE.equals(messageType)) {
            return "assistant";
        }
        if (AgentChatMessage.TYPE_MCP_ACTION.equals(messageType)
                || AgentChatMessage.TYPE_MCP_RESULT.equals(messageType)) {
            return "tool";
        }
        if (AgentChatMessage.TYPE_SYSTEM_ACTION.equals(messageType)
                || AgentChatMessage.TYPE_SYSTEM_RESULT.equals(messageType)) {
            return "system";
        }
        return role != null ? role : "unknown";
    }

    private MemoryPriority mapMessageTypeToPriority(String messageType) {
        if (AgentChatMessage.TYPE_USER_INPUT.equals(messageType)
                || AgentChatMessage.TYPE_USER_EVENT.equals(messageType)) {
            return MemoryPriority.P1;
        }
        if (AgentChatMessage.TYPE_MCP_ACTION.equals(messageType)
                || AgentChatMessage.TYPE_SYSTEM_ACTION.equals(messageType)) {
            return MemoryPriority.P2;
        }
        return MemoryPriority.P3;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JSON.parseObject(metadataJson, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
