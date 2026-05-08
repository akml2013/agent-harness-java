package com.example.core.react.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.example.core.entity.Agent;
import com.example.core.react.ReactEngine;
import com.example.core.react.model.ReactSession;
import com.example.core.service.AgentService;
import com.example.core.service.SseEmitterService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentRuntime {

    private final String agentId;
    private final String sessionId;
    private final Long userId;
    private volatile String status;

    private final BlockingQueue<AgentMessage> messageQueue;
    private final ExecutorService singleThreadExecutor;
    private volatile boolean running = true;

    private final ReactEngine reactEngine;
    private final AgentService agentService;
    private final SseEmitterService sseEmitterService;

    public AgentRuntime(Agent agent, ReactEngine reactEngine, AgentService agentService,
            SseEmitterService sseEmitterService) {
        this.agentId = agent.getAgentId();
        this.sessionId = agent.getSessionId();
        this.userId = agent.getUserId();
        this.status = agent.getAgentStatus() != null ? agent.getAgentStatus() : Agent.RUNTIME_IDLE;
        this.messageQueue = new LinkedBlockingQueue<>();
        this.singleThreadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "agent-runtime-" + agentId);
            t.setDaemon(true);
            return t;
        });
        this.reactEngine = reactEngine;
        this.agentService = agentService;
        this.sseEmitterService = sseEmitterService;

        startConsumerLoop();
    }

    private void startConsumerLoop() {
        singleThreadExecutor.submit(() -> {
            log.info("AgentRuntime消息消费循环启动: agentId={}", agentId);
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    AgentMessage message = messageQueue.poll(30, TimeUnit.SECONDS);
                    if (message == null) {
                        continue;
                    }
                    processMessage(message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("AgentRuntime消费循环被中断: agentId={}", agentId);
                    break;
                } catch (Exception e) {
                    log.error("AgentRuntime消费循环异常: agentId={}, error={}", agentId, e.getMessage(), e);
                    updateStatus(Agent.RUNTIME_ERROR);
                }
            }
            log.info("AgentRuntime消息消费循环结束: agentId={}", agentId);
        });
    }

    private void processMessage(AgentMessage message) {
        log.info("AgentRuntime处理消息: agentId={}, triggerSource={}", agentId, message.getTriggerSource());

        updateStatus(Agent.RUNTIME_RUNNING);

        try {
            switch (message.getTriggerSource()) {
                case USER -> processUserMessage(message);
                case SCHEDULED_TASK -> processScheduledTask(message);
                case RESUME -> processResume();
                default -> log.warn("未知的触发源: {}", message.getTriggerSource());
            }
        } catch (Exception e) {
            log.error("AgentRuntime处理消息异常: agentId={}, error={}", agentId, e.getMessage(), e);
            updateStatus(Agent.RUNTIME_ERROR);
        }

        ReactSession activeSession = reactEngine.getSession(sessionId);
        if (activeSession != null && activeSession.isWaitingUserInput()) {
            updateStatus(Agent.RUNTIME_WAITING_USER_INPUT);
        } else if (Agent.RUNTIME_RUNNING.equals(getStatus())) {
            updateStatus(Agent.RUNTIME_IDLE);
        }
    }

    private void processUserMessage(AgentMessage message) {
        reactEngine.runWithSse(sessionId, userId, message.getContent(),
                message.getSelectedOption(), message.getUserInput(), sseEmitterService);
    }

    private void processScheduledTask(AgentMessage message) {
        log.info("定时任务触发执行: agentId={}, taskId={}", agentId, message.getScheduledTaskId());
        reactEngine.runFromScheduledTask(sessionId, userId, message.getContent(), sseEmitterService);
    }

    private void processResume() {
        log.info("Agent恢复运行: agentId={}", agentId);
    }

    public void submitMessage(AgentMessage message) {
        if (!running) {
            log.warn("AgentRuntime已停止，拒绝消息: agentId={}", agentId);
            return;
        }
        messageQueue.offer(message);
        log.debug("消息已提交到AgentRuntime队列: agentId={}, triggerSource={}", agentId, message.getTriggerSource());
    }

    public void destroy() {
        running = false;
        singleThreadExecutor.shutdownNow();
        try {
            if (!singleThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("AgentRuntime终止超时: agentId={}", agentId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("AgentRuntime已销毁: agentId={}", agentId);
    }

    private void updateStatus(String newStatus) {
        this.status = newStatus;
        try {
            agentService.updateAgentStatus(agentId, newStatus);
        } catch (Exception e) {
            log.warn("更新Agent状态失败(非致命): agentId={}, status={}, error={}", agentId, newStatus, e.getMessage());
        }
    }

    public String getAgentId() {
        return agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public int getQueueSize() {
        return messageQueue.size();
    }

    public boolean isRunning() {
        return running;
    }
}
