package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_knowledge_chunks")
public class AgentKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String chunkId;

    private String kbId;

    private Long userId;

    private Integer chunkIndex;

    private String content;

    private String contentHash;

    private Integer charCount;

    private String milvusId;

    private String metadata;

    private LocalDateTime createTime;
}
