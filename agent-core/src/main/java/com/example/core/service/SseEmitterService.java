package com.example.core.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSON;
import com.example.core.react.ToolNameMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEmitterService {

    private final ToolNameMapper toolNameMapper;

    private static final long SSE_TIMEOUT = 1_800_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> userHeartbeatFutures = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userClientMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionAgentMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public void registerSessionAgent(String sessionId, String agentId, Long userId) {
        sessionAgentMap.put(sessionId, agentId);
        sessionUserMap.put(sessionId, String.valueOf(userId));
    }

    public void unregisterSession(String sessionId) {
        sessionAgentMap.remove(sessionId);
        sessionUserMap.remove(sessionId);
    }

    public SseEmitter createGlobalEmitter(String userId) {
        return createGlobalEmitter(userId, null);
    }

    public SseEmitter createGlobalEmitter(String userId, String clientId) {
        String emitterKey = clientId != null ? userId + ":" + clientId : userId;

        SseEmitter existing = userEmitters.get(emitterKey);
        if (existing != null) {
            userEmitters.remove(emitterKey);
            stopClientHeartbeat(emitterKey);
            try {
                existing.complete();
            } catch (Exception e) {
                log.debug("关闭旧SSE连接: key={}", emitterKey);
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.debug("全局SSE连接完成: key={}", emitterKey);
            if (userEmitters.get(emitterKey) == emitter) {
                userEmitters.remove(emitterKey);
                stopClientHeartbeat(emitterKey);
                removeClientFromUser(userId, clientId);
            }
        });

        emitter.onTimeout(() -> {
            log.debug("全局SSE连接超时: key={}", emitterKey);
            if (userEmitters.get(emitterKey) == emitter) {
                userEmitters.remove(emitterKey);
                stopClientHeartbeat(emitterKey);
                removeClientFromUser(userId, clientId);
            }
        });

        emitter.onError(e -> {
            log.debug("全局SSE连接错误: key={}, error={}", emitterKey, e.getMessage());
            if (userEmitters.get(emitterKey) == emitter) {
                userEmitters.remove(emitterKey);
                stopClientHeartbeat(emitterKey);
                removeClientFromUser(userId, clientId);
            }
        });

        userEmitters.put(emitterKey, emitter);
        if (clientId != null) {
            userClientMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(clientId);
        }
        startClientHeartbeat(emitterKey, emitter);
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data("{\"type\":\"CONNECTED\"}"));
        } catch (IOException e) {
            log.warn("SSE初始连接消息发送失败: key={}, error={}", emitterKey, e.getMessage());
        }
        log.info("全局SSE连接创建: userId={}, clientId={}", userId, clientId);
        return emitter;
    }

    private void removeClientFromUser(String userId, String clientId) {
        if (clientId != null) {
            Set<String> clients = userClientMap.get(userId);
            if (clients != null) {
                clients.remove(clientId);
                if (clients.isEmpty()) {
                    userClientMap.remove(userId);
                }
            }
        }
    }

    private void startClientHeartbeat(String emitterKey, SseEmitter emitter) {
        stopClientHeartbeat(emitterKey);
        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (userEmitters.containsKey(emitterKey)) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } else {
                    stopClientHeartbeat(emitterKey);
                }
            } catch (IOException e) {
                log.debug("SSE心跳失败: key={}, error={}", emitterKey, e.getMessage());
                stopClientHeartbeat(emitterKey);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        userHeartbeatFutures.put(emitterKey, future);
    }

    private void stopClientHeartbeat(String emitterKey) {
        ScheduledFuture<?> future = userHeartbeatFutures.remove(emitterKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Deprecated
    private void startUserHeartbeat(String userId, SseEmitter emitter) {
        startClientHeartbeat(userId, emitter);
    }

    @Deprecated
    private void stopUserHeartbeat(String userId) {
        stopClientHeartbeat(userId);
    }

    private void sendAndFlush(SseEmitter emitter, String jsonData) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(jsonData));
        emitter.send(SseEmitter.event().comment(""));
    }

    private boolean emitToUser(String userId, java.util.HashMap<String, Object> event) {
        Set<String> clients = userClientMap.get(userId);
        if (clients != null && !clients.isEmpty()) {
            boolean anySuccess = false;
            for (String clientId : clients) {
                String emitterKey = userId + ":" + clientId;
                SseEmitter emitter = userEmitters.get(emitterKey);
                if (emitter != null) {
                    try {
                        sendAndFlush(emitter, JSON.toJSONString(event));
                        anySuccess = true;
                    } catch (IOException e) {
                        log.warn("SSE推送失败: key={}, error={}", emitterKey, e.getMessage());
                        userEmitters.remove(emitterKey);
                        stopClientHeartbeat(emitterKey);
                    }
                }
            }
            return anySuccess;
        }

        SseEmitter emitter = userEmitters.get(userId);
        if (emitter == null) {
            return false;
        }
        try {
            sendAndFlush(emitter, JSON.toJSONString(event));
            return true;
        } catch (IOException e) {
            log.warn("全局SSE推送失败: userId={}, error={}", userId, e.getMessage());
            userEmitters.remove(userId);
            stopClientHeartbeat(userId);
            return false;
        }
    }

    private boolean emitToSession(String sessionId, java.util.HashMap<String, Object> event) {
        if (sessionId != null) {
            String agentId = sessionAgentMap.get(sessionId);
            String userId = sessionUserMap.get(sessionId);

            if (agentId != null) {
                event.put("agent_id", agentId);
            }

            if (userId != null) {
                return emitToUser(userId, event);
            }
        }

        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            try {
                sendAndFlush(entry.getValue(), JSON.toJSONString(event));
            } catch (IOException e) {
                log.warn("SSE广播推送失败: userId={}, error={}", entry.getKey(), e.getMessage());
            }
        }
        return !userEmitters.isEmpty();
    }

    public void emitChatMessage(String sessionId, String messageType, Object payload) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "CHAT_MESSAGE");
        event.put("payload", payload != null ? payload : Map.of());
        emitToSession(sessionId, event);
    }

    public void emitThoughtChunk(String sessionId, Long thoughtId, String chunk, boolean finished) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "THOUGHT_CHUNK");
        event.put("content", toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(chunk)));
        if (thoughtId != null) {
            event.put("thought_id", thoughtId);
        }
        event.put("finished", finished);
        emitToSession(sessionId, event);
    }

    public void emitStreamChunk(String sessionId, Long msgId, String content) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "STREAM_CHUNK");
        event.put("content", toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(content)));
        if (msgId != null) {
            event.put("msg_id", msgId);
        }
        emitToSession(sessionId, event);
    }

    public void emitSessionStatus(String sessionId, String status, String finalAnswer) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", status);
        if (finalAnswer != null) {
            payload.put("final_answer",
                    toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(finalAnswer)));
        }
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "SESSION_STATUS");
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitWaitingUserInput(String sessionId, String question, java.util.List<String> options) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("waiting_user_input", true);
        payload.put("question",
                question != null ? toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(question)) : "");
        payload.put("options", options != null ? options : java.util.Collections.emptyList());
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "WAITING_INPUT");
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitMessageCountUpdate(String sessionId, int count) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "MESSAGE_COUNT_UPDATE");
        event.put("count", count);
        emitToSession(sessionId, event);
    }

    public void completeEmitter(String sessionId) {
        String agentId = sessionAgentMap.get(sessionId);
        String userId = sessionUserMap.get(sessionId);

        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "DONE");
        if (agentId != null) {
            event.put("agent_id", agentId);
        }

        if (userId != null) {
            SseEmitter emitter = userEmitters.get(userId);
            if (emitter != null) {
                try {
                    sendAndFlush(emitter, JSON.toJSONString(event));
                    log.info("SSE DONE事件已发送(连接保持): sessionId={}, agentId={}", sessionId, agentId);
                } catch (IOException e) {
                    log.debug("SSE DONE推送失败(可忽略): sessionId={}", sessionId);
                }
            }
        }
    }

    public void completeWithError(String sessionId, String error) {
        String agentId = sessionAgentMap.get(sessionId);
        String userId = sessionUserMap.get(sessionId);

        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "ERROR");
        event.put("error", error);
        if (agentId != null) {
            event.put("agent_id", agentId);
        }

        if (userId != null) {
            SseEmitter emitter = userEmitters.get(userId);
            if (emitter != null) {
                try {
                    sendAndFlush(emitter, JSON.toJSONString(event));
                } catch (IOException e) {
                    log.debug("SSE错误推送失败(可忽略): sessionId={}", sessionId);
                }
            }
        }
    }

    public void emitTitleUpdate(String sessionId, String title) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "TITLE_UPDATE");
        event.put("session_id", sessionId);
        event.put("title", title);
        emitToSession(sessionId, event);
    }

    public void emitCompressing(String sessionId) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "COMPRESSING");
        event.put("session_id", sessionId);
        emitToSession(sessionId, event);
    }

    public void emitCompressDone(String sessionId) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "COMPRESS_DONE");
        event.put("session_id", sessionId);
        emitToSession(sessionId, event);
    }

    public void emitTaskListIncomplete(String sessionId, java.util.List<java.util.Map<String, Object>> taskItems) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "TASK_LIST_INCOMPLETE");
        event.put("payload", java.util.Map.of(
                "task_items", taskItems,
                "message", "仍有未完成的任务，是否继续执行？"));
        emitToSession(sessionId, event);
    }

    public boolean hasEmitter(String sessionId) {
        String userId = sessionUserMap.get(sessionId);
        return userId != null && userEmitters.containsKey(userId);
    }

    public boolean hasUserEmitter(String userId) {
        return userEmitters.containsKey(userId);
    }

    public void emitAgentStatusUpdate(String agentId, String status, Long userId) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "AGENT_STATUS_UPDATE");
        event.put("agent_id", agentId);
        event.put("status", status);
        emitToUser(String.valueOf(userId), event);
    }

    public void emitWorkflowCreated(String sessionId, String workflowId, String workflowName) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "WORKFLOW_CREATED");
        event.put("payload", java.util.Map.of(
                "workflow_id", workflowId != null ? workflowId : "",
                "name", workflowName != null ? workflowName : ""));
        emitToSession(sessionId, event);
    }

    public void emitWorkflowNodeStatus(String sessionId, String workflowId, String nodeId, String status) {
        emitWorkflowNodeStatus(sessionId, workflowId, nodeId, status, null, -1);
    }

    public void emitWorkflowNodeStatus(String sessionId, String workflowId, String nodeId, String status,
            String executionId) {
        emitWorkflowNodeStatus(sessionId, workflowId, nodeId, status, executionId, -1);
    }

    public void emitWorkflowNodeStatus(String sessionId, String workflowId, String nodeId, String status,
            String executionId, int completedNodes) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "WORKFLOW_NODE_STATUS");
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("workflow_id", workflowId != null ? workflowId : "");
        payload.put("node_id", nodeId != null ? nodeId : "");
        payload.put("status", status != null ? status : "idle");
        if (executionId != null) {
            payload.put("execution_id", executionId);
        }
        if (completedNodes >= 0) {
            payload.put("completed_nodes", completedNodes);
        }
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitWorkflowExecutionStart(String sessionId, String workflowId) {
        emitWorkflowExecutionStart(sessionId, workflowId, null, 0);
    }

    public void emitWorkflowExecutionStart(String sessionId, String workflowId, String executionId) {
        emitWorkflowExecutionStart(sessionId, workflowId, executionId, 0);
    }

    public void emitWorkflowExecutionStart(String sessionId, String workflowId, String executionId, int totalNodes) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("workflow_id", workflowId != null ? workflowId : "");
        if (executionId != null) {
            payload.put("execution_id", executionId);
        }
        if (totalNodes > 0) {
            payload.put("total_nodes", totalNodes);
        }
        event.put("type", "WORKFLOW_EXECUTION_START");
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitWorkflowExecutionEnd(String sessionId, String workflowId) {
        emitWorkflowExecutionEnd(sessionId, workflowId, null, null);
    }

    public void emitWorkflowExecutionEnd(String sessionId, String workflowId, String executionId, String status) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("workflow_id", workflowId != null ? workflowId : "");
        if (executionId != null) {
            payload.put("execution_id", executionId);
        }
        if (status != null) {
            payload.put("status", status);
        }
        event.put("type", "WORKFLOW_EXECUTION_END");
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitWorkflowDeleted(String sessionId, String workflowId) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "WORKFLOW_DELETED");
        event.put("payload", java.util.Map.of(
                "workflow_id", workflowId != null ? workflowId : ""));
        emitToSession(sessionId, event);
    }

    public void emitScheduledTaskCreated(String sessionId, String taskId, String taskName,
            String repeatType, String nextExecuteTime) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "SCHEDULED_TASK_CREATED");
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("task_id", taskId != null ? taskId : "");
        payload.put("task_name", taskName != null ? taskName : "");
        payload.put("repeat_type", repeatType != null ? repeatType : "");
        payload.put("next_execute_time", nextExecuteTime != null ? nextExecuteTime : "");
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitScheduledTaskUpdated(String sessionId, String taskId, String taskName,
            String status, String nextExecuteTime) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "SCHEDULED_TASK_UPDATED");
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("task_id", taskId != null ? taskId : "");
        payload.put("task_name", taskName != null ? taskName : "");
        payload.put("status", status != null ? status : "");
        payload.put("next_execute_time", nextExecuteTime != null ? nextExecuteTime : "");
        event.put("payload", payload);
        emitToSession(sessionId, event);
    }

    public void emitScheduledTaskCancelled(String sessionId, String taskId) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "SCHEDULED_TASK_CANCELLED");
        event.put("payload", java.util.Map.of(
                "task_id", taskId != null ? taskId : ""));
        emitToSession(sessionId, event);
    }

    public void emitScheduledTaskTriggered(String sessionId, String taskId, String taskName) {
        java.util.HashMap<String, Object> event = new java.util.HashMap<>();
        event.put("type", "SCHEDULED_TASK_TRIGGERED");
        event.put("payload", java.util.Map.of(
                "task_id", taskId != null ? taskId : "",
                "task_name", taskName != null ? taskName : ""));
        emitToSession(sessionId, event);
    }
}
