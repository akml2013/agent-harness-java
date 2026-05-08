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
@TableName("agent_scheduled_tasks")
public class AgentScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String agentId;

    private String sessionId;

    private Long userId;

    private String taskName;

    private String taskDescription;

    private String repeatType;

    private String repeatRule;

    private String taskInput;

    private String status;

    private LocalDateTime lastExecuteTime;

    private LocalDateTime nextExecuteTime;

    private Integer executeCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String REPEAT_ONCE = "ONCE";
    public static final String REPEAT_HOURLY = "HOURLY";
    public static final String REPEAT_DAILY = "DAILY";
    public static final String REPEAT_WEEKLY = "WEEKLY";
    public static final String REPEAT_MONTHLY = "MONTHLY";
    public static final String REPEAT_YEARLY = "YEARLY";
}
