package com.example.core.service.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import com.example.core.entity.AgentSession;
import com.example.core.service.DeepseekService;
import com.example.core.service.SessionService;
import com.example.core.service.SessionTitleService;
import com.example.core.service.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleServiceImpl implements SessionTitleService {

    private final DeepseekService deepseekService;
    private final SessionService sessionService;
    private final SseEmitterService sseEmitterService;

    private static final String TITLE_SYSTEM_PROMPT = "你是一个会话标题生成器。请根据用户的消息，生成一个简短的会话标题。要求：1.不超过15个字；2.不要使用引号；3.直接输出标题文本，不要任何额外说明；4.用中文生成。";

    private static final long TITLE_TIMEOUT_SECONDS = 10L;

    @Override
    public String generateTitle(String userMessage) {
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    () -> deepseekService.chatWithSystem(TITLE_SYSTEM_PROMPT, userMessage));

            String title = future.get(TITLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (title != null && !title.isBlank()) {
                title = title.trim();
                title = title.replaceAll("^[\\\"'\u201c\u201d]+|[\\\"'\u201c\u201d]+$", "");
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }
                return title;
            }
        } catch (TimeoutException e) {
            log.warn("生成会话标题超时({}s): 使用默认标题", TITLE_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("生成会话标题失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void generateAndApplyTitle(String sessionId, Long userId, String userMessage) {
        try {
            AgentSession session = sessionService.getBySessionId(sessionId);
            if (session == null) {
                return;
            }

            String currentTitle = session.getTitle();
            if (currentTitle != null && !isDefaultTitle(currentTitle)) {
                return;
            }

            String newTitle = generateTitle(userMessage);
            if (newTitle != null && !newTitle.isBlank()) {
                sessionService.updateSession(sessionId, newTitle);
                sseEmitterService.emitTitleUpdate(sessionId, newTitle);
                log.info("会话标题已自动生成: sessionId={}, title={}", sessionId, newTitle);
            } else {
                sessionService.updateSession(sessionId, "新会话");
                sseEmitterService.emitTitleUpdate(sessionId, "新会话");
                log.info("会话标题生成失败/超时, 使用默认标题: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.warn("自动生成会话标题失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private boolean isDefaultTitle(String title) {
        if ("新会话".equals(title)) {
            return true;
        }
        if (title.endsWith("...") && title.length() <= 23) {
            return true;
        }
        return false;
    }
}
