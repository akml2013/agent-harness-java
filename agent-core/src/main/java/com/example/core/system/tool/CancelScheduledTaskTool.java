package com.example.core.system.tool;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.entity.AgentScheduledTask;
import com.example.core.service.ScheduledTaskService;
import com.example.core.service.SseEmitterService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelScheduledTaskTool implements SystemTool {

    private final ScheduledTaskService scheduledTaskService;
    private final SseEmitterService sseEmitterService;

    @Override
    public String getName() {
        return "cancel_scheduled_task";
    }

    @Override
    public String getDescription() {
        return "取消/删除定时任务。当用户要求取消某个定时任务时使用此工具。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("task_id", Map.of("type", "string", "description", "要取消的定时任务ID"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String taskId = SystemToolParamParser.getString(params, "task_id");
        Long userId = SystemToolParamParser.getLong(params, "user_id");

        if (taskId == null || taskId.isBlank()) {
            return SystemToolResult.error("task_id参数缺失");
        }

        try {
            AgentScheduledTask task = scheduledTaskService.getByTaskId(taskId);
            if (task == null) {
                return SystemToolResult.error("定时任务不存在: " + taskId);
            }

            boolean success = scheduledTaskService.cancelTask(taskId, userId);
            if (!success) {
                return SystemToolResult.error("取消定时任务失败，任务可能已取消或已完成");
            }

            sseEmitterService.emitScheduledTaskCancelled(task.getSessionId(), taskId);

            Map<String, Object> data = new HashMap<>();
            data.put("task_id", taskId);
            return SystemToolResult.success("定时任务已取消: " + task.getTaskName(), data);
        } catch (Exception e) {
            log.error("取消定时任务失败: {}", e.getMessage(), e);
            return SystemToolResult.error("取消定时任务失败: " + e.getMessage());
        }
    }
}
