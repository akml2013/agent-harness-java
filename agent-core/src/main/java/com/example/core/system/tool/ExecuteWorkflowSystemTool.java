package com.example.core.system.tool;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.service.WorkflowService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteWorkflowSystemTool implements SystemTool {

    private final WorkflowService workflowService;

    @Override
    public String getName() {
        return "execute_workflow";
    }

    @Override
    public String getDescription() {
        return "执行指定的工作流，返回工作流定义(节点列表+执行顺序)，AI按顺序依次执行各节点的MCP工具";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("workflow_id", Map.of("type", "string", "description", "工作流ID(必填)"));
        schema.put("user_input", Map.of("type", "string", "description", "用户输入参数(可选，JSON格式，供变量引用)"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String workflowId = SystemToolParamParser.getString(params, "workflow_id");
        Long userId = SystemToolParamParser.getLong(params, "user_id");

        if (workflowId == null || workflowId.isBlank()) {
            return SystemToolResult.error("缺少必填参数: workflow_id");
        }
        if (userId == null) {
            return SystemToolResult.error("缺少必填参数: user_id");
        }

        try {
            Map<String, Object> workflowData = workflowService.getWorkflowForExecution(userId, workflowId);

            SystemToolResult result = SystemToolResult.success(
                    "工作流\"" + workflowData.get("workflow_name") + "\"已加载，共"
                            + workflowData.get("total_nodes") + "个任务节点，请按sort_order顺序执行",
                    workflowData);
            return result;
        } catch (IllegalArgumentException e) {
            return SystemToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("执行工作流失败: workflowId={}", workflowId, e);
            return SystemToolResult.error("执行工作流失败: " + e.getMessage());
        }
    }
}
