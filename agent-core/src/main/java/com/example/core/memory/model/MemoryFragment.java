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
public class MemoryFragment {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String content;

    @Builder.Default
    private MemoryType sourceType = MemoryType.SHORT_TERM;

    @Builder.Default
    private MemoryPriority priority = MemoryPriority.P2;

    @Builder.Default
    private double relevanceScore = 1.0;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public int estimateTokens() {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.length() / 2;
    }

    public static MemoryFragment fromMessage(MemoryMessage message) {
        return MemoryFragment.builder()
                .id(message.getId())
                .content(message.getRole() + ": " + message.getContent())
                .sourceType(message.getType())
                .priority(message.getPriority())
                .relevanceScore(1.0)
                .timestamp(message.getTimestamp())
                .metadata(message.getMetadata())
                .build();
    }
}
