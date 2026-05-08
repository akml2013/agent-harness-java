package com.example.core.react.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskMessage {

    private String sessionId;

    private Long userId;

    private String userMessage;

    private Long taskId;
}
