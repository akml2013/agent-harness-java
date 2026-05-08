<template>
  <div
    class="right-panel"
    :class="{ collapsed: !visible }"
    @click="closeContextMenu"
  >
    <div class="panel-header">
      <h4 class="panel-title">工作台</h4>
      <el-button
        :icon="Close"
        text
        circle
        size="small"
        @click="$emit('close')"
      />
    </div>

    <div class="panel-content">
      <div class="panel-section">
        <div
          class="section-header"
          @click="sectionExpanded.quickActions = !sectionExpanded.quickActions"
        >
          <span class="section-title">快捷操作</span>
          <el-icon :class="{ rotated: sectionExpanded.quickActions }"
            ><ArrowRight
          /></el-icon>
        </div>
        <div v-show="sectionExpanded.quickActions" class="section-body">
          <div class="quick-actions">
            <div class="action-card" @click="handleAction('report')">
              <div
                class="action-icon"
                :style="{
                  background: 'var(--bg-action-report)',
                  color: 'var(--color-action-report)',
                }"
              >
                <el-icon :size="20"><EditPen /></el-icon>
              </div>
              <span class="action-name">生成周报</span>
            </div>
            <div class="action-card" @click="handleAction('data')">
              <div
                class="action-icon"
                :style="{
                  background: 'var(--bg-action-data)',
                  color: 'var(--color-action-data)',
                }"
              >
                <el-icon :size="20"><DataAnalysis /></el-icon>
              </div>
              <span class="action-name">数据分析</span>
            </div>
            <div class="action-card" @click="handleAction('excel')">
              <div
                class="action-icon"
                :style="{
                  background: 'var(--bg-action-excel)',
                  color: 'var(--color-action-excel)',
                }"
              >
                <el-icon :size="20"><Grid /></el-icon>
              </div>
              <span class="action-name">Excel处理</span>
            </div>
            <div class="action-card" @click="showKnowledgeBase = true">
              <div
                class="action-icon"
                :style="{
                  background: 'var(--bg-action-kb)',
                  color: 'var(--color-action-kb)',
                }"
              >
                <el-icon :size="20"><Collection /></el-icon>
              </div>
              <span class="action-name">知识库</span>
            </div>
            <div class="action-card" @click="showWorkflowListModal = true">
              <div
                class="action-icon"
                :style="{
                  background: 'var(--bg-action-wf)',
                  color: 'var(--color-action-wf)',
                }"
              >
                <el-icon :size="20"><Share /></el-icon>
              </div>
              <span class="action-name">工作流</span>
            </div>
            <div class="action-card" @click="showScheduledTaskModal = true">
              <div
                class="action-icon"
                :style="{
                  background: 'var(--bg-action-st)',
                  color: 'var(--color-action-st)',
                }"
              >
                <el-icon :size="20"><AlarmClock /></el-icon>
              </div>
              <span class="action-name">定时任务</span>
            </div>
          </div>
        </div>
      </div>

      <div class="panel-section">
        <div class="section-header" @click="toggleRecentFiles">
          <span class="section-title">会话文件</span>
          <el-badge
            v-if="
              chatStore.currentNewFileCount > 0 && !sectionExpanded.recentFiles
            "
            :value="chatStore.currentNewFileCount"
            :max="9"
            class="section-badge"
          />
          <el-icon :class="{ rotated: sectionExpanded.recentFiles }"
            ><ArrowRight
          /></el-icon>
        </div>
        <div v-show="sectionExpanded.recentFiles" class="section-body">
          <div v-if="chatStore.currentFiles.length > 0" class="doc-list">
            <div
              v-for="file in displayedFiles"
              :key="file.fileId"
              class="doc-card"
              @click="handleFileClick(file.fileId)"
              @contextmenu.prevent="onFileContext($event, file)"
            >
              <div
                class="doc-icon"
                :class="`doc-${getFileTypeClass(file.fileType)}`"
              >
                <el-icon :size="16">
                  <Document
                    v-if="
                      ['doc', 'docx'].includes(file.fileType?.toLowerCase())
                    "
                  />
                  <Grid
                    v-else-if="
                      ['xls', 'xlsx'].includes(file.fileType?.toLowerCase())
                    "
                  />
                  <Picture
                    v-else-if="
                      ['png', 'jpg', 'jpeg', 'gif', 'svg', 'bmp'].includes(
                        file.fileType?.toLowerCase(),
                      )
                    "
                  />
                  <Document
                    v-else-if="['pdf'].includes(file.fileType?.toLowerCase())"
                  />
                  <Document v-else />
                </el-icon>
              </div>
              <div class="doc-info">
                <div class="doc-name">
                  {{ file.fileName }}
                </div>
                <div class="doc-meta">
                  <el-tag
                    :type="file.source === 'upload' ? 'primary' : 'success'"
                    size="small"
                    class="source-tag"
                    >{{ file.source === "upload" ? "上传" : "生成" }}</el-tag
                  >
                  <span class="doc-meta-text"
                    >{{ file.fileType.toUpperCase() }} ·
                    {{ formatFileSize(file.fileSize) }}</span
                  >
                </div>
                <div class="doc-time" v-if="file.createTime">
                  {{ formatFileTime(file.createTime) }}
                </div>
              </div>
              <el-button
                text
                size="small"
                type="primary"
                :icon="Download"
                @click="handleDownload(file.fileId)"
              />
            </div>
            <div
              v-if="chatStore.currentFiles.length > fileShowLimit"
              class="file-toggle"
            >
              <el-button
                text
                size="small"
                @click="showAllFiles = !showAllFiles"
              >
                {{
                  showAllFiles
                    ? "收起"
                    : `展开全部 ${chatStore.currentFiles.length} 个文件`
                }}
                <el-icon :class="{ rotated: showAllFiles }"
                  ><ArrowRight
                /></el-icon>
              </el-button>
            </div>
          </div>
          <div v-else class="empty-hint">
            <el-icon :size="32" color="var(--text-placeholder)"
              ><FolderOpened
            /></el-icon>
            <span>暂无文件</span>
          </div>
        </div>
      </div>

      <div class="panel-section">
        <div class="section-header" @click="toggleSessionTasks">
          <span class="section-title">会话任务</span>
          <el-badge
            v-if="
              chatStore.currentNewSessionTaskCount > 0 &&
              !sectionExpanded.sessionTasks
            "
            :value="chatStore.currentNewSessionTaskCount"
            :max="9"
            class="section-badge"
          />
          <el-icon :class="{ rotated: sectionExpanded.sessionTasks }"
            ><ArrowRight
          /></el-icon>
        </div>
        <div v-show="sectionExpanded.sessionTasks" class="section-body">
          <div
            v-if="workflowStore.currentRecentExecutions.length === 0"
            class="empty-hint"
          >
            <el-icon :size="32" color="var(--text-placeholder)"
              ><List
            /></el-icon>
            <span>暂无执行记录</span>
          </div>
          <div v-else class="exec-list">
            <div
              v-for="exec in workflowStore.currentRecentExecutions"
              :key="exec.executionId"
              class="exec-card"
              @click="handleViewExecution(exec)"
              @contextmenu.prevent="onSessionTaskContext($event, exec)"
            >
              <div class="exec-card-header">
                <span class="exec-name">{{ exec.workflowName }}</span>
                <el-tag
                  :type="getExecutionStatusType(exec.status)"
                  size="small"
                  effect="plain"
                >
                  <el-icon
                    v-if="exec.status === 'RUNNING'"
                    class="is-loading"
                    :size="10"
                    style="margin-right: 2px"
                  >
                    <Loading />
                  </el-icon>
                  {{ getExecutionStatusText(exec.status) }}
                </el-tag>
              </div>
              <div class="exec-card-progress">
                <el-progress
                  :percentage="
                    exec.totalNodes > 0
                      ? Math.round(
                          (exec.completedNodes / exec.totalNodes) * 100,
                        )
                      : 0
                  "
                  :stroke-width="4"
                  :color="getProgressColor(exec.status)"
                  :show-text="false"
                />
                <span class="exec-progress-text"
                  >{{ exec.completedNodes }}/{{ exec.totalNodes }}</span
                >
              </div>
              <div class="exec-card-meta">
                <span>{{ formatDateTime(exec.startTime) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="panel-section">
        <div class="section-header" @click="toggleScheduledTasks">
          <span class="section-title">定时任务</span>
          <el-badge
            v-if="
              chatStore.currentNewScheduledTaskCount > 0 &&
              !sectionExpanded.scheduledTasks
            "
            :value="chatStore.currentNewScheduledTaskCount"
            :max="9"
            class="section-badge"
          />
          <el-icon :class="{ rotated: sectionExpanded.scheduledTasks }"
            ><ArrowRight
          /></el-icon>
        </div>
        <div v-show="sectionExpanded.scheduledTasks" class="section-body">
          <div v-if="scheduledTaskStore.loading" class="empty-hint">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div
            v-else-if="scheduledTaskStore.tasks.length === 0"
            class="empty-hint"
          >
            <el-icon :size="32" color="var(--text-placeholder)"
              ><AlarmClock
            /></el-icon>
            <span>暂无定时任务</span>
          </div>

          <div v-else class="st-list">
            <div
              v-for="task in scheduledTaskStore.tasks"
              :key="task.taskId"
              class="st-item"
              @contextmenu.prevent="onScheduledTaskContext($event, task)"
            >
              <div class="st-item-header">
                <span class="st-item-name">{{ task.taskName }}</span>
                <el-tag
                  :type="scheduledTaskStore.getStatusType(task.status)"
                  size="small"
                  effect="plain"
                >
                  {{ scheduledTaskStore.getStatusLabel(task.status) }}
                </el-tag>
              </div>
              <div class="st-item-meta">
                <el-tag size="small" type="info" effect="plain">
                  {{ scheduledTaskStore.getRepeatTypeLabel(task.repeatType) }}
                </el-tag>
                <span class="st-item-time" v-if="task.nextExecuteTime">
                  下次: {{ formatDateTime(task.nextExecuteTime) }}
                </span>
              </div>
              <div class="st-item-meta" v-if="task.executeCount > 0">
                <span class="st-item-count"
                  >已执行 {{ task.executeCount }} 次</span
                >
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="panel-section">
        <div class="section-header" @click="toggleWorkflowPanel">
          <span class="section-title">工作流</span>
          <el-badge
            v-if="
              chatStore.currentNewWorkflowCount > 0 &&
              !sectionExpanded.workflows
            "
            :value="chatStore.currentNewWorkflowCount"
            :max="9"
            class="section-badge"
          />
          <el-icon :class="{ rotated: sectionExpanded.workflows }"
            ><ArrowRight
          /></el-icon>
        </div>
        <div v-show="sectionExpanded.workflows" class="section-body">
          <div v-if="workflowStore.loading" class="empty-hint">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div
            v-else-if="workflowStore.workflows.length === 0"
            class="empty-hint"
          >
            <el-icon :size="32" color="var(--text-placeholder)"
              ><Share
            /></el-icon>
            <span>暂无工作流，可让AI创建</span>
          </div>

          <div v-else class="wf-list">
            <div
              v-for="wf in workflowStore.workflows"
              :key="wf.workflow_id"
              class="wf-item"
              @click="handleViewWorkflow(wf)"
              @contextmenu.prevent="onWorkflowContext($event, wf)"
            >
              <el-icon
                :size="14"
                color="var(--warning-color)"
                style="flex-shrink: 0"
                ><Share
              /></el-icon>
              <span class="wf-item-name">{{ wf.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Right-click context menu -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu-overlay"
        @click="closeContextMenu"
        @contextmenu.prevent="closeContextMenu"
      >
        <div
          class="context-menu"
          :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        >
          <div
            class="context-menu-item"
            :class="{ 'is-disabled': isAgentRunning }"
            @click.stop="handleContextAction"
          >
            <el-icon :size="14"><Delete /></el-icon>
            <span>{{ contextMenu.label }}</span>
          </div>
          <el-tooltip
            v-if="isAgentRunning"
            content="Agent运行中，无法删除"
            placement="left"
            :show-after="0"
          >
            <div class="context-menu-disabled-tip">
              <el-icon :size="12"><Warning /></el-icon>
            </div>
          </el-tooltip>
        </div>
      </div>
    </Teleport>

    <KnowledgeBaseModal
      :visible="showKnowledgeBase"
      @close="showKnowledgeBase = false"
    />

    <WorkflowEditorModal
      :visible="workflowStore.showEditor"
      @close="workflowStore.closeEditor()"
    />

    <ScheduledTaskModal
      :visible="showScheduledTaskModal"
      @close="showScheduledTaskModal = false"
    />

    <WorkflowListModal
      :visible="showWorkflowListModal"
      @close="showWorkflowListModal = false"
    />
  </div>
</template>

<script setup lang="ts">
import {
  DEFAULT_USER_ID,
  deleteFile as deleteFileApi,
  getDownloadUrl,
  type WorkflowInfo,
} from "@/services/agentApi";
import { useChatStore, type FileItem } from "@/stores/chat";
import {
  useScheduledTaskStore,
  type ScheduledTask,
} from "@/stores/scheduledTask";
import { useWorkflowStore, type ExecutionInfo } from "@/stores/workflow";
import {
  AlarmClock,
  ArrowRight,
  Close,
  Collection,
  DataAnalysis,
  Delete,
  Document,
  Download,
  EditPen,
  FolderOpened,
  Grid,
  List,
  Loading,
  Picture,
  Share,
  Warning,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, reactive, ref, watch } from "vue";
import KnowledgeBaseModal from "./KnowledgeBaseModal.vue";
import ScheduledTaskModal from "./ScheduledTaskModal.vue";
import WorkflowEditorModal from "./WorkflowEditorModal.vue";
import WorkflowListModal from "./WorkflowListModal.vue";

const chatStore = useChatStore();
const workflowStore = useWorkflowStore();
const scheduledTaskStore = useScheduledTaskStore();
const showKnowledgeBase = ref(false);
const showScheduledTaskModal = ref(false);
const showWorkflowListModal = ref(false);

defineProps<{
  visible: boolean;
}>();

defineEmits<{
  (e: "close"): void;
}>();

const sectionExpanded = reactive({
  quickActions: true,
  recentFiles: true,
  scheduledTasks: true,
  sessionTasks: true,
  workflows: false,
});

const fileShowLimit = 5;
const showAllFiles = ref(false);

const displayedFiles = computed(() => {
  const files = chatStore.currentFiles;
  if (showAllFiles.value || files.length <= fileShowLimit) {
    return files;
  }
  return files.slice(0, fileShowLimit);
});

onMounted(() => {
  workflowStore.loadWorkflows();
  loadSessionTasks();
});

function toggleRecentFiles() {
  sectionExpanded.recentFiles = !sectionExpanded.recentFiles;
  if (sectionExpanded.recentFiles && chatStore.currentNewFileCount > 0) {
    chatStore.clearNewFileCount(chatStore.currentAgentId);
  }
}

function toggleSessionTasks() {
  sectionExpanded.sessionTasks = !sectionExpanded.sessionTasks;
  if (sectionExpanded.sessionTasks) {
    loadSessionTasks();
    if (chatStore.currentNewSessionTaskCount > 0) {
      chatStore.clearNewSessionTaskCount(chatStore.currentAgentId);
    }
  }
}

function loadSessionTasks() {
  const agentId = chatStore.currentAgentId;
  if (agentId) {
    workflowStore.loadSessionTasks(agentId);
  }
}

function toggleScheduledTasks() {
  sectionExpanded.scheduledTasks = !sectionExpanded.scheduledTasks;
  if (sectionExpanded.scheduledTasks) {
    loadScheduledTasks();
    if (chatStore.currentNewScheduledTaskCount > 0) {
      chatStore.clearNewScheduledTaskCount(chatStore.currentAgentId);
    }
  }
}

function loadScheduledTasks() {
  const agentId = chatStore.currentAgentId;
  if (agentId) {
    scheduledTaskStore.loadTasks(agentId);
  }
}

function toggleWorkflowPanel() {
  sectionExpanded.workflows = !sectionExpanded.workflows;
  if (sectionExpanded.workflows) {
    workflowStore.loadWorkflows();
    if (chatStore.currentNewWorkflowCount > 0) {
      chatStore.clearNewWorkflowCount(chatStore.currentAgentId);
    }
  }
}

function handleViewWorkflow(wf: WorkflowInfo) {
  workflowStore.openEditor(wf.workflow_id);
}

function handleViewExecution(exec: ExecutionInfo) {
  workflowStore.openEditor(exec.workflowId, exec.executionId);
}

function getExecutionStatusType(
  status: string,
): "success" | "warning" | "info" | "danger" | "primary" {
  switch (status) {
    case "RUNNING":
      return "primary";
    case "COMPLETED":
      return "success";
    case "FAILED":
      return "danger";
    case "ABORTED":
      return "info";
    case "PENDING":
      return "warning";
    default:
      return "info";
  }
}

function getExecutionStatusText(status: string): string {
  switch (status) {
    case "RUNNING":
      return "执行中";
    case "COMPLETED":
      return "已完成";
    case "FAILED":
      return "失败";
    case "ABORTED":
      return "已中止";
    case "PENDING":
      return "等待中";
    default:
      return status;
  }
}

function getCSSVar(name: string): string {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
}

function getProgressColor(status: string): string {
  switch (status) {
    case "RUNNING":
      return getCSSVar("--accent-color");
    case "COMPLETED":
      return getCSSVar("--success-color");
    case "FAILED":
      return getCSSVar("--danger-color");
    case "ABORTED":
      return getCSSVar("--info-color");
    default:
      return getCSSVar("--accent-color");
  }
}

watch(
  () => workflowStore.executingWorkflowId,
  (newWfId) => {
    if (newWfId) {
      sectionExpanded.sessionTasks = true;
      if (chatStore.currentNewSessionTaskCount > 0) {
        chatStore.clearNewSessionTaskCount(chatStore.currentAgentId);
      }
    }
  },
);

function getFileTypeClass(fileType: string): string {
  const t = fileType.toLowerCase();
  if (["doc", "docx"].includes(t)) return "docx";
  if (["xls", "xlsx"].includes(t)) return "xlsx";
  if (["pdf"].includes(t)) return "pdf";
  if (["png", "jpg", "jpeg", "gif", "svg", "bmp"].includes(t)) return "image";
  return "default";
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

function formatFileTime(timeStr: string | null): string {
  if (!timeStr) return "";
  try {
    const date = new Date(timeStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return "刚刚";
    if (diffMin < 60) return `${diffMin}分钟前`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour}小时前`;
    const diffDay = Math.floor(diffHour / 24);
    if (diffDay < 7) return `${diffDay}天前`;
    return `${date.getMonth() + 1}/${date.getDate()}`;
  } catch {
    return "";
  }
}

function formatDateTime(timeStr: string | null): string {
  if (!timeStr) return "-";
  try {
    const date = new Date(timeStr);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
  } catch {
    return "-";
  }
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

function handleFileClick(fileId: number) {
  const agentId = chatStore.currentAgentId;
  if (agentId) {
    chatStore.decrementNewFileCount(agentId);
  }
  handleDownload(fileId);
}

const isAgentRunning = computed(
  () => chatStore.currentAgent?.runtimeStatus === "RUNNING",
);

// Context menu state
type ContextMenuType = "scheduledTask" | "workflow" | "sessionTask" | "file";

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  type: "" as ContextMenuType,
  label: "",
  targetId: "",
  targetData: null as any,
});

function closeContextMenu() {
  contextMenu.visible = false;
}

function getZoom(): number {
  return (
    parseFloat(
      getComputedStyle(document.documentElement).getPropertyValue("--app-zoom"),
    ) || 1
  );
}

function showContextMenu(
  event: MouseEvent,
  type: ContextMenuType,
  label: string,
  targetId: string,
  targetData: any,
) {
  const zoom = getZoom();
  const menuWidth = 140;
  const menuHeight = 36;
  let x = event.clientX / zoom;
  let y = event.clientY / zoom;
  if (x + menuWidth > window.innerWidth / zoom) {
    x = window.innerWidth / zoom - menuWidth - 4;
  }
  if (y + menuHeight > window.innerHeight / zoom) {
    y = window.innerHeight / zoom - menuHeight - 4;
  }

  contextMenu.x = x;
  contextMenu.y = y;
  contextMenu.type = type;
  contextMenu.label = label;
  contextMenu.targetId = targetId;
  contextMenu.targetData = targetData;
  contextMenu.visible = true;
}

function onScheduledTaskContext(event: MouseEvent, task: ScheduledTask) {
  showContextMenu(event, "scheduledTask", "取消任务", task.taskId, task);
}

function onWorkflowContext(event: MouseEvent, wf: WorkflowInfo) {
  showContextMenu(event, "workflow", "删除工作流", wf.workflow_id, wf);
}

function onSessionTaskContext(event: MouseEvent, exec: ExecutionInfo) {
  showContextMenu(event, "sessionTask", "删除记录", exec.executionId, exec);
}

function onFileContext(event: MouseEvent, file: FileItem) {
  showContextMenu(event, "file", "删除文件", String(file.fileId), file);
}

async function handleContextAction() {
  if (isAgentRunning.value) return;

  const { type, label, targetId } = contextMenu;
  closeContextMenu();

  try {
    await ElMessageBox.confirm(`确定要${label}吗？`, label, {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
  } catch {
    return; // User cancelled
  }

  try {
    if (type === "scheduledTask") {
      const agentId = chatStore.currentAgentId;
      if (!agentId) return;
      const res = await scheduledTaskStore.cancelTask(
        agentId,
        targetId,
        DEFAULT_USER_ID,
      );
      if (res.success) {
        ElMessage.success("任务已取消");
        chatStore.loadMoreMessages();
      } else {
        ElMessage.error(res.message || "取消失败");
      }
    } else if (type === "workflow") {
      const agentId = chatStore.currentAgentId;
      const res = await workflowStore.removeWorkflow(targetId, agentId);
      if (res.success) {
        ElMessage.success("工作流已删除");
        chatStore.loadMoreMessages();
      } else {
        ElMessage.error(res.message || "删除失败");
      }
    } else if (type === "sessionTask") {
      const agentId = chatStore.currentAgentId;
      const res = await workflowStore.deleteExecution(
        targetId,
        DEFAULT_USER_ID,
        agentId,
      );
      if (res.success) {
        ElMessage.success("记录已删除");
        chatStore.loadMoreMessages();
      } else {
        ElMessage.error(res.message || "删除失败");
      }
    } else if (type === "file") {
      const agentId = chatStore.currentAgentId;
      const res = await deleteFileApi(
        Number(targetId),
        DEFAULT_USER_ID,
        agentId || undefined,
      );
      if (res.success) {
        ElMessage.success("文件已删除");
        chatStore.removeFile(Number(targetId));
        chatStore.loadMoreMessages();
      } else {
        ElMessage.error(res.message || "删除失败");
      }
    }
  } catch (e) {
    console.error("操作失败:", e);
    ElMessage.error("操作失败");
  }
}

async function handleAction(type: string) {
  const map: Record<string, string> = {
    report: "请帮我生成本周工作周报",
    data: "请帮我分析最近的销售数据",
    excel: "请帮我处理Excel数据",
  };
  const message = map[type] || type;
  if (!chatStore.currentAgentId) {
    ElMessage.warning("请先选择或创建Agent");
    return;
  }
  await chatStore.sendUserMessage(message);
}
</script>

<style scoped>
.right-panel {
  width: var(--right-panel-width);
  height: 100%;
  background: var(--bg-white);
  border-left: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  overflow: hidden;
  flex-shrink: 0;
}

.right-panel.collapsed {
  width: 0;
  border-left: none;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.panel-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.panel-section {
  border-bottom: 1px solid var(--border-light);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  user-select: none;
}

.section-header:hover {
  background: var(--bg-primary);
}

.section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.section-badge {
  margin-left: 8px;
}

.section-header .el-icon {
  transition: transform var(--transition-fast);
  color: var(--text-secondary);
}

.section-header .el-icon.rotated {
  transform: rotate(90deg);
}

.section-body {
  padding: 4px 16px 12px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 8px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-card:hover {
  border-color: var(--accent-color);
  box-shadow: var(--shadow-sm);
}

.action-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-name {
  font-size: 12px;
  color: var(--text-regular);
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.doc-card:hover {
  background: var(--bg-primary);
}

.doc-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-pdf {
  background: var(--bg-doc-pdf);
  color: var(--color-doc-pdf);
}

.doc-docx {
  background: var(--bg-doc-word);
  color: var(--color-doc-word);
}

.doc-xlsx {
  background: var(--bg-doc-excel);
  color: var(--color-doc-excel);
}

.doc-image {
  background: var(--bg-doc-image);
  color: var(--color-doc-image);
}

.doc-default {
  background: var(--bg-doc-default);
  color: var(--color-doc-default);
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-name {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 6px;
}

.source-tag {
  flex-shrink: 0;
  transform: scale(0.85);
  transform-origin: left center;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
}

.doc-meta-text {
  font-size: 11px;
  color: var(--text-secondary);
}

.doc-time {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.file-toggle {
  text-align: center;
  padding: 4px 0;
}

.file-toggle .el-button {
  font-size: 12px;
  color: var(--text-secondary);
}

.file-toggle .el-icon {
  transition: transform 0.2s;
}

.file-toggle .rotated {
  transform: rotate(90deg);
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.st-toolbar-inner {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.st-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.st-item {
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-light);
  transition: background var(--transition-fast);
}

.st-item:hover {
  background: var(--bg-primary);
}

.st-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.st-item-name {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.st-item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.st-item-time {
  font-size: 11px;
  color: var(--text-secondary);
}

.st-item-count {
  font-size: 11px;
  color: var(--text-secondary);
}

.exec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.exec-card {
  padding: 10px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: pointer;
}

.exec-card:hover {
  border-color: var(--accent-color);
  box-shadow: var(--shadow-sm);
}

.exec-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.exec-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exec-card-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.exec-card-progress .el-progress {
  flex: 1;
}

.exec-progress-text {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.exec-card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.wf-toolbar-inner {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.wf-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.wf-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.wf-item:hover {
  background: var(--bg-primary);
}

.wf-item-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style>
.context-menu-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3000;
}

.context-menu {
  position: fixed;
  background: var(--bg-context-menu);
  border: 1px solid var(--border-context-menu);
  border-radius: 6px;
  box-shadow: var(--shadow-context-menu);
  padding: 4px 0;
  min-width: 120px;
  z-index: 3001;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-context-danger);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}

.context-menu-item:hover {
  background: var(--bg-context-danger-hover);
}

.context-menu-item.is-disabled {
  color: var(--color-context-disabled);
  cursor: not-allowed;
}

.context-menu-item.is-disabled:hover {
  background: transparent;
}

.context-menu-disabled-tip {
  position: absolute;
  right: -4px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-context-warning);
  pointer-events: auto;
}
</style>
