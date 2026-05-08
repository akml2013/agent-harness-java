<template>
  <div class="chat-area">
    <div class="chat-header">
      <div class="chat-header-left">
        <h3 class="chat-title">
          {{ chatStore.currentAgent?.agentName || "新的对话" }}
        </h3>
        <el-tag
          v-if="chatStore.currentAgent"
          size="small"
          :type="statusTagType"
          effect="plain"
        >
          {{ statusLabel }}
        </el-tag>
      </div>
      <div class="chat-header-right">
        <el-tooltip v-if="chatStore.sendingMessage" content="停止当前操作">
          <el-button
            :icon="VideoPause"
            text
            circle
            type="danger"
            @click="handleStopReAct"
          />
        </el-tooltip>
        <el-tooltip :content="rightPanelVisible ? '隐藏工作台' : '显示工作台'">
          <el-button :icon="InfoFilled" text circle @click="toggleRightPanel" />
        </el-tooltip>
      </div>
    </div>

    <div
      class="chat-messages"
      ref="messagesContainer"
      @scroll="handleContainerScroll"
    >
      <div
        v-if="
          !chatStore.currentAgentId || chatStore.currentMessages.length === 0
        "
        class="chat-welcome"
      >
        <div class="welcome-icon">
          <el-icon :size="48"><Monitor /></el-icon>
        </div>
        <h2 class="welcome-title">AI Agent 企业智能办公助手</h2>
        <p class="welcome-desc">
          我可以帮您处理文档、生成报告、分析数据等办公任务
        </p>
        <div class="welcome-suggestions">
          <div
            v-for="suggestion in suggestions"
            :key="suggestion.text"
            class="suggestion-card"
            @click="handleSuggestion(suggestion.text)"
          >
            <el-icon :size="20"><component :is="suggestion.icon" /></el-icon>
            <span>{{ suggestion.text }}</span>
          </div>
        </div>
      </div>

      <div v-else class="messages-list">
        <div
          v-if="chatStore.currentHasMoreMessages"
          class="load-more-container"
        >
          <el-button
            type="primary"
            text
            size="small"
            :loading="loadingMore"
            @click="handleLoadMore"
          >
            加载更多消息
          </el-button>
        </div>

        <template v-for="message in processedMessages" :key="message.id">
          <template v-if="message.messageType === 'USER_MESSAGE'">
            <div class="message-item">
              <div class="message-row user-row">
                <div class="message-body">
                  <div class="message-header">
                    <span class="message-name">{{
                      userStore.displayName
                    }}</span>
                    <span class="message-time">{{
                      formatMsgTime(message.createTime)
                    }}</span>
                  </div>
                  <div class="message-content user-content">
                    {{ message.content }}
                  </div>
                </div>
                <div class="message-avatar">
                  <el-avatar :size="36" class="avatar-user">
                    {{ userStore.displayName?.charAt(0) }}
                  </el-avatar>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'AI_TEXT'">
            <div class="message-item">
              <div class="message-row assistant-row">
                <div class="message-avatar">
                  <el-avatar :size="36" class="avatar-assistant">
                    <el-icon><Monitor /></el-icon>
                  </el-avatar>
                </div>
                <div class="message-body">
                  <div class="message-header">
                    <span class="message-name">AI Agent</span>
                    <span class="message-time">{{
                      formatMsgTime(message.createTime)
                    }}</span>
                    <span v-if="message.durationMs" class="message-duration"
                      >{{ message.durationMs }}ms</span
                    >
                  </div>
                  <div class="message-content content-markdown">
                    <div v-html="renderMarkdown(message.content || '')"></div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'AI_THOUGHT_BRIEF'">
            <div class="message-item">
              <div
                class="thought-card"
                :class="{ 'thought-streaming': message._thoughtStreaming }"
              >
                <div class="thought-header" @click="toggleThought(message.id)">
                  <el-icon
                    :size="14"
                    :color="
                      message._thoughtStreaming
                        ? 'var(--accent-color)'
                        : 'var(--text-secondary)'
                    "
                    ><Cpu
                  /></el-icon>
                  <span class="thought-label">{{
                    message._thoughtStreaming ? "正在思考..." : "思考过程"
                  }}</span>
                  <el-icon
                    v-if="message._thoughtStreaming"
                    class="mcp-loading-icon thought-loading"
                  >
                    <Loading />
                  </el-icon>
                  <el-icon
                    v-else
                    class="thought-toggle"
                    :class="{ expanded: expandedThoughts.has(message.id) }"
                  >
                    <ArrowRight />
                  </el-icon>
                </div>
                <div
                  v-show="
                    expandedThoughts.has(message.id) ||
                    message._thoughtStreaming
                  "
                  class="thought-content"
                >
                  {{ message.content }}
                  <span v-if="message._thoughtStreaming" class="thought-cursor"
                    >|</span
                  >
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'AI_THOUGHT'">
          </template>

          <template
            v-else-if="
              message.messageType === 'MCP_ACTION' ||
              message.messageType === 'SYSTEM_ACTION'
            "
          >
            <div class="message-item">
              <div
                :class="
                  message.messageType === 'SYSTEM_ACTION'
                    ? 'mcp-card system-action-card'
                    : 'mcp-card mcp-action-card'
                "
              >
                <div class="mcp-header">
                  <el-icon
                    :size="14"
                    :color="
                      message.messageType === 'SYSTEM_ACTION'
                        ? 'var(--success-color)'
                        : 'var(--accent-color)'
                    "
                    ><SetUp
                  /></el-icon>
                  <span class="mcp-label"
                    >{{
                      message.messageType === "SYSTEM_ACTION"
                        ? "系统操作："
                        : "工具调用："
                    }}{{ getToolChineseName(message.metadata?.tool_name)
                    }}{{
                      message._batchCount
                        ? ` (${message._batchCount}项操作)`
                        : ""
                    }}</span
                  >
                  <template v-if="message._resultStatus === 'loading'">
                    <el-tag
                      size="small"
                      type="primary"
                      effect="plain"
                      class="mcp-status-tag"
                    >
                      <el-icon
                        class="is-loading"
                        :size="10"
                        style="margin-right: 2px"
                        ><Loading
                      /></el-icon>
                      执行中
                    </el-tag>
                  </template>
                  <template v-else-if="message._resultStatus === 'success'">
                    <el-tag
                      size="small"
                      type="success"
                      effect="plain"
                      class="mcp-status-tag"
                      >成功</el-tag
                    >
                  </template>
                  <template v-else-if="message._resultStatus === 'failed'">
                    <el-tag
                      size="small"
                      type="danger"
                      effect="plain"
                      class="mcp-status-tag"
                      >失败</el-tag
                    >
                  </template>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'ASK_USER'">
            <div class="message-item">
              <div class="ask-user-card">
                <div class="ask-header">
                  <el-icon :size="16" color="var(--warning-color)"
                    ><QuestionFilled
                  /></el-icon>
                  <span class="ask-label">需要您的确认</span>
                </div>
                <div class="ask-question">{{ message.content }}</div>
                <div class="ask-options">
                  <el-button
                    v-for="opt in message.metadata?.options || ['确认', '取消']"
                    :key="opt"
                    type="primary"
                    plain
                    size="small"
                    @click="handleOptionSelect(opt)"
                  >
                    {{ opt }}
                  </el-button>
                </div>
                <div class="ask-custom">
                  <el-input
                    v-model="customInput"
                    placeholder="或输入自定义内容..."
                    size="small"
                    @keydown.enter="handleCustomSubmit"
                  >
                    <template #append>
                      <el-button
                        @click="handleCustomSubmit"
                        :disabled="!customInput.trim()"
                        >发送</el-button
                      >
                    </template>
                  </el-input>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'USER_INPUT'">
            <div class="message-item">
              <div class="message-row user-row">
                <div class="message-body">
                  <div class="message-header">
                    <span class="message-name">{{
                      userStore.displayName
                    }}</span>
                    <span class="message-time">{{
                      formatMsgTime(message.createTime)
                    }}</span>
                  </div>
                  <div class="message-content user-content">
                    {{ message.content }}
                  </div>
                </div>
                <div class="message-avatar">
                  <el-avatar :size="36" class="avatar-user">
                    {{ userStore.displayName?.charAt(0) }}
                  </el-avatar>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'FILE_GENERATED'">
            <div class="message-item">
              <div class="file-card">
                <div class="file-header">
                  <el-icon :size="16" color="var(--accent-color)"
                    ><Document
                  /></el-icon>
                  <span class="file-label">{{
                    message.metadata?.source === "upload"
                      ? "上传文件"
                      : "生成文件"
                  }}</span>
                </div>
                <div class="file-info">
                  <el-icon
                    :size="24"
                    class="file-type-icon"
                    :class="`file-type-${getFileTypeClass(message.metadata?.file_type)}`"
                  >
                    <component :is="getFileIcon(message.metadata?.file_type)" />
                  </el-icon>
                  <div class="file-detail">
                    <div class="file-name">
                      {{ message.metadata?.file_name || message.content }}
                    </div>
                    <div class="file-meta">
                      {{ (message.metadata?.file_type || "").toUpperCase() }}
                    </div>
                  </div>
                  <el-button
                    v-if="message.metadata?.file_id"
                    size="small"
                    type="primary"
                    text
                    @click="handleDownload(message.metadata.file_id)"
                  >
                    <el-icon><Download /></el-icon>
                    下载
                  </el-button>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'TASK_UPDATE'">
            <div class="message-item">
              <div class="task-card">
                <div class="task-header">
                  <el-icon :size="14" color="var(--accent-color)"
                    ><List
                  /></el-icon>
                  <span class="task-label">任务列表</span>
                </div>
                <div class="task-content">
                  <div
                    v-for="task in parseTaskList(message.metadata?.task_list)"
                    :key="task.index"
                    class="task-item"
                    :class="`task-status-${task.status.toLowerCase()}`"
                  >
                    <el-icon
                      v-if="task.status === 'COMPLETED'"
                      :size="14"
                      color="var(--success-color)"
                      ><CircleCheck
                    /></el-icon>
                    <el-icon
                      v-else-if="task.status === 'RUNNING'"
                      :size="14"
                      class="mcp-loading-icon"
                      color="var(--accent-color)"
                      ><Loading
                    /></el-icon>
                    <el-icon
                      v-else-if="task.status === 'FAILED'"
                      :size="14"
                      color="var(--danger-color)"
                      ><CircleCloseFilled
                    /></el-icon>
                    <el-icon v-else :size="14" color="var(--text-secondary)"
                      ><List
                    /></el-icon>
                    <span class="task-desc">{{ task.description }}</span>
                    <el-tag
                      v-if="task.status === 'COMPLETED'"
                      size="small"
                      type="success"
                      effect="plain"
                      >已完成</el-tag
                    >
                    <el-tag
                      v-else-if="task.status === 'RUNNING'"
                      size="small"
                      type="primary"
                      effect="plain"
                      >进行中</el-tag
                    >
                    <el-tag
                      v-else-if="task.status === 'FAILED'"
                      size="small"
                      type="danger"
                      effect="plain"
                      >失败</el-tag
                    >
                    <el-tag v-else size="small" type="info" effect="plain"
                      >待执行</el-tag
                    >
                  </div>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="message.messageType === 'FILE_DELETED'">
            <div class="message-item">
              <div
                class="file-card"
                style="
                  border-color: var(--danger-color);
                  background: var(--bg-context-danger-hover);
                "
              >
                <div class="file-header">
                  <el-icon :size="16" color="var(--danger-color)"
                    ><Delete
                  /></el-icon>
                  <span class="file-label" style="color: var(--danger-color)"
                    >删除文件</span
                  >
                </div>
                <div
                  class="file-info"
                  v-for="(f, idx) in message.metadata?.deleted_files"
                  :key="idx"
                >
                  <el-icon
                    :size="24"
                    class="file-type-icon"
                    :class="`file-type-${getFileTypeClass(f.file_type)}`"
                  >
                    <component :is="getFileIcon(f.file_type)" />
                  </el-icon>
                  <div class="file-detail">
                    <div
                      class="file-name"
                      style="
                        text-decoration: line-through;
                        color: var(--text-secondary);
                      "
                    >
                      {{ f.file_name }}
                    </div>
                    <div class="file-meta" style="color: var(--danger-color)">
                      {{ (f.file_type || "").toUpperCase() }} · 已删除
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </template>

        <div
          v-if="
            chatStore.sendingMessage &&
            !chatStore.waitingUserInput.waiting &&
            !hasLoadingMcpAction &&
            !currentIsCompressing
          "
          class="loading-indicator"
        >
          <el-icon class="loading-icon"><Loading /></el-icon>
          <span>AI 正在思考...</span>
        </div>

        <div
          v-if="
            chatStore.sendingMessage &&
            !chatStore.waitingUserInput.waiting &&
            !hasLoadingMcpAction &&
            currentIsCompressing
          "
          class="loading-indicator compressing-indicator"
        >
          <el-icon class="loading-icon compressing-icon"><Loading /></el-icon>
          <span>压缩中...</span>
        </div>

        <div
          v-if="chatStore.incompleteTaskList.showing"
          class="incomplete-task-banner"
        >
          <div class="incomplete-task-banner-content">
            <el-icon :size="18" color="var(--warning-color)"
              ><WarningFilled
            /></el-icon>
            <span class="incomplete-task-message">{{
              chatStore.incompleteTaskList.message
            }}</span>
            <div class="incomplete-task-items">
              <div
                v-for="task in chatStore.incompleteTaskList.taskItems"
                :key="task.index"
                class="incomplete-task-item"
              >
                <el-icon
                  v-if="task.status === 'COMPLETED'"
                  :size="12"
                  color="var(--success-color)"
                  ><CircleCheck
                /></el-icon>
                <el-icon v-else :size="12" color="var(--text-secondary)"
                  ><List
                /></el-icon>
                <span>{{ task.description }}</span>
                <el-tag
                  size="small"
                  :type="task.status === 'COMPLETED' ? 'success' : 'info'"
                  effect="plain"
                  >{{
                    task.status === "COMPLETED" ? "已完成" : "待执行"
                  }}</el-tag
                >
              </div>
            </div>
            <div class="incomplete-task-actions">
              <el-button
                type="primary"
                size="small"
                @click="handleContinueTaskList"
              >
                继续执行
              </el-button>
              <el-button size="small" @click="handleDismissIncompleteTaskList">
                暂不继续
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="input-toolbar">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleFileChange"
          multiple
        >
          <el-tooltip content="上传文件">
            <el-button :icon="UploadFilled" text size="small" />
          </el-tooltip>
        </el-upload>
      </div>
      <div class="input-wrapper">
        <el-input
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="输入消息，按 Enter 发送，Shift+Enter 换行..."
          resize="none"
          :disabled="chatStore.sendingMessage || !chatStore.currentAgentId"
          @keydown="handleKeyDown"
        />
        <el-button
          class="send-btn"
          type="primary"
          :icon="Promotion"
          circle
          :disabled="
            !inputText.trim() ||
            chatStore.sendingMessage ||
            !chatStore.currentAgentId
          "
          @click="handleSend"
        />
      </div>
      <div class="input-hint">
        <span>AI Agent 可能会出错，请核实重要信息</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getDownloadUrl, uploadAgentFile } from "@/services/agentApi";
