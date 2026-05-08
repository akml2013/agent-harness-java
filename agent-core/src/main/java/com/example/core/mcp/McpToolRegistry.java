package com.example.core.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new HashMap<>();

    public void register(McpTool tool) {
        tools.put(tool.getName(), tool);
        log.info("MCP工具注册: {} - {}", tool.getName(), tool.getDescription());
    }

    public void registerAll(List<McpTool> toolList) {
        for (McpTool tool : toolList) {
            register(tool);
        }
    }

    public McpTool getTool(String name) {
        return tools.get(name);
    }

    public Map<String, McpTool> getAllTools() {
        return new HashMap<>(tools);
    }

    public String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用工具列表：\n\n");
        for (McpTool tool : tools.values()) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append("描述: ").append(tool.getDescription()).append("\n");
            sb.append("参数: ").append(JSON.toJSONString(tool.getParameterSchema())).append("\n\n");
        }
        return sb.toString();
    }

    public McpToolResult executeTool(String toolName, Map<String, Object> params) {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("MCP工具不存在: {}", toolName);
            return McpToolResult.error("工具不存在: " + toolName);
        }
        log.info("执行MCP工具: {}, 参数: {}", toolName, params);
        try {
            McpToolResult result = tool.execute(params);
            log.info("MCP工具执行完成: {}, 成功: {}", toolName, result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("MCP工具执行异常: {}, 错误: {}", toolName, e.getMessage(), e);
            return McpToolResult.error("工具执行异常: " + e.getMessage());
        }
    }
}
