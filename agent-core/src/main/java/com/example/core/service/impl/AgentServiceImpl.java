package com.example.core.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.Agent;
import com.example.core.entity.AgentSession;
import com.example.core.mapper.AgentMapper;
import com.example.core.service.AgentService;
import com.example.core.service.SessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final SessionService sessionService;

    private static final String DEFAULT_AGENT_NAME = "AI助手";
    private static final String DEFAULT_CAPABILITIES = "文档处理,数据分析,工作流执行";

    @Override
    @Transactional
    public Agent createAgent(Long userId, String agentName, String agentAvatar, String systemPrompt,
            String capabilities) {
        AgentSession session = sessionService.createSession(userId,
                agentName != null ? agentName : DEFAULT_AGENT_NAME);

        Agent agent = new Agent();
        agent.setAgentId(UUID.randomUUID().toString().replace("-", ""));
        agent.setSessionId(session.getSessionId());
        agent.setUserId(userId);
        agent.setAgentName(agentName != null && !agentName.isBlank() ? agentName : DEFAULT_AGENT_NAME);
        agent.setAgentAvatar(agentAvatar);
        agent.setAgentStatus(Agent.RUNTIME_IDLE);
        agent.setSystemPrompt(systemPrompt);
        agent.setCapabilities(capabilities != null && !capabilities.isBlank() ? capabilities : DEFAULT_CAPABILITIES);
        agent.setStatus(Agent.STATUS_ACTIVE);
        agentMapper.insert(agent);

        log.info("创建Agent: agentId={}, sessionId={}, userId={}", agent.getAgentId(), agent.getSessionId(), userId);
        return agent;
    }

    @Override
    public Agent getByAgentId(String agentId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getAgentId, agentId)
                .eq(Agent::getStatus, Agent.STATUS_ACTIVE);
        return agentMapper.selectOne(wrapper);
    }

    @Override
    public Agent getBySessionId(String sessionId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getSessionId, sessionId)
                .eq(Agent::getStatus, Agent.STATUS_ACTIVE);
        return agentMapper.selectOne(wrapper);
    }

    @Override
    public List<Agent> getUserAgents(Long userId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getUserId, userId)
                .eq(Agent::getStatus, Agent.STATUS_ACTIVE)
                .orderByDesc(Agent::getUpdateTime);
        return agentMapper.selectList(wrapper);
    }

    @Override
    public List<Agent> getAllActiveAgents() {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getStatus, Agent.STATUS_ACTIVE);
        return agentMapper.selectList(wrapper);
    }

    @Override
    public Agent updateAgentName(String agentId, String agentName) {
        Agent agent = getByAgentId(agentId);
        if (agent == null) {
            return null;
        }
        LambdaUpdateWrapper<Agent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Agent::getAgentId, agentId)
                .set(Agent::getAgentName, agentName)
                .set(Agent::getUpdateTime, LocalDateTime.now());
        agentMapper.update(null, wrapper);

        sessionService.updateSession(agent.getSessionId(), agentName);

        return getByAgentId(agentId);
    }

    @Override
    public Agent updateAgentStatus(String agentId, String agentStatus) {
        LambdaUpdateWrapper<Agent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Agent::getAgentId, agentId)
                .set(Agent::getAgentStatus, agentStatus)
                .set(Agent::getUpdateTime, LocalDateTime.now());
        agentMapper.update(null, wrapper);
        return getByAgentId(agentId);
    }

    @Override
    @Transactional
    public boolean deleteAgent(String agentId) {
        Agent agent = getByAgentId(agentId);
        if (agent == null) {
            return false;
        }

        LambdaUpdateWrapper<Agent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Agent::getAgentId, agentId)
                .set(Agent::getStatus, Agent.STATUS_DELETED)
                .set(Agent::getUpdateTime, LocalDateTime.now());
        agentMapper.update(null, wrapper);

        sessionService.deleteSession(agent.getSessionId());

        log.info("删除Agent: agentId={}, sessionId={}", agentId, agent.getSessionId());
        return true;
    }
}
