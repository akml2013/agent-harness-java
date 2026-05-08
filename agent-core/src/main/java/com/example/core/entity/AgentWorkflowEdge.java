package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_workflow_edges")
public class AgentWorkflowEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String edgeId;

    private String workflowId;

    private Long userId;

    private String sourceNodeId;

    private String targetNodeId;

    @TableField("`condition`")
    private String edgeCondition;

    private LocalDateTime createTime;
}
