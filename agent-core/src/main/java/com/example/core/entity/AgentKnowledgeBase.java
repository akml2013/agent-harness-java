package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_knowledge_bases")
public class AgentKnowledgeBase {

    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_CHUNKING = "CHUNKING";
    public static final String STATUS_EMBEDDING = "EMBEDDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ERROR = "ERROR";

    public static final String STRATEGY_PARAGRAPH = "PARAGRAPH";
    public static final String STRATEGY_FIXED_SIZE = "FIXED_SIZE";
    public static final String STRATEGY_CHAPTER = "CHAPTER";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String kbId;

    private Long userId;

    private String name;

    private String description;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String storageKey;

    private Integer chunkCount;

    private String chunkStrategy;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private String status;

    private Integer progress;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
