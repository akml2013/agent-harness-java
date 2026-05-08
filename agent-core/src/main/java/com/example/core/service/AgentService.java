package com.example.core.service;

import java.util.List;

import com.example.core.entity.Agent;

public interface AgentService {

    Agent createAgent(Long userId, String agentName, String agentAvatar, String systemPrompt, String capabilities);

    Agent getByAgentId(String agentId);

    Agent getBySessionId(String sessionId);

    List<Agent> getUserAgents(Long userId);

    List<Agent> getAllActiveAgents();

    Agent updateAgentName(String agentId, String agentName);

    Agent updateAgentStatus(String agentId, String agentStatus);

    boolean deleteAgent(String agentId);
}
