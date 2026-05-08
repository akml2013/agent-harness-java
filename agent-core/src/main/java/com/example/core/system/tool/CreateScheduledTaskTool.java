package com.example.core.system.tool;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.entity.Agent;
import com.example.core.entity.AgentScheduledTask;
import com.example.core.service.AgentService;
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
public class CreateScheduledTaskTool implements SystemTool {

    private final ScheduledTaskService scheduledTaskService;
    private final AgentService agentService;
    private final SseEmitterService sseEmitterService;

    @Override
    public String getName() {
        return "create_scheduled_task";
    }

    @Override
    public String getDescription() {
        return "创建定时任务。当用户请求包含周期性或定时执行的需求时使用此工具"
                + "（如\"每周一\"、\"每月1号\"、\"每天早上8点\"、\"定期\"、\"到时候自动\"等）。"
                + "如果用户只是说\"帮我生成周报\"（无周期性表述），则直接执行，不创建定时任务。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("task_name", Map.of("type", "string", "description",
                "任务名称，简洁描述任务内容，如\"生成周报\"、\"数据备份\""));
        schema.put("task_description", Map.of("type", "string", "description",
                "任务描述(AI可读)，用于心跳触发时构建执行指令，如\"生成本周工作周报并保存为Word文档\""));
        schema.put("repeat_type", Map.of("type", "string", "description",
                "重复类型: ONCE(一次性)/HOURLY(每小时)/DAILY(每天)/WEEKLY(每周)/MONTHLY(每月)/YEARLY(每年)"));
        schema.put("repeat_rule", Map.of("type", "string", "description",
                "重复规则(JSON字符串)。ONCE:{\"execute_at\":\"2026-05-01 09:00:00\"}; "
                        + "HOURLY:{\"minute\":30,\"startTime\":\"08:00\",\"endTime\":\"18:00\",\"startDate\":\"2026-05-01\"}; "
                        + "DAILY:{\"times\":[\"08:00\",\"18:00\"],\"startDate\":\"2026-05-01\"}; "
                        + "WEEKLY:{\"weekdays\":[1,3],\"times\":[\"08:00\",\"16:00\"],\"startDate\":\"2026-05-01\"}; "
                        + "MONTHLY:{\"daysOfMonth\":[1,15],\"times\":[\"09:00\"],\"startDate\":\"2026-05-01\"}; "
                        + "YEARLY:{\"monthsOfYear\":[1,7],\"daysOfMonth\":[1],\"times\":[\"10:00\"],\"startDate\":\"2026-05-01\"}。"
                        + "HOURLY的minute:每小时的第几分钟(0-59); startTime/endTime:执行时间范围; "
                        + "weekdays:1=周一到7=周日; daysOfMonth:1-31; monthsOfYear:1-12; times:24小时制HH:mm"));
        schema.put("task_input", Map.of("type", "string", "description",
                "执行时AI应处理的内容描述，如\"生成周报并保存\"、\"执行财务汇报工作流\""));
        schema.put("session_id", Map.of("type", "string", "description", "会话ID（系统自动注入）"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String sessionId = SystemToolParamParser.getString(params, "session_id");
        Long userId = SystemToolParamParser.getLong(params, "user_id");
        String taskName = SystemToolParamParser.getString(params, "task_name");
        String taskDescription = SystemToolParamParser.getString(params, "task_description");
        String repeatType = SystemToolParamParser.getString(params, "repeat_type");
        String repeatRule = SystemToolParamParser.getString(params, "repeat_rule");
        String taskInput = SystemToolParamParser.getString(params, "task_input");

        if (sessionId == null || sessionId.isBlank()) {
            return SystemToolResult.error("session_id参数缺失");
        }
        if (taskName == null || taskName.isBlank()) {
            return SystemToolResult.error("task_name参数缺失");
        }
        if (repeatType == null || repeatType.isBlank()) {
            return SystemToolResult.error("repeat_type参数缺失");
        }
        if (repeatRule == null || repeatRule.isBlank()) {
            return SystemToolResult.error("repeat_rule参数缺失");
        }

        Agent agent = agentService.getBySessionId(sessionId);
        if (agent == null) {
            return SystemToolResult.error("未找到对应的Agent");
        }

        try {
            AgentScheduledTask task = scheduledTaskService.createTask(
                    agent.getAgentId(), sessionId, userId,
                    taskName, taskDescription, repeatType, repeatRule, taskInput);

            Map<String, Object> data = new HashMap<>();
            data.put("task_id", task.getTaskId());
            data.put("task_name", task.getTaskName());
            data.put("repeat_type", task.getRepeatType());
            data.put("next_execute_time", task.getNextExecuteTime() != null
                    ? task.getNextExecuteTime().toString()
                    : null);

            sseEmitterService.emitScheduledTaskCreated(sessionId, task.getTaskId(),
                    task.getTaskName(), task.getRepeatType(),
                    task.getNextExecuteTime() != null ? task.getNextExecuteTime().toString() : null);

            return SystemToolResult.success("定时任务已创建: " + taskName, data);
        } catch (Exception e) {
            log.error("创建定时任务失败: {}", e.getMessage(), e);
            return SystemToolResult.error("创建定时任务失败: " + e.getMessage());
        }
    }
}
