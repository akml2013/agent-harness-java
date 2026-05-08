package com.example.core.system.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AskUserSystemTool implements SystemTool {

    @Override
    public String getName() {
        return "ask_user";
    }

    @Override
    public String getDescription() {
        return "向用户提问，等待用户回复后继续执行。"
                + "当你不确定用户意图、需要用户确认、或需要用户提供额外信息时使用此工具。"
                + "可以提供选项供用户选择，用户也可以输入自定义文本。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("question", Map.of("type", "string", "description",
                "要向用户提出的问题（必填）", "required", true));
        schema.put("options", Map.of("type", "array", "description",
                "供用户选择的选项列表（可选，2-5个选项，如[\"选项A\",\"选项B\"])"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String question = SystemToolParamParser.getString(params, "question");
        if (question == null || question.isBlank()) {
            return SystemToolResult.error("缺少必填参数: question");
        }

        Object optionsObj = params.get("options");
        List<String> options = null;
        if (optionsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> rawList = (List<Object>) optionsObj;
            options = rawList.stream().map(Object::toString).toList();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("question", question);
        if (options != null && !options.isEmpty()) {
            data.put("options", options);
        }

        SystemToolResult result = SystemToolResult.success("等待用户输入", data);
        result.setNeedsUserInput(true);
        return result;
    }
}
