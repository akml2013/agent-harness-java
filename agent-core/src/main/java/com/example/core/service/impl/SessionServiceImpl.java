package com.example.core.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.AgentChatMessage;
import com.example.core.entity.AgentFileStorage;
import com.example.core.entity.AgentSession;
import com.example.core.management.ObjectDataManager;
import com.example.core.mapper.AgentChatMessageMapper;
import com.example.core.mapper.AgentFileStorageMapper;
import com.example.core.mapper.AgentSessionMapper;
import com.example.core.memory.MemoryLayer;
import com.example.core.service.SessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final AgentSessionMapper sessionMapper;
    private final AgentChatMessageMapper chatMessageMapper;
    private final AgentFileStorageMapper fileStorageMapper;
    private final ObjectDataManager objectDataManager;
    private final MemoryLayer memoryLayer;

    @Override
    public AgentSession createSession(Long userId, String title) {
        AgentSession session = new AgentSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setTitle(title != null && !title.isBlank() ? title : "新会话");
        session.setStatus("RUNNING");
        session.setMessageCount(0);
        session.setLastMessageTime(LocalDateTime.now());
        sessionMapper.insert(session);
        log.info("创建会话: sessionId={}, userId={}", session.getSessionId(), userId);
        return session;
    }

    @Override
    public AgentSession getBySessionId(String sessionId) {
        LambdaQueryWrapper<AgentSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSession::getSessionId, sessionId);
        return sessionMapper.selectOne(wrapper);
    }

    @Override
    public List<AgentSession> getUserSessions(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<AgentSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSession::getUserId, userId)
                .eq(status != null && !status.isBlank(), AgentSession::getStatus, status)
                .orderByDesc(AgentSession::getLastMessageTime);
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        return sessionMapper.selectList(wrapper);
    }

    @Override
    public AgentSession updateSession(String sessionId, String title) {
        AgentSession session = getBySessionId(sessionId);
        if (session == null) {
            return null;
        }
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return session;
    }

    @Override
    @Transactional
    public boolean deleteSession(String sessionId) {
        AgentSession session = getBySessionId(sessionId);
        if (session == null) {
            return false;
        }

        try {
            memoryLayer.clearSession(sessionId);
            log.info("会话记忆已清理: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("会话记忆清理失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
        }

        LambdaQueryWrapper<AgentFileStorage> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(AgentFileStorage::getSessionId, sessionId);
        List<AgentFileStorage> files = fileStorageMapper.selectList(fileWrapper);
        for (AgentFileStorage file : files) {
            try {
                objectDataManager.delete(file.getStorageKey());
            } catch (Exception e) {
                log.warn("MinIO文件删除失败(非致命): fileKey={}, error={}", file.getStorageKey(), e.getMessage());
            }
        }
        if (!files.isEmpty()) {
            fileStorageMapper.delete(fileWrapper);
            log.info("会话文件记录已清理: sessionId={}, count={}", sessionId, files.size());
        }

        LambdaQueryWrapper<AgentChatMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(AgentChatMessage::getSessionId, sessionId);
        long msgCount = chatMessageMapper.selectCount(msgWrapper);
        if (msgCount > 0) {
            chatMessageMapper.delete(msgWrapper);
            log.info("会话消息已清理: sessionId={}, count={}", sessionId, msgCount);
        }

        sessionMapper.deleteById(session.getId());
        log.info("删除会话完成: sessionId={}", sessionId);
        return true;
    }

    @Override
    public AgentSession updateSessionStatus(String sessionId, String status) {
        LambdaUpdateWrapper<AgentSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentSession::getSessionId, sessionId)
                .set(AgentSession::getStatus, status)
                .set(AgentSession::getUpdateTime, LocalDateTime.now());
        sessionMapper.update(null, wrapper);
        return getBySessionId(sessionId);
    }

    @Override
    public AgentSession incrementMessageCount(String sessionId) {
        AgentSession session = getBySessionId(sessionId);
        if (session == null) {
            return null;
        }
        session.setMessageCount(session.getMessageCount() != null ? session.getMessageCount() + 1 : 1);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return session;
    }

    @Override
    public AgentSession updateMessageCount(String sessionId, int count) {
        LambdaUpdateWrapper<AgentSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentSession::getSessionId, sessionId)
                .set(AgentSession::getMessageCount, count)
                .set(AgentSession::getUpdateTime, LocalDateTime.now());
        sessionMapper.update(null, wrapper);
        return getBySessionId(sessionId);
    }

    @Override
    public AgentSession updateLastMessageTime(String sessionId) {
        LambdaUpdateWrapper<AgentSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentSession::getSessionId, sessionId)
                .set(AgentSession::getLastMessageTime, LocalDateTime.now())
                .set(AgentSession::getUpdateTime, LocalDateTime.now());
        sessionMapper.update(null, wrapper);
        return getBySessionId(sessionId);
    }
}
