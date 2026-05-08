package com.example.core.memory.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryMessage {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String sessionId;

    private Long userId;

    private String role;

    private String content;

    @Builder.Default
    private MemoryType type = MemoryType.SHORT_TERM;

    @Builder.Default
    private MemoryPriority priority = MemoryPriority.P2;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static MemoryMessage userMessage(String sessionId, String content) {
        return MemoryMessage.builder()
                .sessionId(sessionId)
                .role("user")
                .content(content)
                .type(MemoryType.SHORT_TERM)
                .priority(MemoryPriority.P1)
                .build();
    }

    public static MemoryMessage assistantMessage(String sessionId, String content) {
        return MemoryMessage.builder()
                .sessionId(sessionId)
                .role("assistant")
                .content(content)
                .type(MemoryType.SHORT_TERM)
                .priority(MemoryPriority.P2)
                .build();
    }

    public static MemoryMessage systemMessage(String sessionId, String content) {
        return MemoryMessage.builder()
                .sessionId(sessionId)
                .role("system")
                .content(content)
                .type(MemoryType.SHORT_TERM)
                .priority(MemoryPriority.P0)
                .build();
    }

    public static MemoryMessage longTermMessage(String sessionId, String content, Long userId) {
        return MemoryMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role("memory")
                .content(content)
                .type(MemoryType.LONG_TERM)
                .priority(MemoryPriority.P3)
                .build();
    }
}
