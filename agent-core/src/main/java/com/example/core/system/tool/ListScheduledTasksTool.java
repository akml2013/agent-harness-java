package com.example.core.system.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.entity.Agent;
import com.example.core.entity.AgentScheduledTask;
import com.example.core.service.AgentService;
import com.example.core.service.ScheduledTaskService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListScheduledTasksTool implements SystemTool {

    private final ScheduledTaskService scheduledTaskService;
    private final AgentService agentService;

    @Override
    public String getName() {
        return "list_scheduled_tasks";
    }

    @Override
    public String getDescription() {
        return "列出当前Agent的所有定时任务（包括活跃和暂停的），用于查看或管理定时任务";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("session_id", Map.of("type", "string", "description", "会话ID（系统自动注入）"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String sessionId = SystemToolParamParser.getString(params, "session_id");

        if (sessionId == null || sessionId.isBlank()) {
            return SystemToolResult.error("session_id参数缺失");
        }

        Agent agent = agentService.getBySessionId(sessionId);
        if (agent == null) {
            return SystemToolResult.error("未找到对应的Agent");
        }

        try {
            List<AgentScheduledTask> tasks = scheduledTaskService.getActiveTasksByAgentId(agent.getAgentId());
            List<Map<String, Object>> taskList = new ArrayList<>();
            for (AgentScheduledTask task : tasks) {
                Map<String, Object> item = new HashMap<>();
                item.put("task_id", task.getTaskId());
                item.put("task_name", task.getTaskName());
                item.put("task_description", task.getTaskDescription());
                item.put("repeat_type", task.getRepeatType());
                item.put("repeat_rule", task.getRepeatRule());
                item.put("task_input", task.getTaskInput());
                item.put("status", task.getStatus());
                item.put("next_execute_time", task.getNextExecuteTime() != null
                        ? task.getNextExecuteTime().toString() : null);
                item.put("last_execute_time", task.getLastExecuteTime() != null
                        ? task.getLastExecuteTime().toString() : null);
                item.put("execute_count", task.getExecuteCount());
                taskList.add(item);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("tasks", taskList);
            data.put("total", taskList.size());
            return SystemToolResult.success("查询到" + taskList.size() + "个定时任务", data);
        } catch (Exception e) {
            log.error("查询定时任务失败: {}", e.getMessage(), e);
            return SystemToolResult.error("查询定时任务失败: " + e.getMessage());
        }
    }
}
