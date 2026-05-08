package com.example.core.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;

import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolRegistry;
import com.example.core.react.ToolNameMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SystemToolAutoRegistration {

    private final SystemToolRegistry systemToolRegistry;
    private final ToolNameMapper toolNameMapper;
    private final List<SystemTool> systemTools;

    @PostConstruct
    public void registerAllTools() {
        systemToolRegistry.registerAll(systemTools);
        toolNameMapper.rebuild();
        log.info("系统工具自动注册完成，共注册{}个工具", systemTools.size());
    }
}
