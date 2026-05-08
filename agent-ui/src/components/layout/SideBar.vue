<template>
  <div class="sidebar" :class="{ collapsed: collapsed }">
    <div class="sidebar-header">
      <div class="logo" v-show="!collapsed">
        <el-icon :size="24" color="var(--accent-color)"><Monitor /></el-icon>
        <span class="logo-text">AI Agent</span>
      </div>
      <div class="logo-collapsed" v-show="collapsed">
        <el-icon :size="24" color="var(--accent-color)"><Monitor /></el-icon>
      </div>
      <el-button
        class="collapse-btn"
        :icon="collapsed ? 'Expand' : 'Fold'"
        text
        @click="collapsed = !collapsed"
      />
    </div>

    <div class="new-chat-btn" v-show="!collapsed">
      <el-button type="primary" @click="handleNewAgent" style="width: 100%">
        <el-icon><Plus /></el-icon>
        新建Agent
      </el-button>
    </div>

    <div class="nav-menu" v-show="!collapsed">
      <div
        class="nav-item"
        :class="{ active: activeNav === 'chat' }"
        @click="activeNav = 'chat'"
      >
        <el-icon><ChatDotRound /></el-icon>
        <span>对话</span>
      </div>
    </div>

    <div class="nav-icons" v-show="collapsed">
      <el-tooltip content="对话" placement="right">
        <div
          class="nav-icon-item"
          :class="{ active: activeNav === 'chat' }"
          @click="activeNav = 'chat'"
        >
          <el-icon><ChatDotRound /></el-icon>
        </div>
      </el-tooltip>
    </div>

    <div class="agent-list" v-show="!collapsed && activeNav === 'chat'">
      <div class="agent-list-header">
        <span class="agent-list-title">Agent 列表</span>
        <div
          class="sse-status"
          :class="{ connected: chatStore.globalSseConnected }"
        >
          <span class="sse-dot"></span>
          <span class="sse-text">{{
            chatStore.globalSseConnected ? "已连接" : "未连接"
          }}</span>
        </div>
      </div>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索Agent"
        size="small"
        clearable
        prefix-icon="Search"
        class="agent-search"
      />
      <div class="agent-items" v-loading="chatStore.loading">
        <div
          v-for="agent in filteredAgents"
          :key="agent.agentId"
          class="agent-item"
          :class="{ active: chatStore.currentAgentId === agent.agentId }"
          @click="handleSelectAgent(agent.agentId)"
          @contextmenu.prevent="showContextMenu($event, agent)"
        >
          <div class="agent-item-content">
            <div class="agent-avatar-wrapper">
              <el-avatar
                :size="36"
                :src="agent.agentAvatar || undefined"
                class="agent-avatar"
              >
                <el-icon :size="20"><Monitor /></el-icon>
              </el-avatar>
              <span
                class="status-dot"
                :style="{
                  backgroundColor: chatStore.getStatusColor(
                    agent.runtimeStatus,
                  ),
                }"
                :title="chatStore.getStatusLabel(agent.runtimeStatus)"
              ></span>
            </div>
            <div class="agent-item-info">
              <div class="agent-name">{{ agent.agentName }}</div>
              <div class="agent-status-row">
                <span
                  class="agent-status-label"
                  :style="{
                    color: chatStore.getStatusColor(agent.runtimeStatus),
                  }"
                >
                  {{ chatStore.getStatusLabel(agent.runtimeStatus) }}
                </span>
                <span class="agent-msg-count"
                  >{{ agent.messageCount }} 条消息</span
                >
                <span class="agent-time">{{
                  formatTime(agent.lastMessageTime || agent.createTime)
                }}</span>
              </div>
            </div>
          </div>
        </div>
        <div
          v-if="filteredAgents.length === 0 && !chatStore.loading"
          class="agent-empty"
        >
          <el-empty description="暂无Agent" :image-size="60" />
        </div>
      </div>
    </div>

    <div class="sidebar-footer">
      <div class="user-info" v-show="!collapsed">
        <el-avatar :size="32" class="user-avatar">
          {{ userStore.displayName?.charAt(0) }}
        </el-avatar>
        <div class="user-detail">
          <div class="user-name">{{ userStore.displayName }}</div>
          <div class="user-role">普通用户</div>
        </div>
      </div>
      <div class="user-info-collapsed" v-show="collapsed">
        <el-tooltip :content="userStore.displayName" placement="right">
          <el-avatar :size="32" class="user-avatar">
            {{ userStore.displayName?.charAt(0) }}
          </el-avatar>
        </el-tooltip>
      </div>
    </div>

    <teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <div class="context-menu-item" @click="handleRenameAgent">
          <el-icon><Edit /></el-icon>
          重命名
        </div>
        <div class="context-menu-item danger" @click="handleDeleteAgent">
          <el-icon><Delete /></el-icon>
          删除Agent
        </div>
      </div>
    </teleport>

    <el-dialog
      v-model="renameDialogVisible"
      title="重命名Agent"
      width="360px"
      :close-on-click-modal="false"
    >
      <el-input
        v-model="renameTitle"
        placeholder="请输入新名称"
        maxlength="50"
        show-word-limit
      />
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { Agent } from "@/stores/chat";
import { useChatStore } from "@/stores/chat";
import { useUserStore } from "@/stores/user";
import { Monitor } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { Ref } from "vue";
import { computed, inject, onMounted, onUnmounted, reactive, ref } from "vue";

