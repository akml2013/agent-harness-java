import api from "./api";

export interface SessionInfo {
  session_id: string;
  title: string;
  status: string;
  message_count: number;
  last_message_time: string | null;
  create_time: string | null;
}

export interface ChatMessage {
  id: number;
  message_type: string;
  role: string;
  content: string | null;
  metadata: string | null;
  parent_id: number | null;
  round_index: number | null;
  sort_order: number;
  duration_ms: number | null;
  create_time: string | null;
}

export interface SessionResponse {
  success: boolean;
  session_id: string;
  status: string;
  finished: boolean;
  waiting_user_input: boolean;
  question?: string;
  options?: string[];
  final_answer?: string;
  rounds: number;
  task_list?: any[];
  error?: string;
}

export interface FileInfo {
  file_id: number;
  file_name: string;
  file_type: string;
  file_size: number;
  source: string;
  create_time: string | null;
  download_url: string | null;
}

export const DEFAULT_USER_ID = 1;

export function fetchSessions(
  userId: number = DEFAULT_USER_ID,
  page: number = 1,
  size: number = 50,
) {
  return api.get("/agent/sessions", {
    params: { user_id: userId, page, size },
  }) as Promise<{ success: boolean; sessions: SessionInfo[]; total: number }>;
}

export function createSession(
  userId: number = DEFAULT_USER_ID,
  title?: string,
) {
  return api.post("/agent/sessions", {
    user_id: userId,
    title: title || "新会话",
  }) as Promise<{
    success: boolean;
    session_id: string;
    title: string;
    status: string;
    create_time: string;
  }>;
}

export function getSessionDetail(sessionId: string) {
  return api.get(`/agent/sessions/${sessionId}`) as Promise<{
    success: boolean;
    session_id: string;
    title: string;
    status: string;
    message_count: number;
    [key: string]: any;
  }>;
}

export function updateSession(sessionId: string, title: string) {
  return api.put(`/agent/sessions/${sessionId}`, { title }) as Promise<{
    success: boolean;
  }>;
}

export function deleteSession(sessionId: string) {
  return api.delete(`/agent/sessions/${sessionId}`) as Promise<{
    success: boolean;
  }>;
}

export function fetchMessages(
  sessionId: string,
  page: number = 1,
  size: number = 100,
) {
  return api.get(`/agent/sessions/${sessionId}/messages`, {
    params: { page, size },
  }) as Promise<{
    success: boolean;
    messages: ChatMessage[];
    total: number;
    page: number;
    size: number;
  }>;
}

export function sendMessage(
  sessionId: string,
  message: string,
  userId: number = DEFAULT_USER_ID,
  selectedOption?: string,
  userInput?: string,
) {
  const payload: any = { user_id: userId, message };
  if (selectedOption && selectedOption.trim())
    payload.selected_option = selectedOption;
  if (userInput && userInput.trim()) payload.user_input = userInput;
  return api.post(
    `/agent/sessions/${sessionId}/messages`,
    payload,
  ) as Promise<SessionResponse>;
}

export function fetchSessionFiles(sessionId: string, source?: string) {
  return api.get(`/agent/sessions/${sessionId}/files`, {
    params: { source },
  }) as Promise<{ success: boolean; files: FileInfo[]; total: number }>;
}

export function uploadFile(
  file: File,
  userId: number = DEFAULT_USER_ID,
  sessionId?: string,
) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("user_id", String(userId));
  if (sessionId) formData.append("session_id", sessionId);
  return api.post("/agent/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000,
  }) as Promise<{
    success: boolean;
    file_id: number;
    file_key: string;
    file_name: string;
    file_type: string;
    session_id: string;
  }>;
}

export function getDownloadUrl(fileId: number) {
  return api.get(`/agent/download/${fileId}`) as Promise<{
    success: boolean;
    download_url: string;
    file_name: string;
    file_type: string;
  }>;
}

export function stopSession(sessionId: string) {
  return api.post(`/agent/sessions/${sessionId}/stop`) as Promise<{
    success: boolean;
    message: string;
  }>;
}

export function fetchMessagesByRounds(
  sessionId: string,
  rounds?: number,
  beforeSortOrder?: number,
) {
  const params: any = {};
  if (rounds !== undefined) params.rounds = rounds;
  if (beforeSortOrder !== undefined) params.before_sort_order = beforeSortOrder;
  return api.get(`/agent/sessions/${sessionId}/messages/by-rounds`, {
    params,
  }) as Promise<{
    success: boolean;
    messages: ChatMessage[];
    has_more: boolean;
    total_rounds: number;
    loaded_rounds: number;
  }>;
}

