package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("memory_long_term")
public class MemoryLongTerm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String milvusId;

    private String sessionId;

    private Long userId;

    private String content;

    private String contentHash;

    private String sourceType;

    private Double relevanceScore;

    private String metadata;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
