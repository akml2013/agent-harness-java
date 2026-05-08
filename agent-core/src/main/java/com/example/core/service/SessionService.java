package com.example.core.service;

import java.util.List;

import com.example.core.entity.AgentSession;

public interface SessionService {

    AgentSession createSession(Long userId, String title);

    AgentSession getBySessionId(String sessionId);

    List<AgentSession> getUserSessions(Long userId, String status, int page, int size);

    AgentSession updateSession(String sessionId, String title);

    boolean deleteSession(String sessionId);

    AgentSession updateSessionStatus(String sessionId, String status);

    AgentSession incrementMessageCount(String sessionId);

    AgentSession updateMessageCount(String sessionId, int count);

    AgentSession updateLastMessageTime(String sessionId);
}
