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
@TableName("agent_session_summaries")
public class AgentSessionSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String summaryType;

    private String summaryContent;

    private Long lastMessageId;

    private Integer tokenCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final String TYPE_INCREMENTAL = "INCREMENTAL";
    public static final String TYPE_FULL = "FULL";
}
