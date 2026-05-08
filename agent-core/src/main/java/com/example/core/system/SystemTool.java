package com.example.core.system;

import java.util.Map;

public interface SystemTool {

    String getName();

    String getDescription();

    Map<String, Object> getParameterSchema();

    SystemToolResult execute(Map<String, Object> params);
}
