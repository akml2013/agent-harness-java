package com.example.core.memory.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.example.core.entity.AgentSessionSummary;
import com.example.core.memory.MemoryLayer;
import com.example.core.memory.config.MemoryProperties;
import com.example.core.memory.model.AssembledContext;
import com.example.core.memory.model.MemoryFragment;
import com.example.core.memory.model.MemoryMessage;
import com.example.core.memory.model.MemoryPriority;
import com.example.core.memory.model.MemoryType;
import com.example.core.memory.service.SummaryCompressService;
import com.example.core.memory.store.impl.LongTermMemoryStore;
import com.example.core.memory.store.impl.ShortTermMemoryStore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoryLayerImpl implements MemoryLayer {

    private final ShortTermMemoryStore shortTermStore;
    private final LongTermMemoryStore longTermStore;
    private final SummaryCompressService summaryCompressService;
    private final MemoryProperties properties;
    private com.example.core.service.SseEmitterService sseEmitterService;

    private final ConcurrentHashMap<String, CompletableFuture<Void>> incrementalSummaryFutures = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public MemoryLayerImpl(ShortTermMemoryStore shortTermStore,
            LongTermMemoryStore longTermStore,
            SummaryCompressService summaryCompressService,
            MemoryProperties properties) {
        this.shortTermStore = shortTermStore;
        this.longTermStore = longTermStore;
        this.summaryCompressService = summaryCompressService;
        this.properties = properties;
    }

    public void setSseEmitterService(com.example.core.service.SseEmitterService sseEmitterService) {
        this.sseEmitterService = sseEmitterService;
    }

    @Override
    public void storeMemory(MemoryMessage message) {
        if (message == null) {
            log.warn("收到null消息，忽略存储");
            return;
        }
        shortTermStore.store(message);
        log.debug("记忆存储完成: type={}, sessionId={}", message.getType(), message.getSessionId());
    }

    @Override
    public void storeMemoryBatch(List<MemoryMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("批量存储消息列表为空，忽略");
            return;
        }

        List<MemoryMessage> shortTermMessages = new ArrayList<>();
        for (MemoryMessage message : messages) {
            if (message != null) {
                shortTermMessages.add(message);
            }
        }

        if (!shortTermMessages.isEmpty()) {
            shortTermStore.storeBatch(shortTermMessages);
        }

        log.debug("批量记忆存储完成: 短期={}", shortTermMessages.size());
    }

    @Override
    public AssembledContext buildContext(String sessionId, String userMessage, String systemPrompt) {
        return buildContext(sessionId, userMessage, systemPrompt, null);
    }

    @Override
    public AssembledContext buildContext(String sessionId, String userMessage, String systemPrompt, Long userId) {
        log.debug("开始构建上下文: sessionId={}, userId={}", sessionId, userId);

        int n = properties.getN();
        int effectiveCount = properties.getEffectiveConversationCount();

        List<MemoryFragment> shortTermFragments = shortTermStore.retrieve(sessionId, n);
        for (MemoryFragment f : shortTermFragments) {
            f.setSourceType(MemoryType.SHORT_TERM);
            f.setPriority(MemoryPriority.P2);
        }
        log.debug("短期记忆召回: {} 条", shortTermFragments.size());

        List<MemoryFragment> effectiveFragments = new ArrayList<>();
        String summaryContent = null;

        if (properties.getLongTerm().isEnabled()) {
            if (effectiveCount > 0) {
                effectiveFragments = longTermStore.retrieveEffectiveConversations(
                        sessionId, n, effectiveCount);
                log.debug("有效对话段召回: {} 条", effectiveFragments.size());
            }

            AgentSessionSummary summary = longTermStore.retrieveSummary(sessionId);
            if (summary != null) {
                if (longTermStore.needsFullCompression(sessionId)) {
                    log.info("历史摘要Token超界限，执行全量压缩: sessionId={}, tokenCount={}",
                            sessionId, summary.getTokenCount());
                    summaryContent = executeFullCompression(sessionId, userId, summary);
                } else {
                    summaryContent = summary.getSummaryContent();
                }
                log.debug("历史摘要召回: tokenCount={}", summary.getTokenCount());
            }
        }

        StringBuilder userPrompt = new StringBuilder();

        if (summaryContent != null && !summaryContent.isEmpty()) {
            userPrompt.append("[历史摘要]\n").append(summaryContent).append("\n\n");
        }

        if (!effectiveFragments.isEmpty()) {
            userPrompt.append("[历史对话]\n");
            for (MemoryFragment f : effectiveFragments) {
                userPrompt.append(formatFragmentWithTimestamp(f)).append("\n");
            }
            userPrompt.append("\n");
        }

        if (!shortTermFragments.isEmpty()) {
            userPrompt.append("[近期对话]\n");
            for (MemoryFragment f : shortTermFragments) {
                userPrompt.append(formatFragmentWithTimestamp(f)).append("\n");
            }
        }

        if (userMessage != null && !userMessage.isEmpty()) {
            userPrompt.append("\n[当前用户消息]\n").append(userMessage);
        }

        int estimatedTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt.toString());

        AssembledContext context = AssembledContext.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userPrompt.toString())
                .estimatedTokens(estimatedTokens)
                .build();

        log.debug("上下文构建完成: 估计Token={}", estimatedTokens);
        return context;
    }

    @Override
    public void clearSession(String sessionId) {
        if (sessionId == null) {
            log.warn("收到null会话ID，忽略清除");
            return;
        }
        shortTermStore.delete(sessionId);
        try {
            longTermStore.deleteSummary(sessionId);
        } catch (Exception e) {
            log.warn("长期记忆摘要清理失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
        }

        CompletableFuture<Void> future = incrementalSummaryFutures.remove(sessionId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        log.info("会话记忆已清除: sessionId={}", sessionId);
    }

    @Override
    public void triggerIncrementalSummary(String sessionId, Long userId) {
        if (!properties.getLongTerm().isEnabled()) {
            log.debug("长期记忆未启用，跳过增量压缩: sessionId={}", sessionId);
            return;
        }

        if (incrementalSummaryFutures.containsKey(sessionId)) {
            log.debug("增量压缩已在进行中，跳过: sessionId={}", sessionId);
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                executeIncrementalCompression(sessionId, userId);
            } catch (Exception e) {
                log.error("增量压缩执行异常: sessionId={}, error={}", sessionId, e.getMessage(), e);
            } finally {
                incrementalSummaryFutures.remove(sessionId);
            }
        });

        incrementalSummaryFutures.put(sessionId, future);
        log.info("增量压缩已触发: sessionId={}", sessionId);
    }

    @Override
    public void awaitIncrementalSummary(String sessionId) {
        CompletableFuture<Void> future = incrementalSummaryFutures.get(sessionId);
        if (future == null) {
            return;
        }

        try {
            log.info("等待增量压缩完成: sessionId={}", sessionId);
            future.get(properties.getCompress().getAwaitTimeoutSeconds(), TimeUnit.SECONDS);
            log.info("增量压缩等待完成: sessionId={}", sessionId);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("增量压缩等待超时({}秒)，降级继续: sessionId={}",
                    properties.getCompress().getAwaitTimeoutSeconds(), sessionId);
        } catch (Exception e) {
            log.warn("增量压缩等待异常，降级继续: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    @Override
    @Deprecated
    public void consolidateMemory(String sessionId) {
        consolidateMemory(sessionId, null);
    }

    @Override
    @Deprecated
    public void consolidateMemory(String sessionId, Long userId) {
        log.info("记忆固化已废弃，请使用triggerIncrementalSummary: sessionId={}", sessionId);
    }

    private void executeIncrementalCompression(String sessionId, Long userId) {
        int effectiveCount = properties.getEffectiveConversationCount();

        int totalEffective = longTermStore.countEffectiveConversations(sessionId);
        log.info("增量压缩检查: sessionId={}, 有效对话数={}, 有效对话段上限={}",
                sessionId, totalEffective, effectiveCount);

        if (totalEffective <= effectiveCount) {
            log.info("有效对话数({})未超过有效对话段上限({})，无需增量压缩: sessionId={}",
                    totalEffective, effectiveCount, sessionId);
            return;
        }

        Long lastMessageId = longTermStore.getSummaryLastMessageId(sessionId);
        Long earliestEffectiveId = longTermStore.getEarliestEffectiveConversationId(
                sessionId, effectiveCount);

        if (earliestEffectiveId == null) {
            log.debug("无法确定有效对话段最早ID，跳过增量压缩: sessionId={}", sessionId);
            return;
        }

        List<MemoryFragment> pendingRecords = longTermStore.retrievePendingCompression(
                sessionId, lastMessageId, earliestEffectiveId);

        if (pendingRecords.isEmpty()) {
            log.debug("无待压缩记录，跳过增量压缩: sessionId={}", sessionId);
            return;
        }

        log.info("开始增量压缩: sessionId={}, 待压缩记录={}", sessionId, pendingRecords.size());

        String summaryFragment = summaryCompressService.incrementalCompress(pendingRecords);

        AgentSessionSummary existingSummary = longTermStore.retrieveSummary(sessionId);
        String newSummaryContent;
        if (existingSummary != null && existingSummary.getSummaryContent() != null
                && !existingSummary.getSummaryContent().isEmpty()) {
            newSummaryContent = existingSummary.getSummaryContent() + "\n" + summaryFragment;
        } else {
            newSummaryContent = summaryFragment;
        }

        Long pendingLastId = null;
        for (MemoryFragment f : pendingRecords) {
            try {
                Long fragId = Long.valueOf(f.getId());
                if (pendingLastId == null || fragId > pendingLastId) {
                    pendingLastId = fragId;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        int newTokenCount = summaryCompressService.estimateTokens(newSummaryContent);

        longTermStore.saveSummary(sessionId, userId, newSummaryContent,
                pendingLastId != null ? pendingLastId : earliestEffectiveId,
                AgentSessionSummary.TYPE_INCREMENTAL, newTokenCount);

        log.info("增量压缩完成: sessionId={}, 新增片段长度={}, 总Token={}",
                sessionId, summaryFragment.length(), newTokenCount);
    }

    private String executeFullCompression(String sessionId, Long userId, AgentSessionSummary summary) {
        if (sseEmitterService != null) {
            sseEmitterService.emitCompressing(sessionId);
        }

        String currentSummary = summary.getSummaryContent();
        String compressed = summaryCompressService.fullCompress(currentSummary);

        int newTokenCount = summaryCompressService.estimateTokens(compressed);
        longTermStore.saveSummary(sessionId, userId, compressed,
                summary.getLastMessageId(),
                AgentSessionSummary.TYPE_FULL, newTokenCount);

        if (sseEmitterService != null) {
            sseEmitterService.emitCompressDone(sessionId);
        }

        log.info("全量压缩完成: sessionId={}, 原Token={}, 新Token={}",
                sessionId, summary.getTokenCount(), newTokenCount);

        return compressed;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 2;
    }

    private String formatFragmentWithTimestamp(MemoryFragment fragment) {
        String content = fragment.getContent();
        if (content == null || content.isEmpty()) {
            return content;
        }

        LocalDateTime timestamp = fragment.getTimestamp();
        if (timestamp == null) {
            return content;
        }

        String timeStr = timestamp.format(TIMESTAMP_FORMATTER);
        return "[" + timeStr + "] " + content;
    }
}
