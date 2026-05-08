package com.example.core.system.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.core.entity.AgentOperationDetail;
import com.example.core.mapper.AgentOperationDetailMapper;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryOperationDetailTool implements SystemTool {

    private final AgentOperationDetailMapper operationDetailMapper;

    @Override
    public String getName() {
        return "query_operation_detail";
    }

    @Override
    public String getDescription() {
        return "查询当前会话中MCP工具或系统工具的操作详情，包括完整的输入参数和返回结果。"
                + "可按消息ID、工具名、轮次、文件key等条件查询。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("session_id", Map.of("type", "string", "description", "会话ID（系统自动注入）"));
        schema.put("message_id", Map.of("type", "integer", "description", "指定消息ID"));
        schema.put("tool_name", Map.of("type", "string", "description", "指定工具名，如modify_word、generate_excel等"));
        schema.put("round_index", Map.of("type", "integer", "description", "指定ReAct轮次"));
        schema.put("file_key", Map.of("type", "string", "description", "指定文件key"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String sessionId = SystemToolParamParser.getString(params, "session_id");
        Long messageId = SystemToolParamParser.getLong(params, "message_id");
        String toolName = SystemToolParamParser.getString(params, "tool_name");
        Integer roundIndex = SystemToolParamParser.getInteger(params, "round_index");
        String fileKey = SystemToolParamParser.getString(params, "file_key");

        if (sessionId == null || sessionId.isBlank()) {
            return SystemToolResult.error("session_id参数缺失");
        }

        try {
            List<AgentOperationDetail> details;

            if (messageId != null) {
                AgentOperationDetail detail = operationDetailMapper.selectOne(
                        new LambdaQueryWrapper<AgentOperationDetail>()
                                .eq(AgentOperationDetail::getMessageId, messageId));
                details = detail != null ? List.of(detail) : List.of();
            } else if (fileKey != null && !fileKey.isBlank()) {
                details = operationDetailMapper.selectBySessionIdAndFileKey(sessionId, fileKey);
            } else if (roundIndex != null) {
                details = operationDetailMapper.selectBySessionIdAndRound(sessionId, roundIndex);
            } else if (toolName != null && !toolName.isBlank()) {
                details = operationDetailMapper.selectBySessionIdAndToolName(sessionId, toolName);
            } else {
                details = operationDetailMapper.selectBySessionId(sessionId);
            }

            List<Map<String, Object>> detailList = new ArrayList<>();
            for (AgentOperationDetail d : details) {
                Map<String, Object> item = new HashMap<>();
                item.put("message_id", d.getMessageId());
                item.put("operation_type", d.getOperationType());
                item.put("tool_name", d.getToolName());
                item.put("action_input", d.getActionInput() != null
                        ? com.alibaba.fastjson2.JSON.parseObject(d.getActionInput(), Map.class)
                        : null);
                item.put("action_result", d.getActionResult() != null
                        ? com.alibaba.fastjson2.JSON.parseObject(d.getActionResult(), Map.class)
                        : null);
                item.put("file_key", d.getFileKey());
                item.put("round_index", d.getRoundIndex());
                item.put("duration_ms", d.getDurationMs());
                item.put("create_time", d.getCreateTime() != null ? d.getCreateTime().toString() : null);
                detailList.add(item);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("details", detailList);
            data.put("total", detailList.size());

            return SystemToolResult.success("查询到" + detailList.size() + "条操作详情", data);
        } catch (Exception e) {
            log.error("查询操作详情失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return SystemToolResult.error("查询操作详情失败: " + e.getMessage());
        }
    }
}