import { useChatStore, type Message } from "@/stores/chat";
import { useUserStore } from "@/stores/user";
import {
  ArrowRight,
  CircleCheck,
  CircleCloseFilled,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
  Download,
  EditPen,
  InfoFilled,
  List,
  Loading,
  Monitor,
  Promotion,
  QuestionFilled,
  SetUp,
  TrendCharts,
  UploadFilled,
  VideoPause,
  WarningFilled,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from "vue";

const chatStore = useChatStore();
const userStore = useUserStore();

const inputText = ref("");
const customInput = ref("");
const loadingMore = ref(false);
const messagesContainer = ref<HTMLElement | null>(null);
const expandedThoughts = reactive(new Set<number>());

let userScrolling = false;
let scrollTimer: ReturnType<typeof setTimeout> | null = null;

function handleContainerScroll() {
  if (!messagesContainer.value) return;
  const el = messagesContainer.value;
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
  if (distFromBottom > 80) {
    userScrolling = true;
  }
  if (scrollTimer) {
    clearTimeout(scrollTimer);
  }
  scrollTimer = setTimeout(() => {
    userScrolling = false;
  }, 5000);
}

function scrollToBottom() {
  nextTick(() => {
    if (userScrolling) return;
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
}

onBeforeUnmount(() => {
  if (scrollTimer) {
    clearTimeout(scrollTimer);
  }
});

defineProps<{
  rightPanelVisible: boolean;
}>();

const emit = defineEmits<{
  (e: "toggle-right-panel"): void;
}>();

const TOOL_NAME_MAP: Record<string, string> = {};

function getToolChineseName(toolName?: string): string {
  if (!toolName) return "工具";
  return TOOL_NAME_MAP[toolName] || toolName;
}

interface ProcessedMessage extends Message {
  _resultStatus?: "loading" | "success" | "failed";
  _batchCount?: number;
  _hidden?: boolean;
}

const MODIFY_TOOLS = new Set(["modify_word", "modify_excel"]);

function getFileKeyFromAction(msg: Message): string | null {
  const meta = msg.metadata;
  if (!meta) return null;
  const actionInput = meta.action_input;
  if (!actionInput) return null;
  if (typeof actionInput === "object" && actionInput.file_key) {
    return String(actionInput.file_key);
  }
  return null;
}

const processedMessages = computed(() => {
  const msgs = chatStore.currentMessages;
  const result: ProcessedMessage[] = [];
  const resultMap = new Map<number, Message>();

  for (const msg of msgs) {
    if (
      (msg.messageType === "MCP_RESULT" ||
        msg.messageType === "SYSTEM_RESULT") &&
      msg.parentId
    ) {
      resultMap.set(msg.parentId, msg);
    }
  }

  const actionMsgs: ProcessedMessage[] = [];
  for (const msg of msgs) {
    if (
      msg.messageType === "MCP_RESULT" ||
      msg.messageType === "SYSTEM_RESULT"
    ) {
      continue;
    }

    if (
      msg.messageType === "MCP_ACTION" ||
      msg.messageType === "SYSTEM_ACTION"
    ) {
      const processed: ProcessedMessage = { ...msg };
      const resultMsg = resultMap.get(msg.id);
      if (resultMsg) {
        const meta = resultMsg.metadata;
        if (meta?.success === false || meta?.success === "false") {
          processed._resultStatus = "failed";
        } else {
          processed._resultStatus = "success";
        }
      } else {
        processed._resultStatus = "loading";
      }
      const meta = msg.metadata;
      if (meta?.action_input?.batch_count) {
        processed._batchCount = meta.action_input.batch_count;
      }
      actionMsgs.push(processed);
    } else {
      actionMsgs.push(msg);
    }
  }

  for (let i = 0; i < actionMsgs.length; i++) {
    const msg = actionMsgs[i];
    if (msg._hidden) continue;

    if (
      msg.messageType === "MCP_ACTION" &&
      !msg._batchCount &&
      MODIFY_TOOLS.has(msg.metadata?.tool_name)
    ) {
      const fileKey = getFileKeyFromAction(msg);
      if (fileKey) {
        let mergeCount = 1;
        for (let j = i + 1; j < actionMsgs.length; j++) {
          const next = actionMsgs[j];
          if (next.messageType !== "MCP_ACTION") break;
          if (!MODIFY_TOOLS.has(next.metadata?.tool_name)) break;
          if (next._batchCount) break;
          const nextFileKey = getFileKeyFromAction(next);
          if (nextFileKey !== fileKey) break;
          next._hidden = true;
          mergeCount++;
        }
        if (mergeCount > 1) {
          msg._batchCount = mergeCount;
        }
      }
    }

    result.push(msg);
  }

  return result;
});

const hasLoadingMcpAction = computed(() => {
  const msgs = processedMessages.value;
  if (msgs.length === 0) return false;
  const lastMsg = msgs[msgs.length - 1];
  return (
    (lastMsg.messageType === "MCP_ACTION" ||
      lastMsg.messageType === "SYSTEM_ACTION") &&
    (lastMsg as ProcessedMessage)._resultStatus === "loading"
  );
});

const currentIsCompressing = computed(() => {
  return chatStore.isCompressing[chatStore.currentAgentId] || false;
});

interface TaskItemParsed {
  index: number;
  description: string;
  status: string;
}

function parseTaskList(taskList: any): TaskItemParsed[] {
  if (!taskList) return [];
  if (Array.isArray(taskList)) return taskList;
  try {
    return typeof taskList === "string" ? JSON.parse(taskList) : [];
  } catch {
    return [];
  }
}

const suggestions = [
  { text: "帮我分析销售数据", icon: DataAnalysis },
  { text: "生成本周工作周报", icon: EditPen },
  { text: "上传文档并提取关键信息", icon: Document },
  { text: "创建数据可视化报告", icon: TrendCharts },
];

const statusTagType = ref<"success" | "warning" | "info" | "danger">("success");
const statusLabel = ref("在线");

watch(
  () => chatStore.currentAgent?.runtimeStatus,
  (status) => {
    if (status === "RUNNING") {
      statusTagType.value = "success";
      statusLabel.value = "进行中";
    } else if (status === "FINISHED") {
      statusTagType.value = "info";
      statusLabel.value = "已完成";
    } else if (status === "WAITING_USER_INPUT") {
      statusTagType.value = "warning";
      statusLabel.value = "等待输入";
    } else {
      statusTagType.value = "info";
      statusLabel.value = status || "未知";
    }
  },
  { immediate: true },
);

function toggleRightPanel() {
  emit("toggle-right-panel");
}

function toggleThought(id: number) {
  if (expandedThoughts.has(id)) {
    expandedThoughts.delete(id);
  } else {
    expandedThoughts.add(id);
  }
}

async function handleSend() {
  const text = inputText.value.trim();
  if (!text || chatStore.sendingMessage) return;
  inputText.value = "";
  if (chatStore.incompleteTaskList.showing) {
    chatStore.dismissIncompleteTaskList();
  }
  await chatStore.sendUserMessage(text);
  scrollToBottom();
}

function handleKeyDown(event: Event) {
  const kbEvent = event as KeyboardEvent;
  if (kbEvent.key === "Enter" && !kbEvent.shiftKey) {
    event.preventDefault();
    handleSend();
  }
}

async function handleSuggestion(text: string) {
  if (!chatStore.currentAgentId) {
    await chatStore.createNewAgent();
  }
  await chatStore.sendUserMessage(text);
  scrollToBottom();
}

async function handleOptionSelect(option: string) {
  await chatStore.respondToUserInput(option);
  scrollToBottom();
}

async function handleContinueTaskList() {
  await chatStore.continueTaskList();
  scrollToBottom();
}

async function handleStopReAct() {
  await chatStore.stopReAct();
}

async function handleLoadMore() {
  loadingMore.value = true;
  const container = messagesContainer.value;
  const prevScrollHeight = container?.scrollHeight || 0;
  await chatStore.loadMoreMessages();
  loadingMore.value = false;
  nextTick(() => {
    if (container) {
      container.scrollTop = container.scrollHeight - prevScrollHeight;
    }
  });
}

function handleDismissIncompleteTaskList() {
  chatStore.dismissIncompleteTaskList();
}

async function handleCustomSubmit() {
  const text = customInput.value.trim();
  if (!text) return;
  customInput.value = "";
  await chatStore.respondToUserInput("", text);
  scrollToBottom();
}

function handleFileChange(uploadFile: any) {
  if (!chatStore.currentAgentId) {
    ElMessage.warning("请先创建Agent");
    return;
  }
  const file = uploadFile.raw || uploadFile;
  if (!file) return;
  uploadAgentFile(chatStore.currentAgentId, file)
    .then((res: any) => {
      if (res.success) {
        ElMessage.success(`文件 ${res.file_name} 上传成功`);
        chatStore.loadMessages(chatStore.currentAgentId);
        chatStore.loadFiles(chatStore.currentAgentId);
      } else {
        ElMessage.error(res.error || "上传失败");
      }
    })
    .catch(() => {
      ElMessage.error("文件上传失败");
    });
}

async function handleDownload(fileId: number) {
  try {
    const res = await getDownloadUrl(fileId);
    if (res.success && res.download_url) {
      window.open(res.download_url, "_blank");
    } else {
      ElMessage.error("获取下载链接失败");
    }
  } catch {
    ElMessage.error("下载失败");
  }
}

function getFileTypeClass(fileType?: string): string {
  if (!fileType) return "default";
  const t = fileType.toLowerCase();
  if (["doc", "docx"].includes(t)) return "word";
  if (["xls", "xlsx"].includes(t)) return "excel";
  if (["pdf"].includes(t)) return "pdf";
  if (["txt", "md"].includes(t)) return "text";
  if (["png", "jpg", "jpeg", "gif", "svg"].includes(t)) return "image";
  return "default";
}

function getFileIcon(fileType?: string) {
  const t = (fileType || "").toLowerCase();
  if (["doc", "docx"].includes(t)) return "Document";
  if (["xls", "xlsx"].includes(t)) return "Grid";
  if (["pdf"].includes(t)) return "Document";
  if (["png", "jpg", "jpeg", "gif", "svg"].includes(t)) return "Picture";
  return "Document";
}

function formatMsgTime(timeStr: string | null): string {
  if (!timeStr) return "";
  try {
    const date = new Date(timeStr);
    return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
  } catch {
    return "";
  }
}

function renderMarkdown(content: string): string {
  if (!content) return "";
  let html = content
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  html = html.replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>");
  html = html.replace(/\*(.*?)\*/g, "<em>$1</em>");
  html = html.replace(/`(.*?)`/g, "<code>$1</code>");
  html = html.replace(/^### (.*$)/gm, "<h4>$1</h4>");
  html = html.replace(/^## (.*$)/gm, "<h3>$1</h3>");
  html = html.replace(/^# (.*$)/gm, "<h2>$1</h2>");
  html = html.replace(/^- (.*$)/gm, "<li>$1</li>");
  html = html.replace(/^\d+\. (.*$)/gm, "<li>$1</li>");
  html = html.replace(/\n\n/g, "<br/><br/>");
  html = html.replace(/\n/g, "<br/>");

  return html;
}

watch(
  () => chatStore.currentMessages.length,
  () => {
    scrollToBottom();
  },
);

watch(
  () => chatStore.currentAgentId,
  () => {
    userScrolling = false;
    scrollToBottom();
  },
);
</script>

<style scoped>
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-white);
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.chat-header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg-primary);
}

.chat-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}

.welcome-icon {
  margin-bottom: 20px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    var(--bg-welcome-gradient-start) 0%,
    var(--bg-welcome-gradient-end) 100%
  );
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.welcome-icon .el-icon {
  color: white !important;
}

.welcome-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.welcome-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.welcome-suggestions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 500px;
  width: 100%;
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: var(--bg-white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 14px;
  color: var(--text-regular);
}

.suggestion-card:hover {
  border-color: var(--accent-color);
  color: var(--accent-color);
  background: var(--bg-suggestion-hover);
}

.messages-list {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.load-more-container {
  text-align: center;
  padding: 8px 0 16px;
}

.message-item {
  margin-bottom: 16px;
}

.message-row {
  display: flex;
  gap: 12px;
}

.user-row {
  justify-content: flex-end;
}

.assistant-row {
  justify-content: flex-start;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-user {
  background: var(--accent-color);
  color: white;
  font-size: 14px;
}

.avatar-assistant {
  background: var(--accent-color);
  color: white;
}

.message-body {
  max-width: 70%;
  min-width: 0;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.message-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.message-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.message-duration {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-white);
  padding: 1px 6px;
  border-radius: 3px;
}

.message-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
}

.user-content {
  background: var(--bg-chat-user);
  padding: 10px 14px;
  border-radius: 12px 12px 2px 12px;
}

.content-markdown :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 12px 0 8px;
}

.content-markdown :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 10px 0 6px;
}

.content-markdown :deep(h4) {
  font-size: 15px;
  font-weight: 600;
  margin: 8px 0 4px;
}

.content-markdown :deep(strong) {
  font-weight: 600;
}

.content-markdown :deep(code) {
  background: var(--bg-code);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
  color: var(--text-code);
}

.content-markdown :deep(li) {
  margin-left: 16px;
  list-style: disc;
}

.thought-card {
  background: var(--bg-thought);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  margin: 4px 0;
  overflow: hidden;
}

.thought-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}

