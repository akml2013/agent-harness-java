package com.example.core.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssembledContext {

    private String systemPrompt;
    private String userMessage;

    @Builder.Default
    private int estimatedTokens = 0;

    public String toPromptString() {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append("[System] ").append(systemPrompt).append("\n\n");
        }

        if (userMessage != null && !userMessage.isEmpty()) {
            sb.append(userMessage);
        }

        return sb.toString();
    }
}
