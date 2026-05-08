package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_file_storage")
public class AgentFileStorage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private Long taskId;

    private String fileName;

    private String originalName;

    private String storageKey;

    private Long fileSize;

    private String fileType;

    private String mimeType;

    private String source;

    private Integer isOriginal;

    private Long originalFileId;

    private LocalDateTime expireTime;

    private Integer downloadCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