.thought-label {
  font-size: 12px;
}

.thought-toggle {
  margin-left: auto;
  transition: transform 0.2s;
}

.thought-toggle.expanded {
  transform: rotate(90deg);
}

.thought-content {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  border-top: 1px solid var(--border-light);
  white-space: pre-wrap;
  line-height: 1.6;
}

.thought-streaming {
  border-color: var(--border-thought-streaming);
  background: var(--bg-thought-streaming);
}

.thought-streaming .thought-header {
  color: var(--text-thought-streaming);
}

.thought-loading {
  margin-left: auto;
}

.thought-cursor {
  animation: blink 1s step-end infinite;
  color: var(--accent-color);
  font-weight: bold;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.mcp-card {
  border-radius: var(--radius-md);
  margin: 4px 0;
  overflow: hidden;
}

.mcp-action-card {
  background: var(--bg-mcp-action);
  border: 1px solid var(--border-mcp-action);
}

.system-action-card {
  background: var(--bg-system-action);
  border: 1px solid var(--border-system-action);
}

.mcp-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
}

.mcp-label {
  font-size: 12px;
  font-weight: 500;
}

.mcp-status-tag {
  margin-left: auto;
}

.mcp-loading-icon {
  animation: spin 1s linear infinite;
  margin-left: 4px;
}

