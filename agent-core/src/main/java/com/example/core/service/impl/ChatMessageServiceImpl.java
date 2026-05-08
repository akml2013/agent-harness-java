package com.example.core.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.core.entity.AgentChatMessage;
import com.example.core.mapper.AgentChatMessageMapper;
import com.example.core.service.ChatMessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

        private final AgentChatMessageMapper chatMessageMapper;

        @Override
        public AgentChatMessage saveMessage(String sessionId, Long userId, String messageType,
                        String role, String content, String metadata, Long parentId,
                        Integer roundIndex, Integer durationMs) {
                AgentChatMessage message = AgentChatMessage.builder()
                                .sessionId(sessionId)
                                .userId(userId)
                                .messageType(messageType)
                                .role(role)
                                .content(content)
                                .metadata(metadata)
                                .parentId(parentId)
                                .roundIndex(roundIndex)
                                .sortOrder(getNextSortOrder(sessionId))
                                .durationMs(durationMs)
                                .build();
                chatMessageMapper.insert(message);
                log.debug("保存对话记录: sessionId={}, type={}, sortOrder={}", sessionId, messageType,
                                message.getSortOrder());
                return message;
        }

        @Override
        public AgentChatMessage saveUserMessage(String sessionId, Long userId, String content) {
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_USER_MESSAGE,
                                "user", content, "{}", null, null, null);
        }

        @Override
        public AgentChatMessage saveAiText(String sessionId, Long userId, String content,
                        Integer roundIndex, Integer durationMs, String model) {
                Map<String, Object> meta = new HashMap<>();
                if (model != null) {
                        meta.put("model", model);
                }
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_AI_TEXT,
                                "assistant", content, JSON.toJSONString(meta), null, roundIndex, durationMs);
        }

        @Override
        public AgentChatMessage saveAiThought(String sessionId, Long userId, String content, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("round", roundIndex);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_AI_THOUGHT,
                                "assistant", content, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveAiThoughtBrief(String sessionId, Long userId, String content, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("round", roundIndex);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_AI_THOUGHT_BRIEF,
                                "assistant", content, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveMcpAction(String sessionId, Long userId, String toolName,
                        Map<String, Object> actionInput, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("tool_name", toolName);
                meta.put("action_input", actionInput);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_MCP_ACTION,
                                "assistant", toolName, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveMcpResult(String sessionId, Long userId, String content,
                        Map<String, Object> data, Long parentId, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("success", data != null && data.containsKey("success") ? data.get("success") : true);
                meta.put("data", data);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_MCP_RESULT,
                                "assistant", content, JSON.toJSONString(meta), parentId, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveAskUser(String sessionId, Long userId, String question,
                        List<String> options, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("question", question);
                meta.put("options", options);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_ASK_USER,
                                "assistant", question, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveUserInput(String sessionId, Long userId,
                        String selectedOption, String userInput, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("selected_option", selectedOption);
                meta.put("user_input", userInput);
                String content;
                if (selectedOption != null && userInput != null && !userInput.isBlank()) {
                        content = "选择了\"" + selectedOption + "\"，并输入：" + userInput;
                } else if (selectedOption != null) {
                        content = "选择了\"" + selectedOption + "\"";
                } else if (userInput != null && !userInput.isBlank()) {
                        content = "输入：" + userInput;
                } else {
                        content = "未提供额外信息";
                }
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_USER_INPUT,
                                "user", content, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveFileGenerated(String sessionId, Long userId,
                        Long fileId, String fileKey, String fileName, String fileType, String source) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("file_id", fileId);
                meta.put("file_key", fileKey);
                meta.put("file_name", fileName);
                meta.put("file_type", fileType);
                meta.put("source", source != null ? source : "generated");
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_FILE_GENERATED,
                                "assistant", fileName, JSON.toJSONString(meta), null, null, null);
        }

        @Override
        public AgentChatMessage saveFileDeleted(String sessionId, Long userId,
                        List<Map<String, Object>> deletedFiles) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("deleted_files", deletedFiles);
                meta.put("deleted_count", deletedFiles != null ? deletedFiles.size() : 0);
                String content = "已删除" + (deletedFiles != null ? deletedFiles.size() : 0) + "个文件";
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_FILE_DELETED,
                                "assistant", content, JSON.toJSONString(meta), null, null, null);
        }

        @Override
        public AgentChatMessage saveTaskUpdate(String sessionId, Long userId,
                        List<Map<String, Object>> taskList, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("task_list", taskList);
                String content = "任务列表更新，共" + (taskList != null ? taskList.size() : 0) + "个任务";
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_TASK_UPDATE,
                                "assistant", content, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveError(String sessionId, Long userId, String errorMessage,
                        String errorType, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("error_type", errorType);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_ERROR,
                                "system", errorMessage, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveUserEvent(String sessionId, Long userId, String content,
                        Map<String, Object> metadata) {
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_USER_EVENT,
                                "system", content, metadata != null ? JSON.toJSONString(metadata) : "{}", null, null,
                                null);
        }

        @Override
        public AgentChatMessage saveSystemAction(String sessionId, Long userId, String toolName,
                        Map<String, Object> actionInput, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("tool_name", toolName);
                meta.put("action_input", actionInput);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_SYSTEM_ACTION,
                                "assistant", toolName, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveSystemResult(String sessionId, Long userId, String content,
                        Map<String, Object> data, Long parentId, Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("success", data != null && data.containsKey("success") ? data.get("success") : true);
                meta.put("data", data);
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_SYSTEM_RESULT,
                                "assistant", content, JSON.toJSONString(meta), parentId, roundIndex, null);
        }

        @Override
        public AgentChatMessage saveAgentProactiveMessage(String sessionId, Long userId, String content,
                        Integer roundIndex) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("trigger_source", "SCHEDULED_TASK");
                return saveMessage(sessionId, userId, AgentChatMessage.TYPE_AGENT_PROACTIVE,
                                "assistant", content, JSON.toJSONString(meta), null, roundIndex, null);
        }

        @Override
        public Page<AgentChatMessage> getMessages(String sessionId, String messageType,
                        int page, int size) {
                Page<AgentChatMessage> pageReq = new Page<>(page, size);
                LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .eq(messageType != null && !messageType.isBlank(),
                                                AgentChatMessage::getMessageType, messageType)
                                .orderByAsc(AgentChatMessage::getSortOrder);
                return chatMessageMapper.selectPage(pageReq, wrapper);
        }

        @Override
        public List<AgentChatMessage> getRecentMessages(String sessionId, int limit) {
                LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .orderByDesc(AgentChatMessage::getSortOrder)
                                .last("LIMIT " + limit);
                List<AgentChatMessage> messages = chatMessageMapper.selectList(wrapper);
                messages.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
                return messages;
        }

        @Override
        public int countBySession(String sessionId) {
                LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentChatMessage::getSessionId, sessionId);
                return Math.toIntExact(chatMessageMapper.selectCount(wrapper));
        }

        @Override
        public int countBySessionAndTypes(String sessionId, List<String> types) {
                if (types == null || types.isEmpty()) {
                        return 0;
                }
                LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .in(AgentChatMessage::getMessageType, types);
                return Math.toIntExact(chatMessageMapper.selectCount(wrapper));
        }

        @Override
        public int getNextSortOrder(String sessionId) {
                LambdaQueryWrapper<AgentChatMessage> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .orderByDesc(AgentChatMessage::getSortOrder)
                                .last("LIMIT 1");
                AgentChatMessage last = chatMessageMapper.selectOne(wrapper);
                return last == null ? 1 : last.getSortOrder() + 1;
        }

        @Override
        public void updateContent(Long id, String content) {
                AgentChatMessage msg = chatMessageMapper.selectById(id);
                if (msg != null) {
                        msg.setContent(content);
                        chatMessageMapper.updateById(msg);
                }
        }

        @Override
        public Map<String, Object> getMessagesByRounds(String sessionId, int rounds, Integer beforeSortOrder) {
                LambdaQueryWrapper<AgentChatMessage> userMsgWrapper = new LambdaQueryWrapper<>();
                userMsgWrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .eq(AgentChatMessage::getMessageType, AgentChatMessage.TYPE_USER_MESSAGE)
                                .lt(beforeSortOrder != null, AgentChatMessage::getSortOrder, beforeSortOrder)
                                .orderByDesc(AgentChatMessage::getSortOrder)
                                .last("LIMIT " + rounds);
                List<AgentChatMessage> userMessages = chatMessageMapper.selectList(userMsgWrapper);

                if (userMessages.isEmpty()) {
                        LambdaQueryWrapper<AgentChatMessage> allMsgWrapper2 = new LambdaQueryWrapper<>();
                        allMsgWrapper2.eq(AgentChatMessage::getSessionId, sessionId)
                                        .orderByAsc(AgentChatMessage::getSortOrder);
                        List<AgentChatMessage> allMessages = chatMessageMapper.selectList(allMsgWrapper2);

                        Map<String, Object> result = new HashMap<>();
                        result.put("messages", allMessages);
                        result.put("has_more", false);
                        result.put("total_rounds", 0);
                        result.put("loaded_rounds", 0);
                        return result;
                }

                int minSortOrder = userMessages.stream()
                                .mapToInt(AgentChatMessage::getSortOrder)
                                .min()
                                .orElse(0);
                int maxSortOrder = userMessages.stream()
                                .mapToInt(AgentChatMessage::getSortOrder)
                                .max()
                                .orElse(0);

                LambdaQueryWrapper<AgentChatMessage> allMsgWrapper = new LambdaQueryWrapper<>();
                allMsgWrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .ge(AgentChatMessage::getSortOrder, minSortOrder)
                                .orderByAsc(AgentChatMessage::getSortOrder);
                List<AgentChatMessage> messages = chatMessageMapper.selectList(allMsgWrapper);

                LambdaQueryWrapper<AgentChatMessage> countWrapper = new LambdaQueryWrapper<>();
                countWrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .eq(AgentChatMessage::getMessageType, AgentChatMessage.TYPE_USER_MESSAGE);
                long totalUserMsgs = chatMessageMapper.selectCount(countWrapper);

                LambdaQueryWrapper<AgentChatMessage> hasMoreWrapper = new LambdaQueryWrapper<>();
                hasMoreWrapper.eq(AgentChatMessage::getSessionId, sessionId)
                                .eq(AgentChatMessage::getMessageType, AgentChatMessage.TYPE_USER_MESSAGE)
                                .lt(AgentChatMessage::getSortOrder, minSortOrder);
                boolean hasMore = chatMessageMapper.selectCount(hasMoreWrapper) > 0;

                Map<String, Object> result = new HashMap<>();
                result.put("messages", messages);
                result.put("has_more", hasMore);
                result.put("total_rounds", (int) totalUserMsgs);
                result.put("loaded_rounds", userMessages.size());
                return result;
        }
}
