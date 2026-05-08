package com.example.core.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.core.entity.Agent;
import com.example.core.entity.AgentWorkflowExecution;
import com.example.core.mapper.AgentWorkflowExecutionMapper;
import com.example.core.service.AgentService;
import com.example.core.service.WorkflowExecutionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final AgentWorkflowExecutionMapper executionMapper;
    private final AgentService agentService;

    @Override
    public AgentWorkflowExecution startExecution(String workflowId, Long userId, String sessionId, int totalNodes) {
        AgentWorkflowExecution execution = new AgentWorkflowExecution();
        execution.setExecutionId(UUID.randomUUID().toString().replace("-", ""));
        execution.setWorkflowId(workflowId);
        execution.setUserId(userId);
        execution.setSessionId(sessionId);
        execution.setStatus(AgentWorkflowExecution.STATUS_RUNNING);
        execution.setCurrentNodeId(null);
        execution.setCompletedNodes(0);
        execution.setTotalNodes(totalNodes);
        execution.setStartTime(LocalDateTime.now());
        executionMapper.insert(execution);

        log.info("工作流执行开始: executionId={}, workflowId={}, sessionId={}, totalNodes={}",
                execution.getExecutionId(), workflowId, sessionId, totalNodes);
        return execution;
    }

    @Override
    public void updateNodeProgress(String executionId, String currentNodeId, int completedNodes) {
        AgentWorkflowExecution execution = getByExecutionId(executionId);
        if (execution == null) {
            log.warn("更新节点进度失败，执行记录不存在: executionId={}", executionId);
            return;
        }
        execution.setCurrentNodeId(currentNodeId);
        execution.setCompletedNodes(completedNodes);
        executionMapper.updateById(execution);

        log.debug("工作流节点进度更新: executionId={}, currentNodeId={}, completedNodes={}",
                executionId, currentNodeId, completedNodes);
    }

    @Override
    public void completeExecution(String executionId, String result) {
        AgentWorkflowExecution execution = getByExecutionId(executionId);
        if (execution == null) {
            log.warn("完成执行失败，执行记录不存在: executionId={}", executionId);
            return;
        }
        execution.setStatus(AgentWorkflowExecution.STATUS_COMPLETED);
        execution.setCurrentNodeId(null);
        execution.setCompletedNodes(execution.getTotalNodes());
        execution.setExecutionResult(result);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);

        log.info("工作流执行完成: executionId={}, workflowId={}", executionId, execution.getWorkflowId());
    }

    @Override
    public void failExecution(String executionId, String errorMessage) {
        AgentWorkflowExecution execution = getByExecutionId(executionId);
        if (execution == null) {
            log.warn("标记执行失败失败，执行记录不存在: executionId={}", executionId);
            return;
        }
        execution.setStatus(AgentWorkflowExecution.STATUS_FAILED);
        execution.setErrorMessage(errorMessage);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);

        log.info("工作流执行失败: executionId={}, workflowId={}, error={}",
                executionId, execution.getWorkflowId(), errorMessage);
    }

    @Override
    public void abortExecution(String executionId) {
        AgentWorkflowExecution execution = getByExecutionId(executionId);
        if (execution == null) {
            log.warn("标记执行中止失败，执行记录不存在: executionId={}", executionId);
            return;
        }
        execution.setStatus(AgentWorkflowExecution.STATUS_ABORTED);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);

        log.info("工作流执行中止: executionId={}, workflowId={}", executionId, execution.getWorkflowId());
    }

    @Override
    public List<AgentWorkflowExecution> getActiveExecutionsBySession(String sessionId) {
        return executionMapper.selectList(new LambdaQueryWrapper<AgentWorkflowExecution>()
                .eq(AgentWorkflowExecution::getSessionId, sessionId)
                .eq(AgentWorkflowExecution::getStatus, AgentWorkflowExecution.STATUS_RUNNING)
                .orderByDesc(AgentWorkflowExecution::getStartTime));
    }

    @Override
    public AgentWorkflowExecution getActiveExecutionByWorkflow(String workflowId, Long userId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<AgentWorkflowExecution>()
                .eq(AgentWorkflowExecution::getWorkflowId, workflowId)
                .eq(AgentWorkflowExecution::getUserId, userId)
                .eq(AgentWorkflowExecution::getStatus, AgentWorkflowExecution.STATUS_RUNNING)
                .orderByDesc(AgentWorkflowExecution::getStartTime)
                .last("LIMIT 1"));
    }

    @Override
    public AgentWorkflowExecution getByExecutionId(String executionId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<AgentWorkflowExecution>()
                .eq(AgentWorkflowExecution::getExecutionId, executionId));
    }

    @Override
    public List<AgentWorkflowExecution> getRecentExecutionsBySession(String sessionId, int limit) {
        return executionMapper.selectList(new LambdaQueryWrapper<AgentWorkflowExecution>()
                .eq(AgentWorkflowExecution::getSessionId, sessionId)
                .orderByDesc(AgentWorkflowExecution::getStartTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public List<AgentWorkflowExecution> getRecentExecutionsByAgentId(String agentId, int limit) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null || agent.getSessionId() == null) {
            return List.of();
        }
        return getRecentExecutionsBySession(agent.getSessionId(), limit);
    }

    @Override
    public boolean deleteExecution(String executionId, Long userId) {
        AgentWorkflowExecution execution = getByExecutionId(executionId);
        if (execution == null) {
            log.warn("删除工作流执行记录失败，记录不存在: executionId={}", executionId);
            return false;
        }

        if (AgentWorkflowExecution.STATUS_RUNNING.equals(execution.getStatus())) {
            log.warn("删除工作流执行记录失败，记录正在运行中: executionId={}", executionId);
            return false;
        }

        executionMapper.delete(new LambdaQueryWrapper<AgentWorkflowExecution>()
                .eq(AgentWorkflowExecution::getExecutionId, executionId));

        log.info("工作流执行记录已删除: executionId={}, workflowId={}, status={}",
                executionId, execution.getWorkflowId(), execution.getStatus());
        return true;
    }
}
