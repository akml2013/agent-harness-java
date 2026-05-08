package com.example.core.mcp;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSON;

import lombok.Data;

@Data
public class McpToolResult {

    private boolean success;
    private String message;
    private Map<String, Object> data;
    private boolean needsUserInput;

    public static McpToolResult success(String message) {
        McpToolResult result = new McpToolResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(new HashMap<>());
        return result;
    }

    public static McpToolResult success(String message, Map<String, Object> data) {
        McpToolResult result = new McpToolResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(data != null ? data : new HashMap<>());
        return result;
    }

    public static McpToolResult error(String message) {
        McpToolResult result = new McpToolResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(new HashMap<>());
        return result;
    }

    public McpToolResult addData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }
}
