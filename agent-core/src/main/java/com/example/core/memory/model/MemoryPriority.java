package com.example.core.memory.model;

public enum MemoryPriority {
    P0(0, "系统提示词"),
    P1(1, "当前用户消息"),
    P2(2, "短期记忆"),
    P3(3, "长期记忆-有效对话"),
    P4(4, "长期记忆-历史摘要");

    private final int level;
    private final String description;

    MemoryPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }
}
