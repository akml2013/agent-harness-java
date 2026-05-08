package com.example.core.react.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.core.entity.Agent;
import com.example.core.react.ReactEngine;
import com.example.core.service.AgentService;
import com.example.core.service.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeManager {

    private final Map<String, AgentRuntime> runtimes = new ConcurrentHashMap<>();
    private final Map<String, AgentRuntime> sessionRuntimes = new ConcurrentHashMap<>();

    private final AgentService agentService;
    private final ReactEngine reactEngine;
    private final SseEmitterService sseEmitterService;

    public AgentRuntime getOrCreateRuntime(String agentId, Long userId) {
        return runtimes.computeIfAbsent(agentId, id -> {
            Agent agent = agentService.getByAgentId(id);
            if (agent == null) {
                throw new IllegalArgumentException("Agent不存在: " + id);
            }
            AgentRuntime runtime = new AgentRuntime(agent, reactEngine, agentService, sseEmitterService);
            sessionRuntimes.put(agent.getSessionId(), runtime);
            sseEmitterService.registerSessionAgent(agent.getSessionId(), agent.getAgentId(), agent.getUserId());
            log.info("创建AgentRuntime: agentId={}, sessionId={}", id, agent.getSessionId());
            return runtime;
        });
    }

    public AgentRuntime getRuntime(String agentId) {
        return runtimes.get(agentId);
    }

    public AgentRuntime getRuntimeBySessionId(String sessionId) {
        return sessionRuntimes.get(sessionId);
    }

    public void destroyRuntime(String agentId) {
        AgentRuntime runtime = runtimes.remove(agentId);
        if (runtime != null) {
            sessionRuntimes.remove(runtime.getSessionId());
            runtime.destroy();
            log.info("销毁AgentRuntime: agentId={}", agentId);
        }
    }

    public List<AgentRuntime> getAllRuntimes(Long userId) {
        return runtimes.values().stream()
                .filter(r -> r.getUserId().equals(userId))
                .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        log.info("开始恢复所有AgentRuntime...");
        List<Agent> activeAgents = agentService.getAllActiveAgents();
        int count = 0;
        for (Agent agent : activeAgents) {
            try {
                AgentRuntime runtime = new AgentRuntime(agent, reactEngine, agentService, sseEmitterService);
                runtimes.put(agent.getAgentId(), runtime);
                sessionRuntimes.put(agent.getSessionId(), runtime);
                sseEmitterService.registerSessionAgent(agent.getSessionId(), agent.getAgentId(), agent.getUserId());
                runtime.submitMessage(AgentMessage.resumeMessage());
                count++;
                log.info("恢复AgentRuntime: agentId={}, sessionId={}", agent.getAgentId(), agent.getSessionId());
            } catch (Exception e) {
                log.error("恢复AgentRuntime失败: agentId={}, error={}", agent.getAgentId(), e.getMessage());
            }
        }
        log.info("AgentRuntime恢复完成: 共恢复{}个Agent", count);
    }

    public void shutdownAll() {
        log.info("开始关闭所有AgentRuntime...");
        runtimes.values().forEach(AgentRuntime::destroy);
        runtimes.clear();
        sessionRuntimes.clear();
        log.info("所有AgentRuntime已关闭");
    }

    public int getRuntimeCount() {
        return runtimes.size();
    }
}
