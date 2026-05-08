package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_workflows")
public class AgentWorkflow {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AI = "AI";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workflowId;

    private Long userId;

    private String name;

    private String description;

    private String status;

    private Integer nodeCount;

    private Integer version;

    private String createSource;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
