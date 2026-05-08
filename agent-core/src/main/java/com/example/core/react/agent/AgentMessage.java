package com.example.core.react.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    private TriggerSource triggerSource;
    private String content;
    private String selectedOption;
    private String userInput;
    private String scheduledTaskId;

    public static AgentMessage userMessage(String content) {
        return AgentMessage.builder()
                .triggerSource(TriggerSource.USER)
                .content(content)
                .build();
    }

    public static AgentMessage userMessage(String content, String selectedOption, String userInput) {
        return AgentMessage.builder()
                .triggerSource(TriggerSource.USER)
                .content(content)
                .selectedOption(selectedOption)
                .userInput(userInput)
                .build();
    }

    public static AgentMessage resumeMessage() {
        return AgentMessage.builder()
                .triggerSource(TriggerSource.RESUME)
                .content("恢复Agent运行")
                .build();
    }

    public static AgentMessage scheduledTaskMessage(String taskId, String content) {
        return AgentMessage.builder()
                .triggerSource(TriggerSource.SCHEDULED_TASK)
                .scheduledTaskId(taskId)
                .content(content)
                .build();
    }
}