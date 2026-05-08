package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agents")
public class Agent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentId;

    private String sessionId;

    private Long userId;

    private String agentName;

    private String agentAvatar;

    private String agentStatus;

    private String systemPrompt;

    private String capabilities;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    public static final String RUNTIME_IDLE = "IDLE";
    public static final String RUNTIME_RUNNING = "RUNNING";
    public static final String RUNTIME_WAITING_USER_INPUT = "WAITING_USER_INPUT";
    public static final String RUNTIME_PAUSED = "PAUSED";
    public static final String RUNTIME_ERROR = "ERROR";
}
