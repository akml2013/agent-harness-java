import {
  deleteAgent as apiDeleteAgent,
  createAgentApi,
  DEFAULT_USER_ID,
  fetchAgentFiles,
  fetchAgentMessagesByRounds,
  fetchAgents,
  getAgent,
  getGlobalSseUrl,
  sendAgentMessage,
  stopAgent,
  updateAgent,
  type AgentInfo,
  type ChatMessage,
  type FileInfo,
} from "@/services/agentApi";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

export type MessageType =
  | "USER_MESSAGE"
  | "AI_TEXT"
  | "AI_THOUGHT"
  | "AI_THOUGHT_BRIEF"
  | "MCP_ACTION"
  | "MCP_RESULT"
  | "SYSTEM_ACTION"
  | "SYSTEM_RESULT"
  | "ASK_USER"
  | "USER_INPUT"
  | "FILE_GENERATED"
  | "FILE_DELETED"
  | "TASK_UPDATE"
  | "ERROR"
  | "USER_EVENT"
  | "AGENT_PROACTIVE";

export interface Agent {
  agentId: string;
  agentName: string;
  agentAvatar: string | null;
  capabilities: string;
  runtimeStatus: string;
  messageCount: number;
  lastMessageTime: string | null;
  createTime: string | null;
  sessionId: string;
}

export interface Message {
  id: number;
  messageType: MessageType;
  role: string;
  content: string | null;
  metadata: any;
  parentId: number | null;
  roundIndex: number | null;
  sortOrder: number;
  durationMs: number | null;
  createTime: string | null;
  _thoughtStreaming?: boolean;
}

export interface FileItem {
  fileId: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  source: string;
  createTime: string | null;
  downloadUrl: string | null;
}

export interface WaitingUserInput {
  waiting: boolean;
  question: string;
  options: string[];
}

export interface IncompleteTaskList {
  showing: boolean;
  message: string;
  taskItems: Array<{
    index: number;
    description: string;
    status: string;
    error_message?: string;
  }>;
}

function mapAgent(a: AgentInfo): Agent {
  return {
    agentId: a.agent_id,
    agentName: a.agent_name,
    agentAvatar: a.agent_avatar,
    capabilities: a.capabilities,
    runtimeStatus: a.runtime_status,
    messageCount: a.message_count,
    lastMessageTime: a.last_message_time,
    createTime: a.create_time,
    sessionId: a.session_id || "",
  };
}

function mapMessage(m: ChatMessage): Message {
  let metadata = null;
  if (m.metadata) {
    try {
      metadata =
        typeof m.metadata === "string" ? JSON.parse(m.metadata) : m.metadata;
    } catch {
      metadata = m.metadata;
    }
  }
  return {
    id: m.id,
    messageType: m.message_type as MessageType,
    role: m.role,
    content: m.content,
    metadata,
    parentId: m.parent_id,
    roundIndex: m.round_index,
    sortOrder: m.sort_order,
    durationMs: m.duration_ms,
    createTime: m.create_time,
  };
}

function mapSsePayload(payload: any): Message {
  let metadata = null;
  if (payload.metadata) {
    try {
      metadata =
        typeof payload.metadata === "string"
          ? JSON.parse(payload.metadata)
          : payload.metadata;
    } catch {
      metadata = payload.metadata;
    }
  }
  return {
    id: payload.id,
    messageType: payload.message_type as MessageType,
    role: payload.role,
    content: payload.content,
    metadata,
    parentId: payload.parent_id,
    roundIndex: payload.round_index,
    sortOrder: payload.sort_order,
    durationMs: payload.duration_ms,
    createTime: payload.create_time,
  };
}

