package com.example.core.memory;

import java.util.List;

import com.example.core.memory.model.AssembledContext;
import com.example.core.memory.model.MemoryMessage;

public interface MemoryLayer {

    void storeMemory(MemoryMessage message);

    void storeMemoryBatch(List<MemoryMessage> messages);

    AssembledContext buildContext(String sessionId, String userMessage, String systemPrompt);

    default AssembledContext buildContext(String sessionId, String userMessage, String systemPrompt, Long userId) {
        return buildContext(sessionId, userMessage, systemPrompt);
    }

    void clearSession(String sessionId);

    void triggerIncrementalSummary(String sessionId, Long userId);

    void awaitIncrementalSummary(String sessionId);

    @Deprecated
    void consolidateMemory(String sessionId);

    @Deprecated
    void consolidateMemory(String sessionId, Long userId);
}
