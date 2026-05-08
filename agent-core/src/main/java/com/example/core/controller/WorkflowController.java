package com.example.core.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.core.entity.AgentWorkflow;
import com.example.core.entity.AgentWorkflowEdge;
import com.example.core.entity.AgentWorkflowExecution;
import com.example.core.entity.AgentWorkflowNode;
import com.example.core.service.ChatMessageService;
import com.example.core.service.WorkflowExecutionService;
import com.example.core.service.WorkflowService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/agent/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;
    private final ChatMessageService chatMessageService;
    private final com.example.core.service.AgentService agentService;

    @PostMapping
    public Map<String, Object> createWorkflow(@RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String name = getString(request, "name");
        String description = getString(request, "description");
        String createSource = getString(request, "create_source");

        List<AgentWorkflowNode> nodes = parseNodes(request.get("nodes"));
        List<AgentWorkflowEdge> edges = parseEdges(request.get("edges"));

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }

        try {
            AgentWorkflow workflow = workflowService.createWorkflow(
                    userId, name, description, nodes, edges, createSource);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("workflow_id", workflow.getWorkflowId());
            result.put("name", workflow.getName());
            result.put("status", workflow.getStatus());
            result.put("node_count", workflow.getNodeCount());

            return result;
        } catch (IllegalArgumentException e) {
            return errorResponse(e.getMessage());
        } catch (Exception e) {
            log.error("创建工作流异常", e);
            return errorResponse("创建工作流失败: " + e.getMessage());
        }
    }

    @PutMapping("/{workflowId}")
    public Map<String, Object> updateWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String name = getString(request, "name");
        String description = getString(request, "description");

        List<AgentWorkflowNode> nodes = parseNodes(request.get("nodes"));
        List<AgentWorkflowEdge> edges = parseEdges(request.get("edges"));

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }

        try {
            AgentWorkflow workflow = workflowService.updateWorkflow(
                    userId, workflowId, name, description, nodes, edges);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("workflow_id", workflow.getWorkflowId());
            result.put("name", workflow.getName());
            result.put("version", workflow.getVersion());
            return result;
        } catch (IllegalArgumentException e) {
            return errorResponse(e.getMessage());
        }
    }

    @DeleteMapping("/{workflowId}")
    public Map<String, Object> deleteWorkflow(
            @PathVariable String workflowId,
            @RequestParam Long user_id,
            @RequestParam(value = "agent_id", required = false) String agentId) {
        com.example.core.entity.AgentWorkflow workflow = workflowService.getWorkflow(user_id, workflowId);
        String workflowName = workflow != null ? workflow.getName() : workflowId;

        boolean deleted = workflowService.deleteWorkflow(user_id, workflowId);

        if (deleted && agentId != null && !agentId.isBlank()) {
            try {
                com.example.core.entity.Agent agent = agentService.getByAgentId(agentId);
                if (agent != null && agent.getSessionId() != null) {
                    String eventContent = "用户删除了工作流: " + workflowName;
                    chatMessageService.saveUserEvent(agent.getSessionId(), user_id, eventContent,
                            Map.of("event_type", "WORKFLOW_DELETED", "workflow_name", workflowName));
                }
            } catch (Exception e) {
                log.warn("记录工作流删除事件失败: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        result.put("message", deleted ? "工作流已删除" : "工作流不存在");
        return result;
    }

    @GetMapping
    public Map<String, Object> listWorkflows(@RequestParam Long user_id) {
        List<AgentWorkflow> workflows = workflowService.listWorkflows(user_id);
        List<Map<String, Object>> workflowList = new ArrayList<>();
        for (AgentWorkflow wf : workflows) {
            workflowList.add(toWorkflowMap(wf));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("workflows", workflowList);
        result.put("total", workflowList.size());
        return result;
    }

    @GetMapping("/{workflowId}")
    public Map<String, Object> getWorkflow(
            @PathVariable String workflowId,
            @RequestParam Long user_id) {
        AgentWorkflow workflow = workflowService.getWorkflow(user_id, workflowId);
        if (workflow == null) {
            return errorResponse("工作流不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("workflow", toWorkflowMap(workflow));

        List<AgentWorkflowNode> nodes = workflowService.getWorkflowNodes(workflowId);
        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (AgentWorkflowNode node : nodes) {
            nodeList.add(toNodeMap(node));
        }
        result.put("workflow_nodes", nodeList);

        List<AgentWorkflowEdge> edges = workflowService.getWorkflowEdges(workflowId);
        List<Map<String, Object>> edgeList = new ArrayList<>();
        for (AgentWorkflowEdge edge : edges) {
            edgeList.add(toEdgeMap(edge));
        }
        result.put("workflow_edges", edgeList);

        return result;
    }

    @PutMapping("/{workflowId}/name")
    public Map<String, Object> renameWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String newName = getString(request, "name");

        if (userId == null || newName == null || newName.isBlank()) {
            return errorResponse("缺少必填参数: user_id, name");
        }

        try {
            AgentWorkflow workflow = workflowService.renameWorkflow(userId, workflowId, newName);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("workflow_id", workflow.getWorkflowId());
            result.put("name", workflow.getName());
            return result;
        } catch (IllegalArgumentException e) {
            return errorResponse(e.getMessage());
        }
    }

    @PostMapping("/validate")
    public Map<String, Object> validateWorkflow(@RequestBody Map<String, Object> request) {
        List<AgentWorkflowNode> nodes = parseNodes(request.get("nodes"));
        List<AgentWorkflowEdge> edges = parseEdges(request.get("edges"));

        WorkflowService.ValidationResult validation = workflowService.validateWorkflow(nodes, edges);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("valid", validation.valid());
        result.put("message", validation.message());
        return result;
    }

    @GetMapping("/executions/active")
    public Map<String, Object> getActiveExecutions(
            @RequestParam String session_id,
            @RequestParam Long user_id) {
        List<AgentWorkflowExecution> executions = workflowExecutionService.getActiveExecutionsBySession(session_id);
        List<Map<String, Object>> executionList = new ArrayList<>();
        for (AgentWorkflowExecution exec : executions) {
            if (exec.getUserId().equals(user_id)) {
                executionList.add(toExecutionMap(exec));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("executions", executionList);
        return result;
    }

    @GetMapping("/executions/recent")
    public Map<String, Object> getRecentExecutions(
            @RequestParam String session_id,
            @RequestParam Long user_id,
            @RequestParam(defaultValue = "5") int limit) {
        List<AgentWorkflowExecution> executions = workflowExecutionService.getRecentExecutionsBySession(session_id,
                limit);
        List<Map<String, Object>> executionList = new ArrayList<>();
        for (AgentWorkflowExecution exec : executions) {
            if (exec.getUserId().equals(user_id)) {
                Map<String, Object> execMap = toExecutionMap(exec);
                AgentWorkflow workflow = workflowService.getWorkflow(user_id, exec.getWorkflowId());
                if (workflow != null) {
                    execMap.put("workflow_name", workflow.getName());
                } else {
                    execMap.put("workflow_name", "未知工作流");
                }
                executionList.add(execMap);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("executions", executionList);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<AgentWorkflowNode> parseNodes(Object nodesObj) {
        if (nodesObj == null) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> nodeList;
        if (nodesObj instanceof List) {
            nodeList = (List<Map<String, Object>>) nodesObj;
        } else {
            return new ArrayList<>();
        }

        List<AgentWorkflowNode> nodes = new ArrayList<>();
        for (Map<String, Object> nodeMap : nodeList) {
            AgentWorkflowNode node = new AgentWorkflowNode();
            node.setNodeId(getString(nodeMap, "node_id"));
            node.setNodeType(getString(nodeMap, "node_type"));
            node.setNodeName(getString(nodeMap, "node_name"));
            node.setPositionX(getInteger(nodeMap, "position_x"));
            node.setPositionY(getInteger(nodeMap, "position_y"));
            node.setToolName(getString(nodeMap, "tool_name"));
            node.setToolParams(getString(nodeMap, "tool_params"));
            node.setDescription(getString(nodeMap, "description"));
            node.setSortOrder(getInteger(nodeMap, "sort_order"));
            nodes.add(node);
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private List<AgentWorkflowEdge> parseEdges(Object edgesObj) {
        if (edgesObj == null) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> edgeList;
        if (edgesObj instanceof List) {
            edgeList = (List<Map<String, Object>>) edgesObj;
        } else {
            return new ArrayList<>();
        }

        List<AgentWorkflowEdge> edges = new ArrayList<>();
        for (Map<String, Object> edgeMap : edgeList) {
            AgentWorkflowEdge edge = new AgentWorkflowEdge();
            edge.setEdgeId(getString(edgeMap, "edge_id"));
            edge.setSourceNodeId(getString(edgeMap, "source_node_id"));
            edge.setTargetNodeId(getString(edgeMap, "target_node_id"));
            edge.setEdgeCondition(getString(edgeMap, "edge_condition"));
            edges.add(edge);
        }
        return edges;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
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

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
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

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }

    private Map<String, Object> toNodeMap(AgentWorkflowNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("node_id", node.getNodeId());
        map.put("node_type", node.getNodeType());
        map.put("node_name", node.getNodeName());
        map.put("position_x", node.getPositionX());
        map.put("position_y", node.getPositionY());
        map.put("tool_name", node.getToolName());
        map.put("tool_params", node.getToolParams());
        map.put("description", node.getDescription());
        map.put("sort_order", node.getSortOrder());
        return map;
    }

    private Map<String, Object> toEdgeMap(AgentWorkflowEdge edge) {
        Map<String, Object> map = new HashMap<>();
        map.put("edge_id", edge.getEdgeId());
        map.put("source_node_id", edge.getSourceNodeId());
        map.put("target_node_id", edge.getTargetNodeId());
        map.put("edge_condition", edge.getEdgeCondition());
        return map;
    }

    private Map<String, Object> toWorkflowMap(AgentWorkflow wf) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", wf.getId());
        map.put("workflow_id", wf.getWorkflowId());
        map.put("user_id", wf.getUserId());
        map.put("name", wf.getName());
        map.put("description", wf.getDescription());
        map.put("status", wf.getStatus());
        map.put("node_count", wf.getNodeCount());
        map.put("version", wf.getVersion());
        map.put("create_source", wf.getCreateSource());
        map.put("create_time", wf.getCreateTime());
        map.put("update_time", wf.getUpdateTime());
        return map;
    }

    private Map<String, Object> toExecutionMap(AgentWorkflowExecution exec) {
        Map<String, Object> map = new HashMap<>();
        map.put("execution_id", exec.getExecutionId());
        map.put("workflow_id", exec.getWorkflowId());
        map.put("user_id", exec.getUserId());
        map.put("session_id", exec.getSessionId());
        map.put("status", exec.getStatus());
        map.put("current_node_id", exec.getCurrentNodeId());
        map.put("completed_nodes", exec.getCompletedNodes());
        map.put("total_nodes", exec.getTotalNodes());
        map.put("start_time", exec.getStartTime());
        map.put("end_time", exec.getEndTime());
        return map;
    }
}
