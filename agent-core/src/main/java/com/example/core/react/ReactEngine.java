package com.example.core.react;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.example.core.entity.AgentChatMessage;
import com.example.core.entity.AgentFileStorage;
import com.example.core.entity.AgentOperationDetail;
import com.example.core.entity.AgentTaskList;
import com.example.core.entity.AgentWorkflowExecution;
import com.example.core.entity.AgentWorkflowNode;
import com.example.core.mapper.AgentFileStorageMapper;
import com.example.core.mapper.AgentOperationDetailMapper;
import com.example.core.mapper.AgentTaskListMapper;
import com.example.core.mcp.McpToolRegistry;
import com.example.core.mcp.McpToolResult;
import com.example.core.memory.MemoryLayer;
import com.example.core.memory.config.MemoryProperties;
import com.example.core.memory.model.AssembledContext;
import com.example.core.react.model.LlmResponse;
import com.example.core.react.model.ReactSession;
import com.example.core.react.model.ReactStep;
import com.example.core.react.model.TaskItem;
import com.example.core.react.prompt.ReactPromptBuilder;
import com.example.core.service.AgentService;
import com.example.core.service.ChatMessageService;
import com.example.core.service.DeepseekService;
import com.example.core.service.SessionService;
import com.example.core.service.SessionTitleService;
import com.example.core.service.SseEmitterService;
import com.example.core.system.SystemToolRegistry;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactEngine {

    private final DeepseekService deepseekService;
    private final McpToolRegistry toolRegistry;
    private final SystemToolRegistry systemToolRegistry;
    private final MemoryLayer memoryLayer;
    private final MemoryProperties memoryProperties;
    private final ChatMessageService chatMessageService;
    private final SessionService sessionService;
    private final SessionTitleService sessionTitleService;
    private final com.example.core.mapper.AgentSessionMapper agentSessionMapper;
    private final AgentFileStorageMapper fileStorageMapper;
    private final AgentOperationDetailMapper operationDetailMapper;
    private final AgentTaskListMapper taskListMapper;
    private final ToolNameMapper toolNameMapper;
    private final com.example.core.service.KnowledgeBaseService knowledgeBaseService;
    private final com.example.core.service.WorkflowService workflowService;
    private final com.example.core.service.WorkflowExecutionService workflowExecutionService;
    private final SseEmitterService sseEmitterService;
    private final AgentService agentService;
    private final com.example.core.service.ScheduledTaskService scheduledTaskService;

    private final Map<String, ReactSession> activeSessions = new ConcurrentHashMap<>();

    public ReactSession createSession(String sessionId, Long userId, String userMessage) {
        ReactSession session = new ReactSession(sessionId, userId, userMessage);
        session.setMaxRounds(memoryProperties.getReact().getDefaultMaxRounds());
        session.setContinueAddRounds(memoryProperties.getReact().getContinueAddRounds());
        activeSessions.put(sessionId, session);

        com.example.core.entity.Agent agent = agentService.getBySessionId(sessionId);
        if (agent != null) {
            sseEmitterService.registerSessionAgent(sessionId, agent.getAgentId(), userId);
        }

        log.info("ReAct会话创建: sessionId={}, userId={}", sessionId, userId);
        return session;
    }

    public ReactSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

    public boolean abortSession(String sessionId) {
        ReactSession session = activeSessions.get(sessionId);
        if (session == null || session.isFinished()) {
            return false;
        }
        session.abort();
        log.info("请求中断ReAct会话: sessionId={}", sessionId);
        return true;
    }

    public ReactSession run(String sessionId, Long userId, String userMessage) {
        return run(sessionId, userId, userMessage, null, null);
    }

    public ReactSession run(String sessionId, Long userId, String userMessage,
            String selectedOption, String userInput) {
        return runInternal(sessionId, userId, userMessage, selectedOption, userInput, null);
    }

    public ReactSession runWithSse(String sessionId, Long userId, String userMessage,
            String selectedOption, String userInput, SseEmitterService sseService) {
        return runInternal(sessionId, userId, userMessage, selectedOption, userInput, sseService);
    }

    public ReactSession runFromScheduledTask(String sessionId, Long userId, String taskContent,
            SseEmitterService sseService) {
        ReactSession session = activeSessions.get(sessionId);

        if (session != null && !session.isFinished() && !session.isWaitingUserInput()) {
            log.info("定时任务跳过: Agent正在执行任务, sessionId={}", sessionId);
            return session;
        }

        if (session != null && session.isFinished()) {
            activeSessions.remove(sessionId);
        }

        session = createSession(sessionId, userId, taskContent);
        session.setTriggerSource(com.example.core.react.agent.TriggerSource.SCHEDULED_TASK);

        AgentTaskList persistedTaskList = loadActiveTaskList(sessionId);
        if (persistedTaskList != null) {
            restoreTaskListToSession(session, persistedTaskList);
        }

        AgentChatMessage proactiveMsg = chatMessageService.saveAgentProactiveMessage(
                sessionId, userId, "[定时任务] " + taskContent, session.getCurrentRound());
        pushSseMessage(sseService, sessionId, proactiveMsg);

        ensureSessionRecord(sessionId, userId, taskContent);

        memoryLayer.awaitIncrementalSummary(sessionId);

        return executeReActLoop(session, sseService);
    }

    private ReactSession runInternal(String sessionId, Long userId, String userMessage,
            String selectedOption, String userInput, SseEmitterService sseService) {
        ReactSession session = activeSessions.get(sessionId);

        if (session != null && session.isWaitingUserInput()) {
            log.info("ReAct会话恢复: sessionId={}, 用户输入: selectedOption={}, userInput={}",
                    sessionId, selectedOption, userInput);
            session.applyUserInput(userInput, selectedOption);

            chatMessageService.saveUserInput(sessionId, userId,
                    selectedOption, userInput, session.getCurrentRound());
        } else {
            if (session != null && session.isFinished()) {
                log.info("ReAct会话已结束，开启新一轮对话: sessionId={}", sessionId);
                activeSessions.remove(sessionId);
            }
            session = createSession(sessionId, userId, userMessage);

            AgentTaskList persistedTaskList = loadActiveTaskList(sessionId);
            if (persistedTaskList != null) {
                restoreTaskListToSession(session, persistedTaskList);
                log.info("恢复持久化任务列表: sessionId={}, 任务数={}", sessionId, session.getTaskList().size());
            }

            AgentChatMessage userMsg = chatMessageService.saveUserMessage(sessionId, userId, userMessage);
            pushSseMessage(sseService, sessionId, userMsg);

            ensureSessionRecord(sessionId, userId, userMessage);

            triggerTitleGeneration(sessionId, userId, userMessage);
        }

        memoryLayer.awaitIncrementalSummary(sessionId);

        return executeReActLoop(session, sseService);
    }

    private ReactSession executeReActLoop(ReactSession session, SseEmitterService sseService) {
        String sessionId = session.getSessionId();
        Long userId = session.getUserId();

        try {
            while (!session.isFinished() && !session.isWaitingUserInput() && !session.isAborted()) {
                long roundStart = System.currentTimeMillis();

                ReactStep step;
                if (sseService != null) {
                    step = executeOneRoundWithSse(session, sseService);
                } else {
                    step = executeOneRound(session);
                }

                int durationMs = (int) (System.currentTimeMillis() - roundStart);
                log.debug("ReAct round={}, action={}, decision={}", session.getCurrentRound(), step.getAction(),
                        step.getDecision());
                session.addStep(step);
                session.incrementRound();

                handlePostStepMessages(session, step, durationMs, sseService);

                int msgCount = chatMessageService.countBySessionAndTypes(sessionId,
                        java.util.List.of(AgentChatMessage.TYPE_USER_MESSAGE, AgentChatMessage.TYPE_AI_TEXT));
                sessionService.updateMessageCount(sessionId, msgCount);
                if (sseService != null) {
                    sseService.emitMessageCountUpdate(sessionId, msgCount);
                }

                if (step.isFinished()) {
                    completeAllPendingTasks(session);
                    session.setFinished(true);
                    if (session.getFinalAnswer() == null) {
                        session.setFinalAnswer(step.getDecision());
                    }
                    handleFinalAnswer(session, sseService, durationMs);
                    session.setFinalAnswerHandled(true);
                }

                boolean hasToolAction = step.getAction() != null && !step.getAction().isBlank();
                if (hasToolAction) {
                    session.setConsecutiveEmptyRounds(0);
                } else {
                    session.setConsecutiveEmptyRounds(session.getConsecutiveEmptyRounds() + 1);
                }

                if (session.getCurrentRound() >= session.getMaxRounds()) {
                    log.warn("ReAct达到最大轮数限制: sessionId={}, maxRounds={}", sessionId, session.getMaxRounds());

                    if (hasUnfinishedTasks(session)) {
                        persistTaskList(session);
                        session.setWaitingUserInput(
                                "已达到最大轮数限制，仍有未完成的任务。是否继续执行？",
                                java.util.List.of("继续执行", "结束并查看当前结果"));
                        Map<String, Object> maxRoundsMeta = new java.util.HashMap<>();
                        maxRoundsMeta.put("event_type", "MAX_ROUNDS_EXCEEDED");
                        maxRoundsMeta.put("round_index", session.getCurrentRound());
                        maxRoundsMeta.put("has_unfinished_tasks", true);
                        AgentChatMessage maxRoundsMsg = chatMessageService.saveUserEvent(sessionId, userId,
                                "达到最大轮数限制，仍有未完成任务", maxRoundsMeta);
                        pushSseMessage(sseService, sessionId, maxRoundsMsg);
                        if (sseService != null) {
                            sseService.emitTaskListIncomplete(sessionId, buildTaskListPayload(session));
                        }
                    } else {
                        completeAllPendingTasks(session);
                        session.setFinished(true);
                        if (session.getFinalAnswer() == null) {
                            session.setFinalAnswer("所有任务已完成。");
                        }
                        completeActiveWorkflowExecution(session);
                        handleFinalAnswer(session, sseService, durationMs);
                        session.setFinalAnswerHandled(true);
                    }
                }
            }

            if (session.isAborted()) {
                log.info("ReAct被用户中断: sessionId={}, round={}", sessionId, session.getCurrentRound());
                session.setFinished(true);
                failActiveWorkflowExecution(session, "用户主动停止");
                persistTaskList(session);
                if (session.getFinalAnswer() == null) {
                    session.setFinalAnswer("已停止处理");
                }
                Map<String, Object> abortMeta = new java.util.HashMap<>();
                abortMeta.put("event_type", "USER_ABORTED");
                abortMeta.put("round_index", session.getCurrentRound());
                AgentChatMessage abortMsg = chatMessageService.saveUserEvent(sessionId, userId,
                        "用户主动停止", abortMeta);
                pushSseMessage(sseService, sessionId, abortMsg);
            }
        } catch (

        Exception e) {
            log.error("ReAct执行异常: sessionId={}, error={}", sessionId, e.getMessage(), e);
            session.setFinished(true);
            failActiveWorkflowExecution(session, "执行异常: " + e.getMessage());
            session.setFinalAnswer("处理过程中出现异常: " + e.getMessage());
            Map<String, Object> errorMeta = new java.util.HashMap<>();
            errorMeta.put("event_type", "RUNTIME_ERROR");
            errorMeta.put("error_message", e.getMessage());
            errorMeta.put("round_index", session.getCurrentRound());
            AgentChatMessage errorMsg = chatMessageService.saveUserEvent(sessionId, userId,
                    "处理异常: " + e.getMessage(), errorMeta);
            pushSseMessage(sseService, sessionId, errorMsg);
        }

        if (session.isFinished()) {
            sessionService.updateSessionStatus(sessionId, "FINISHED");

            completeTaskListIfNeeded(session);

            if (session.getExecutingWorkflowId() != null) {
                completeActiveWorkflowExecution(session);
            }

            if (!session.isFinalAnswerHandled() && session.getFinalAnswer() != null
                    && !session.getFinalAnswer().isBlank()) {
                try {
                    handleFinalAnswer(session, sseService, 0);
                } catch (Exception e) {
                    log.warn("处理最终答案失败(非致命): {}", e.getMessage());
                    chatMessageService.saveAiText(sessionId, userId,
                            session.getFinalAnswer(), session.getCurrentRound(), 0, "deepseek-chat");
                }
            }

            if (sseService != null) {
                sseService.emitSessionStatus(sessionId, "FINISHED", session.getFinalAnswer());
                sseService.completeEmitter(sessionId);
            }
        }

        if (session.isWaitingUserInput() && sseService != null) {
            sseService.emitWaitingUserInput(sessionId,
                    session.getPendingQuestion(), session.getPendingOptions());
            sseService.emitSessionStatus(sessionId, "WAITING_USER_INPUT", null);
        }

        if (session.isFinished() || session.isWaitingUserInput()) {
            memoryLayer.triggerIncrementalSummary(sessionId, userId);
        }

        sessionService.updateLastMessageTime(sessionId);

        return session;
    }

    private void ensureSessionRecord(String sessionId, Long userId, String userMessage) {
        try {
            com.example.core.entity.AgentSession existing = sessionService.getBySessionId(sessionId);
            if (existing == null) {
                String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
                com.example.core.entity.AgentSession newSession = new com.example.core.entity.AgentSession();
                newSession.setSessionId(sessionId);
                newSession.setUserId(userId);
                newSession.setTitle(title);
                newSession.setStatus("RUNNING");
                newSession.setMessageCount(0);
                newSession.setLastMessageTime(java.time.LocalDateTime.now());
                agentSessionMapper.insert(newSession);
                log.info("创建会话DB记录: sessionId={}", sessionId);
            } else {
                sessionService.updateSessionStatus(sessionId, "RUNNING");
            }
        } catch (Exception e) {
            log.warn("确保会话记录失败(非致命): {}", e.getMessage());
        }
    }

    private void handlePostStepMessages(ReactSession session, ReactStep step, int durationMs,
            SseEmitterService sseService) {
        String sessionId = session.getSessionId();
        Long userId = session.getUserId();
        int round = session.getCurrentRound();
        boolean isSse = sseService != null;

        try {
            if (!isSse && step.getThought() != null && !step.getThought().isBlank()) {
                AgentChatMessage thoughtMsg = chatMessageService.saveAiThought(sessionId, userId, step.getThought(),
                        round);
                pushSseMessage(sseService, sessionId, thoughtMsg);

                String briefContent = step.getBriefThought() != null ? step.getBriefThought()
                        : toolNameMapper.replaceToolNames(step.getThought());
                AgentChatMessage briefMsg = chatMessageService.saveAiThoughtBrief(sessionId, userId, briefContent,
                        round);
                pushSseMessage(sseService, sessionId, briefMsg);
            }

            if ("WAITING_USER_INPUT".equals(step.getDecision())) {
                AgentChatMessage askMsg = chatMessageService.saveAskUser(sessionId, userId,
                        session.getPendingQuestion(), session.getPendingOptions(), round);
                pushSseMessage(sseService, sessionId, askMsg);
            }

            List<AgentChatMessage> fileMsgs = new ArrayList<>();
            checkFileGenerated(session, step, fileMsgs);
            for (AgentChatMessage msg : fileMsgs) {
                pushSseMessage(sseService, sessionId, msg);
            }

            List<AgentChatMessage> deleteMsgs = new ArrayList<>();
            checkFileDeleted(session, step, deleteMsgs);
            for (AgentChatMessage msg : deleteMsgs) {
                pushSseMessage(sseService, sessionId, msg);
            }
        } catch (Exception e) {
            log.warn("处理后续消息失败(非致命): {}", e.getMessage());
        }
    }

    private void checkFileGenerated(ReactSession session, ReactStep step, List<AgentChatMessage> result) {
        checkSingleFileGenerated(session, step.getAction(), step.getObservation(), result);
    }

    private void checkSingleFileGenerated(ReactSession session, String actionName, String obs,
            List<AgentChatMessage> result) {
        if (obs == null || actionName == null) {
            return;
        }
        if (!"word".equals(actionName) && !"excel".equals(actionName)) {
            return;
        }
        if (!obs.contains("file_key")) {
            return;
        }
        try {
            com.alibaba.fastjson2.JSONObject obsJson = JSON.parseObject(obs);
            if (obsJson == null || !obsJson.containsKey("data")) {
                return;
            }
            Object dataObj = obsJson.get("data");
            com.alibaba.fastjson2.JSONObject data = dataObj instanceof com.alibaba.fastjson2.JSONObject
                    ? (com.alibaba.fastjson2.JSONObject) dataObj
                    : null;
            if (data == null || !data.containsKey("file_key")) {
                return;
            }

            String fileType = data.getString("file_type");
            if (fileType == null || fileType.isBlank()) {
                String fn = data.getString("file_name");
                if (fn != null) {
                    int dotIdx = fn.lastIndexOf('.');
                    fileType = dotIdx >= 0 ? fn.substring(dotIdx + 1) : "unknown";
                } else {
                    fileType = "unknown";
                }
            }
            String fileName = data.getString("file_name") != null ? data.getString("file_name") : actionName;

            boolean isModify = data.getBooleanValue("is_modify");
            String originalFileKey = data.getString("original_file_key");

            AgentFileStorage oldFileForModify = null;
            boolean isTemplateDerived = data.getBooleanValue("is_template_derived");
            if (isModify && originalFileKey != null) {
                if (!isTemplateDerived) {
                    markOldFileAsSuperseded(session.getSessionId(), originalFileKey);
                    oldFileForModify = findFileByStorageKey(originalFileKey);
                    if (oldFileForModify != null && oldFileForModify.getOriginalName() != null
                            && !oldFileForModify.getOriginalName().isBlank()) {
                        fileName = oldFileForModify.getOriginalName();
                    }
                }
            }

            AgentFileStorage fileStorage = new AgentFileStorage();
            fileStorage.setSessionId(session.getSessionId());
            fileStorage.setUserId(session.getUserId());
            fileStorage.setStorageKey(data.getString("file_key"));
            fileStorage.setFileName(fileName);
            fileStorage.setOriginalName(fileName);
            fileStorage.setFileType(fileType);
            fileStorage.setFileSize(data.getLong("file_size") != null ? data.getLong("file_size") : 0L);
            fileStorage.setSource("generated");
            fileStorage.setIsOriginal(1);
            fileStorage.setOriginalFileId(null);
            fileStorage.setDeleted(0);

            if (oldFileForModify != null && !isTemplateDerived) {
                fileStorage.setOriginalFileId(oldFileForModify.getId());
            }

            fileStorageMapper.insert(fileStorage);

            markSameNameFilesAsSuperseded(session.getSessionId(), fileName, fileStorage.getId());

            AgentChatMessage fileMsg = chatMessageService.saveFileGenerated(
                    session.getSessionId(), session.getUserId(),
                    fileStorage.getId(),
                    data.getString("file_key"),
                    fileName,
                    fileType,
                    "generated");
            result.add(fileMsg);

            log.info("文件生成记录已入库: sessionId={}, fileName={}, fileId={}, fileKey={}",
                    session.getSessionId(), fileName, fileStorage.getId(), data.getString("file_key"));
        } catch (Exception e) {
            log.warn("检查文件生成记录失败(非致命): action={}, error={}", actionName, e.getMessage());
        }
    }

    private void checkFileDeleted(ReactSession session, ReactStep step, List<AgentChatMessage> result) {
        checkSingleFileDeleted(session, step.getAction(), step.getObservation(), result);
    }

    private void checkSingleFileDeleted(ReactSession session, String actionName, String obs,
            List<AgentChatMessage> result) {
        if (obs == null || actionName == null || !actionName.equals("delete_file")) {
            return;
        }
        try {
            com.alibaba.fastjson2.JSONObject obsJson = JSON.parseObject(obs);
            if (obsJson == null || !obsJson.containsKey("data")) {
                return;
            }
            Object dataObj = obsJson.get("data");
            com.alibaba.fastjson2.JSONObject data = dataObj instanceof com.alibaba.fastjson2.JSONObject
                    ? (com.alibaba.fastjson2.JSONObject) dataObj
                    : null;
            if (data == null) {
                return;
            }

            Object deletedFilesObj = data.get("deleted_files");
            if (deletedFilesObj == null) {
                return;
            }

            List<Map<String, Object>> deletedFiles = new ArrayList<>();
            if (deletedFilesObj instanceof List) {
                for (Object item : (List<?>) deletedFilesObj) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fileMap = new java.util.HashMap<>((Map<String, Object>) item);
                        deletedFiles.add(fileMap);
                    }
                }
            }

            if (deletedFiles.isEmpty()) {
                return;
            }

            AgentChatMessage deleteMsg = chatMessageService.saveFileDeleted(
                    session.getSessionId(), session.getUserId(), deletedFiles);
            result.add(deleteMsg);

            log.info("文件删除事件已记录: sessionId={}, deletedCount={}", session.getSessionId(), deletedFiles.size());
        } catch (Exception e) {
            log.warn("检查文件删除记录失败(非致命): action={}, error={}", actionName, e.getMessage());
        }
    }

    private void triggerTitleGeneration(String sessionId, Long userId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                sessionTitleService.generateAndApplyTitle(sessionId, userId, userMessage);
            } catch (Exception e) {
                log.warn("异步生成标题失败(非致命): sessionId={}, error={}", sessionId, e.getMessage());
            }
        }, "title-gen-" + sessionId);
        thread.setDaemon(true);
        thread.start();
    }

    private AssembledContext buildLlmContext(ReactSession session) {
        Map<String, String> knowledgeBaseMap = null;
        try {
            knowledgeBaseMap = knowledgeBaseService.getKnowledgeBaseMap(session.getUserId());
        } catch (Exception e) {
            log.warn("获取知识库Map失败(非致命): userId={}, error={}", session.getUserId(), e.getMessage());
        }
        Map<String, String> workflowMap = null;
        try {
            workflowMap = workflowService.getWorkflowMap(session.getUserId());
        } catch (Exception e) {
            log.warn("获取工作流Map失败(非致命): userId={}, error={}", session.getUserId(), e.getMessage());
        }

        String systemPrompt = ReactPromptBuilder.buildSystemPrompt(toolRegistry, systemToolRegistry, knowledgeBaseMap,
                workflowMap);
        String userPrompt = ReactPromptBuilder.buildUserPrompt(session);
        return memoryLayer.buildContext(
                session.getSessionId(), userPrompt, systemPrompt, session.getUserId());
    }

    private ReactStep buildStepFromResponse(LlmResponse llmResponse, ReactSession session) {
        ReactStep step = new ReactStep();
        step.setThought(llmResponse.getThought());
        step.setBriefThought(llmResponse.getBriefThought());
        step.setAction(llmResponse.getAction());
        step.setActionInput(llmResponse.getActionInput());
        step.setDecision(llmResponse.getDecision());

        if (llmResponse.getTaskList() != null && !llmResponse.getTaskList().isEmpty()) {
            updateTaskList(session, llmResponse.getTaskList());
        }

        return step;
    }

    private void executeToolActions(ReactStep step, ReactSession session,
            Consumer<AgentChatMessage> ssePush) {
        String actionName = step.getAction();
        Map<String, Object> actionInput = parseActionInput(step.getActionInput());
        String sessionId = session.getSessionId();
        Long userId = session.getUserId();
        int round = session.getCurrentRound();

        if (actionName == null || actionName.isBlank()) {
            step.setObservation("无工具调用");
            return;
        }

        boolean isSystemTool = systemToolRegistry.hasTool(actionName);

        if (isSystemTool) {
            if ("execute_workflow".equals(actionName) && session.getExecutingWorkflowId() == null) {
                String wfId = actionInput.get("workflow_id") != null
                        ? actionInput.get("workflow_id").toString()
                        : null;
                if (wfId != null) {
                    session.setExecutingWorkflowId(wfId);
                    int taskNodeCount = 0;
                    try {
                        List<AgentWorkflowNode> wfNodes = workflowService.getWorkflowNodes(wfId);
                        Map<String, String> nodeMap = new java.util.HashMap<>();
                        for (AgentWorkflowNode n : wfNodes) {
                            if (n.getToolName() != null && !n.getToolName().isBlank()) {
                                nodeMap.put(n.getToolName(), n.getNodeId());
                            }
                            if (AgentWorkflowNode.TYPE_START.equals(n.getNodeType())) {
                                nodeMap.put("_start", n.getNodeId());
                            }
                            if (AgentWorkflowNode.TYPE_END.equals(n.getNodeType())) {
                                nodeMap.put("_end", n.getNodeId());
                            }
                            if (AgentWorkflowNode.TYPE_TASK.equals(n.getNodeType())) {
                                taskNodeCount++;
                            }
                        }
                        session.setWorkflowNodeMap(nodeMap);
                        try {
                            var execution = workflowExecutionService.startExecution(
                                    wfId, userId, sessionId, taskNodeCount);
                            session.setExecutingWorkflowExecutionId(execution.getExecutionId());
                            session.setExecutingWorkflowCompletedNodes(0);
                        } catch (Exception e) {
                            log.error("创建工作流执行记录失败(非致命): workflowId={}, sessionId={}, error={}",
                                    wfId, sessionId, e.getMessage(), e);
                        }
                    } catch (Exception e) {
                        log.warn("获取工作流节点映射失败: {}", e.getMessage());
                    }
                    sseEmitterService.emitWorkflowExecutionStart(sessionId, wfId,
                            session.getExecutingWorkflowExecutionId(), taskNodeCount);
                    String startNodeId = session.getWorkflowNodeMap().get("_start");
                    if (startNodeId != null) {
                        sseEmitterService.emitWorkflowNodeStatus(sessionId, wfId, startNodeId, "completed",
                                session.getExecutingWorkflowExecutionId(), 0);
                    }
                }
            }

            String currentNodeId = null;
            if (session.getExecutingWorkflowId() != null && !"execute_workflow".equals(actionName)
                    && !"create_workflow".equals(actionName) && !"delete_workflow".equals(actionName)) {
                currentNodeId = session.getWorkflowNodeMap().get(actionName);
                if (currentNodeId != null) {
                    session.setExecutingWorkflowNodeId(currentNodeId);
                    sseEmitterService.emitWorkflowNodeStatus(sessionId, session.getExecutingWorkflowId(),
                            currentNodeId, "running", session.getExecutingWorkflowExecutionId(),
                            session.getExecutingWorkflowCompletedNodes());
                }
            }

            AgentChatMessage actionMsg = chatMessageService.saveSystemAction(
                    sessionId, userId, actionName, actionInput, round);
            ssePush.accept(actionMsg);

            SystemToolResult toolResult = executeSystemActionByName(session, actionName, actionInput);

            if (currentNodeId != null) {
                String nodeStatus = toolResult.isSuccess() ? "completed" : "failed";
                int newCompletedNodes = session.getExecutingWorkflowCompletedNodes();
                if (toolResult.isSuccess()) {
                    newCompletedNodes++;
                }
                sseEmitterService.emitWorkflowNodeStatus(sessionId, session.getExecutingWorkflowId(),
                        currentNodeId, nodeStatus, session.getExecutingWorkflowExecutionId(),
                        newCompletedNodes);
                session.setExecutingWorkflowNodeId(null);
                if (toolResult.isSuccess() && session.getExecutingWorkflowExecutionId() != null) {
                    session.setExecutingWorkflowCompletedNodes(
                            session.getExecutingWorkflowCompletedNodes() + 1);
                    try {
                        workflowExecutionService.updateNodeProgress(
                                session.getExecutingWorkflowExecutionId(),
                                currentNodeId,
                                session.getExecutingWorkflowCompletedNodes());
                    } catch (Exception e) {
                        log.warn("更新工作流执行进度失败(非致命): {}", e.getMessage());
                    }
                }
            }

            String obsJson = toolResult.toJson();
            step.setObservation(obsJson);

            if (obsJson != null) {
                String obsContent = obsJson;
                if (obsContent.length() > 500) {
                    obsContent = obsContent.substring(0, 500) + "...";
                }
                AgentChatMessage resultMsg = chatMessageService.saveSystemResult(sessionId, userId, obsContent,
                        null, actionMsg.getId(), round);
                ssePush.accept(resultMsg);
            }
            saveOperationDetail(actionMsg.getId(), sessionId, userId,
                    AgentOperationDetail.TYPE_SYSTEM, actionName, actionInput,
                    toolResult.getData(), round, null);

            if (toolResult.isNeedsUserInput()) {
                String question = null;
                List<String> options = null;
                if (toolResult.getData() != null) {
                    Object q = toolResult.getData().get("question");
                    if (q != null)
                        question = q.toString();
                    Object o = toolResult.getData().get("options");
                    if (o instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> rawList = (List<Object>) o;
                        options = rawList.stream().map(Object::toString).toList();
                    }
                }
                session.setWaitingUserInput(question, options);
                step.setDecision("WAITING_USER_INPUT");
            }
        } else {
            if ("execute_workflow".equals(actionName) && session.getExecutingWorkflowId() == null) {
                String wfId = actionInput.get("workflow_id") != null
                        ? actionInput.get("workflow_id").toString()
                        : null;
                if (wfId != null) {
                    session.setExecutingWorkflowId(wfId);
                    int taskNodeCount = 0;
                    try {
                        List<AgentWorkflowNode> wfNodes = workflowService.getWorkflowNodes(wfId);
                        Map<String, String> nodeMap = new java.util.HashMap<>();
                        for (AgentWorkflowNode n : wfNodes) {
                            if (n.getToolName() != null && !n.getToolName().isBlank()) {
                                nodeMap.put(n.getToolName(), n.getNodeId());
                            }
                            if (AgentWorkflowNode.TYPE_START.equals(n.getNodeType())) {
                                nodeMap.put("_start", n.getNodeId());
                            }
                            if (AgentWorkflowNode.TYPE_END.equals(n.getNodeType())) {
                                nodeMap.put("_end", n.getNodeId());
                            }
                            if (AgentWorkflowNode.TYPE_TASK.equals(n.getNodeType())) {
                                taskNodeCount++;
                            }
                        }
                        session.setWorkflowNodeMap(nodeMap);
                        try {
                            var execution = workflowExecutionService.startExecution(
                                    wfId, userId, sessionId, taskNodeCount);
                            session.setExecutingWorkflowExecutionId(execution.getExecutionId());
                            session.setExecutingWorkflowCompletedNodes(0);
                        } catch (Exception e) {
                            log.error("创建工作流执行记录失败(非致命): workflowId={}, sessionId={}, error={}",
                                    wfId, sessionId, e.getMessage(), e);
                        }
                    } catch (Exception e) {
                        log.warn("获取工作流节点映射失败: {}", e.getMessage());
                    }
                    sseEmitterService.emitWorkflowExecutionStart(sessionId, wfId,
                            session.getExecutingWorkflowExecutionId(), taskNodeCount);
                    String startNodeId = session.getWorkflowNodeMap().get("_start");
                    if (startNodeId != null) {
                        sseEmitterService.emitWorkflowNodeStatus(sessionId, wfId, startNodeId, "completed",
                                session.getExecutingWorkflowExecutionId(), 0);
                    }
                }
            }

            String currentNodeId = null;
            if (session.getExecutingWorkflowId() != null && !"execute_workflow".equals(actionName)
                    && !"create_workflow".equals(actionName) && !"delete_workflow".equals(actionName)) {
                currentNodeId = session.getWorkflowNodeMap().get(actionName);
                if (currentNodeId != null) {
                    session.setExecutingWorkflowNodeId(currentNodeId);
                    sseEmitterService.emitWorkflowNodeStatus(sessionId, session.getExecutingWorkflowId(),
                            currentNodeId, "running", session.getExecutingWorkflowExecutionId(),
                            session.getExecutingWorkflowCompletedNodes());
                }
            }

            AgentChatMessage actionMsg = chatMessageService.saveMcpAction(
                    sessionId, userId, actionName, actionInput, round);
            ssePush.accept(actionMsg);

            McpToolResult toolResult = executeActionByName(session, actionName, actionInput);

            if (currentNodeId != null) {
                String nodeStatus = toolResult.isSuccess() ? "completed" : "failed";
                int newCompletedNodes = session.getExecutingWorkflowCompletedNodes();
                if (toolResult.isSuccess()) {
                    newCompletedNodes++;
                }
                sseEmitterService.emitWorkflowNodeStatus(sessionId, session.getExecutingWorkflowId(),
                        currentNodeId, nodeStatus, session.getExecutingWorkflowExecutionId(),
                        newCompletedNodes);
                session.setExecutingWorkflowNodeId(null);
                if (toolResult.isSuccess() && session.getExecutingWorkflowExecutionId() != null) {
                    session.setExecutingWorkflowCompletedNodes(
                            session.getExecutingWorkflowCompletedNodes() + 1);
                    try {
                        workflowExecutionService.updateNodeProgress(
                                session.getExecutingWorkflowExecutionId(),
                                currentNodeId,
                                session.getExecutingWorkflowCompletedNodes());
                    } catch (Exception e) {
                        log.warn("更新工作流执行进度失败(非致命): {}", e.getMessage());
                    }
                }
            }

            String obsJson = toolResult.toJson();
            step.setObservation(obsJson);

            if (obsJson != null) {
                String obsContent = obsJson;
                if (obsContent.length() > 500) {
                    obsContent = obsContent.substring(0, 500) + "...";
                }
                AgentChatMessage resultMsg = chatMessageService.saveMcpResult(sessionId, userId, obsContent,
                        null, actionMsg.getId(), round);
                ssePush.accept(resultMsg);
            }

            String fileKey = extractFileKey(actionInput);
            saveOperationDetail(actionMsg.getId(), sessionId, userId,
                    AgentOperationDetail.TYPE_MCP, actionName, actionInput,
                    toolResult.getData(), round, fileKey);

            if (toolResult.isNeedsUserInput()) {
                String question = null;
                List<String> options = null;
                if (toolResult.getData() != null) {
                    Object q = toolResult.getData().get("question");
                    if (q != null)
                        question = q.toString();
                    Object o = toolResult.getData().get("options");
                    if (o instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> rawList = (List<Object>) o;
                        options = rawList.stream().map(Object::toString).toList();
                    }
                }
                session.setWaitingUserInput(question, options);
                step.setDecision("WAITING_USER_INPUT");
                log.info("ReAct等待用户输入: sessionId={}, question={}", sessionId, question);
            }
        }
    }

    private void applyFinishCondition(ReactStep step, LlmResponse llmResponse, ReactSession session) {
        if ("FINISH".equalsIgnoreCase(llmResponse.getDecision())) {
            boolean hasAction = step.getAction() != null && !step.getAction().isBlank();
            if (hasAction) {
                log.info("LLM返回FINISH但仍有待执行工具，继续执行工具: action={}", step.getAction());
                step.setFinished(false);
                step.setDecision("CONTINUE");
            } else {
                step.setFinished(true);
                session.setFinalAnswer(llmResponse.getFinalAnswer() != null ? llmResponse.getFinalAnswer() : "任务完成");
            }
        }
    }

    private ReactStep executeOneRoundWithSse(ReactSession session, SseEmitterService sseService) {
        String sessionId = session.getSessionId();
        Long userId = session.getUserId();
        int round = session.getCurrentRound();

        AssembledContext context = buildLlmContext(session);

        AgentChatMessage thoughtMsg = chatMessageService.saveAiThought(sessionId, userId, "", round);

        AgentChatMessage briefThoughtMsg = chatMessageService.saveAiThoughtBrief(sessionId, userId, "", round);
        pushSseMessage(sseService, sessionId, briefThoughtMsg);

        StringBuilder thoughtBuilder = new StringBuilder();
        ChunkBuffer chunkBuffer = new ChunkBuffer();
        String llmRawResponse = deepseekService.chatWithSystemStream(
                context.getSystemPrompt(), context.getUserMessage(),
                chunk -> {
                    thoughtBuilder.append(chunk);
                    String thoughtContent = chunkBuffer.onChunk(chunk);
                    if (thoughtContent != null && !thoughtContent.isEmpty()) {
                        sseService.emitThoughtChunk(sessionId, briefThoughtMsg.getId(), thoughtContent, false);
                    }
                });

        String lastContent = chunkBuffer.onEnd();
        if (lastContent != null && !lastContent.isEmpty()) {
            sseService.emitThoughtChunk(sessionId, briefThoughtMsg.getId(), lastContent, false);
        }

        LlmResponse llmResponse = parseLlmResponse(llmRawResponse);

        if (llmResponse.getParseError() != null) {
            session.setLastParseError(llmResponse.getParseError());
            log.warn("LLM响应解析出错，已记录到session用于下一轮反馈: sessionId={}", sessionId);
        } else {
            session.setLastParseError(null);
        }

        String thoughtContent = llmResponse.getThought() != null ? llmResponse.getThought()
                : extractThoughtFromRawText(thoughtBuilder.toString());
        thoughtMsg.setContent(thoughtContent);
        chatMessageService.updateContent(thoughtMsg.getId(), thoughtContent);

        String briefThoughtContent = llmResponse.getBriefThought() != null ? llmResponse.getBriefThought()
                : toolNameMapper.replaceToolNames(thoughtContent);
        briefThoughtMsg.setContent(briefThoughtContent);
        chatMessageService.updateContent(briefThoughtMsg.getId(), briefThoughtContent);
        sseService.emitThoughtChunk(sessionId, briefThoughtMsg.getId(), briefThoughtContent, true);

        log.debug("LLM原始响应: {}", llmRawResponse);

        ReactStep step = buildStepFromResponse(llmResponse, session);
        sendTaskListIfNeeded(session, sseService);
        executeToolActions(step, session, msg -> pushSseMessage(sseService, sessionId, msg));
        applyFinishCondition(step, llmResponse, session);

        return step;
    }

    public ReactStep executeOneRound(ReactSession session) {
        log.info("executeOneRound开始: sessionId={}, round={}", session.getSessionId(), session.getCurrentRound());

        AssembledContext context = buildLlmContext(session);

        String llmRawResponse = deepseekService.chatWithSystem(context.getSystemPrompt(), context.getUserMessage());

        log.debug("LLM原始响应: {}", llmRawResponse);

        LlmResponse llmResponse = parseLlmResponse(llmRawResponse);

        if (llmResponse.getParseError() != null) {
            session.setLastParseError(llmResponse.getParseError());
            log.warn("LLM响应解析出错，已记录到session用于下一轮反馈: sessionId={}", session.getSessionId());
        } else {
            session.setLastParseError(null);
        }

        ReactStep step = buildStepFromResponse(llmResponse, session);
        executeToolActions(step, session, msg -> {
        });
        applyFinishCondition(step, llmResponse, session);

        return step;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseActionInput(Object actionInput) {
        try {
            if (actionInput instanceof Map) {
                return new java.util.HashMap<>((Map<String, Object>) actionInput);
            } else if (actionInput instanceof String) {
                return JSON.parseObject((String) actionInput, Map.class);
            }
        } catch (Exception e) {
            log.warn("解析action_input失败: {}", e.getMessage());
        }
        return new java.util.HashMap<>();
    }

    private McpToolResult executeActionByName(ReactSession session, String actionName,
            Map<String, Object> actionInput) {
        actionInput.put("user_id", session.getUserId());
        actionInput.put("session_id", session.getSessionId());
        return toolRegistry.executeTool(actionName, actionInput);
    }

    private SystemToolResult executeSystemActionByName(ReactSession session, String actionName,
            Map<String, Object> actionInput) {
        actionInput.put("user_id", session.getUserId());
        actionInput.put("session_id", session.getSessionId());
        return systemToolRegistry.executeTool(actionName, actionInput);
    }

    private void saveOperationDetail(Long messageId, String sessionId, Long userId,
            String operationType, String toolName, Map<String, Object> actionInput,
            Map<String, Object> actionResult, Integer roundIndex, String fileKey) {
        if (operationDetailMapper == null) {
            log.warn("operationDetailMapper为null，跳过操作详情保存");
            return;
        }
        try {
            AgentOperationDetail detail = AgentOperationDetail.builder()
                    .messageId(messageId)
                    .sessionId(sessionId)
                    .userId(userId)
                    .operationType(operationType)
                    .toolName(toolName)
                    .actionInput(actionInput != null ? JSON.toJSONString(actionInput) : null)
                    .actionResult(actionResult != null ? JSON.toJSONString(actionResult) : null)
                    .fileKey(fileKey)
                    .roundIndex(roundIndex)
                    .build();
            operationDetailMapper.insert(detail);
            log.info("操作详情已保存: messageId={}, type={}, tool={}", messageId, operationType, toolName);
        } catch (Exception e) {
            log.warn("保存操作详情失败(非致命): messageId={}, error={}", messageId, e.getMessage());
        }
    }

    private String extractFileKey(Map<String, Object> actionInput) {
        if (actionInput == null) {
            return null;
        }
        Object fileKey = actionInput.get("file_key");
        if (fileKey != null) {
            return fileKey.toString();
        }
        return null;
    }

    private void markOldFileAsSuperseded(String sessionId, String originalFileKey) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentFileStorage> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(AgentFileStorage::getSessionId, sessionId)
                    .eq(AgentFileStorage::getStorageKey, originalFileKey)
                    .eq(AgentFileStorage::getDeleted, 0);
            AgentFileStorage oldFile = fileStorageMapper.selectOne(wrapper);
            if (oldFile != null && oldFile.getIsOriginal() != null && oldFile.getIsOriginal() == 1) {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentFileStorage> updateWrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                updateWrapper.eq(AgentFileStorage::getId, oldFile.getId())
                        .set(AgentFileStorage::getIsOriginal, 0);
                fileStorageMapper.update(null, updateWrapper);
                log.info("旧版本文件已标记为非最新: fileId={}, fileKey={}", oldFile.getId(), originalFileKey);
            }
        } catch (Exception e) {
            log.warn("标记旧文件版本失败(非致命): fileKey={}, error={}", originalFileKey, e.getMessage());
        }
    }

    private AgentFileStorage findFileByStorageKey(String storageKey) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentFileStorage> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(AgentFileStorage::getStorageKey, storageKey)
                    .eq(AgentFileStorage::getDeleted, 0)
                    .last("LIMIT 1");
            return fileStorageMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("按storageKey查找文件失败(非致命): {}", e.getMessage());
            return null;
        }
    }

    private void markSameNameFilesAsSuperseded(String sessionId, String fileName, Long newFileId) {
        try {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentFileStorage> nameUpdate = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            nameUpdate.eq(AgentFileStorage::getSessionId, sessionId)
                    .eq(AgentFileStorage::getOriginalName, fileName)
                    .eq(AgentFileStorage::getIsOriginal, 1)
                    .ne(AgentFileStorage::getId, newFileId)
                    .set(AgentFileStorage::getIsOriginal, 0);
            int updated = fileStorageMapper.update(null, nameUpdate);
            if (updated > 0) {
                log.info("同名文件去重: sessionId={}, fileName={}, 标记{}条旧记录为非最新", sessionId, fileName, updated);
            }
        } catch (Exception e) {
            log.warn("同名文件去重失败(非致命): {}", e.getMessage());
        }
    }

    private boolean hasUnfinishedTasks(ReactSession session) {
        if (session.getTaskList() == null || session.getTaskList().isEmpty()) {
            return false;
        }
        return session.getTaskList().stream()
                .anyMatch(item -> !"COMPLETED".equals(item.getStatus()));
    }

    private void completeAllPendingTasks(ReactSession session) {
        if (session.getTaskList() == null || session.getTaskList().isEmpty()) {
            return;
        }
        for (TaskItem item : session.getTaskList()) {
            if (!"COMPLETED".equals(item.getStatus())) {
                log.info("FINISH时自动完成任务: itemIndex={}, description={}", item.getItemIndex(), item.getDescription());
                item.setStatus("COMPLETED");
            }
        }
        persistTaskList(session);
    }

    private void failActiveWorkflowExecution(ReactSession session, String reason) {
        if (session.getExecutingWorkflowExecutionId() != null) {
            try {
                workflowExecutionService.failExecution(
                        session.getExecutingWorkflowExecutionId(), reason);
            } catch (Exception e) {
                log.warn("标记工作流执行失败失败(非致命): {}", e.getMessage());
            }
            sseEmitterService.emitWorkflowExecutionEnd(session.getSessionId(), session.getExecutingWorkflowId(),
                    session.getExecutingWorkflowExecutionId(), AgentWorkflowExecution.STATUS_FAILED);
            session.setExecutingWorkflowId(null);
            session.setExecutingWorkflowExecutionId(null);
            session.setExecutingWorkflowCompletedNodes(0);
            session.setWorkflowNodeMap(new java.util.HashMap<>());
        }
    }

    private void completeActiveWorkflowExecution(ReactSession session) {
        if (session.getExecutingWorkflowExecutionId() != null) {
            try {
                workflowExecutionService.completeExecution(
                        session.getExecutingWorkflowExecutionId(), "任务已完成");
            } catch (Exception e) {
                log.warn("标记工作流执行完成失败(非致命): {}", e.getMessage());
            }
            sseEmitterService.emitWorkflowExecutionEnd(session.getSessionId(), session.getExecutingWorkflowId(),
                    session.getExecutingWorkflowExecutionId(), AgentWorkflowExecution.STATUS_COMPLETED);
            session.setExecutingWorkflowId(null);
            session.setExecutingWorkflowExecutionId(null);
            session.setExecutingWorkflowCompletedNodes(0);
            session.setWorkflowNodeMap(new java.util.HashMap<>());
        }
    }

    private static final String FINAL_ANSWER_SYSTEM_PROMPT = "你是一个AI办公助手的总结模块。下面是助手完成用户请求后的操作总结，请将其润色为自然流畅的中文回复，直接输出润色结果，不要添加额外解释。\n"
            + "要求：1.用第一人称\"我\"描述助手执行的操作；2.语气简洁专业；3.保留关键信息（如文件名、操作结果）；4.不要说\"您说得对\"、\"看起来你提供了\"等回应性语句；5.直接以操作结果开头，如\"我已经生成了...\"、\"我查询了...\"；6.不要在总结中暴露内部ID、工具英文名等技术细节，使用用户友好的中文描述（如\"读取了Excel文件\"而非\"调用了excel工具\"，\"生成了Word文档\"而非\"调用了word工具\"）。";

    private void handleFinalAnswer(ReactSession session, SseEmitterService sseService, int durationMs) {
        String sessionId = session.getSessionId();
        Long userId = session.getUserId();

        if (session.getExecutingWorkflowId() != null) {
            String endNodeId = session.getWorkflowNodeMap().get("_end");
            if (endNodeId != null) {
                sseEmitterService.emitWorkflowNodeStatus(sessionId, session.getExecutingWorkflowId(),
                        endNodeId, "completed", session.getExecutingWorkflowExecutionId(),
                        session.getExecutingWorkflowCompletedNodes());
            }
            sseEmitterService.emitWorkflowExecutionEnd(sessionId, session.getExecutingWorkflowId(),
                    session.getExecutingWorkflowExecutionId(), AgentWorkflowExecution.STATUS_COMPLETED);

            if (session.getExecutingWorkflowExecutionId() != null) {
                try {
                    workflowExecutionService.completeExecution(
                            session.getExecutingWorkflowExecutionId(), "工作流执行完成");
                } catch (Exception e) {
                    log.warn("标记工作流执行完成失败(非致命): {}", e.getMessage());
                }
            }

            session.setExecutingWorkflowId(null);
            session.setExecutingWorkflowExecutionId(null);
            session.setExecutingWorkflowCompletedNodes(0);
            session.setWorkflowNodeMap(new java.util.HashMap<>());
        }

        if (sseService != null) {
            AgentChatMessage finalMsg = chatMessageService.saveAiText(sessionId, userId,
                    "", session.getCurrentRound(), durationMs, "deepseek-chat");
            pushSseMessage(sseService, sessionId, finalMsg);

            String fullAnswer = deepseekService.chatWithSystemStream(
                    FINAL_ANSWER_SYSTEM_PROMPT, session.getFinalAnswer(),
                    chunk -> sseService.emitStreamChunk(sessionId, finalMsg.getId(), chunk));

            finalMsg.setContent(fullAnswer);
            chatMessageService.updateContent(finalMsg.getId(), fullAnswer);
            session.setFinalAnswer(fullAnswer);
        } else {
            String polished = deepseekService.chatWithSystem(FINAL_ANSWER_SYSTEM_PROMPT, session.getFinalAnswer());
            chatMessageService.saveAiText(sessionId, userId,
                    polished, session.getCurrentRound(), durationMs, "deepseek-chat");
        }
    }

    private void updateTaskList(ReactSession session, List<TaskItem> newTaskList) {
        if (session.getTaskList().isEmpty()) {
            session.setTaskList(new ArrayList<>(newTaskList));
            log.info("ReAct创建任务列表: sessionId={}, 任务数={}", session.getSessionId(), newTaskList.size());
        } else {
            java.util.Set<Integer> completingIndices = new java.util.HashSet<>();
            for (TaskItem newItem : newTaskList) {
                if ("COMPLETED".equals(newItem.getStatus())) {
                    completingIndices.add(newItem.getItemIndex());
                }
            }

            for (TaskItem newItem : newTaskList) {
                boolean found = false;
                for (TaskItem existingItem : session.getTaskList()) {
                    if (existingItem.getItemIndex().equals(newItem.getItemIndex())) {
                        if (newItem.getStatus() != null) {
                            if ("COMPLETED".equals(newItem.getStatus())
                                    && !"COMPLETED".equals(existingItem.getStatus())) {
                                for (TaskItem prevItem : session.getTaskList()) {
                                    if (prevItem.getItemIndex() < newItem.getItemIndex()
                                            && !"COMPLETED".equals(prevItem.getStatus())
                                            && !completingIndices.contains(prevItem.getItemIndex())) {
                                        prevItem.setStatus("COMPLETED");
                                        log.info("跳过即完成: 自动完成被跳过的任务 itemIndex={}, description={}",
                                                prevItem.getItemIndex(), prevItem.getDescription());
                                    }
                                }
                            }
                            existingItem.setStatus(newItem.getStatus());
                        }
                        if (newItem.getErrorMessage() != null) {
                            existingItem.setErrorMessage(newItem.getErrorMessage());
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    boolean isDuplicate = session.getTaskList().stream()
                            .anyMatch(existing -> existing.getDescription() != null
                                    && existing.getDescription().trim()
                                            .equals(newItem.getDescription() != null ? newItem.getDescription().trim()
                                                    : ""));
                    if (!isDuplicate) {
                        session.getTaskList().add(newItem);
                    } else {
                        log.info("跳过重复任务: description={}", newItem.getDescription());
                    }
                }
            }
        }

        persistTaskList(session);
    }

    private LlmResponse parseLlmResponse(String rawResponse) {
        LlmResponse response = new LlmResponse();
        String parseErrorInfo = null;

        try {
            String jsonStr = extractJson(rawResponse);
            if (jsonStr != null) {
                try {
                    response = JSON.parseObject(jsonStr, LlmResponse.class);
                } catch (JSONException e1) {
                    String normalized = normalizeChineseQuotes(jsonStr);
                    if (!normalized.equals(jsonStr)) {
                        log.info("首次JSON解析失败，尝试替换中文引号后重新解析");
                        try {
                            response = JSON.parseObject(normalized, LlmResponse.class);
                        } catch (JSONException e2) {
                            throw e1;
                        }
                    } else {
                        throw e1;
                    }
                }
            }
        } catch (JSONException e) {
            log.warn("解析LLM响应为JSON失败，尝试修复JSON: {}", e.getMessage());
            parseErrorInfo = e.getMessage();

            String jsonStr = extractJson(rawResponse);
            if (jsonStr != null) {
                String repaired = repairJson(jsonStr);
                if (!repaired.equals(jsonStr)) {
                    log.info("尝试使用修复后的JSON重新解析");
                    try {
                        response = JSON.parseObject(repaired, LlmResponse.class);
                        parseErrorInfo = null;
                        log.info("修复JSON后解析成功");
                    } catch (JSONException e3) {
                        log.warn("修复JSON后仍然解析失败，尝试提取action_list: {}", e3.getMessage());
                    }
                }

                if (parseErrorInfo != null) {
                    String extractedAction = extractActionFromRawText(jsonStr);
                    Object extractedActionInput = extractActionInputFromRawText(jsonStr);
                    String extractedThought = extractThoughtFromRawText(rawResponse);
                    List<TaskItem> extractedTaskList = extractTaskListFromRawText(jsonStr);

                    response.setThought(extractedThought);
                    if (extractedAction != null && !extractedAction.isBlank()) {
                        response.setAction(extractedAction);
                        response.setActionInput(extractedActionInput);
                        response.setDecision("CONTINUE");
                        log.info("从原始文本中提取到action: {}", extractedAction);
                    } else {
                        response.setDecision("FINISH");
                        response.setFinalAnswer(extractedThought);
                    }
                    if (!extractedTaskList.isEmpty()) {
                        response.setTaskList(extractedTaskList);
                    }
                }
            } else {
                String extractedThought = extractThoughtFromRawText(rawResponse);
                response.setThought(extractedThought);
                response.setDecision("FINISH");
                response.setFinalAnswer(extractedThought);
            }
        }

        if (response.getDecision() == null) {
            response.setDecision("CONTINUE");
        }

        if (parseErrorInfo != null) {
            response.setParseError(parseErrorInfo);
        }

        return response;
    }

    private String repairJson(String json) {
        if (json == null)
            return null;
        String repaired = json;
        while (repaired.contains(",,")) {
            repaired = repaired.replace(",,", ",");
        }
        repaired = repaired.replaceAll(",\\s*}", "}");
        repaired = repaired.replaceAll(",\\s*]", "]");
        return repaired;
    }

    private String extractActionFromRawText(String text) {
        if (text == null)
            return null;
        try {
            java.util.regex.Pattern actionPattern = java.util.regex.Pattern.compile(
                    "\"action\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = actionPattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.warn("从原始文本提取action异常: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object extractActionInputFromRawText(String text) {
        if (text == null)
            return null;
        try {
            java.util.regex.Pattern inputPattern = java.util.regex.Pattern.compile(
                    "\"action_input\"\\s*:\\s*\\{");
            java.util.regex.Matcher startMatcher = inputPattern.matcher(text);
            if (!startMatcher.find()) {
                return Map.of();
            }
            int startIdx = startMatcher.start() + "\"action_input\"".length() + 1;
            int braceCount = 0;
            int endIdx = -1;
            for (int i = startIdx; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{')
                    braceCount++;
                else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        endIdx = i + 1;
                        break;
                    }
                }
            }
            if (endIdx == -1)
                return Map.of();
            String inputJson = text.substring(startIdx, endIdx);
            String repaired = repairJson(inputJson);
            return JSON.parseObject(repaired, Map.class);
        } catch (Exception e) {
            log.warn("从原始文本提取action_input异常: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<TaskItem> extractTaskListFromRawText(String text) {
        List<TaskItem> taskItems = new ArrayList<>();
        if (text == null)
            return taskItems;

        try {
            java.util.regex.Pattern taskListPattern = java.util.regex.Pattern.compile(
                    "\"task_list\"\\s*:\\s*\\[");
            java.util.regex.Matcher startMatcher = taskListPattern.matcher(text);
            if (!startMatcher.find()) {
                return taskItems;
            }

            int startIdx = startMatcher.end() - 1;
            int bracketCount = 0;
            int endIdx = -1;
            for (int i = startIdx; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '[')
                    bracketCount++;
                else if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        endIdx = i + 1;
                        break;
                    }
                }
            }

            if (endIdx == -1)
                return taskItems;

            String taskListJson = text.substring(startIdx, endIdx);
            String repaired = repairJson(taskListJson);
            taskItems = JSON.parseArray(repaired, TaskItem.class);
        } catch (Exception e) {
            log.debug("从原始文本提取task_list失败(非致命): {}", e.getMessage());
        }

        return taskItems != null ? taskItems : new ArrayList<>();
    }

    private String extractThoughtFromRawText(String text) {
        if (text == null)
            return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "\"thought\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
        if (matcher.find()) {
            String thought = matcher.group(1);
            thought = thought.replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            return thought;
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    private String extractJson(String text) {
        if (text == null)
            return null;

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return null;
    }

    private String normalizeChineseQuotes(String json) {
        if (json == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        char stringDelimiter = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < json.length()) {
                    sb.append(c);
                    sb.append(json.charAt(i + 1));
                    i++;
                    continue;
                }
                if (c == stringDelimiter) {
                    inString = false;
                    sb.append(c);
                    continue;
                }
                sb.append(c);
            } else {
                if (c == '"') {
                    inString = true;
                    stringDelimiter = '"';
                    sb.append(c);
                } else if (c == '\u201c') {
                    inString = true;
                    stringDelimiter = '\u201d';
                    sb.append('"');
                } else if (c == '\u201d') {
                    inString = false;
                    sb.append('"');
                } else if (c == '\u2018') {
                    inString = true;
                    stringDelimiter = '\u2019';
                    sb.append('\'');
                } else if (c == '\u2019') {
                    inString = false;
                    sb.append('\'');
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private void pushSseMessage(SseEmitterService sseService, String sessionId, AgentChatMessage msg) {
        if (sseService == null || msg == null) {
            return;
        }
        try {
            String content;
            if (AgentChatMessage.TYPE_USER_MESSAGE.equals(msg.getMessageType())) {
                content = msg.getContent();
            } else {
                content = toolNameMapper.fixPerspective(toolNameMapper.replaceToolNames(msg.getContent()));
            }
            Object metadata = msg.getMetadata();
            if (metadata instanceof String metaStr && !metaStr.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metaMap = com.alibaba.fastjson2.JSON.parseObject(metaStr,
                            java.util.Map.class);
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
                Map<String, Object> metaMap = new java.util.HashMap<>((Map<String, Object>) metadata);
                Object toolName = metaMap.get("tool_name");
                if (toolName instanceof String) {
                    metaMap.put("tool_name", toolNameMapper.getChineseName((String) toolName));
                }
                metadata = metaMap;
            }

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", msg.getId());
            payload.put("message_type", msg.getMessageType());
            payload.put("role", msg.getRole());
            payload.put("content", content);
            payload.put("metadata", metadata);
            payload.put("parent_id", msg.getParentId());
            payload.put("round_index", msg.getRoundIndex());
            payload.put("sort_order", msg.getSortOrder());
            payload.put("duration_ms", msg.getDurationMs());
            payload.put("create_time", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            sseService.emitChatMessage(sessionId, msg.getMessageType(), payload);
        } catch (Exception e) {
            log.debug("SSE推送消息失败(非致命): sessionId={}, msgId={}", sessionId, msg.getId());
        }
    }

    private static class ThoughtChunkExtractor {

        private enum State {
            IDLE,
            IN_KEY,
            AFTER_THOUGHT_KEY,
            BEFORE_THOUGHT_VALUE,
            IN_THOUGHT_VALUE,
            DONE
        }

        private State state = State.IDLE;
        private final StringBuilder keyBuffer = new StringBuilder();
        private boolean escape = false;
        private int charCount = 0;

        public String extract(String chunk) {
            if (state == State.DONE) {
                return null;
            }

            StringBuilder toEmit = new StringBuilder();

            for (int i = 0; i < chunk.length(); i++) {
                char c = chunk.charAt(i);

                switch (state) {
                    case IDLE:
                        if (c == '"') {
                            state = State.IN_KEY;
                            keyBuffer.setLength(0);
                        }
                        break;

                    case IN_KEY:
                        if (escape) {
                            keyBuffer.append(c);
                            escape = false;
                        } else if (c == '\\') {
                            escape = true;
                        } else if (c == '"') {
                            if ("thought".equals(keyBuffer.toString())) {
                                state = State.AFTER_THOUGHT_KEY;
                            } else {
                                state = State.IDLE;
                            }
                        } else {
                            keyBuffer.append(c);
                        }
                        break;

                    case AFTER_THOUGHT_KEY:
                        if (c == ':') {
                            state = State.BEFORE_THOUGHT_VALUE;
                        }
                        break;

                    case BEFORE_THOUGHT_VALUE:
                        if (c == '"') {
                            state = State.IN_THOUGHT_VALUE;
                            escape = false;
                            charCount = 0;
                        } else if (c == 'n') {
                            state = State.IDLE;
                        } else if (!Character.isWhitespace(c)) {
                            state = State.IDLE;
                        }
                        break;

                    case IN_THOUGHT_VALUE:
                        if (escape) {
                            toEmit.append(unescapeJson(c));
                            escape = false;
                            charCount++;
                        } else if (c == '\\') {
                            escape = true;
                        } else if (c == '"') {
                            state = State.DONE;
                        } else {
                            toEmit.append(c);
                            charCount++;
                            if (charCount > 10000) {
                                log.warn("ThoughtChunkExtractor: 内容超过10000字符仍未结束，可能存在格式问题");
                            }
                        }
                        break;

                    case DONE:
                        return toEmit.length() > 0 ? toEmit.toString() : null;
                }
            }

            return toEmit.length() > 0 ? toEmit.toString() : null;
        }

        private char unescapeJson(char c) {
            return switch (c) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                default -> c;
            };
        }
    }

    private static class BriefThoughtChunkExtractor {

        private enum State {
            IDLE,
            IN_KEY,
            AFTER_BRIEF_THOUGHT_KEY,
            BEFORE_VALUE,
            IN_VALUE,
            DONE
        }

        private State state = State.IDLE;
        private final StringBuilder keyBuffer = new StringBuilder();
        private boolean escape = false;
        private int charCount = 0;

        public String extract(String chunk) {
            if (state == State.DONE) {
                return null;
            }

            StringBuilder toEmit = new StringBuilder();

            for (int i = 0; i < chunk.length(); i++) {
                char c = chunk.charAt(i);

                switch (state) {
                    case IDLE:
                        if (c == '"') {
                            state = State.IN_KEY;
                            keyBuffer.setLength(0);
                        }
                        break;

                    case IN_KEY:
                        if (escape) {
                            keyBuffer.append(c);
                            escape = false;
                        } else if (c == '\\') {
                            escape = true;
                        } else if (c == '"') {
                            if ("brief_thought".equals(keyBuffer.toString())) {
                                state = State.AFTER_BRIEF_THOUGHT_KEY;
                            } else {
                                state = State.IDLE;
                            }
                        } else {
                            keyBuffer.append(c);
                        }
                        break;

                    case AFTER_BRIEF_THOUGHT_KEY:
                        if (c == ':') {
                            state = State.BEFORE_VALUE;
                        }
                        break;

                    case BEFORE_VALUE:
                        if (c == '"') {
                            state = State.IN_VALUE;
                            escape = false;
                            charCount = 0;
                        } else if (c == 'n') {
                            state = State.IDLE;
                        } else if (!Character.isWhitespace(c)) {
                            state = State.IDLE;
                        }
                        break;

                    case IN_VALUE:
                        if (escape) {
                            toEmit.append(unescapeJson(c));
                            escape = false;
                            charCount++;
                        } else if (c == '\\') {
                            escape = true;
                        } else if (c == '"') {
                            state = State.DONE;
                        } else {
                            toEmit.append(c);
                            charCount++;
                            if (charCount > 10000) {
                                log.warn("BriefThoughtChunkExtractor: 内容超过10000字符仍未结束，可能存在格式问题");
                            }
                        }
                        break;

                    case DONE:
                        return toEmit.length() > 0 ? toEmit.toString() : null;
                }
            }

            return toEmit.length() > 0 ? toEmit.toString() : null;
        }

        private char unescapeJson(char c) {
            return switch (c) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                default -> c;
            };
        }
    }

    private static class ChunkBuffer {

        private static final Set<Character> ESCAPE_TARGETS = Set.of('n', 't', 'r', '"', '\\', '/', 'u');

        private final ArrayDeque<String> queue = new ArrayDeque<>(2);
        private final BriefThoughtChunkExtractor briefExtractor;
        private final ThoughtChunkExtractor fallbackExtractor;
        private boolean useBriefExtractor = true;

        ChunkBuffer() {
            this.briefExtractor = new BriefThoughtChunkExtractor();
            this.fallbackExtractor = new ThoughtChunkExtractor();
        }

        String onChunk(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return null;
            }

            queue.addLast(chunk);

            if (queue.size() == 2) {
                String first = queue.peekFirst();
                String second = queue.peekLast();

                if (hasBoundaryIssue(first, second)) {
                    String merged = first + second;
                    String fixedFirst = merged.substring(0, first.length() + 1);
                    String fixedSecond = merged.substring(first.length() + 1);
                    queue.clear();
                    queue.addLast(fixedFirst);
                    if (!fixedSecond.isEmpty()) {
                        queue.addLast(fixedSecond);
                    }
                }

                String toProcess = queue.pollFirst();
                return extractFromChunk(toProcess);
            }

            return null;
        }

        String onEnd() {
            if (!queue.isEmpty()) {
                String last = queue.pollFirst();
                return extractFromChunk(last);
            }
            return null;
        }

        private String extractFromChunk(String chunk) {
            if (useBriefExtractor) {
                String result = briefExtractor.extract(chunk);
                if (result != null) {
                    return result;
                }
            }
            return fallbackExtractor.extract(chunk);
        }

        private boolean hasBoundaryIssue(String first, String second) {
            if (first == null || first.isEmpty() || second == null || second.isEmpty()) {
                return false;
            }
            char lastChar = first.charAt(first.length() - 1);
            char nextChar = second.charAt(0);
            if (lastChar == '\\' && ESCAPE_TARGETS.contains(nextChar)) {
                return true;
            }
            return false;
        }
    }

    private void persistTaskList(ReactSession session) {
        if (session.getTaskList() == null || session.getTaskList().isEmpty()) {
            return;
        }
        try {
            String sessionId = session.getSessionId();
            Long userId = session.getUserId();
            List<TaskItem> items = session.getTaskList();

            long completedCount = items.stream().filter(i -> "COMPLETED".equals(i.getStatus())).count();
            String taskItemsJson = JSON.toJSONString(items);
            String status = completedCount == items.size() ? "COMPLETED" : "ACTIVE";

            AgentTaskList existing = loadActiveTaskList(sessionId);
            if (existing != null) {
                existing.setTaskItems(taskItemsJson);
                existing.setStatus(status);
                existing.setCompletedCount((int) completedCount);
                existing.setTotalCount(items.size());
                taskListMapper.updateById(existing);
                log.info("更新持久化任务列表: id={}, completed={}/{}", existing.getId(), completedCount, items.size());
            } else {
                AgentTaskList newRecord = new AgentTaskList();
                newRecord.setSessionId(sessionId);
                newRecord.setUserId(userId);
                newRecord.setStatus(status);
                newRecord.setTaskItems(taskItemsJson);
                newRecord.setCompletedCount((int) completedCount);
                newRecord.setTotalCount(items.size());
                taskListMapper.insert(newRecord);
                log.info("创建持久化任务列表: sessionId={}, completed={}/{}", sessionId, completedCount, items.size());
            }
        } catch (Exception e) {
            log.warn("持久化任务列表失败(非致命): {}", e.getMessage());
        }
    }

    private AgentTaskList loadActiveTaskList(String sessionId) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentTaskList> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(AgentTaskList::getSessionId, sessionId)
                    .eq(AgentTaskList::getStatus, "ACTIVE")
                    .orderByDesc(AgentTaskList::getId)
                    .last("LIMIT 1");
            return taskListMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("加载持久化任务列表失败(非致命): {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreTaskListToSession(ReactSession session, AgentTaskList persisted) {
        try {
            List<TaskItem> items = JSON.parseArray(persisted.getTaskItems(), TaskItem.class);
            session.setTaskList(items);
            session.setTaskId(persisted.getId());
        } catch (Exception e) {
            log.warn("恢复任务列表到Session失败(非致命): {}", e.getMessage());
        }
    }

    private void completeTaskListIfNeeded(ReactSession session) {
        if (session.getTaskList() == null || session.getTaskList().isEmpty()) {
            return;
        }
        boolean allCompleted = session.getTaskList().stream()
                .allMatch(i -> "COMPLETED".equals(i.getStatus()));
        if (allCompleted) {
            try {
                if (session.getTaskId() != null) {
                    AgentTaskList record = taskListMapper.selectById(session.getTaskId());
                    if (record != null && "ACTIVE".equals(record.getStatus())) {
                        record.setStatus("COMPLETED");
                        taskListMapper.updateById(record);
                        log.info("标记任务列表为完成: id={}", record.getId());
                    }
                } else {
                    AgentTaskList active = loadActiveTaskList(session.getSessionId());
                    if (active != null) {
                        active.setStatus("COMPLETED");
                        taskListMapper.updateById(active);
                        log.info("标记任务列表为完成: id={}", active.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("标记任务列表完成失败(非致命): {}", e.getMessage());
            }
        }
    }

    private List<Map<String, Object>> buildTaskListPayload(ReactSession session) {
        List<Map<String, Object>> taskListData = new ArrayList<>();
        for (TaskItem item : session.getTaskList()) {
            Map<String, Object> itemMap = new java.util.HashMap<>();
            itemMap.put("index", item.getItemIndex());
            itemMap.put("description", item.getDescription());
            itemMap.put("status", item.getStatus());
            if (item.getErrorMessage() != null) {
                itemMap.put("error_message", item.getErrorMessage());
            }
            taskListData.add(itemMap);
        }
        return taskListData;
    }

    private void sendTaskListIfNeeded(ReactSession session, SseEmitterService sseService) {
        if (session.getTaskList() == null || session.getTaskList().isEmpty())
            return;

        boolean shouldSend = false;
        long currentCompletedCount = session.getTaskList().stream()
                .filter(i -> "COMPLETED".equals(i.getStatus())).count();

        if (!session.isTaskListFirstSent()) {
            shouldSend = true;
        } else if (currentCompletedCount != session.getLastSentCompletedCount()) {
            shouldSend = true;
        }

        if (shouldSend) {
            List<Map<String, Object>> taskListData = buildTaskListPayload(session);
            AgentChatMessage taskMsg = chatMessageService.saveTaskUpdate(
                    session.getSessionId(), session.getUserId(), taskListData, session.getCurrentRound());
            pushSseMessage(sseService, session.getSessionId(), taskMsg);
            session.setTaskListFirstSent(true);
            session.setLastSentCompletedCount((int) currentCompletedCount);
        }
    }
}
