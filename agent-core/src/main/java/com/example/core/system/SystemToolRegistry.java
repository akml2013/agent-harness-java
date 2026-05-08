package com.example.core.system;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SystemToolRegistry {

    private final Map<String, SystemTool> tools = new HashMap<>();

    public void register(SystemTool tool) {
        tools.put(tool.getName(), tool);
        log.info("系统工具注册: {} - {}", tool.getName(), tool.getDescription());
    }

    public void registerAll(List<SystemTool> toolList) {
        for (SystemTool tool : toolList) {
            register(tool);
        }
    }

    public SystemTool getTool(String name) {
        return tools.get(name);
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public Map<String, SystemTool> getAllTools() {
        return new HashMap<>(tools);
    }

    public String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用系统工具列表：\n\n");
        for (SystemTool tool : tools.values()) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append("描述: ").append(tool.getDescription()).append("\n");
            sb.append("参数: ").append(JSON.toJSONString(tool.getParameterSchema())).append("\n\n");
        }
        return sb.toString();
    }

    public SystemToolResult executeTool(String toolName, Map<String, Object> params) {
        SystemTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("系统工具不存在: {}", toolName);
            return SystemToolResult.error("系统工具不存在: " + toolName);
        }
        log.info("执行系统工具: {}, 参数: {}", toolName, params);
        try {
            SystemToolResult result = tool.execute(params);
            log.info("系统工具执行完成: {}, 成功: {}", toolName, result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("系统工具执行异常: {}, 错误: {}", toolName, e.getMessage(), e);
            return SystemToolResult.error("系统工具执行异常: " + e.getMessage());
        }
    }
}