.ask-user-card {
  background: var(--bg-ask-user);
  border: 1px solid var(--border-ask-user);
  border-radius: var(--radius-md);
  margin: 4px 0;
  padding: 12px;
}

.ask-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.ask-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--warning-color);
}

.ask-question {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.ask-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.ask-custom {
  margin-top: 8px;
}

.file-card {
  background: var(--bg-file-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  margin: 4px 0;
  padding: 12px;
}

.file-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.file-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-type-icon {
  font-size: 24px;
}

.file-type-word {
  color: var(--color-doc-word);
}

.file-type-excel {
  color: var(--color-doc-excel);
}

.file-type-pdf {
  color: var(--color-doc-pdf);
}

.file-type-text {
  color: var(--color-doc-default);
}

.file-type-image {
  color: var(--color-doc-image);
}

.file-detail {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.task-card {
  background: var(--bg-task-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  margin: 4px 0;
  overflow: hidden;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
}

.task-label {
  font-size: 12px;
  font-weight: 500;
}

.task-content {
  padding: 0 12px 8px;
  font-size: 13px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--border-light);
}

.task-item:last-child {
  border-bottom: none;
}

.task-desc {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
}

.task-status-completed .task-desc {
  text-decoration: line-through;
  color: var(--text-secondary);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.compressing-indicator {
  color: var(--bg-compressing);
}

.compressing-icon {
  color: var(--bg-compressing);
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.incomplete-task-banner {
  padding: 12px 16px;
  margin: 8px 0;
  background: linear-gradient(
    135deg,
    var(--bg-incomplete-banner-start) 0%,
    var(--bg-incomplete-banner-end) 100%
  );
  border: 1px solid var(--warning-color);
  border-radius: 8px;
}

.incomplete-task-banner-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.incomplete-task-message {
  font-size: 14px;
  font-weight: 500;
  color: var(--warning-color);
}

.incomplete-task-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 0;
}

.incomplete-task-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-regular);
}

.incomplete-task-actions {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}

.chat-input-area {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--border-light);
  flex-shrink: 0;
  background: var(--bg-white);
}

.input-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  transition: border-color var(--transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--accent-color);
}

.input-wrapper :deep(.el-textarea__inner) {
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 14px;
  line-height: 1.5;
}

.input-wrapper :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.send-btn {
  flex-shrink: 0;
}

.input-hint {
  text-align: center;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
