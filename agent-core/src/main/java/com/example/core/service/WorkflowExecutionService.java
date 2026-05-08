package com.example.core.service;

import java.util.List;

import com.example.core.entity.AgentWorkflowExecution;

public interface WorkflowExecutionService {

    AgentWorkflowExecution startExecution(String workflowId, Long userId, String sessionId, int totalNodes);

    void updateNodeProgress(String executionId, String currentNodeId, int completedNodes);

    void completeExecution(String executionId, String result);

    void failExecution(String executionId, String errorMessage);

    void abortExecution(String executionId);

    List<AgentWorkflowExecution> getActiveExecutionsBySession(String sessionId);

    AgentWorkflowExecution getActiveExecutionByWorkflow(String workflowId, Long userId);

    AgentWorkflowExecution getByExecutionId(String executionId);

    List<AgentWorkflowExecution> getRecentExecutionsBySession(String sessionId, int limit);

    List<AgentWorkflowExecution> getRecentExecutionsByAgentId(String agentId, int limit);

    boolean deleteExecution(String executionId, Long userId);
}