export function chat(
  userId: number,
  message: string,
  sessionId?: string,
  selectedOption?: string,
  userInput?: string,
) {
  const payload: any = { user_id: userId, message };
  if (sessionId) payload.session_id = sessionId;
  if (selectedOption) payload.selected_option = selectedOption;
  if (userInput) payload.user_input = userInput;
  return api.post("/agent/chat", payload) as Promise<SessionResponse>;
}

export interface KnowledgeBaseInfo {
  id: number;
  kbId: string;
  userId: number;
  name: string;
  description: string | null;
  fileName: string;
  fileType: string;
  fileSize: number;
  storageKey: string;
  chunkCount: number;
  chunkStrategy: string;
  chunkSize: number;
  chunkOverlap: number;
  status: string;
  progress: number;
  errorMessage: string | null;
  createTime: string | null;
  updateTime: string | null;
}

export function fetchKnowledgeBases(userId: number = DEFAULT_USER_ID) {
  return api.get("/agent/knowledge-bases", {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    knowledge_bases: KnowledgeBaseInfo[];
    total: number;
  }>;
}

export function uploadKnowledgeBase(
  file: File,
  userId: number = DEFAULT_USER_ID,
  options?: {
    name?: string;
    description?: string;
    chunkStrategy?: string;
    chunkSize?: number;
    chunkOverlap?: number;
  },
) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("user_id", String(userId));
  if (options?.name) formData.append("name", options.name);
  if (options?.description) formData.append("description", options.description);
  if (options?.chunkStrategy)
    formData.append("chunk_strategy", options.chunkStrategy);
  if (options?.chunkSize)
    formData.append("chunk_size", String(options.chunkSize));
  if (options?.chunkOverlap)
    formData.append("chunk_overlap", String(options.chunkOverlap));
  return api.post("/agent/knowledge-bases/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  }) as Promise<{
    success: boolean;
    kb_id: string;
    name: string;
    status: string;
    message: string;
    error?: string;
  }>;
}

