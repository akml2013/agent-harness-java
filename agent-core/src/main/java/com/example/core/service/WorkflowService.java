package com.example.core.service;

import java.util.List;
import java.util.Map;

import com.example.core.entity.AgentWorkflow;
import com.example.core.entity.AgentWorkflowEdge;
import com.example.core.entity.AgentWorkflowNode;

public interface WorkflowService {

    AgentWorkflow createWorkflow(Long userId, String name, String description,
            List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges,
            String createSource);

    AgentWorkflow createWorkflowFromTaskNodes(Long userId, String name, String description,
            List<Map<String, Object>> taskNodeMaps, String createSource);

    AgentWorkflow updateWorkflow(Long userId, String workflowId,
            String name, String description,
            List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges);

    boolean deleteWorkflow(Long userId, String workflowId);

    List<AgentWorkflow> listWorkflows(Long userId);

    AgentWorkflow getWorkflow(Long userId, String workflowId);

    List<AgentWorkflowNode> getWorkflowNodes(String workflowId);

    List<AgentWorkflowEdge> getWorkflowEdges(String workflowId);

    AgentWorkflow renameWorkflow(Long userId, String workflowId, String newName);

    String generateWorkflowName(String description);

    ValidationResult validateWorkflow(List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges);

    Map<String, Object> getWorkflowForExecution(Long userId, String workflowId);

    Map<String, String> getWorkflowMap(Long userId);

    record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "校验通过");
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }
}
