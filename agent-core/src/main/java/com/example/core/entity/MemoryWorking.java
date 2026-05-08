package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("memory_working")
public class MemoryWorking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String sessionId;

    private Long userId;

    private String role;

    private String content;

    private String priority;

    private String metadata;

    private LocalDateTime createTime;
}
