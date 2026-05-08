package com.example.core.react.message;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.example.core.react.ReactEngine;
import com.example.core.react.model.ReactSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = "rocketMQTemplate")
@RocketMQMessageListener(topic = "agent-task-topic", consumerGroup = "agent-task-consumer-group")
public class AgentTaskConsumer implements RocketMQListener<AgentTaskMessage> {

    private final ReactEngine reactEngine;

    @Override
    public void onMessage(AgentTaskMessage message) {
        log.info("收到Agent任务消息: sessionId={}, userId={}", message.getSessionId(), message.getUserId());
        try {
            ReactSession session = reactEngine.run(
                    message.getSessionId(),
                    message.getUserId(),
                    message.getUserMessage());
            log.info("Agent任务执行完成: sessionId={}, finished={}, rounds={}",
                    message.getSessionId(), session.isFinished(), session.getCurrentRound());
        } catch (Exception e) {
            log.error("Agent任务执行失败: sessionId={}, error={}", message.getSessionId(), e.getMessage(), e);
        }
    }
}
