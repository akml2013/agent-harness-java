package com.example.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SkillService {

    @Value("${agent.skills.directory:}")
    private String skillsDirectory;

    @PostConstruct
    public void init() {
        if (skillsDirectory == null || skillsDirectory.isBlank()) {
            log.warn("SKILLS目录未配置，技能系统不可用");
            return;
        }
        Path dir = Paths.get(skillsDirectory);
        if (!Files.isDirectory(dir)) {
            log.warn("SKILLS目录不存在: {}", skillsDirectory);
            return;
        }
        log.info("SKILLS系统初始化完成，目录: {}（动态加载，修改文件即时生效）", skillsDirectory);
    }

    public String getIndexContent() {
        if (skillsDirectory == null || skillsDirectory.isBlank()) {
            return "";
        }
        Path indexPath = Paths.get(skillsDirectory, "index.md");
        if (!Files.exists(indexPath)) {
            return "";
        }
        try {
            return Files.readString(indexPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("加载SKILLS索引失败: {}", e.getMessage());
            return "";
        }
    }

    public String loadSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return "错误：skill_id不能为空";
        }
        if (skillsDirectory == null || skillsDirectory.isBlank()) {
            return "错误：技能系统不可用";
        }

        Path skillPath = Paths.get(skillsDirectory, skillId + ".md");
        if (!Files.exists(skillPath)) {
            return "错误：技能文件不存在: " + skillId;
        }

        try {
            String content = Files.readString(skillPath, StandardCharsets.UTF_8);
            log.debug("技能加载成功: {}, 长度: {}", skillId, content.length());
            return content;
        } catch (IOException e) {
            log.error("加载技能失败: {}, 错误: {}", skillId, e.getMessage());
            return "错误：加载技能失败: " + e.getMessage();
        }
    }

    public String loadSkills(String skillIds) {
        if (skillIds == null || skillIds.isBlank()) {
            return "错误：skill_id不能为空";
        }

        StringBuilder sb = new StringBuilder();
        String[] ids = skillIds.split(",");

        for (String id : ids) {
            String trimmedId = id.trim();
            if (trimmedId.isBlank())
                continue;

            if (sb.length() > 0) {
                sb.append("\n\n---\n\n");
            }
            sb.append(loadSkill(trimmedId));
        }

        return sb.toString();
    }

    public Map<String, String> listAvailableSkills() {
        Map<String, String> skills = new LinkedHashMap<>();
        if (skillsDirectory == null || skillsDirectory.isBlank()) {
            return skills;
        }

        Path dir = Paths.get(skillsDirectory);
        if (!Files.isDirectory(dir)) {
            return skills;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                if ("index.md".equals(fileName))
                    continue;
                String skillId = fileName.substring(0, fileName.length() - 3);
                skills.put(skillId, skillId);
            }
        } catch (IOException e) {
            log.error("扫描SKILLS目录失败: {}", e.getMessage());
        }

        return skills;
    }

    public boolean isAvailable() {
        return skillsDirectory != null && !skillsDirectory.isBlank()
                && Files.isDirectory(Paths.get(skillsDirectory));
    }
}