export function deleteKnowledgeBase(
  kbId: string,
  userId: number = DEFAULT_USER_ID,
) {
  return api.delete(`/agent/knowledge-bases/${kbId}`, {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    message: string;
  }>;
}

export function updateKnowledgeBase(
  kbId: string,
  data: { name?: string; description?: string },
  userId: number = DEFAULT_USER_ID,
) {
  return api.put(`/agent/knowledge-bases/${kbId}`, data, {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    knowledge_base: KnowledgeBaseInfo;
    error?: string;
  }>;
}

export function retryKnowledgeBaseIndex(
  kbId: string,
  userId: number = DEFAULT_USER_ID,
) {
  return api.post(`/agent/knowledge-bases/${kbId}/retry`, null, {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    message: string;
  }>;
}

export interface WorkflowInfo {
  id: number;
  workflow_id: string;
  user_id: number;
  name: string;
  description: string | null;
  status: string;
  node_count: number;
  version: number;
  create_source: string;
  create_time: string | null;
  update_time: string | null;
}

export interface WorkflowNode {
  node_id: string;
  node_type: string;
  node_name: string;
  position_x: number;
  position_y: number;
  tool_name: string | null;
  tool_params: string | null;
  description: string | null;
  sort_order: number;
}

export interface WorkflowEdge {
  edge_id: string;
  source_node_id: string;
  target_node_id: string;
  edge_condition: string | null;
}

export function fetchWorkflows(userId: number = DEFAULT_USER_ID) {
  return api.get("/agent/workflows", {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    workflows: WorkflowInfo[];
    total: number;
  }>;
}

export function getWorkflow(
  workflowId: string,
  userId: number = DEFAULT_USER_ID,
) {
  return api.get(`/agent/workflows/${workflowId}`, {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    workflow: WorkflowInfo;
    workflow_nodes: WorkflowNode[];
    workflow_edges: WorkflowEdge[];
    error?: string;
  }>;
}

export function createWorkflow(data: {
  user_id: number;
  name?: string;
  description: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  create_source?: string;
}) {
  return api.post("/agent/workflows", data) as Promise<{
    success: boolean;
    workflow_id: string;
    name: string;
    status: string;
    node_count: number;
    error?: string;
  }>;
}

export function updateWorkflow(
  workflowId: string,
  data: {
    user_id: number;
    name?: string;
    description?: string;
    nodes?: WorkflowNode[];
    edges?: WorkflowEdge[];
  },
) {
  return api.put(`/agent/workflows/${workflowId}`, data) as Promise<{
    success: boolean;
    workflow_id: string;
    name: string;
    version: number;
    error?: string;
  }>;
}

export function deleteWorkflow(
  workflowId: string,
  userId: number = DEFAULT_USER_ID,
  agentId?: string,
) {
  return api.delete(`/agent/workflows/${workflowId}`, {
    params: { user_id: userId, agent_id: agentId },
  }) as Promise<{
    success: boolean;
    message: string;
  }>;
}

export function renameWorkflow(
  workflowId: string,
  name: string,
  userId: number = DEFAULT_USER_ID,
) {
  return api.put(`/agent/workflows/${workflowId}/name`, {
    user_id: userId,
    name,
  }) as Promise<{
    success: boolean;
    workflow_id: string;
    name: string;
    error?: string;
  }>;
}

export function validateWorkflow(nodes: WorkflowNode[], edges: WorkflowEdge[]) {
  return api.post("/agent/workflows/validate", {
    nodes,
    edges,
  }) as Promise<{
    success: boolean;
    valid: boolean;
    message: string;
  }>;
}

export interface WorkflowExecution {
  execution_id: string;
  workflow_id: string;
  user_id: number;
  session_id: string;
  status: string;
  current_node_id: string | null;
  completed_nodes: number;
  total_nodes: number;
  start_time: string | null;
  end_time: string | null;
}

export function fetchActiveExecutions(
  sessionId: string,
  userId: number = DEFAULT_USER_ID,
) {
  return api.get("/agent/workflows/executions/active", {
    params: { session_id: sessionId, user_id: userId },
  }) as Promise<{
    success: boolean;
    executions: WorkflowExecution[];
  }>;
}

export interface WorkflowExecutionDetail extends WorkflowExecution {
  workflow_name: string;
}

export function fetchRecentExecutions(
  sessionId: string,
  userId: number = DEFAULT_USER_ID,
  limit: number = 5,
) {
  return api.get("/agent/workflows/executions/recent", {
    params: { session_id: sessionId, user_id: userId, limit },
  }) as Promise<{
    success: boolean;
    executions: WorkflowExecutionDetail[];
  }>;
}

export function fetchRecentExecutionsByAgent(
  agentId: string,
  limit: number = 5,
) {
  return api.get(`/agent/agents/${agentId}/workflow-executions/recent`, {
    params: { limit },
  }) as Promise<{
    success: boolean;
    executions: WorkflowExecutionDetail[];
  }>;
}

export interface AgentInfo {
  agent_id: string;
  agent_name: string;
  agent_avatar: string | null;
  capabilities: string;
  runtime_status: string;
  message_count: number;
  last_message_time: string | null;
  create_time: string | null;
  session_id?: string;
}

export function fetchAgents(userId: number = DEFAULT_USER_ID) {
  return api.get("/agent/agents", {
    params: { user_id: userId },
  }) as Promise<{ success: boolean; agents: AgentInfo[] }>;
}

export function createAgentApi(
  userId: number = DEFAULT_USER_ID,
  agentName?: string,
  agentAvatar?: string,
  capabilities?: string,
) {
  const payload: any = { user_id: userId };
  if (agentName) payload.agent_name = agentName;
  if (agentAvatar) payload.agent_avatar = agentAvatar;
  if (capabilities) payload.capabilities = capabilities;
  return api.post("/agent/agents", payload) as Promise<{
    success: boolean;
    agent_id: string;
    agent_name: string;
    agent_avatar: string | null;
    session_id: string;
    runtime_status: string;
    capabilities: string;
  }>;
}

export function getAgent(agentId: string) {
  return api.get(`/agent/agents/${agentId}`) as Promise<{
    success: boolean;
    agent_id: string;
    agent_name: string;
    agent_avatar: string | null;
    capabilities: string;
    session_id: string;
    runtime_status: string;
    message_count: number;
    title: string;
    last_message_time: string | null;
    create_time: string | null;
  }>;
}

export function updateAgent(agentId: string, agentName: string) {
  return api.put(`/agent/agents/${agentId}`, {
    agent_name: agentName,
  }) as Promise<{ success: boolean; agent_id: string; agent_name: string }>;
}

export function deleteAgent(agentId: string) {
  return api.delete(`/agent/agents/${agentId}`) as Promise<{
    success: boolean;
  }>;
}

export function getAgentStatus(agentId: string) {
  return api.get(`/agent/agents/${agentId}/status`) as Promise<{
    success: boolean;
    agent_id: string;
    runtime_status: string;
  }>;
}

export function sendAgentMessage(
  agentId: string,
  message: string,
  userId: number = DEFAULT_USER_ID,
  selectedOption?: string,
  userInput?: string,
) {
  const payload: any = { user_id: userId, message };
  if (selectedOption && selectedOption.trim())
    payload.selected_option = selectedOption;
  if (userInput && userInput.trim()) payload.user_input = userInput;
  return api.post(`/agent/agents/${agentId}/chat`, payload) as Promise<{
    success: boolean;
    agent_id: string;
    runtime_status: string;
  }>;
}

export function fetchAgentMessages(
  agentId: string,
  page: number = 1,
  size: number = 50,
  messageType?: string,
) {
  return api.get(`/agent/agents/${agentId}/messages`, {
    params: { page, size, message_type: messageType },
  }) as Promise<{
    success: boolean;
    messages: ChatMessage[];
    total: number;
    page: number;
    size: number;
  }>;
}

export function fetchAgentMessagesByRounds(
  agentId: string,
  rounds?: number,
  beforeSortOrder?: number,
) {
  const params: any = {};
  if (rounds !== undefined) params.rounds = rounds;
  if (beforeSortOrder !== undefined) params.before_sort_order = beforeSortOrder;
  return api.get(`/agent/agents/${agentId}/messages/by-rounds`, {
    params,
  }) as Promise<{
    success: boolean;
    messages: ChatMessage[];
    has_more: boolean;
    total_rounds: number;
    loaded_rounds: number;
  }>;
}

export function stopAgent(agentId: string) {
  return api.post(`/agent/agents/${agentId}/stop`) as Promise<{
    success: boolean;
    message: string;
  }>;
}

export function fetchAgentFiles(agentId: string, source?: string) {
  return api.get(`/agent/agents/${agentId}/files`, {
    params: { source },
  }) as Promise<{ success: boolean; files: FileInfo[]; total: number }>;
}

export function uploadAgentFile(
  agentId: string,
  file: File,
  userId: number = DEFAULT_USER_ID,
) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("user_id", String(userId));
  return api.post(`/agent/agents/${agentId}/upload`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000,
  }) as Promise<{
    success: boolean;
    file_id: number;
    file_key: string;
    file_name: string;
    file_type: string;
    session_id: string;
  }>;
}

export function deleteFile(
  fileId: number,
  userId: number = DEFAULT_USER_ID,
  agentId?: string,
) {
  return api.delete(`/agent/files/${fileId}`, {
    params: { user_id: userId, agent_id: agentId },
  }) as Promise<{
    success: boolean;
    message: string;
    deleted_versions: number;
  }>;
}

export function getGlobalSseUrl(
  userId: number = DEFAULT_USER_ID,
  clientId?: string,
) {
  const base = `/api/agent/users/${userId}/stream`;
  return clientId ? `${base}?client_id=${clientId}` : base;
}

export interface ScheduledTaskInfo {
  task_id: string;
  task_name: string;
  task_description: string | null;
  repeat_type: string;
  repeat_rule: string;
  task_input: string | null;
  status: string;
  next_execute_time: string | null;
  last_execute_time: string | null;
  execute_count: number;
  create_time: string | null;
  update_time?: string | null;
}

export function fetchScheduledTasks(agentId: string) {
  return api.get(`/agent/agents/${agentId}/scheduled-tasks`) as Promise<{
    success: boolean;
    tasks: ScheduledTaskInfo[];
    total: number;
  }>;
}

export function fetchScheduledTask(agentId: string, taskId: string) {
  return api.get(
    `/agent/agents/${agentId}/scheduled-tasks/${taskId}`,
  ) as Promise<{
    success: boolean;
    task: ScheduledTaskInfo;
  }>;
}

export function deleteScheduledTask(
  agentId: string,
  taskId: string,
  userId: number = DEFAULT_USER_ID,
) {
  return api.delete(`/agent/agents/${agentId}/scheduled-tasks/${taskId}`, {
    params: { user_id: userId },
  }) as Promise<{
    success: boolean;
    message: string;
  }>;
}

export function deleteWorkflowExecution(
  executionId: string,
  userId: number = DEFAULT_USER_ID,
  agentId?: string,
) {
  return api.delete(`/agent/agents/workflow-executions/${executionId}`, {
    params: { user_id: userId, agent_id: agentId },
  }) as Promise<{
    success: boolean;
    message: string;
  }>;
}
