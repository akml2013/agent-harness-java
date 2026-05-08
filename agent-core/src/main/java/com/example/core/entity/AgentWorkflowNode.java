package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_workflow_nodes")
public class AgentWorkflowNode {

    public static final String TYPE_START = "START";
    public static final String TYPE_END = "END";
    public static final String TYPE_TASK = "TASK";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nodeId;

    private String workflowId;

    private Long userId;

    private String nodeType;

    private String nodeName;

    private Integer positionX;

    private Integer positionY;

    private String toolName;

    private String toolParams;

    private String description;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
