package com.example.core.react;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.core.mcp.McpTool;
import com.example.core.mcp.McpToolRegistry;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolRegistry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNameMapper {

    private final McpToolRegistry mcpToolRegistry;
    private final SystemToolRegistry systemToolRegistry;

    private volatile Map<String, String> nameMap = new LinkedHashMap<>();
    private volatile Pattern replacePattern = Pattern.compile("");

    private static final Map<String, String> BASE_MAP = Map.ofEntries(
            Map.entry("read_word", "读取Word文档"),
            Map.entry("read_excel", "读取Excel文档"),
            Map.entry("read_pdf", "读取PDF文档"),
            Map.entry("read_txt", "读取文本文件"),
            Map.entry("modify_word", "修改Word文档"),
            Map.entry("modify_excel", "修改Excel文档"),
            Map.entry("generate_word", "生成Word文档"),
            Map.entry("generate_excel", "生成Excel文档"),
            Map.entry("generate_pdf", "生成PDF文档"),
            Map.entry("ask_user", "向用户提问"),
            Map.entry("list_files", "查询文件列表"),
            Map.entry("delete_file", "删除文件"),
            Map.entry("search_files", "搜索文件"),
            Map.entry("web_search", "网络搜索"),
            Map.entry("calculate", "数据计算"),
            Map.entry("send_email", "发送邮件"),
            Map.entry("query_operation_detail", "查询操作详情"),
            Map.entry("query_knowledge_base", "查询知识库"),
            Map.entry("generate_chart", "生成图表"),
            Map.entry("execute_workflow", "执行工作流"),
            Map.entry("create_workflow", "创建工作流"),
            Map.entry("delete_workflow", "删除工作流"),
            Map.entry("get_workflow", "查看工作流"),
            Map.entry("create_scheduled_task", "创建定时任务"),
            Map.entry("list_scheduled_tasks", "查看定时任务"),
            Map.entry("update_scheduled_task", "修改定时任务"),
            Map.entry("cancel_scheduled_task", "取消定时任务"),
            Map.entry("get_current_time", "获取当前时间"),
            Map.entry("load_skill", "加载技能"));

    @PostConstruct
    public void init() {
        rebuild();
    }

    public synchronized void rebuild() {
        Map<String, String> newMap = new LinkedHashMap<>(BASE_MAP);

        for (Map.Entry<String, McpTool> entry : mcpToolRegistry.getAllTools().entrySet()) {
            String name = entry.getKey();
            if (!newMap.containsKey(name)) {
                String desc = entry.getValue().getDescription();
                newMap.put(name, extractChineseName(name, desc));
            }
        }

        for (Map.Entry<String, SystemTool> entry : systemToolRegistry.getAllTools().entrySet()) {
            String name = entry.getKey();
            if (!newMap.containsKey(name)) {
                String desc = entry.getValue().getDescription();
                newMap.put(name, extractChineseName(name, desc));
            }
        }

        this.nameMap = newMap;
        this.replacePattern = buildPattern(newMap);
        log.info("工具名映射表重建完成，共{}条映射", newMap.size());
    }

    public String getChineseName(String englishName) {
        return nameMap.getOrDefault(englishName, englishName);
    }

    public String replaceToolNames(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = replacePattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        matcher.reset();
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            String replacement = nameMap.getOrDefault(matched, matched);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String fixPerspective(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        result = result.replaceAll("已为您", "已经");
        result = result.replaceAll("将为您", "将");
        result = result.replaceAll("会为您", "会");
        result = result.replaceAll("可以为您", "可以");
        result = result.replaceAll("为您生成", "生成了");
        result = result.replaceAll("为您处理", "处理了");
        result = result.replaceAll("为您查询", "查询了");
        result = result.replaceAll("为您创建", "创建了");
        result = result.replaceAll("为您修改", "修改了");
        result = result.replaceAll("为您分析", "分析了");
        result = result.replaceAll("您已成功", "我已成功");
        result = result.replaceAll("您已生成", "我已生成");
        result = result.replaceAll("您已创建", "我已创建");
        result = result.replaceAll("您已修改", "我已修改");
        result = result.replaceAll("您已完成", "我已完成");
        result = result.replaceAll("你已成功", "我已成功");
        result = result.replaceAll("你已生成", "我已生成");
        result = result.replaceAll("你已创建", "我已创建");
        result = result.replaceAll("你已修改", "我已修改");
        result = result.replaceAll("你已完成", "我已完成");
        return result;
    }

    String extractChineseName(String toolName, String description) {
        if (description == null || description.isEmpty()) {
            return toolName;
        }
        String firstSentence = description.split("[。，,\\.\\n]")[0].trim();
        if (!firstSentence.isEmpty() && firstSentence.length() <= 20) {
            return firstSentence;
        }
        return toolName;
    }

    private Pattern buildPattern(Map<String, String> map) {
        StringBuilder regex = new StringBuilder();
        regex.append("(?<![a-zA-Z0-9_])(");
        boolean first = true;
        for (String name : map.keySet()) {
            if (!first) {
                regex.append("|");
            }
            regex.append(Pattern.quote(name));
            first = false;
        }
        regex.append(")(?![a-zA-Z0-9_])");
        return Pattern.compile(regex.toString());
    }
}
