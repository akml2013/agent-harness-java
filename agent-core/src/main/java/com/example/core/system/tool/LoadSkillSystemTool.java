package com.example.core.system.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.core.skill.SkillService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolResult;

public class LoadSkillSystemTool implements SystemTool {

    private final SkillService skillService;

    public LoadSkillSystemTool(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public String getName() {
        return "load_skill";
    }

    @Override
    public String getDescription() {
        return "加载技能文档，获取特定任务的详细指导。当你判断当前任务与某个技能相关时，调用此工具加载技能内容，严格按照技能指导执行。"
                + "可以一次加载多个技能，skill_id用逗号分隔。"
                + "可用技能：word_report_generation(Word报告生成)、excel_data_report(Excel数据报表)";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("skill_id",
                "技能ID(必填)，如'word_report_generation'。多个技能用逗号分隔，如'word_report_generation,excel_data_report'");
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String skillId = null;
        if (params.containsKey("skill_id")) {
            Object val = params.get("skill_id");
            skillId = val != null ? val.toString() : null;
        }

        if (skillId == null || skillId.isBlank()) {
            return SystemToolResult.error("缺少必填参数: skill_id");
        }

        if (!skillService.isAvailable()) {
            return SystemToolResult.error("技能系统不可用，SKILLS目录未配置");
        }

        String content;
        if (skillId.contains(",")) {
            content = skillService.loadSkills(skillId);
        } else {
            content = skillService.loadSkill(skillId);
        }

        if (content.startsWith("错误：")) {
            return SystemToolResult.error(content);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skill_id", skillId);
        data.put("content_length", content.length());

        return SystemToolResult.success(content, data);
    }
}
