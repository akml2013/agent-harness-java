package com.example.core.react.message;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentTaskProducer {

    private static final String TOPIC = "agent-task-topic";

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public void sendTask(AgentTaskMessage message) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate不可用，跳过任务消息发送: sessionId={}", message.getSessionId());
            return;
        }
        try {
            rocketMQTemplate.syncSend(
                    TOPIC,
                    MessageBuilder.withPayload(message).build());
            log.info("Agent任务消息发送成功: sessionId={}, userId={}", message.getSessionId(), message.getUserId());
        } catch (Exception e) {
            log.error("Agent任务消息发送失败: sessionId={}, error={}", message.getSessionId(), e.getMessage());
            throw new RuntimeException("任务消息发送失败: " + e.getMessage(), e);
        }
    }
}
