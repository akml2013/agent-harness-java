package com.example.core.mcp;

import java.util.Map;

public interface McpTool {

    String getName();

    String getDescription();

    Map<String, Object> getParameterSchema();

    McpToolResult execute(Map<String, Object> params);

    default boolean supportsBatch() {
        return false;
    }
}
