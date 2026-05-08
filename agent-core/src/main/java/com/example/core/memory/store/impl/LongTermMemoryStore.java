package com.example.core.memory.store.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.core.entity.AgentChatMessage;
import com.example.core.entity.AgentSessionSummary;
import com.example.core.mapper.AgentChatMessageMapper;
import com.example.core.mapper.AgentSessionSummaryMapper;
import com.example.core.memory.config.MemoryProperties;
import com.example.core.memory.model.MemoryFragment;
import com.example.core.memory.model.MemoryPriority;
import com.example.core.memory.model.MemoryType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LongTermMemoryStore {

    private static final String SUMMARY_CACHE_PREFIX = "memory:long:summary:";
    private static final Set<String> EFFECTIVE_CONVERSATION_TYPES = Set.of(
            AgentChatMessage.TYPE_USER_MESSAGE,
            AgentChatMessage.TYPE_USER_INPUT,
            AgentChatMessage.TYPE_AI_TEXT);

    private final Object redisTemplate;
    private final AgentChatMessageMapper chatMessageMapper;
    private final AgentSessionSummaryMapper summaryMapper;
    private final MemoryProperties properties;

    public LongTermMemoryStore(Object redisTemplate,
            AgentChatMessageMapper chatMessageMapper,
            AgentSessionSummaryMapper summaryMapper,
            MemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.chatMessageMapper = chatMessageMapper;
        this.summaryMapper = summaryMapper;
        this.properties = properties;
    }

    public List<MemoryFragment> retrieveEffectiveConversations(String sessionId, int offset, int count) {
        List<MemoryFragment> fragments = new ArrayList<>();
        try {
            LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                    .in(AgentChatMessage::getMessageType, EFFECTIVE_CONVERSATION_TYPES)
                    .orderByDesc(AgentChatMessage::getCreateTime)
                    .last("LIMIT " + (offset + count));

            List<AgentChatMessage> messages = chatMessageMapper.selectList(wrapper);

            int total = messages.size();
            int fromIndex = Math.min(offset, total);
            int toIndex = Math.min(offset + count, total);

            List<AgentChatMessage> effectiveMessages = messages.subList(fromIndex, toIndex);

            for (AgentChatMessage msg : effectiveMessages) {
                String role = isUserType(msg.getMessageType()) ? "user" : "assistant";
                MemoryFragment fragment = MemoryFragment.builder()
                        .id(String.valueOf(msg.getId()))
                        .content(role + ": " + msg.getContent())
                        .sourceType(MemoryType.LONG_TERM)
                        .priority(MemoryPriority.P3)
                        .relevanceScore(1.0)
                        .timestamp(msg.getCreateTime())
                        .build();
                fragments.add(fragment);
            }

            fragments.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

            log.debug("召回有效对话段: sessionId={}, offset={}, count={}, 实际={}",
                    sessionId, offset, count, fragments.size());
        } catch (Exception e) {
            log.error("召回有效对话段失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
        return fragments;
    }

    public AgentSessionSummary retrieveSummary(String sessionId) {
        AgentSessionSummary summary = retrieveSummaryFromRedis(sessionId);
        if (summary != null) {
            return summary;
        }

        summary = retrieveSummaryFromMySQL(sessionId);
        if (summary != null) {
            cacheSummaryToRedis(sessionId, summary);
        }
        return summary;
    }

    public Long getSummaryLastMessageId(String sessionId) {
        AgentSessionSummary summary = retrieveSummary(sessionId);
        return summary != null ? summary.getLastMessageId() : null;
    }

    public void saveSummary(String sessionId, Long userId, String summaryContent,
            Long lastMessageId, String summaryType, int tokenCount) {
        try {
            AgentSessionSummary existing = retrieveLatestSummaryFromMySQL(sessionId);

            if (existing != null) {
                existing.setSummaryContent(summaryContent);
                existing.setLastMessageId(lastMessageId);
                existing.setSummaryType(summaryType);
                existing.setTokenCount(tokenCount);
                existing.setUpdateTime(java.time.LocalDateTime.now());
                summaryMapper.updateById(existing);
                log.debug("更新摘要: sessionId={}, type={}, lastMsgId={}, tokens={}",
                        sessionId, summaryType, lastMessageId, tokenCount);
            } else {
                AgentSessionSummary newSummary = AgentSessionSummary.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .summaryType(summaryType)
                        .summaryContent(summaryContent)
                        .lastMessageId(lastMessageId)
                        .tokenCount(tokenCount)
                        .build();
                summaryMapper.insert(newSummary);
                log.debug("创建摘要: sessionId={}, type={}, lastMsgId={}, tokens={}",
                        sessionId, summaryType, lastMessageId, tokenCount);
            }

            invalidateSummaryCache(sessionId);

            AgentSessionSummary updated = retrieveLatestSummaryFromMySQL(sessionId);
            if (updated != null) {
                cacheSummaryToRedis(sessionId, updated);
            }
        } catch (Exception e) {
            log.error("保存摘要失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    public List<MemoryFragment> retrievePendingCompression(String sessionId, Long afterMessageId,
            Long beforeMessageId) {
        List<MemoryFragment> fragments = new ArrayList<>();
        try {
            LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                    .in(AgentChatMessage::getMessageType, EFFECTIVE_CONVERSATION_TYPES)
                    .orderByAsc(AgentChatMessage::getCreateTime);

            if (afterMessageId != null) {
                wrapper.gt(AgentChatMessage::getId, afterMessageId);
            }
            if (beforeMessageId != null) {
                wrapper.lt(AgentChatMessage::getId, beforeMessageId);
            }

            List<AgentChatMessage> messages = chatMessageMapper.selectList(wrapper);

            for (AgentChatMessage msg : messages) {
                String role = AgentChatMessage.TYPE_USER_INPUT.equals(msg.getMessageType()) ? "user" : "assistant";
                MemoryFragment fragment = MemoryFragment.builder()
                        .id(String.valueOf(msg.getId()))
                        .content(role + ": " + msg.getContent())
                        .sourceType(MemoryType.LONG_TERM)
                        .priority(MemoryPriority.P3)
                        .relevanceScore(1.0)
                        .timestamp(msg.getCreateTime())
                        .build();
                fragments.add(fragment);
            }

            log.debug("查询待压缩记录: sessionId={}, afterMsgId={}, beforeMsgId={}, count={}",
                    sessionId, afterMessageId, beforeMessageId, fragments.size());
        } catch (Exception e) {
            log.error("查询待压缩记录失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
        return fragments;
    }

    public boolean needsFullCompression(String sessionId) {
        AgentSessionSummary summary = retrieveSummary(sessionId);
        if (summary == null) {
            return false;
        }
        int threshold = properties.getLongTerm().getSummaryTokenThreshold();
        return summary.getTokenCount() != null && summary.getTokenCount() >= threshold;
    }

    public int countEffectiveConversations(String sessionId) {
        try {
            LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                    .in(AgentChatMessage::getMessageType, EFFECTIVE_CONVERSATION_TYPES);
            return Math.toIntExact(chatMessageMapper.selectCount(wrapper));
        } catch (Exception e) {
            log.error("统计有效对话数失败: sessionId={}, error={}", sessionId, e.getMessage());
            return 0;
        }
    }

    public Long getEarliestEffectiveConversationId(String sessionId, int skipLastN) {
        try {
            int total = countEffectiveConversations(sessionId);
            if (total <= skipLastN) {
                return null;
            }
            int offset = total - skipLastN;
            LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                    .in(AgentChatMessage::getMessageType, EFFECTIVE_CONVERSATION_TYPES)
                    .orderByAsc(AgentChatMessage::getCreateTime)
                    .last("LIMIT 1 OFFSET " + offset);
            AgentChatMessage msg = chatMessageMapper.selectOne(wrapper);
            return msg != null ? msg.getId() : null;
        } catch (Exception e) {
            log.error("获取有效对话段最早ID失败: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    public void deleteSummary(String sessionId) {
        try {
            LambdaQueryWrapper<AgentSessionSummary> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentSessionSummary::getSessionId, sessionId);
            summaryMapper.delete(wrapper);
            invalidateSummaryCache(sessionId);
            log.debug("删除摘要: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("删除摘要失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private AgentSessionSummary retrieveSummaryFromRedis(String sessionId) {
        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;
            String key = SUMMARY_CACHE_PREFIX + sessionId;
            Map<Object, Object> entries = template.opsForHash().entries(key);
            if (entries.isEmpty()) {
                return null;
            }
            AgentSessionSummary summary = new AgentSessionSummary();
            summary.setId(Long.valueOf(entries.get("id").toString()));
            summary.setSessionId(sessionId);
            summary.setUserId(entries.get("userId") != null ? Long.valueOf(entries.get("userId").toString()) : null);
            summary.setSummaryType(entries.get("summaryType") != null ? entries.get("summaryType").toString() : null);
            summary.setSummaryContent(
                    entries.get("summaryContent") != null ? entries.get("summaryContent").toString() : null);
            summary.setLastMessageId(
                    entries.get("lastMessageId") != null ? Long.valueOf(entries.get("lastMessageId").toString())
                            : null);
            summary.setTokenCount(
                    entries.get("tokenCount") != null ? Integer.valueOf(entries.get("tokenCount").toString()) : null);
            return summary;
        } catch (Exception e) {
            log.debug("Redis摘要缓存未命中: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    private AgentSessionSummary retrieveLatestSummaryFromMySQL(String sessionId) {
        try {
            LambdaQueryWrapper<AgentSessionSummary> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentSessionSummary::getSessionId, sessionId)
                    .orderByDesc(AgentSessionSummary::getId)
                    .last("LIMIT 1");
            return summaryMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.error("MySQL摘要查询失败: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    private AgentSessionSummary retrieveSummaryFromMySQL(String sessionId) {
        return retrieveLatestSummaryFromMySQL(sessionId);
    }

    private void cacheSummaryToRedis(String sessionId, AgentSessionSummary summary) {
        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;
            String key = SUMMARY_CACHE_PREFIX + sessionId;
            Map<String, String> hash = new HashMap<>();
            hash.put("id", String.valueOf(summary.getId()));
            hash.put("userId", summary.getUserId() != null ? String.valueOf(summary.getUserId()) : "0");
            hash.put("summaryType", summary.getSummaryType() != null ? summary.getSummaryType() : "");
            hash.put("summaryContent", summary.getSummaryContent() != null ? summary.getSummaryContent() : "");
            hash.put("lastMessageId",
                    summary.getLastMessageId() != null ? String.valueOf(summary.getLastMessageId()) : "0");
            hash.put("tokenCount", summary.getTokenCount() != null ? String.valueOf(summary.getTokenCount()) : "0");
            template.opsForHash().putAll(key, hash);
            template.expire(key, properties.getLongTerm().getSummaryCacheTtl(), TimeUnit.SECONDS);
            log.debug("摘要缓存到Redis: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("摘要缓存Redis失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private void invalidateSummaryCache(String sessionId) {
        try {
            org.springframework.data.redis.core.StringRedisTemplate template = (org.springframework.data.redis.core.StringRedisTemplate) redisTemplate;
            String key = SUMMARY_CACHE_PREFIX + sessionId;
            template.delete(key);
        } catch (Exception e) {
            log.warn("摘要缓存失效失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private boolean isUserType(String messageType) {
        return AgentChatMessage.TYPE_USER_MESSAGE.equals(messageType)
                || AgentChatMessage.TYPE_USER_INPUT.equals(messageType);
    }
}