const chatStore = useChatStore();
const userStore = useUserStore();

const activeNav = inject<Ref<string>>("activeNav", ref("chat"));
const collapsed = ref(false);
const searchKeyword = ref("");

const renameDialogVisible = ref(false);
const renameTitle = ref("");

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  agent: null as Agent | null,
});

const filteredAgents = computed(() => {
  let list = [...chatStore.agents];
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase();
    list = list.filter((a) => a.agentName.toLowerCase().includes(keyword));
  }
  return list;
});

function formatTime(timeStr: string | null): string {
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

async function handleNewAgent() {
  await chatStore.createNewAgent();
}

async function handleSelectAgent(agentId: string) {
  if (chatStore.currentAgentId === agentId) return;
  await chatStore.selectAgent(agentId);
}

function getZoom(): number {
  return (
    parseFloat(
      getComputedStyle(document.documentElement).getPropertyValue("--app-zoom"),
    ) || 1
  );
}

function showContextMenu(event: MouseEvent, agent: Agent) {
  const zoom = getZoom();
  contextMenu.visible = true;
  contextMenu.x = event.clientX / zoom;
  contextMenu.y = event.clientY / zoom;
  contextMenu.agent = agent;
}

function hideContextMenu() {
  contextMenu.visible = false;
}

function handleRenameAgent() {
  if (contextMenu.agent) {
    renameTitle.value = contextMenu.agent.agentName;
    renameDialogVisible.value = true;
  }
  hideContextMenu();
}

async function confirmRename() {
  if (!contextMenu.agent || !renameTitle.value.trim()) return;
  await chatStore.renameAgent(
    contextMenu.agent.agentId,
    renameTitle.value.trim(),
  );
  renameDialogVisible.value = false;
  ElMessage.success("重命名成功");
}

async function handleDeleteAgent() {
  if (!contextMenu.agent) return;
  const agentId = contextMenu.agent.agentId;
  const agentName = contextMenu.agent.agentName;
  hideContextMenu();
  try {
    await ElMessageBox.confirm(
      `确定要删除Agent"${agentName}"吗？删除后不可恢复。`,
      "删除确认",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await chatStore.deleteAgentById(agentId);
    ElMessage.success("Agent已删除");
  } catch {
    // cancelled
  }
}

onMounted(async () => {
  document.addEventListener("click", hideContextMenu);
  await chatStore.loadAgents();
  chatStore.connectGlobalSse();
  if (chatStore.agents.length > 0 && !chatStore.currentAgentId) {
    await chatStore.selectAgent(chatStore.agents[0].agentId);
  }
});

onUnmounted(() => {
  document.removeEventListener("click", hideContextMenu);
});
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  overflow: hidden;
  flex-shrink: 0;
  border-right: 1px solid var(--border-color);
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--border-light);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-sidebar);
  white-space: nowrap;
}

