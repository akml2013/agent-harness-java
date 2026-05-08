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
@TableName("agent_operation_details")
public class AgentOperationDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private String sessionId;

    private Long userId;

    private String operationType;

    private String toolName;

    private String actionInput;

    private String actionResult;

    private String fileKey;

    private Integer roundIndex;

    private Integer durationMs;

    private LocalDateTime createTime;

    public static final String TYPE_MCP = "MCP";
    public static final String TYPE_SYSTEM = "SYSTEM";
}
