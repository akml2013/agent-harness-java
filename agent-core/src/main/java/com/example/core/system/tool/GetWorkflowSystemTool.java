package com.example.core.system.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.entity.AgentWorkflow;
import com.example.core.entity.AgentWorkflowEdge;
import com.example.core.entity.AgentWorkflowNode;
import com.example.core.service.WorkflowService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetWorkflowSystemTool implements SystemTool {

    private final WorkflowService workflowService;

    @Override
    public String getName() {
        return "get_workflow";
    }

    @Override
    public String getDescription() {
        return "查看指定工作流的详细信息，包括节点列表和连线关系。不会执行工作流，仅查看。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("workflow_id", Map.of("type", "string", "description", "工作流ID(必填)"));
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
            AgentWorkflow workflow = workflowService.getWorkflow(userId, workflowId);
            if (workflow == null) {
                return SystemToolResult.error("工作流不存在: " + workflowId);
            }

            List<AgentWorkflowNode> nodes = workflowService.getWorkflowNodes(workflowId);
            List<AgentWorkflowEdge> edges = workflowService.getWorkflowEdges(workflowId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("workflow_id", workflow.getWorkflowId());
            data.put("name", workflow.getName());
            data.put("description", workflow.getDescription());
            data.put("status", workflow.getStatus());
            data.put("node_count", workflow.getNodeCount());
            data.put("create_source", workflow.getCreateSource());

            List<Map<String, Object>> nodeList = new ArrayList<>();
            for (AgentWorkflowNode node : nodes) {
                Map<String, Object> nodeMap = new LinkedHashMap<>();
                nodeMap.put("node_id", node.getNodeId());
                nodeMap.put("node_type", node.getNodeType());
                nodeMap.put("node_name", node.getNodeName());
                if (node.getToolName() != null) {
                    nodeMap.put("tool_name", node.getToolName());
                }
                if (node.getToolParams() != null) {
                    nodeMap.put("tool_params", node.getToolParams());
                }
                nodeMap.put("sort_order", node.getSortOrder());
                nodeList.add(nodeMap);
            }
            data.put("nodes", nodeList);

            List<Map<String, Object>> edgeList = new ArrayList<>();
            for (AgentWorkflowEdge edge : edges) {
                Map<String, Object> edgeMap = new LinkedHashMap<>();
                edgeMap.put("source_node_id", edge.getSourceNodeId());
                edgeMap.put("target_node_id", edge.getTargetNodeId());
                edgeList.add(edgeMap);
            }
            data.put("edges", edgeList);

            return SystemToolResult.success("工作流详情", data);
        } catch (Exception e) {
            log.error("查看工作流详情失败: workflowId={}", workflowId, e);
            return SystemToolResult.error("查看工作流详情失败: " + e.getMessage());
        }
    }
}
