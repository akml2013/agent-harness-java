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
public class UpdateScheduledTaskTool implements SystemTool {

    private final ScheduledTaskService scheduledTaskService;
    private final SseEmitterService sseEmitterService;

    @Override
    public String getName() {
        return "update_scheduled_task";
    }

    @Override
    public String getDescription() {
        return "修改定时任务（名称、描述、重复规则、执行内容、状态等）。"
                + "可以暂停任务(status=PAUSED)或恢复任务(status=ACTIVE)。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("task_id", Map.of("type", "string", "description", "要修改的定时任务ID"));
        schema.put("task_name", Map.of("type", "string", "description", "新任务名称(可选)"));
        schema.put("task_description", Map.of("type", "string", "description", "新任务描述(可选)"));
        schema.put("repeat_type", Map.of("type", "string", "description",
                "新重复类型ONCE/WEEKLY/MONTHLY/YEARLY(可选)"));
        schema.put("repeat_rule", Map.of("type", "string", "description", "新重复规则JSON(可选)"));
        schema.put("task_input", Map.of("type", "string", "description", "新执行内容描述(可选)"));
        schema.put("status", Map.of("type", "string", "description",
                "新状态ACTIVE/PAUSED(可选，用于暂停或恢复任务)"));
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

        String taskName = SystemToolParamParser.getString(params, "task_name");
        String taskDescription = SystemToolParamParser.getString(params, "task_description");
        String repeatType = SystemToolParamParser.getString(params, "repeat_type");
        String repeatRule = SystemToolParamParser.getString(params, "repeat_rule");
        String taskInput = SystemToolParamParser.getString(params, "task_input");
        String status = SystemToolParamParser.getString(params, "status");

        if (taskName == null && taskDescription == null && repeatType == null
                && repeatRule == null && taskInput == null && status == null) {
            return SystemToolResult.error("至少需要提供一个要修改的字段");
        }

        try {
            AgentScheduledTask task = scheduledTaskService.updateTask(
                    taskId, userId, taskName, taskDescription, repeatType, repeatRule, taskInput, status);
            if (task == null) {
                return SystemToolResult.error("定时任务不存在: " + taskId);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("task_id", task.getTaskId());
            data.put("task_name", task.getTaskName());
            data.put("status", task.getStatus());
            data.put("next_execute_time", task.getNextExecuteTime() != null
                    ? task.getNextExecuteTime().toString()
                    : null);

            sseEmitterService.emitScheduledTaskUpdated(task.getSessionId(), task.getTaskId(),
                    task.getTaskName(), task.getStatus(),
                    task.getNextExecuteTime() != null ? task.getNextExecuteTime().toString() : null);

            return SystemToolResult.success("定时任务已更新: " + task.getTaskName(), data);
        } catch (Exception e) {
            log.error("更新定时任务失败: {}", e.getMessage(), e);
            return SystemToolResult.error("更新定时任务失败: " + e.getMessage());
        }
    }
}
