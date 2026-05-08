package com.example.core.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;

import com.example.core.mcp.McpTool;
import com.example.core.mcp.McpToolRegistry;
import com.example.core.react.ToolNameMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpToolAutoRegistration {

    private final McpToolRegistry toolRegistry;
    private final ToolNameMapper toolNameMapper;
    private final List<McpTool> tools;

    @PostConstruct
    public void registerAllTools() {
        toolRegistry.registerAll(tools);
        toolNameMapper.rebuild();
        log.info("MCP工具自动注册完成，共注册{}个工具", tools.size());
    }
}
