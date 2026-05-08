package com.example.core.service;

import java.util.List;

import com.example.core.entity.AgentScheduledTask;

public interface ScheduledTaskService {

    AgentScheduledTask createTask(String agentId, String sessionId, Long userId, String taskName,
            String taskDescription, String repeatType, String repeatRule, String taskInput);

    AgentScheduledTask updateTask(String taskId, Long userId, String taskName, String taskDescription,
            String repeatType, String repeatRule, String taskInput, String status);

    boolean pauseTask(String taskId, Long userId);

    boolean resumeTask(String taskId, Long userId);

    boolean cancelTask(String taskId, Long userId);

    List<AgentScheduledTask> getActiveTasksByAgentId(String agentId);

    List<AgentScheduledTask> getDueTasks();

    List<AgentScheduledTask> getDueTasksByAgentId(String agentId);

    List<AgentScheduledTask> getTasksByUserId(Long userId);

    AgentScheduledTask getByTaskId(String taskId);

    void markExecuted(String taskId);

    java.time.LocalDateTime calculateNextExecuteTime(String repeatType, String repeatRule,
            java.time.LocalDateTime afterTime);
}