.logo-collapsed {
  display: flex;
  align-items: center;
  justify-content: center;
}

.collapse-btn {
  color: var(--text-sidebar-secondary) !important;
}

.collapse-btn:hover {
  color: var(--text-sidebar) !important;
}

.new-chat-btn {
  padding: 12px 16px;
}

.nav-menu {
  padding: 4px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--text-sidebar);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 14px;
  position: relative;
}

.nav-item:hover:not(.disabled) {
  background: var(--bg-sidebar-hover);
  color: var(--text-sidebar-active);
}

.nav-item.active {
  background: var(--bg-sidebar-active);
  color: var(--text-sidebar-active);
}

.nav-icons {
  padding: 4px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.nav-icon-item {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  color: var(--text-sidebar);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.nav-icon-item:hover:not(.disabled) {
  background: var(--bg-sidebar-hover);
  color: var(--text-sidebar-active);
}

.nav-icon-item.active {
  background: var(--bg-sidebar-active);
  color: var(--text-sidebar-active);
}

.agent-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-top: 8px;
}

.agent-list-header {
  padding: 0 16px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.agent-list-title {
  font-size: 12px;
  color: var(--text-sidebar-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sse-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-sidebar-secondary);
}

.sse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--danger-color);
}

.sse-status.connected .sse-dot {
  background-color: var(--success-color);
}

.sse-text {
  white-space: nowrap;
}

.agent-search {
  margin: 0 12px 8px;
  width: auto;
  flex-shrink: 0;
  :deep(.el-input__wrapper) {
    background: var(--bg-search-sidebar);
    box-shadow: 0 0 0 1px var(--border-light) inset;
  }
  :deep(.el-input__wrapper:hover) {
    box-shadow: 0 0 0 1px var(--accent-color) inset;
  }
  :deep(.el-input__inner) {
    color: var(--text-sidebar);
    font-size: 13px;
  }
  :deep(.el-input__inner::placeholder) {
    color: var(--text-sidebar-secondary);
  }
}

.agent-items {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.agent-item {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 2px;
}

.agent-item:hover {
  background: var(--bg-sidebar-hover);
}

.agent-item.active {
  background: var(--bg-sidebar-active);
}

.agent-item-content {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-avatar-wrapper {
  position: relative;
  flex-shrink: 0;
}

.agent-avatar {
  background: var(--accent-color);
  color: white;
  font-size: 16px;
  font-weight: 600;
}

.status-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--bg-sidebar);
}

.agent-item-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-size: 14px;
  color: var(--text-sidebar);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.agent-item.active .agent-name {
  color: var(--text-sidebar-active);
  font-weight: 500;
}

.agent-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.agent-status-label {
  font-size: 11px;
  font-weight: 500;
}

.agent-msg-count {
  font-size: 12px;
  color: var(--text-sidebar-secondary);
  white-space: nowrap;
}

.agent-time {
  font-size: 11px;
  color: var(--text-sidebar-secondary);
  margin-left: auto;
  white-space: nowrap;
}

.agent-empty {
  padding: 20px 0;
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  background: var(--accent-color);
  color: white;
  font-size: 14px;
  flex-shrink: 0;
}

.user-detail {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  color: var(--text-sidebar);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 12px;
  color: var(--text-sidebar-secondary);
}

.user-info-collapsed {
  display: flex;
  justify-content: center;
}

.context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--bg-context-menu);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 4px 0;
  min-width: 140px;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
}

.context-menu-item:hover {
  background: var(--bg-primary);
}

.context-menu-item.danger {
  color: var(--danger-color);
}

.context-menu-item.danger:hover {
  background: var(--bg-context-danger-hover);
}
</style>
