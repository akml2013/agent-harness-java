package com.example.core.system.tool;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.core.service.KnowledgeBaseService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryKnowledgeBaseSystemTool implements SystemTool {

    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public String getName() {
        return "query_knowledge_base";
    }

    @Override
    public String getDescription() {
        return "查询知识库获取相关信息。根据问题语义搜索最相关的知识片段，"
                + "支持指定知识库ID或搜索全部知识库。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("query", Map.of("type", "string", "description",
                "查询内容（必填，语义搜索的文本）", "required", true));
        schema.put("top_k", Map.of("type", "integer", "description",
                "返回结果数量（可选，默认5，最大20）"));
        schema.put("kb_ids", Map.of("type", "array", "description",
                "指定知识库ID列表（可选，不填则搜索全部知识库）"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        Long userId = SystemToolParamParser.getLong(params, "user_id");
        if (userId == null) {
            return SystemToolResult.error("缺少必填参数: user_id");
        }

        String query = SystemToolParamParser.getString(params, "query");
        if (query == null || query.isBlank()) {
            return SystemToolResult.error("缺少必填参数: query");
        }

        int topK = SystemToolParamParser.getInteger(params, "top_k", 5);
        if (topK <= 0 || topK > 20) {
            topK = 5;
        }

        try {
            // 解析kb_ids参数
            Object kbIdsObj = params.get("kb_ids");
            List<String> kbIds = null;
            if (kbIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) kbIdsObj;
                kbIds = rawList.stream().map(Object::toString).collect(Collectors.toList());
            }

            List<Map<String, Object>> results;
            if (kbIds != null && !kbIds.isEmpty()) {
                results = knowledgeBaseService.searchKnowledgeByKb(userId, kbIds, query, topK);
            } else {
                results = knowledgeBaseService.searchKnowledge(userId, query, topK);
            }

            if (results.isEmpty()) {
                return SystemToolResult.success("未找到相关知识片段。请检查知识库是否已建立索引，或尝试换一种方式提问。");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("results", results);
            data.put("total", results.size());

            StringBuilder msg = new StringBuilder();
            msg.append("找到").append(results.size()).append("条相关知识片段");
            return SystemToolResult.success(msg.toString(), data);
        } catch (Exception e) {
            log.error("知识库查询失败: userId={}, query={}, error={}", userId, query, e.getMessage());
            return SystemToolResult.error("知识库查询失败: " + e.getMessage());
        }
    }
}
