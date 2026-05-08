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
@TableName("agent_chat_messages")
public class AgentChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String messageType;

    private String role;

    private String content;

    private String metadata;

    private Long parentId;

    private Integer roundIndex;

    private Integer sortOrder;

    private Integer durationMs;

    private LocalDateTime createTime;

    public static final String TYPE_USER_MESSAGE = "USER_MESSAGE";
    public static final String TYPE_AI_TEXT = "AI_TEXT";
    public static final String TYPE_AI_THOUGHT = "AI_THOUGHT";
    public static final String TYPE_AI_THOUGHT_BRIEF = "AI_THOUGHT_BRIEF";
    public static final String TYPE_MCP_ACTION = "MCP_ACTION";
    public static final String TYPE_MCP_RESULT = "MCP_RESULT";
    public static final String TYPE_ASK_USER = "ASK_USER";
    public static final String TYPE_USER_INPUT = "USER_INPUT";
    public static final String TYPE_FILE_GENERATED = "FILE_GENERATED";
    public static final String TYPE_FILE_DELETED = "FILE_DELETED";
    public static final String TYPE_TASK_UPDATE = "TASK_UPDATE";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_USER_EVENT = "USER_EVENT";
    public static final String TYPE_SYSTEM_ACTION = "SYSTEM_ACTION";
    public static final String TYPE_SYSTEM_RESULT = "SYSTEM_RESULT";
    public static final String TYPE_AGENT_PROACTIVE = "AGENT_PROACTIVE";
}
