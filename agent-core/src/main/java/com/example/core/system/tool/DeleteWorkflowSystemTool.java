package com.example.core.system.tool;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.service.SseEmitterService;
import com.example.core.service.WorkflowService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteWorkflowSystemTool implements SystemTool {

    private final WorkflowService workflowService;
    private final SseEmitterService sseEmitterService;

    @Override
    public String getName() {
        return "delete_workflow";
    }

    @Override
    public String getDescription() {
        return "删除指定的工作流。删除后工作流将标记为已删除状态。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("workflow_id", Map.of("type", "string", "description", "要删除的工作流ID(必填)"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        schema.put("session_id", Map.of("type", "string", "description", "会话ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String workflowId = SystemToolParamParser.getString(params, "workflow_id");
        Long userId = SystemToolParamParser.getLong(params, "user_id");
        String sessionId = SystemToolParamParser.getString(params, "session_id");

        if (workflowId == null || workflowId.isBlank()) {
            return SystemToolResult.error("缺少必填参数: workflow_id");
        }
        if (userId == null) {
            return SystemToolResult.error("缺少必填参数: user_id");
        }

        try {
            boolean deleted = workflowService.deleteWorkflow(userId, workflowId);
            if (deleted) {
                sseEmitterService.emitWorkflowDeleted(sessionId, workflowId);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("workflow_id", workflowId);
                return SystemToolResult.success("工作流已删除", data);
            } else {
                return SystemToolResult.error("工作流不存在或已删除: " + workflowId);
            }
        } catch (Exception e) {
            log.error("删除工作流失败: workflowId={}", workflowId, e);
            return SystemToolResult.error("删除工作流失败: " + e.getMessage());
        }
    }
}
