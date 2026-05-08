package com.example.core.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("agent_workflow_executions")
public class AgentWorkflowExecution {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ABORTED = "ABORTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String executionId;

    private String workflowId;

    private Long userId;

    private String sessionId;

    private String status;

    private String currentNodeId;

    private Integer completedNodes;

    private Integer totalNodes;

    private String executionResult;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;
}
