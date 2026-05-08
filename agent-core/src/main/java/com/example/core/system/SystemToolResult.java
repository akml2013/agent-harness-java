package com.example.core.system;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSON;

import lombok.Data;

@Data
public class SystemToolResult {

    private boolean success;
    private String message;
    private Map<String, Object> data;

    private boolean needsUserInput;

    public static SystemToolResult success(String message) {
        SystemToolResult result = new SystemToolResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(new HashMap<>());
        return result;
    }

    public static SystemToolResult success(String message, Map<String, Object> data) {
        SystemToolResult result = new SystemToolResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(data != null ? data : new HashMap<>());
        return result;
    }

    public static SystemToolResult error(String message) {
        SystemToolResult result = new SystemToolResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(new HashMap<>());
        return result;
    }

    public SystemToolResult addData(String key, Object value) {
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
