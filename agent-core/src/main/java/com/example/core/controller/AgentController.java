package com.example.core.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.core.entity.Agent;
import com.example.core.entity.AgentChatMessage;
import com.example.core.entity.AgentFileStorage;
import com.example.core.entity.AgentSession;
import com.example.core.mapper.AgentFileStorageMapper;
import com.example.core.memory.MemoryLayer;
import com.example.core.memory.model.MemoryMessage;
import com.example.core.memory.model.MemoryPriority;
import com.example.core.memory.model.MemoryType;
import com.example.core.react.ReactEngine;
import com.example.core.react.agent.AgentRuntime;
import com.example.core.react.agent.AgentRuntimeManager;
import com.example.core.react.agent.HeartbeatScheduler;
import com.example.core.react.message.AgentTaskMessage;
import com.example.core.react.message.AgentTaskProducer;
import com.example.core.react.model.ReactSession;
import com.example.core.service.AgentService;
import com.example.core.service.ChatMessageService;
import com.example.core.service.SessionService;
import com.example.core.service.SseEmitterService;
import com.example.core.storage.FileStorageService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    @Value("${agent.message.default-rounds:3}")
    private int defaultMessageRounds;

    private final ReactEngine reactEngine;
    private final AgentTaskProducer taskProducer;
    private final FileStorageService fileStorageService;
    private final AgentFileStorageMapper fileStorageMapper;
    private final ChatMessageService chatMessageService;
    private final SessionService sessionService;
    private final SseEmitterService sseEmitterService;
    private final MemoryLayer memoryLayer;
    private final com.example.core.react.ToolNameMapper toolNameMapper;
    private final AgentService agentService;
    private final AgentRuntimeManager agentRuntimeManager;
    private final HeartbeatScheduler heartbeatScheduler;
    private final com.example.core.service.ScheduledTaskService scheduledTaskService;
    private final com.example.core.service.WorkflowExecutionService workflowExecutionService;
    private final com.example.core.service.WorkflowService workflowService;

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String message = getString(request, "message");
        String sessionId = getString(request, "session_id");
        Boolean async = (Boolean) request.getOrDefault("async", false);
        String selectedOption = getString(request, "selected_option");
        String userInput = getString(request, "user_input");

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }
        boolean hasMessage = message != null && !message.isBlank();
        boolean hasOption = selectedOption != null && !selectedOption.isBlank();
        boolean hasUserInput = userInput != null && !userInput.isBlank();
        if (!hasMessage && !hasOption && !hasUserInput) {
            return errorResponse("缺少消息内容: message/selected_option/user_input至少需要一个");
        }

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        if (Boolean.TRUE.equals(async)) {
            AgentTaskMessage taskMessage = AgentTaskMessage.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .userMessage(message)
                    .build();
            taskProducer.sendTask(taskMessage);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("session_id", sessionId);
            result.put("status", "PROCESSING");
            result.put("message", "任务已提交异步处理");
            return result;
        }

        ReactSession session = reactEngine.run(sessionId, userId, message, selectedOption, userInput);

        return buildSessionResponse(session);
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user_id") Long userId,
            @RequestParam(value = "session_id", required = false) String sessionId) {

        if (file.isEmpty()) {
            return errorResponse("上传文件不能为空");
        }

        try {
            String originalName = file.getOriginalFilename();
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
            }

            String storageKey = fileStorageService.generateUploadKey(userId, sessionId, originalName);
            fileStorageService.upload(storageKey, file.getInputStream(), file.getSize(), file.getContentType());

            String fileType = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();

            AgentFileStorage fileRecord = new AgentFileStorage();
            fileRecord.setUserId(userId);
            fileRecord.setSessionId(sessionId);
            fileRecord.setFileName(storageKey.substring(storageKey.lastIndexOf('/') + 1));
            fileRecord.setOriginalName(originalName);
            fileRecord.setStorageKey(storageKey);
            fileRecord.setFileSize(file.getSize());
            fileRecord.setFileType(fileType);
            fileRecord.setMimeType(file.getContentType());
            fileRecord.setSource("upload");
            fileRecord.setIsOriginal(1);
            fileRecord.setDeleted(0);
            fileStorageMapper.insert(fileRecord);

            chatMessageService.saveFileGenerated(sessionId, userId,
                    fileRecord.getId(), storageKey, originalName, fileType, "upload");

            Map<String, Object> eventMeta = new HashMap<>();
            eventMeta.put("event_type", "FILE_UPLOAD");
            eventMeta.put("file_id", fileRecord.getId());
            eventMeta.put("file_name", originalName);
            eventMeta.put("file_type", fileType);
            String eventContent = "用户上传了文件\"" + originalName + "\"";
            chatMessageService.saveUserEvent(sessionId, userId, eventContent, eventMeta);

            memoryLayer.storeMemory(MemoryMessage.builder()
                    .sessionId(sessionId).userId(userId).role("system")
                    .content(eventContent).type(MemoryType.SHORT_TERM)
                    .priority(MemoryPriority.P1).build());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("file_id", fileRecord.getId());
            result.put("file_key", storageKey);
            result.put("file_name", originalName);
            result.put("file_type", fileType);
            result.put("session_id", sessionId);
            return result;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            return errorResponse("文件上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/session/{sessionId}")
    public Map<String, Object> getSession(@PathVariable String sessionId) {
        ReactSession session = reactEngine.getSession(sessionId);
        if (session == null) {
            return errorResponse("会话不存在: " + sessionId);
        }

        return buildSessionResponse(session);
    }

    @PostMapping("/session/{sessionId}/clear")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        reactEngine.removeSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "会话已清除");
        return result;
    }

    @PostMapping("/sessions/{sessionId}/stop")
    public Map<String, Object> stopSession(@PathVariable String sessionId) {
        boolean aborted = reactEngine.abortSession(sessionId);
        if (aborted) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已发送停止请求");
            return result;
        }
        ReactSession session = reactEngine.getSession(sessionId);
        if (session == null) {
            sessionService.updateSessionStatus(sessionId, "FINISHED");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "会话已不在运行中");
            return result;
        }
        return errorResponse("无法停止当前会话");
    }

    @GetMapping("/download/{fileId}")
    public Map<String, Object> getDownloadUrl(@PathVariable Long fileId) {
        AgentFileStorage fileRecord = fileStorageMapper.selectById(fileId);
        if (fileRecord == null || fileRecord.getDeleted() == 1) {
            return errorResponse("文件不存在");
        }

        String downloadUrl = fileStorageService.generateDownloadUrl(fileRecord.getStorageKey(), 60,
                fileRecord.getOriginalName());

        fileRecord.setDownloadCount(fileRecord.getDownloadCount() + 1);
        fileStorageMapper.updateById(fileRecord);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("download_url", downloadUrl);
        result.put("file_name", fileRecord.getOriginalName());
        result.put("file_type", fileRecord.getFileType());
        return result;
    }

    @GetMapping("/sessions")
    public Map<String, Object> listSessions(
            @RequestParam("user_id") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status) {
        List<AgentSession> sessions = sessionService.getUserSessions(userId, status, page, size);

        List<Map<String, Object>> sessionList = new ArrayList<>();
        for (AgentSession s : sessions) {
            int msgCount = chatMessageService.countBySessionAndTypes(s.getSessionId(),
                    java.util.List.of(AgentChatMessage.TYPE_USER_MESSAGE, AgentChatMessage.TYPE_AI_TEXT));
            if (msgCount != s.getMessageCount()) {
                sessionService.updateMessageCount(s.getSessionId(), msgCount);
            }
            Map<String, Object> item = new HashMap<>();
            item.put("session_id", s.getSessionId());
            item.put("title", s.getTitle());
            item.put("status", s.getStatus());
            item.put("message_count", msgCount);
            item.put("last_message_time", s.getLastMessageTime() != null ? s.getLastMessageTime().toString() : null);
            item.put("create_time", s.getCreateTime() != null ? s.getCreateTime().toString() : null);
            sessionList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sessions", sessionList);
        result.put("total", sessionList.size());
        return result;
    }

    @PostMapping("/sessions")
    public Map<String, Object> createSession(@RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String title = getString(request, "title");

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }

        AgentSession session = sessionService.createSession(userId, title);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("session_id", session.getSessionId());
        result.put("title", session.getTitle());
        result.put("status", session.getStatus());
        result.put("create_time", session.getCreateTime() != null ? session.getCreateTime().toString() : null);
        return result;
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSessionDetail(@PathVariable String sessionId) {
        AgentSession session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            return errorResponse("会话不存在: " + sessionId);
        }

        ReactSession activeSession = reactEngine.getSession(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("session_id", session.getSessionId());
        result.put("title", session.getTitle());
        result.put("status", session.getStatus());
        result.put("message_count", session.getMessageCount());
        result.put("last_message_time",
                session.getLastMessageTime() != null ? session.getLastMessageTime().toString() : null);
        result.put("create_time", session.getCreateTime() != null ? session.getCreateTime().toString() : null);
        if (activeSession != null) {
            result.put("task_list", activeSession.getTaskList());
            result.put("rounds", activeSession.getCurrentRound());
        }
        return result;
    }

    @PutMapping("/sessions/{sessionId}")
    public Map<String, Object> updateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        String title = getString(request, "title");
        if (title == null || title.isBlank()) {
            return errorResponse("缺少必填参数: title");
        }

        AgentSession session = sessionService.updateSession(sessionId, title);
        if (session == null) {
            return errorResponse("会话不存在: " + sessionId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        boolean deleted = sessionService.deleteSession(sessionId);
        if (!deleted) {
            return errorResponse("会话不存在: " + sessionId);
        }

        reactEngine.removeSession(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> getMessages(
            @PathVariable String sessionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "message_type", required = false) String messageType) {
        Page<AgentChatMessage> messagePage = chatMessageService.getMessages(sessionId, messageType, page, size);

        List<Map<String, Object>> messages = new ArrayList<>();
        for (AgentChatMessage msg : messagePage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", msg.getId());
            item.put("message_type", msg.getMessageType());
            item.put("role", msg.getRole());
            item.put("content", AgentChatMessage.TYPE_USER_MESSAGE.equals(msg.getMessageType())
                    ? msg.getContent()
                    : toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(msg.getContent())));
            Object metadata = msg.getMetadata();
            if (metadata instanceof String metaStr && !metaStr.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metaMap = com.alibaba.fastjson2.JSON.parseObject(metaStr, Map.class);
                    if (metaMap != null) {
                        Object toolName = metaMap.get("tool_name");
                        if (toolName instanceof String) {
                            metaMap.put("tool_name", toolNameMapper.getChineseName((String) toolName));
                        }
                        metadata = metaMap;
                    }
                } catch (Exception e) {
                    log.debug("解析metadata JSON失败: {}", e.getMessage());
                }
            } else if (metadata instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metaMap = new HashMap<>((Map<String, Object>) metadata);
                Object toolName = metaMap.get("tool_name");
                if (toolName instanceof String) {
                    metaMap.put("tool_name", toolNameMapper.getChineseName((String) toolName));
                }
                metadata = metaMap;
            }
            item.put("metadata", metadata);
            item.put("parent_id", msg.getParentId());
            item.put("round_index", msg.getRoundIndex());
            item.put("sort_order", msg.getSortOrder());
            item.put("duration_ms", msg.getDurationMs());
            item.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            messages.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("messages", messages);
        result.put("total", messagePage.getTotal());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @GetMapping("/sessions/{sessionId}/messages/by-rounds")
    public Map<String, Object> getMessagesByRounds(
            @PathVariable String sessionId,
            @RequestParam(value = "rounds", required = false) Integer rounds,
            @RequestParam(value = "before_sort_order", required = false) Integer beforeSortOrder) {
        int effectiveRounds = (rounds != null && rounds > 0) ? rounds : defaultMessageRounds;
        Map<String, Object> serviceResult = chatMessageService.getMessagesByRounds(sessionId, effectiveRounds,
                beforeSortOrder);

        @SuppressWarnings("unchecked")
        List<AgentChatMessage> rawMessages = (List<AgentChatMessage>) serviceResult.get("messages");

        List<Map<String, Object>> messages = new ArrayList<>();
        for (AgentChatMessage msg : rawMessages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", msg.getId());
            item.put("message_type", msg.getMessageType());
            item.put("role", msg.getRole());
            item.put("content", AgentChatMessage.TYPE_USER_MESSAGE.equals(msg.getMessageType())
                    ? msg.getContent()
                    : toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(msg.getContent())));
            Object metadata = msg.getMetadata();
            if (metadata instanceof String metaStr && !metaStr.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metaMap = com.alibaba.fastjson2.JSON.parseObject(metaStr, Map.class);
                    if (metaMap != null) {
                        Object toolName = metaMap.get("tool_name");
                        if (toolName instanceof String) {
                            metaMap.put("tool_name", toolNameMapper.getChineseName((String) toolName));
                        }
                        metadata = metaMap;
                    }
                } catch (Exception e) {
                    log.debug("解析metadata JSON失败: {}", e.getMessage());
                }
            } else if (metadata instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metaMap = new HashMap<>((Map<String, Object>) metadata);
                Object toolName = metaMap.get("tool_name");
                if (toolName instanceof String) {
                    metaMap.put("tool_name", toolNameMapper.getChineseName((String) toolName));
                }
                metadata = metaMap;
            }
            item.put("metadata", metadata);
            item.put("parent_id", msg.getParentId());
            item.put("round_index", msg.getRoundIndex());
            item.put("sort_order", msg.getSortOrder());
            item.put("duration_ms", msg.getDurationMs());
            item.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            messages.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("messages", messages);
        result.put("has_more", serviceResult.get("has_more"));
        result.put("total_rounds", serviceResult.get("total_rounds"));
        result.put("loaded_rounds", serviceResult.get("loaded_rounds"));
        return result;
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> sendMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String message = getString(request, "message");
        String selectedOption = getString(request, "selected_option");
        String userInput = getString(request, "user_input");

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }

        if ((message == null || message.isBlank()) && selectedOption == null && userInput == null) {
            return errorResponse("缺少消息内容");
        }

        ReactSession session = reactEngine.runWithSse(sessionId, userId,
                message != null ? message : "", selectedOption, userInput, sseEmitterService);

        return buildSessionResponse(session);
    }

    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSession(
            @PathVariable String sessionId,
            @RequestParam(value = "user_id", required = false) Long userId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        if (userId != null) {
            sseEmitterService.registerSessionAgent(sessionId,
                    agentService.getBySessionId(sessionId) != null
                            ? agentService.getBySessionId(sessionId).getAgentId()
                            : sessionId,
                    userId);
            SseEmitter emitter = sseEmitterService.createGlobalEmitter(String.valueOf(userId));
            log.info("SSE流连接建立(全局模式): sessionId={}, userId={}", sessionId, userId);
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(1_800_000L);
        log.warn("SSE流连接建立(无userId，仅占位): sessionId={}", sessionId);
        return emitter;
    }

    @GetMapping("/sessions/{sessionId}/files")
    public Map<String, Object> getSessionFiles(
            @PathVariable String sessionId,
            @RequestParam(value = "source", required = false) String source) {
        List<AgentFileStorage> files = fileStorageMapper.selectLatestByName(sessionId);

        if (source != null && !source.isBlank()) {
            files = files.stream().filter(f -> source.equals(f.getSource())).toList();
        }

        List<Map<String, Object>> fileList = new ArrayList<>();
        for (AgentFileStorage f : files) {
            Map<String, Object> item = new HashMap<>();
            item.put("file_id", f.getId());
            item.put("file_name", f.getOriginalName());
            item.put("file_type", f.getFileType());
            item.put("file_size", f.getFileSize());
            item.put("source", f.getSource());
            item.put("create_time", f.getCreateTime() != null ? f.getCreateTime().toString() : null);
            try {
                String downloadUrl = fileStorageService.generateDownloadUrl(f.getStorageKey(), 60);
                item.put("download_url", downloadUrl);
            } catch (Exception e) {
                item.put("download_url", null);
            }
            fileList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("files", fileList);
        result.put("total", fileList.size());
        return result;
    }

    private Map<String, Object> buildSessionResponse(ReactSession session) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("session_id", session.getSessionId());
        result.put("status", session.getStatus().name());
        result.put("finished", session.isFinished());

        if (session.isWaitingUserInput()) {
            result.put("waiting_user_input", true);
            result.put("question", session.getPendingQuestion());
            result.put("options", session.getPendingOptions());
        } else {
            result.put("waiting_user_input", false);
        }

        if (session.isFinished()) {
            result.put("final_answer",
                    toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(session.getFinalAnswer())));
        }

        result.put("rounds", session.getCurrentRound());
        result.put("task_list", session.getTaskList());
        return result;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number)
            return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }

    @GetMapping("/agents")
    public Map<String, Object> listAgents(@RequestParam("user_id") Long userId) {
        List<Agent> agents = agentService.getUserAgents(userId);

        List<Map<String, Object>> agentList = new ArrayList<>();
        for (Agent agent : agents) {
            Map<String, Object> item = new HashMap<>();
            item.put("agent_id", agent.getAgentId());
            item.put("agent_name", agent.getAgentName());
            item.put("agent_avatar", agent.getAgentAvatar());
            item.put("capabilities", agent.getCapabilities());
            item.put("session_id", agent.getSessionId());
            item.put("create_time", agent.getCreateTime() != null ? agent.getCreateTime().toString() : null);

            AgentRuntime runtime = agentRuntimeManager.getRuntime(agent.getAgentId());
            item.put("runtime_status", runtime != null ? runtime.getStatus() : Agent.RUNTIME_IDLE);

            int msgCount = chatMessageService.countBySessionAndTypes(agent.getSessionId(),
                    java.util.List.of(AgentChatMessage.TYPE_USER_MESSAGE, AgentChatMessage.TYPE_AI_TEXT));
            item.put("message_count", msgCount);

            AgentSession session = sessionService.getBySessionId(agent.getSessionId());
            if (session != null) {
                item.put("last_message_time",
                        session.getLastMessageTime() != null ? session.getLastMessageTime().toString() : null);
            }

            agentList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("agents", agentList);
        return result;
    }

    @PostMapping("/agents")
    public Map<String, Object> createAgent(@RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String agentName = getString(request, "agent_name");
        String agentAvatar = getString(request, "agent_avatar");
        String systemPrompt = getString(request, "system_prompt");
        String capabilities = getString(request, "capabilities");

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }

        Agent agent = agentService.createAgent(userId, agentName, agentAvatar, systemPrompt, capabilities);

        AgentRuntime runtime = agentRuntimeManager.getOrCreateRuntime(agent.getAgentId(), userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("agent_id", agent.getAgentId());
        result.put("agent_name", agent.getAgentName());
        result.put("agent_avatar", agent.getAgentAvatar());
        result.put("session_id", agent.getSessionId());
        result.put("runtime_status", runtime.getStatus());
        result.put("capabilities", agent.getCapabilities());
        return result;
    }

    @GetMapping("/agents/{agentId}")
    public Map<String, Object> getAgent(@PathVariable String agentId) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("agent_id", agent.getAgentId());
        result.put("agent_name", agent.getAgentName());
        result.put("agent_avatar", agent.getAgentAvatar());
        result.put("capabilities", agent.getCapabilities());
        result.put("session_id", agent.getSessionId());
        result.put("create_time", agent.getCreateTime() != null ? agent.getCreateTime().toString() : null);

        AgentRuntime runtime = agentRuntimeManager.getRuntime(agentId);
        result.put("runtime_status", runtime != null ? runtime.getStatus() : Agent.RUNTIME_IDLE);

        int msgCount = chatMessageService.countBySessionAndTypes(agent.getSessionId(),
                java.util.List.of(AgentChatMessage.TYPE_USER_MESSAGE, AgentChatMessage.TYPE_AI_TEXT));
        result.put("message_count", msgCount);

        AgentSession session = sessionService.getBySessionId(agent.getSessionId());
        if (session != null) {
            result.put("title", session.getTitle());
            result.put("last_message_time",
                    session.getLastMessageTime() != null ? session.getLastMessageTime().toString() : null);
        }

        return result;
    }

    @PutMapping("/agents/{agentId}")
    public Map<String, Object> updateAgent(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> request) {
        String agentName = getString(request, "agent_name");
        if (agentName == null || agentName.isBlank()) {
            return errorResponse("缺少必填参数: agent_name");
        }

        Agent agent = agentService.updateAgentName(agentId, agentName);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("agent_id", agent.getAgentId());
        result.put("agent_name", agent.getAgentName());
        return result;
    }

    @DeleteMapping("/agents/{agentId}")
    public Map<String, Object> deleteAgent(@PathVariable String agentId) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        agentRuntimeManager.destroyRuntime(agentId);
        boolean deleted = agentService.deleteAgent(agentId);
        if (!deleted) {
            return errorResponse("删除Agent失败: " + agentId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/agents/{agentId}/status")
    public Map<String, Object> getAgentStatus(@PathVariable String agentId) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        AgentRuntime runtime = agentRuntimeManager.getRuntime(agentId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("agent_id", agentId);
        result.put("runtime_status", runtime != null ? runtime.getStatus() : Agent.RUNTIME_IDLE);
        return result;
    }

    @PostMapping("/agents/{agentId}/chat")
    public Map<String, Object> agentChat(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> request) {
        Long userId = getLong(request, "user_id");
        String message = getString(request, "message");
        String selectedOption = getString(request, "selected_option");
        String userInput = getString(request, "user_input");

        if (userId == null) {
            return errorResponse("缺少必填参数: user_id");
        }
        boolean hasMessage = message != null && !message.isBlank();
        boolean hasOption = selectedOption != null && !selectedOption.isBlank();
        boolean hasUserInput = userInput != null && !userInput.isBlank();
        if (!hasMessage && !hasOption && !hasUserInput) {
            return errorResponse("缺少消息内容: message/selected_option/user_input至少需要一个");
        }

        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        AgentRuntime runtime = agentRuntimeManager.getOrCreateRuntime(agentId, userId);
        runtime.submitMessage(
                com.example.core.react.agent.AgentMessage.userMessage(message, selectedOption, userInput));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("agent_id", agentId);
        result.put("runtime_status", runtime.getStatus());
        return result;
    }

    @GetMapping("/agents/{agentId}/messages")
    public Map<String, Object> getAgentMessages(
            @PathVariable String agentId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "message_type", required = false) String messageType) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        return getMessages(agent.getSessionId(), page, size, messageType);
    }

    @GetMapping("/agents/{agentId}/messages/by-rounds")
    public Map<String, Object> getAgentMessagesByRounds(
            @PathVariable String agentId,
            @RequestParam(value = "rounds", required = false) Integer rounds,
            @RequestParam(value = "before_sort_order", required = false) Integer beforeSortOrder) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        return getMessagesByRounds(agent.getSessionId(), rounds, beforeSortOrder);
    }

    @PostMapping("/agents/{agentId}/stop")
    public Map<String, Object> stopAgent(@PathVariable String agentId) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        boolean aborted = reactEngine.abortSession(agent.getSessionId());
        Map<String, Object> result = new HashMap<>();
        result.put("success", aborted);
        result.put("message", aborted ? "已发送停止请求" : "Agent未在运行中");
        return result;
    }

    @GetMapping("/agents/{agentId}/files")
    public Map<String, Object> getAgentFiles(
            @PathVariable String agentId,
            @RequestParam(value = "source", required = false) String source) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        return getSessionFiles(agent.getSessionId(), source);
    }

    @DeleteMapping("/files/{fileId}")
    public Map<String, Object> deleteFile(
            @PathVariable Long fileId,
            @RequestParam("user_id") Long userId,
            @RequestParam(value = "agent_id", required = false) String agentId) {
        try {
            AgentFileStorage file = fileStorageMapper.selectById(fileId);
            if (file == null || file.getDeleted() == 1) {
                return errorResponse("文件不存在: " + fileId);
            }

            List<Long> allFileIds = new ArrayList<>();
            collectAllVersionIds(fileId, allFileIds);

            String fileName = file.getOriginalName();
            int deletedMinioCount = 0;
            int deletedDbCount = 0;

            for (Long id : allFileIds) {
                AgentFileStorage f = fileStorageMapper.selectById(id);
                if (f == null)
                    continue;

                try {
                    fileStorageService.delete(f.getStorageKey());
                    deletedMinioCount++;
                } catch (Exception e) {
                    log.warn("删除Minio文件失败(继续删除DB): key={}, error={}", f.getStorageKey(), e.getMessage());
                }

                fileStorageMapper.physicalDeleteById(id);
                deletedDbCount++;
            }

            String sessionId = file.getSessionId();
            if (agentId != null && !agentId.isBlank()) {
                Agent agent = agentService.getByAgentId(agentId);
                if (agent != null) {
                    sessionId = agent.getSessionId();
                }
            }

            if (sessionId != null) {
                String eventContent = "用户删除了文件: " + fileName + "（含" + allFileIds.size() + "个版本）";
                chatMessageService.saveUserEvent(sessionId, userId, eventContent,
                        Map.of("event_type", "FILE_DELETED", "file_name", fileName, "version_count",
                                allFileIds.size()));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "文件已删除: " + fileName);
            result.put("deleted_versions", allFileIds.size());
            result.put("deleted_minio", deletedMinioCount);
            result.put("deleted_db", deletedDbCount);
            return result;
        } catch (Exception e) {
            log.error("删除文件失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return errorResponse("删除文件失败: " + e.getMessage());
        }
    }

    private void collectAllVersionIds(Long fileId, List<Long> collected) {
        if (collected.contains(fileId))
            return;
        AgentFileStorage file = fileStorageMapper.selectById(fileId);
        if (file == null)
            return;

        collected.add(fileId);

        if (file.getOriginalFileId() != null) {
            collectAllVersionIds(file.getOriginalFileId(), collected);
        }

        List<AgentFileStorage> childVersions = fileStorageMapper.selectByOriginalFileId(String.valueOf(fileId));
        for (AgentFileStorage child : childVersions) {
            collectAllVersionIds(child.getId(), collected);
        }
    }

    @PostMapping("/agents/{agentId}/upload")
    public Map<String, Object> agentUploadFile(
            @PathVariable String agentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("user_id") Long userId) {
        Agent agent = agentService.getByAgentId(agentId);
        if (agent == null) {
            return errorResponse("Agent不存在: " + agentId);
        }

        return uploadFile(file, userId, agent.getSessionId());
    }

    @GetMapping(value = "/users/{userId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter globalStream(@PathVariable Long userId,
            @RequestParam(value = "client_id", required = false) String clientId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = sseEmitterService.createGlobalEmitter(String.valueOf(userId), clientId);
        log.info("全局SSE流连接建立: userId={}, clientId={}", userId, clientId);
        return emitter;
    }

    @GetMapping("/agents/{agentId}/scheduled-tasks")
    public Map<String, Object> getScheduledTasks(@PathVariable String agentId) {
        try {
            List<com.example.core.entity.AgentScheduledTask> tasks = scheduledTaskService
                    .getActiveTasksByAgentId(agentId);
            List<Map<String, Object>> taskList = new ArrayList<>();
            for (com.example.core.entity.AgentScheduledTask task : tasks) {
                Map<String, Object> item = new HashMap<>();
                item.put("task_id", task.getTaskId());
                item.put("task_name", task.getTaskName());
                item.put("task_description", task.getTaskDescription());
                item.put("repeat_type", task.getRepeatType());
                item.put("repeat_rule", task.getRepeatRule());
                item.put("task_input", task.getTaskInput());
                item.put("status", task.getStatus());
                item.put("next_execute_time", task.getNextExecuteTime() != null
                        ? task.getNextExecuteTime().toString()
                        : null);
                item.put("last_execute_time", task.getLastExecuteTime() != null
                        ? task.getLastExecuteTime().toString()
                        : null);
                item.put("execute_count", task.getExecuteCount());
                item.put("create_time", task.getCreateTime() != null
                        ? task.getCreateTime().toString()
                        : null);
                taskList.add(item);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("tasks", taskList);
            result.put("total", taskList.size());
            return result;
        } catch (Exception e) {
            log.error("查询定时任务失败: agentId={}, error={}", agentId, e.getMessage(), e);
            return errorResponse("查询定时任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/agents/{agentId}/scheduled-tasks/{taskId}")
    public Map<String, Object> getScheduledTask(@PathVariable String agentId, @PathVariable String taskId) {
        try {
            com.example.core.entity.AgentScheduledTask task = scheduledTaskService.getByTaskId(taskId);
            if (task == null) {
                return errorResponse("定时任务不存在");
            }
            Map<String, Object> item = new HashMap<>();
            item.put("task_id", task.getTaskId());
            item.put("task_name", task.getTaskName());
            item.put("task_description", task.getTaskDescription());
            item.put("repeat_type", task.getRepeatType());
            item.put("repeat_rule", task.getRepeatRule());
            item.put("task_input", task.getTaskInput());
            item.put("status", task.getStatus());
            item.put("next_execute_time", task.getNextExecuteTime() != null
                    ? task.getNextExecuteTime().toString()
                    : null);
            item.put("last_execute_time", task.getLastExecuteTime() != null
                    ? task.getLastExecuteTime().toString()
                    : null);
            item.put("execute_count", task.getExecuteCount());
            item.put("create_time", task.getCreateTime() != null
                    ? task.getCreateTime().toString()
                    : null);
            item.put("update_time", task.getUpdateTime() != null
                    ? task.getUpdateTime().toString()
                    : null);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("task", item);
            return result;
        } catch (Exception e) {
            log.error("查询定时任务详情失败: taskId={}, error={}", taskId, e.getMessage(), e);
            return errorResponse("查询定时任务详情失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/agents/{agentId}/scheduled-tasks/{taskId}")
    public Map<String, Object> deleteScheduledTask(
            @PathVariable String agentId,
            @PathVariable String taskId,
            @RequestParam("user_id") Long userId) {
        try {
            com.example.core.entity.AgentScheduledTask task = scheduledTaskService.getByTaskId(taskId);
            if (task == null) {
                return errorResponse("定时任务不存在: " + taskId);
            }

            boolean success = scheduledTaskService.cancelTask(taskId, userId);
            if (!success) {
                return errorResponse("取消定时任务失败，任务可能已取消或已完成");
            }

            sseEmitterService.emitScheduledTaskCancelled(task.getSessionId(), taskId);

            try {
                Agent agent = agentService.getByAgentId(agentId);
                if (agent != null && agent.getSessionId() != null) {
                    String eventContent = "用户取消了定时任务: " + task.getTaskName();
                    chatMessageService.saveUserEvent(agent.getSessionId(), userId, eventContent,
                            Map.of("event_type", "SCHEDULED_TASK_CANCELLED", "task_name", task.getTaskName()));
                }
            } catch (Exception e) {
                log.warn("记录定时任务取消事件失败: {}", e.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "定时任务已取消");
            return result;
        } catch (Exception e) {
            log.error("取消定时任务失败: agentId={}, taskId={}, error={}", agentId, taskId, e.getMessage(), e);
            return errorResponse("取消定时任务失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/agents/workflow-executions/{executionId}")
    public Map<String, Object> deleteWorkflowExecution(
            @PathVariable String executionId,
            @RequestParam("user_id") Long userId,
            @RequestParam(value = "agent_id", required = false) String agentId) {
        try {
            com.example.core.entity.AgentWorkflowExecution execution = workflowExecutionService
                    .getByExecutionId(executionId);
            String execName = execution != null ? execution.getWorkflowId() : executionId;

            boolean success = workflowExecutionService.deleteExecution(executionId, userId);
            if (!success) {
                return errorResponse("删除工作流执行记录失败，记录不存在或正在运行中");
            }

            if (agentId != null && !agentId.isBlank()) {
                try {
                    Agent agent = agentService.getByAgentId(agentId);
                    if (agent != null && agent.getSessionId() != null) {
                        String eventContent = "用户删除了会话任务: " + execName;
                        chatMessageService.saveUserEvent(agent.getSessionId(), userId, eventContent,
                                Map.of("event_type", "SESSION_TASK_DELETED", "execution_name", execName));
                    }
                } catch (Exception e) {
                    log.warn("记录会话任务删除事件失败: {}", e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "工作流执行记录已删除");
            return result;
        } catch (Exception e) {
            log.error("删除工作流执行记录失败: executionId={}, error={}", executionId, e.getMessage(), e);
            return errorResponse("删除工作流执行记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/agents/{agentId}/workflow-executions/recent")
    public Map<String, Object> getRecentWorkflowExecutions(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<com.example.core.entity.AgentWorkflowExecution> executions = workflowExecutionService
                    .getRecentExecutionsByAgentId(agentId, limit);
            List<Map<String, Object>> executionList = new ArrayList<>();
            for (com.example.core.entity.AgentWorkflowExecution ex : executions) {
                Map<String, Object> item = new HashMap<>();
                item.put("execution_id", ex.getExecutionId());
                item.put("workflow_id", ex.getWorkflowId());
                String workflowName = ex.getWorkflowId();
                try {
                    com.example.core.entity.AgentWorkflow wf = workflowService.getWorkflow(1L,
                            ex.getWorkflowId());
                    if (wf != null) {
                        workflowName = wf.getName();
                    }
                } catch (Exception ignored) {
                }
                item.put("workflow_name", workflowName);
                item.put("status", ex.getStatus());
                item.put("current_node_id", ex.getCurrentNodeId());
                item.put("completed_nodes", ex.getCompletedNodes());
                item.put("total_nodes", ex.getTotalNodes());
                item.put("start_time", ex.getStartTime() != null
                        ? ex.getStartTime().toString()
                        : null);
                item.put("end_time", ex.getEndTime() != null
                        ? ex.getEndTime().toString()
                        : null);
                item.put("execution_result", ex.getExecutionResult());
                item.put("error_message", ex.getErrorMessage());
                executionList.add(item);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("executions", executionList);
            return result;
        } catch (Exception e) {
            log.error("查询工作流执行记录失败: agentId={}, error={}", agentId, e.getMessage(), e);
            return errorResponse("查询工作流执行记录失败: " + e.getMessage());
        }
    }
}
