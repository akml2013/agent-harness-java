package com.example.core.memory.service;

import java.util.List;

import com.example.core.memory.config.MemoryProperties;
import com.example.core.memory.model.MemoryFragment;
import com.example.core.service.DeepseekService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SummaryCompressService {

    private static final String INCREMENTAL_SYSTEM_PROMPT =
            "你是一个对话摘要助手。请将以下对话记录压缩为简洁的摘要，保留关键信息和上下文。"
            + "不要遗漏重要的用户需求、决策和结论。";

    private static final String FULL_SYSTEM_PROMPT =
            "你是一个对话摘要助手。请将以下历史摘要重新压缩为一份完整、连贯的摘要。"
            + "要求：1. 保留所有关键信息；2. 摘要中不要出现ID；3. 确保语义连贯，去除重复内容；"
            + "4. 按时间顺序组织信息。";

    private final DeepseekService deepseekService;
    private final MemoryProperties properties;

    public SummaryCompressService(DeepseekService deepseekService, MemoryProperties properties) {
        this.deepseekService = deepseekService;
        this.properties = properties;
    }

    public String incrementalCompress(List<MemoryFragment> pendingRecords) {
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return "";
        }

        StringBuilder input = new StringBuilder();
        for (MemoryFragment fragment : pendingRecords) {
            input.append(fragment.getContent()).append("\n");
        }

        String userInput = input.toString();

        try {
            String result = deepseekService.chatWithSystem(INCREMENTAL_SYSTEM_PROMPT, userInput);
            if (result != null && !result.isBlank() && !result.startsWith("API调用")) {
                log.debug("增量压缩成功: 输入记录={}, 输出长度={}", pendingRecords.size(), result.length());
                return result;
            }
            log.warn("增量压缩LLM返回异常: {}", result);
            return fallbackCompress(pendingRecords);
        } catch (Exception e) {
            log.warn("增量压缩LLM调用失败，降级为系统级压缩: {}", e.getMessage());
            return fallbackCompress(pendingRecords);
        }
    }

    public String fullCompress(String currentSummary) {
        if (currentSummary == null || currentSummary.isBlank()) {
            return "";
        }

        try {
            String result = deepseekService.chatWithSystem(FULL_SYSTEM_PROMPT, currentSummary);
            if (result != null && !result.isBlank() && !result.startsWith("API调用")) {
                log.debug("全量压缩成功: 输入长度={}, 输出长度={}", currentSummary.length(), result.length());
                return result;
            }
            log.error("全量压缩LLM返回异常: {}", result);
            return currentSummary;
        } catch (Exception e) {
            log.error("全量压缩LLM调用失败(无降级): {}", e.getMessage());
            return currentSummary;
        }
    }

    public String fallbackCompress(List<MemoryFragment> pendingRecords) {
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return "";
        }

        int truncateLength = properties.getCompress().getFallbackTruncateLength();

        StringBuilder sb = new StringBuilder();
        for (MemoryFragment fragment : pendingRecords) {
            String content = fragment.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }
            if (content.length() > truncateLength) {
                content = content.substring(0, truncateLength) + "...";
            }
            sb.append("【系统压缩】").append(content).append("\n");
        }

        log.debug("系统级降级压缩: 输入记录={}, 输出长度={}", pendingRecords.size(), sb.length());
        return sb.toString();
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 2;
    }
}
