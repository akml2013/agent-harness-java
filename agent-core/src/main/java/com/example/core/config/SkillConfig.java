package com.example.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.core.skill.SkillService;
import com.example.core.system.tool.LoadSkillSystemTool;

@Configuration
public class SkillConfig {

    @Bean
    public LoadSkillSystemTool loadSkillSystemTool(SkillService skillService) {
        return new LoadSkillSystemTool(skillService);
    }
}
