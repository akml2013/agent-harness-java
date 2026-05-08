package com.example.core.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpToolParamParser {

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseParams(String jsonParams) {
        if (jsonParams == null || jsonParams.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JSON.parseObject(jsonParams, Map.class);
        } catch (Exception e) {
            log.warn("解析MCP工具参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public static String getString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    public static String getString(Map<String, Object> params, String key, String defaultValue) {
        String value = getString(params, key);
        return value != null ? value : defaultValue;
    }

    public static Integer getInteger(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Integer getInteger(Map<String, Object> params, String key, int defaultValue) {
        Integer value = getInteger(params, key);
        return value != null ? value : defaultValue;
    }

    public static Long getLong(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean getBool(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        if (value instanceof String) {
            try {
                return JSON.parseArray((String) value, Object.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public static Double getDouble(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
