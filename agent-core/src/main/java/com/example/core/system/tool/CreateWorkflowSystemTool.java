package com.example.core.system.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.example.core.entity.AgentWorkflow;
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
public class CreateWorkflowSystemTool implements SystemTool {

    private final WorkflowService workflowService;
    private final SseEmitterService sseEmitterService;

    @Override
    public String getName() {
        return "create_workflow";
    }

    @Override
    public String getDescription() {
        return "创建工作流。只需按执行顺序传入TASK节点列表即可，系统会自动添加START和END节点、" +
                "自动生成所有节点和边的ID、自动按顺序连线。不要传node_id、edge_id等ID字段，" +
                "不要传START和END节点，不要传edges参数。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", Map.of("type", "string", "description", "工作流名称(可选，不填则自动生成)"));
        schema.put("description", Map.of("type", "string", "description", "工作流描述(必填)"));
        schema.put("nodes", Map.of("type", "string", "description",
                "任务节点列表(必填，JSON数组，按执行顺序排列)。每个节点只需包含: node_name(必填，节点名称), tool_name(必填，MCP工具名), tool_params(可选，工具参数JSON字符串)。不要传node_id、node_type、position_x、position_y、sort_order等字段。示例: [{\"node_name\":\"搜索数据\",\"tool_name\":\"query_knowledge_base\",\"tool_params\":\"{\\\"query\\\":\\\"关键词\\\"}\"}]"));
        schema.put("session_id", Map.of("type", "string", "description", "会话ID（系统自动注入）"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String name = SystemToolParamParser.getString(params, "name");
        String description = SystemToolParamParser.getString(params, "description");
        Long userId = SystemToolParamParser.getLong(params, "user_id");

        if (userId == null) {
            return SystemToolResult.error("缺少必填参数: user_id");
        }
        if (description == null || description.isBlank()) {
            return SystemToolResult.error("缺少必填参数: description");
        }

        List<Map<String, Object>> taskNodeMaps = parseNodeMaps(params.get("nodes"));

        if (taskNodeMaps != null) {
            taskNodeMaps.removeIf(nodeMap -> {
                String nodeType = getString(nodeMap, "node_type");
                return "START".equalsIgnoreCase(nodeType) || "END".equalsIgnoreCase(nodeType);
            });
        }

        if (taskNodeMaps == null || taskNodeMaps.isEmpty()) {
            return SystemToolResult.error("缺少必填参数: nodes，至少需要一个TASK节点。" +
                    "nodes是JSON数组，每个元素包含node_name和tool_name。" +
                    "示例: [{\"node_name\":\"搜索数据\",\"tool_name\":\"query_knowledge_base\"}]");
        }

        for (int i = 0; i < taskNodeMaps.size(); i++) {
            String toolName = getString(taskNodeMaps.get(i), "tool_name");
            if (toolName == null || toolName.isBlank()) {
                String nodeName = getString(taskNodeMaps.get(i), "node_name");
                return SystemToolResult.error("第" + (i + 1) + "个任务节点\"" +
                        (nodeName != null ? nodeName : "未命名") + "\"缺少tool_name");
            }
        }

        try {
            AgentWorkflow workflow = workflowService.createWorkflowFromTaskNodes(
                    userId, name, description, taskNodeMaps, "AI");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("workflow_id", workflow.getWorkflowId());
            data.put("name", workflow.getName());
            data.put("node_count", workflow.getNodeCount());

            String sessionId = SystemToolParamParser.getString(params, "session_id");
            sseEmitterService.emitWorkflowCreated(sessionId, workflow.getWorkflowId(), workflow.getName());

            return SystemToolResult.success(
                    "工作流\"" + workflow.getName() + "\"创建成功，共" +
                            workflow.getNodeCount() + "个任务节点。workflow_id: " +
                            workflow.getWorkflowId(),
                    data);
        } catch (IllegalArgumentException e) {
            return SystemToolResult.error("工作流校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建工作流失败", e);
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            return SystemToolResult.error("创建工作流失败: " + msg);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseNodeMaps(Object nodesObj) {
        if (nodesObj == null) {
            return null;
        }

        if (nodesObj instanceof List) {
            List<?> rawList = (List<?>) nodesObj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result.isEmpty() ? null : result;
        }

        if (nodesObj instanceof String) {
            String jsonStr = ((String) nodesObj).trim();
            if (jsonStr.isBlank()) {
                return null;
            }
            try {
                List<?> rawList = JSON.parseArray(jsonStr);
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map) {
                        result.add((Map<String, Object>) item);
                    }
                }
                return result.isEmpty() ? null : result;
            } catch (JSONException e) {
                log.warn("解析nodes JSON数组失败，尝试修复: {}", e.getMessage());
                String repaired = repairJson(jsonStr);
                try {
                    List<?> rawList = JSON.parseArray(repaired);
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof Map) {
                            result.add((Map<String, Object>) item);
                        }
                    }
                    return result.isEmpty() ? null : result;
                } catch (JSONException e2) {
                    log.warn("修复JSON后仍然解析失败: {}", e2.getMessage());
                    return null;
                }
            }
        }

        return null;
    }

    private String repairJson(String json) {
        if (json == null) {
            return null;
        }
        String repaired = json;
        while (repaired.contains(",,")) {
            repaired = repaired.replace(",,", ",");
        }
        repaired = repaired.replaceAll(",\\s*}", "}");
        repaired = repaired.replaceAll(",\\s*]", "]");
        return repaired;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