export const useChatStore = defineStore("chat", () => {
  const agents = ref<Agent[]>([]);
  const currentAgentId = ref<string>("");
  const messages = ref<Record<string, Message[]>>({});
  const files = ref<Record<string, FileItem[]>>({});
  const loading = ref(false);
  const sendingMessage = ref(false);
  const waitingUserInput = ref<WaitingUserInput>({
    waiting: false,
    question: "",
    options: [],
  });
  const incompleteTaskList = ref<IncompleteTaskList>({
    showing: false,
    message: "",
    taskItems: [],
  });
  const newFileCount = ref<Record<string, number>>({});
  const newScheduledTaskCount = ref<Record<string, number>>({});
  const newSessionTaskCount = ref<Record<string, number>>({});
  const newWorkflowCount = ref<Record<string, number>>({});
  const isCompressing = ref<Record<string, boolean>>({});
  const globalEventSource = ref<EventSource | null>(null);
  const globalSseConnected = ref(false);
  const reconnectAttempt = ref(0);
  const MAX_RECONNECT_ATTEMPTS = 10;
  let sseConnectTimer: ReturnType<typeof setTimeout> | null = null;
  let sseReconnectTimer: ReturnType<typeof setTimeout> | null = null;
  const sseClientId = crypto.randomUUID();
  const hasMoreMessages = ref<Record<string, boolean>>({});
  const totalRounds = ref<Record<string, number>>({});
  const earliestSortOrder = ref<Record<string, number | null>>({});

  const currentMessages = computed(() => {
    const all = messages.value[currentAgentId.value] || [];
    return all.filter((m) => m.messageType !== "AGENT_PROACTIVE");
  });

  const currentAgent = computed(() => {
    return agents.value.find((a) => a.agentId === currentAgentId.value);
  });

  const currentFiles = computed(() => {
    return files.value[currentAgentId.value] || [];
  });

  const currentNewFileCount = computed(() => {
    return newFileCount.value[currentAgentId.value] || 0;
  });

  const currentNewScheduledTaskCount = computed(() => {
    return newScheduledTaskCount.value[currentAgentId.value] || 0;
  });

  const currentNewSessionTaskCount = computed(() => {
    return newSessionTaskCount.value[currentAgentId.value] || 0;
  });

  const currentNewWorkflowCount = computed(() => {
    return newWorkflowCount.value[currentAgentId.value] || 0;
  });

  const currentHasMoreMessages = computed(() => {
    return hasMoreMessages.value[currentAgentId.value] || false;
  });

  async function loadAgents() {
    try {
      const res = await fetchAgents(DEFAULT_USER_ID);
      if (res.success) {
        agents.value = res.agents.map(mapAgent);
      }
      if (!globalSseConnected.value) {
        connectGlobalSse();
      }
    } catch (e) {
      console.error("加载Agent列表失败:", e);
    }
  }

  async function createNewAgent() {
    try {
      const res = await createAgentApi(DEFAULT_USER_ID);
      if (res.success) {
        const agent: Agent = {
          agentId: res.agent_id,
          agentName: res.agent_name,
          agentAvatar: res.agent_avatar,
          capabilities: res.capabilities,
          runtimeStatus: res.runtime_status,
          messageCount: 0,
          lastMessageTime: null,
          createTime: null,
          sessionId: res.session_id,
        };
        agents.value.unshift(agent);
        messages.value[agent.agentId] = [];
        files.value[agent.agentId] = [];
        newFileCount.value[agent.agentId] = 0;
        newScheduledTaskCount.value[agent.agentId] = 0;
        newSessionTaskCount.value[agent.agentId] = 0;
        newWorkflowCount.value[agent.agentId] = 0;
        currentAgentId.value = agent.agentId;
        waitingUserInput.value = { waiting: false, question: "", options: [] };
        try {
          const { useWorkflowStore } = await import("@/stores/workflow");
          const workflowStore = useWorkflowStore();
          workflowStore.setCurrentAgentId(agent.agentId);
        } catch (e) {
          console.warn("设置workflowStore agentId失败:", e);
        }
        return agent;
      }
    } catch (e) {
      console.error("创建Agent失败:", e);
    }
    return null;
  }

  async function selectAgent(agentId: string) {
    currentAgentId.value = agentId;
    waitingUserInput.value = { waiting: false, question: "", options: [] };
    incompleteTaskList.value = { showing: false, message: "", taskItems: [] };
    hasMoreMessages.value[agentId] = false;
    totalRounds.value[agentId] = 0;
    earliestSortOrder.value[agentId] = null;
    const agent = agents.value.find((a) => a.agentId === agentId);
    sendingMessage.value = agent?.runtimeStatus === "RUNNING";
    await loadMessages(agentId);
    await loadFiles(agentId);
    try {
      const { useWorkflowStore } = await import("@/stores/workflow");
      const workflowStore = useWorkflowStore();
      workflowStore.setCurrentAgentId(agentId);
      await workflowStore.loadSessionTasks(agentId);
    } catch (e) {
      console.warn("加载工作流执行状态失败:", e);
    }
    try {
      const { useScheduledTaskStore } = await import("@/stores/scheduledTask");
      const stStore = useScheduledTaskStore();
      await stStore.loadTasks(agentId);
    } catch (e) {
      console.warn("加载定时任务失败:", e);
    }
  }

  async function loadMessages(agentId: string) {
    try {
      const res = await fetchAgentMessagesByRounds(agentId);
      if (res.success) {
        messages.value[agentId] = res.messages
          .map(mapMessage)
          .filter(
            (m: Message) =>
              m.messageType !== "USER_EVENT" &&
              m.messageType !== "ERROR" &&
              m.messageType !== "AGENT_PROACTIVE",
          );
        hasMoreMessages.value[agentId] = res.has_more;
        totalRounds.value[agentId] = res.total_rounds;
        if (messages.value[agentId].length > 0) {
          earliestSortOrder.value[agentId] = Math.min(
            ...messages.value[agentId].map((m) => m.sortOrder),
          );
        } else {
          earliestSortOrder.value[agentId] = null;
        }
        const agent = agents.value.find((a) => a.agentId === agentId);
        if (agent) {
          agent.messageCount = messages.value[agentId].filter(
            (m: Message) =>
              m.messageType === "USER_MESSAGE" || m.messageType === "AI_TEXT",
          ).length;
        }
      }
    } catch (e) {
      console.error("加载消息失败:", e);
    }
  }

  async function loadMoreMessages() {
    const agentId = currentAgentId.value;
    if (!agentId || !hasMoreMessages.value[agentId]) return;

    const beforeSort = earliestSortOrder.value[agentId];
    if (beforeSort == null) return;

    try {
      const res = await fetchAgentMessagesByRounds(
        agentId,
        undefined,
        beforeSort,
      );
      if (res.success) {
        const olderMessages = res.messages
          .map(mapMessage)
          .filter((m: Message) => m.messageType !== "USER_EVENT");

        const existingIds = new Set(
          (messages.value[agentId] || []).map((m) => m.id),
        );
        const newMessages = olderMessages.filter((m) => !existingIds.has(m.id));

        messages.value[agentId] = [
          ...newMessages,
          ...(messages.value[agentId] || []),
        ];
        messages.value[agentId].sort((a, b) => a.sortOrder - b.sortOrder);

        hasMoreMessages.value[agentId] = res.has_more;
        if (newMessages.length > 0) {
          earliestSortOrder.value[agentId] = Math.min(
            ...newMessages.map((m) => m.sortOrder),
          );
        }
      }
    } catch (e) {
      console.error("加载更多消息失败:", e);
    }
  }

  async function loadFiles(agentId: string) {
    try {
      const res = await fetchAgentFiles(agentId);
      if (res.success) {
        files.value[agentId] = res.files.map((f: FileInfo) => ({
          fileId: f.file_id,
          fileName: f.file_name,
          fileType: f.file_type,
          fileSize: f.file_size,
          source: f.source,
          createTime: f.create_time,
          downloadUrl: f.download_url,
        }));
      }
    } catch (e) {
      console.error("加载文件列表失败:", e);
    }
  }

  function removeFile(fileId: number) {
    const agentId = currentAgentId.value;
    if (!agentId) return;
    const list = files.value[agentId];
    if (list) {
      files.value[agentId] = list.filter((f) => f.fileId !== fileId);
    }
  }

  function connectGlobalSse() {
    if (sseReconnectTimer) {
      clearTimeout(sseReconnectTimer);
      sseReconnectTimer = null;
    }

    const existing = globalEventSource.value;
    if (existing) {
      if (existing.readyState === EventSource.OPEN) {
        return;
      }
      if (existing.readyState === EventSource.CONNECTING) {
        return;
      }
      existing.close();
      globalEventSource.value = null;
    }

    const url = getGlobalSseUrl(DEFAULT_USER_ID, sseClientId);
    const es = new EventSource(url);
    globalEventSource.value = es;

    if (sseConnectTimer) {
      clearTimeout(sseConnectTimer);
    }
    sseConnectTimer = setTimeout(() => {
      if (es.readyState !== EventSource.OPEN) {
        console.warn("全局SSE连接超时(5s)，关闭重试");
        es.close();
        globalEventSource.value = null;
        globalSseConnected.value = false;
        scheduleReconnect();
      }
    }, 5000);

    es.onopen = () => {
      if (sseConnectTimer) {
        clearTimeout(sseConnectTimer);
        sseConnectTimer = null;
      }
      globalSseConnected.value = true;
      reconnectAttempt.value = 0;
      console.log("全局SSE连接已建立");
    };

    es.addEventListener("message", async (event) => {
      try {
        const data = JSON.parse(event.data);

        if (data.type === "CONNECTED") {
          globalSseConnected.value = true;
          reconnectAttempt.value = 0;
          console.log("全局SSE连接确认(通过CONNECTED事件)");
          return;
        }

        const agentId = data.agent_id;

        if (!agentId) return;

        if (data.type === "CHAT_MESSAGE" && data.payload) {
          const msg = mapSsePayload(data.payload);
          if (
            msg.messageType === "USER_EVENT" ||
            msg.messageType === "ERROR" ||
            msg.messageType === "AGENT_PROACTIVE"
          ) {
            return;
          }
          if (!messages.value[agentId]) {
            messages.value[agentId] = [];
          }
          const existingById = messages.value[agentId].find(
            (m) => m.id === msg.id,
          );
          if (existingById) {
            Object.assign(existingById, msg);
          } else {
            const localIdx = messages.value[agentId].findIndex(
              (m) =>
                m.id < 0 &&
                m.messageType === msg.messageType &&
                m.content === msg.content,
            );
            if (localIdx >= 0) {
              Object.assign(messages.value[agentId][localIdx], msg);
            } else {
              messages.value[agentId].push(msg);
            }
            messages.value[agentId].sort((a, b) => a.sortOrder - b.sortOrder);
          }

          if (msg.messageType === "FILE_GENERATED") {
            newFileCount.value[agentId] =
              (newFileCount.value[agentId] || 0) + 1;
            loadFiles(agentId);
          }
          if (msg.messageType === "FILE_DELETED") {
            loadFiles(agentId);
          }
        } else if (data.type === "THOUGHT_CHUNK") {
          const sessionMsgs = messages.value[agentId];
          if (sessionMsgs && sessionMsgs.length > 0) {
            const thoughtMsg = sessionMsgs.find(
              (m) =>
                (m.messageType === "AI_THOUGHT_BRIEF" ||
                  m.messageType === "AI_THOUGHT") &&
                m.id === data.thought_id,
            );
            if (thoughtMsg) {
              if (data.finished && data.content) {
                thoughtMsg.content = data.content;
                thoughtMsg._thoughtStreaming = false;
              } else {
                if (data.content) {
                  thoughtMsg.content =
                    (thoughtMsg.content || "") + data.content;
                }
                if (data.finished) {
                  thoughtMsg._thoughtStreaming = false;
                } else {
                  thoughtMsg._thoughtStreaming = true;
                }
              }
            }
          }
        } else if (data.type === "STREAM_CHUNK" && data.content) {
          const sessionMsgs = messages.value[agentId];
          if (sessionMsgs && sessionMsgs.length > 0) {
            const lastAiText = [...sessionMsgs]
              .reverse()
              .find((m) => m.messageType === "AI_TEXT" && m.id === data.msg_id);
            if (lastAiText) {
              lastAiText.content = (lastAiText.content || "") + data.content;
            }
          }
        } else if (data.type === "WAITING_INPUT" && data.payload) {
          if (agentId === currentAgentId.value) {
            sendingMessage.value = false;
            waitingUserInput.value = {
              waiting: true,
              question: data.payload.question || "",
              options: data.payload.options || [],
            };
          }
        } else if (data.type === "MESSAGE_COUNT_UPDATE") {
          const agent = agents.value.find((a) => a.agentId === agentId);
          if (agent) {
            agent.messageCount = data.count;
          }
        } else if (data.type === "COMPRESSING") {
          isCompressing.value[agentId] = true;
        } else if (data.type === "COMPRESS_DONE") {
          isCompressing.value[agentId] = false;
        } else if (data.type === "TITLE_UPDATE") {
          const agent = agents.value.find((a) => a.agentId === agentId);
          if (agent) {
            agent.agentName = data.title;
          }
        } else if (data.type === "SESSION_STATUS" && data.payload) {
          if (
            data.payload.status === "FINISHED" ||
            data.payload.status === "WAITING_USER_INPUT"
          ) {
            const agent = agents.value.find((a) => a.agentId === agentId);
            if (agent) {
              agent.runtimeStatus =
                data.payload.status === "FINISHED"
                  ? "IDLE"
                  : "WAITING_USER_INPUT";
            }
            if (agentId === currentAgentId.value) {
              sendingMessage.value = false;
            }
          }
        } else if (data.type === "AGENT_STATUS_UPDATE") {
          const agent = agents.value.find((a) => a.agentId === agentId);
          if (agent) {
            agent.runtimeStatus = data.status;
          }
          if (agentId === currentAgentId.value && data.status !== "RUNNING") {
            sendingMessage.value = false;
          }
        } else if (data.type === "TASK_LIST_INCOMPLETE" && data.payload) {
          if (agentId === currentAgentId.value) {
            incompleteTaskList.value = {
              showing: true,
              message:
                data.payload.message || "仍有未完成的任务，是否继续执行？",
              taskItems: data.payload.task_items || [],
            };
          }
        } else if (data.type === "WORKFLOW_CREATED" && data.payload) {
          try {
            newWorkflowCount.value[agentId] =
              (newWorkflowCount.value[agentId] || 0) + 1;
            const { useWorkflowStore } = await import("@/stores/workflow");
            const workflowStore = useWorkflowStore();
            workflowStore.loadWorkflows();
          } catch (e) {
            console.warn("工作流store加载失败:", e);
          }
        } else if (data.type === "WORKFLOW_EXECUTION_START" && data.payload) {
          try {
            newSessionTaskCount.value[agentId] =
              (newSessionTaskCount.value[agentId] || 0) + 1;
            const { useWorkflowStore } = await import("@/stores/workflow");
            const workflowStore = useWorkflowStore();
            if (agentId === workflowStore.currentAgentId) {
              workflowStore.setExecutingWorkflow(data.payload.workflow_id);
            }
            if (data.payload.execution_id) {
              const wfName =
                workflowStore.workflows.find(
                  (w) => w.workflow_id === data.payload.workflow_id,
                )?.name || "工作流";
              const execs = workflowStore.recentExecutions[agentId] || [];
              const existingExec = execs.find(
                (ex) => ex.executionId === data.payload.execution_id,
              );
              if (!existingExec) {
                execs.unshift({
                  executionId: data.payload.execution_id,
                  workflowId: data.payload.workflow_id,
                  workflowName: wfName,
                  status: "RUNNING",
                  completedNodes: 0,
                  totalNodes: data.payload.total_nodes || 0,
                  startTime: new Date().toISOString(),
                  endTime: null,
                });
                if (execs.length > 5) {
                  execs.pop();
                }
                workflowStore.recentExecutions[agentId] = execs;
              } else {
                existingExec.status = "RUNNING";
              }
            }
          } catch (e) {
            console.warn("工作流执行开始事件处理失败:", e);
          }
        } else if (data.type === "WORKFLOW_NODE_STATUS" && data.payload) {
          try {
            const { useWorkflowStore } = await import("@/stores/workflow");
            const workflowStore = useWorkflowStore();
            if (agentId === workflowStore.currentAgentId) {
              workflowStore.setNodeStatus(
                data.payload.node_id,
                data.payload.status,
              );
            }
            if (data.payload.execution_id) {
              const execs = workflowStore.recentExecutions[agentId] || [];
              const exec = execs.find(
                (ex) => ex.executionId === data.payload.execution_id,
              );
              if (exec) {
                if (
                  data.payload.completed_nodes !== undefined &&
                  data.payload.completed_nodes !== null
                ) {
                  exec.completedNodes = data.payload.completed_nodes;
                } else if (data.payload.status === "completed") {
                  exec.completedNodes = Math.min(
                    exec.completedNodes + 1,
                    exec.totalNodes || Infinity,
                  );
                }
              }
            }
          } catch (e) {
            console.warn("工作流节点状态事件处理失败:", e);
          }
        } else if (data.type === "WORKFLOW_DELETED" && data.payload) {
          try {
            const { useWorkflowStore } = await import("@/stores/workflow");
            const workflowStore = useWorkflowStore();
            workflowStore.loadWorkflows();
          } catch (e) {
            console.warn("工作流删除事件处理失败:", e);
          }
        } else if (data.type === "WORKFLOW_EXECUTION_END" && data.payload) {
          try {
            newSessionTaskCount.value[agentId] =
              (newSessionTaskCount.value[agentId] || 0) + 1;
            const { useWorkflowStore } = await import("@/stores/workflow");
            const workflowStore = useWorkflowStore();
            if (agentId === workflowStore.currentAgentId) {
              workflowStore.clearNodeStatuses();
            }
            if (data.payload.execution_id) {
              const endStatus = data.payload.status || "COMPLETED";
              workflowStore.updateExecutionStatus(
                data.payload.execution_id,
                endStatus,
              );
            }
          } catch (e) {
            console.warn("工作流执行结束事件处理失败:", e);
          }
        } else if (data.type === "SCHEDULED_TASK_CREATED" && data.payload) {
          try {
            newScheduledTaskCount.value[agentId] =
              (newScheduledTaskCount.value[agentId] || 0) + 1;
            const { useScheduledTaskStore } =
              await import("@/stores/scheduledTask");
            const stStore = useScheduledTaskStore();
            if (agentId === stStore.currentAgentId) {
              stStore.addTask({
                task_id: data.payload.task_id,
                task_name: data.payload.task_name,
                task_description: null,
                repeat_type: data.payload.repeat_type,
                repeat_rule: "",
                task_input: null,
                status: "ACTIVE",
                next_execute_time: data.payload.next_execute_time,
                last_execute_time: null,
                execute_count: 0,
                create_time: new Date().toISOString(),
              });
            }
          } catch (e) {
            console.warn("定时任务创建事件处理失败:", e);
          }
        } else if (data.type === "SCHEDULED_TASK_UPDATED" && data.payload) {
          try {
            const { useScheduledTaskStore } =
              await import("@/stores/scheduledTask");
            const stStore = useScheduledTaskStore();
            if (agentId === stStore.currentAgentId) {
              stStore.updateTask(data.payload.task_id, {
                taskName: data.payload.task_name,
                status: data.payload.status,
                nextExecuteTime: data.payload.next_execute_time,
              });
            }
          } catch (e) {
            console.warn("定时任务更新事件处理失败:", e);
          }
        } else if (data.type === "SCHEDULED_TASK_CANCELLED" && data.payload) {
          try {
            const { useScheduledTaskStore } =
              await import("@/stores/scheduledTask");
            const stStore = useScheduledTaskStore();
            if (agentId === stStore.currentAgentId) {
              stStore.removeTask(data.payload.task_id);
            }
          } catch (e) {
            console.warn("定时任务取消事件处理失败:", e);
          }
        } else if (data.type === "SCHEDULED_TASK_TRIGGERED" && data.payload) {
          try {
            newScheduledTaskCount.value[agentId] =
              (newScheduledTaskCount.value[agentId] || 0) + 1;
            const { useScheduledTaskStore } =
              await import("@/stores/scheduledTask");
            const stStore = useScheduledTaskStore();
            const agentIdForTask = agentId;
            if (agentIdForTask && stStore.currentAgentId === agentIdForTask) {
              stStore.loadTasks(agentIdForTask);
            }
          } catch (e) {
            console.warn("定时任务触发事件处理失败:", e);
          }
        } else if (data.type === "DONE") {
          if (agentId === currentAgentId.value) {
            sendingMessage.value = false;
          }
          const agent = agents.value.find((a) => a.agentId === agentId);
          if (agent) {
            agent.runtimeStatus = "IDLE";
          }
          reconnectAttempt.value = 0;
          loadAgents();
        } else if (data.type === "ERROR") {
          console.error("SSE error:", data.error, "agentId:", agentId);
          if (agentId === currentAgentId.value) {
            sendingMessage.value = false;
          }
          const agent = agents.value.find((a) => a.agentId === agentId);
          if (agent) {
            agent.runtimeStatus = "ERROR";
          }
          reconnectAttempt.value = 0;
        }
      } catch (e) {
        console.error("解析SSE事件失败:", e);
      }
    });

    es.onerror = () => {
      globalSseConnected.value = false;
      es.close();
      globalEventSource.value = null;

      for (const agent of agents.value) {
        if (agent.runtimeStatus === "RUNNING") {
          agent.runtimeStatus = "IDLE";
        }
      }
      if (sendingMessage.value) {
        sendingMessage.value = false;
      }

      scheduleReconnect();
    };
  }

  function scheduleReconnect() {
    if (sseReconnectTimer) {
      return;
    }
    if (reconnectAttempt.value < MAX_RECONNECT_ATTEMPTS) {
      const delay = Math.min(1000 * Math.pow(2, reconnectAttempt.value), 30000);
      reconnectAttempt.value++;
      console.log(
        `全局SSE断开，尝试第${reconnectAttempt.value}次重连，${delay}ms后...`,
      );
      sseReconnectTimer = setTimeout(() => {
        sseReconnectTimer = null;
        connectGlobalSse();
      }, delay);
    } else {
      console.log("全局SSE重连失败次数过多，10秒后重试");
      sseReconnectTimer = setTimeout(() => {
        sseReconnectTimer = null;
        reconnectAttempt.value = 0;
        connectGlobalSse();
      }, 10000);
    }
  }

  function disconnectGlobalSse() {
    if (sseConnectTimer) {
      clearTimeout(sseConnectTimer);
      sseConnectTimer = null;
    }
    if (sseReconnectTimer) {
      clearTimeout(sseReconnectTimer);
      sseReconnectTimer = null;
    }
    if (globalEventSource.value) {
      globalEventSource.value.close();
      globalEventSource.value = null;
      globalSseConnected.value = false;
    }
  }

  async function stopReAct() {
    const agentId = currentAgentId.value;
    if (!agentId || !sendingMessage.value) return;

    try {
      await stopAgent(agentId);
    } catch (e) {
      console.error("停止Agent请求失败:", e);
    }

    sendingMessage.value = false;
    waitingUserInput.value = { waiting: false, question: "", options: [] };

    await loadMessages(agentId);
  }

  async function recoverAgentIfNeeded(agentId: string) {
    try {
      const res = await getAgent(agentId);
      if (res.success) {
        const agent = agents.value.find((a) => a.agentId === agentId);
        if (agent) {
          agent.runtimeStatus = res.runtime_status;
        }
        await loadMessages(agentId);
        loadFiles(agentId);
      }
    } catch (e) {
      console.error("恢复Agent状态失败:", e);
    }
    sendingMessage.value = false;
  }

  function addLocalUserMessage(agentId: string, content: string) {
    if (!messages.value[agentId]) {
      messages.value[agentId] = [];
    }
    const maxSort = messages.value[agentId].reduce(
      (max, m) => Math.max(max, m.sortOrder),
      0,
    );
    const localMsg: Message = {
      id: -Date.now(),
      messageType: "USER_MESSAGE",
      role: "user",
      content,
      metadata: null,
      parentId: null,
      roundIndex: null,
      sortOrder: maxSort + 1,
      durationMs: null,
      createTime: new Date().toISOString(),
    };
    messages.value[agentId].push(localMsg);
  }

  function addLocalUserInputMessage(
    agentId: string,
    selectedOption?: string,
    userInput?: string,
  ) {
    if (!messages.value[agentId]) {
      messages.value[agentId] = [];
    }
    const maxSort = messages.value[agentId].reduce(
      (max, m) => Math.max(max, m.sortOrder),
      0,
    );
    let content = "";
    if (selectedOption && userInput) {
      content = `选择了"${selectedOption}"，并输入：${userInput}`;
    } else if (selectedOption) {
      content = `选择了"${selectedOption}"`;
    } else if (userInput) {
      content = `输入：${userInput}`;
    }
    const localMsg: Message = {
      id: -Date.now(),
      messageType: "USER_INPUT",
      role: "user",
      content,
      metadata: { selected_option: selectedOption, user_input: userInput },
      parentId: null,
      roundIndex: null,
      sortOrder: maxSort + 1,
      durationMs: null,
      createTime: new Date().toISOString(),
    };
    messages.value[agentId].push(localMsg);
  }

  async function sendUserMessage(
    content: string,
    selectedOption?: string,
    userInput?: string,
  ) {
    const agentId = currentAgentId.value;
    if (!agentId) return;

    sendingMessage.value = true;
    waitingUserInput.value = { waiting: false, question: "", options: [] };

    const agent = agents.value.find((a) => a.agentId === agentId);
    if (agent) {
      agent.runtimeStatus = "RUNNING";
    }

    addLocalUserMessage(agentId, content);

    if (!globalSseConnected.value) {
      connectGlobalSse();
      await new Promise<void>((resolve) => {
        let checks = 0;
        const check = setInterval(() => {
          checks++;
          if (globalSseConnected.value || checks >= 20) {
            clearInterval(check);
            resolve();
          }
        }, 200);
      });
    }

    try {
      const res = await sendAgentMessage(
        agentId,
        content,
        DEFAULT_USER_ID,
        selectedOption,
        userInput,
      );

      if (!res.success) {
        sendingMessage.value = false;
        if (agent) {
          agent.runtimeStatus = "IDLE";
        }
      }
    } catch (e) {
      console.error("发送消息失败:", e);
      await recoverAgentIfNeeded(agentId);
    }
  }

  async function respondToUserInput(
    selectedOption: string,
    userInput?: string,
  ) {
    const agentId = currentAgentId.value;
    if (!agentId) return;

    sendingMessage.value = true;
    waitingUserInput.value = { waiting: false, question: "", options: [] };

    const agent = agents.value.find((a) => a.agentId === agentId);
    if (agent) {
      agent.runtimeStatus = "RUNNING";
    }

    addLocalUserInputMessage(agentId, selectedOption, userInput);

    if (!globalSseConnected.value) {
      connectGlobalSse();
      await new Promise<void>((resolve) => {
        let checks = 0;
        const check = setInterval(() => {
          checks++;
          if (globalSseConnected.value || checks >= 20) {
            clearInterval(check);
            resolve();
          }
        }, 200);
      });
    }

    try {
      const displayMessage = userInput
        ? userInput
        : selectedOption
          ? selectedOption
          : "";
      const res = await sendAgentMessage(
        agentId,
        displayMessage,
        DEFAULT_USER_ID,
        selectedOption,
        userInput,
      );

      if (!res.success) {
        sendingMessage.value = false;
        if (agent) {
          agent.runtimeStatus = "IDLE";
        }
      }
    } catch (e) {
      console.error("回复用户输入失败:", e);
      await recoverAgentIfNeeded(agentId);
    }
  }

  async function continueTaskList() {
    const agentId = currentAgentId.value;
    if (!agentId) return;

    incompleteTaskList.value = { showing: false, message: "", taskItems: [] };

    await respondToUserInput("继续执行");
  }

  async function dismissIncompleteTaskList() {
    const agentId = currentAgentId.value;
    if (!agentId) return;

    incompleteTaskList.value = { showing: false, message: "", taskItems: [] };

    await respondToUserInput("结束并查看当前结果");
  }

  async function deleteAgentById(agentId: string) {
    try {
      const res = await apiDeleteAgent(agentId);
      if (res.success) {
        agents.value = agents.value.filter((a) => a.agentId !== agentId);
        delete messages.value[agentId];
        delete files.value[agentId];
        delete newFileCount.value[agentId];
        delete newScheduledTaskCount.value[agentId];
        delete newSessionTaskCount.value[agentId];
        delete newWorkflowCount.value[agentId];
        if (currentAgentId.value === agentId) {
          currentAgentId.value =
            agents.value.length > 0 ? agents.value[0].agentId : "";
          if (currentAgentId.value) {
            await loadMessages(currentAgentId.value);
            await loadFiles(currentAgentId.value);
          }
        }
      }
    } catch (e) {
      console.error("删除Agent失败:", e);
    }
  }

  async function renameAgent(agentId: string, name: string) {
    try {
      const res = await updateAgent(agentId, name);
      if (res.success) {
        const agent = agents.value.find((a) => a.agentId === agentId);
        if (agent) agent.agentName = name;
      }
    } catch (e) {
      console.error("重命名Agent失败:", e);
    }
  }

  function clearWaitingInput() {
    waitingUserInput.value = { waiting: false, question: "", options: [] };
  }

  function decrementNewFileCount(agentId: string) {
    const current = newFileCount.value[agentId] || 0;
    if (current > 0) {
      newFileCount.value[agentId] = current - 1;
    }
  }

  function clearNewFileCount(agentId: string) {
    newFileCount.value[agentId] = 0;
  }

  function clearNewScheduledTaskCount(agentId: string) {
    newScheduledTaskCount.value[agentId] = 0;
  }

  function clearNewSessionTaskCount(agentId: string) {
    newSessionTaskCount.value[agentId] = 0;
  }

  function clearNewWorkflowCount(agentId: string) {
    newWorkflowCount.value[agentId] = 0;
  }

  function getStatusColor(status: string): string {
    switch (status) {
      case "IDLE":
        return "#6b8e23";
      case "RUNNING":
        return "#b8860b";
      case "WAITING_USER_INPUT":
        return "#cd853f";
      case "PAUSED":
        return "#8b7355";
      case "ERROR":
        return "#c0392b";
      default:
        return "#8b7355";
    }
  }

  function getStatusLabel(status: string): string {
    switch (status) {
      case "IDLE":
        return "空闲";
      case "RUNNING":
        return "运行中";
      case "WAITING_USER_INPUT":
        return "等待输入";
      case "PAUSED":
        return "暂停";
      case "ERROR":
        return "异常";
      default:
        return "未知";
    }
  }

  return {
    agents,
    currentAgentId,
    messages,
    files,
    loading,
    sendingMessage,
    waitingUserInput,
    incompleteTaskList,
    newFileCount,
    newScheduledTaskCount,
    newSessionTaskCount,
    newWorkflowCount,
    isCompressing,
    globalSseConnected,
    currentMessages,
    currentAgent,
    currentFiles,
    currentNewFileCount,
    currentNewScheduledTaskCount,
    currentNewSessionTaskCount,
    currentNewWorkflowCount,
    currentHasMoreMessages,
    loadAgents,
    createNewAgent,
    selectAgent,
    loadMessages,
    loadMoreMessages,
    loadFiles,
    removeFile,
    sendUserMessage,
    respondToUserInput,
    continueTaskList,
    dismissIncompleteTaskList,
    deleteAgentById,
    renameAgent,
    clearWaitingInput,
    decrementNewFileCount,
    clearNewFileCount,
    clearNewScheduledTaskCount,
    clearNewSessionTaskCount,
    clearNewWorkflowCount,
    stopReAct,
    recoverAgentIfNeeded,
    connectGlobalSse,
    disconnectGlobalSse,
    getStatusColor,
    getStatusLabel,
  };
});
