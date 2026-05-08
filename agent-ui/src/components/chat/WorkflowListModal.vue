<template>
  <Teleport to="body">
    <div v-if="visible" class="wf-overlay" @click.self="$emit('close')">
      <div class="wf-modal">
        <div class="wf-header">
          <h3>工作流</h3>
          <el-button
            :icon="Close"
            text
            circle
            size="small"
            @click="$emit('close')"
          />
        </div>

        <div class="wf-toolbar">
          <el-button :icon="Refresh" @click="loadWorkflows">刷新</el-button>
        </div>

        <div class="wf-content">
          <div v-if="loading" class="wf-loading">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div v-else-if="workflows.length === 0" class="wf-empty">
            <el-icon :size="48" color="var(--text-placeholder)"
              ><Share
            /></el-icon>
            <span>暂无工作流，可让AI创建</span>
          </div>

          <div v-else class="wf-list">
            <div v-for="wf in workflows" :key="wf.workflow_id" class="wf-card">
              <div class="wf-card-header">
                <div class="wf-card-info">
                  <span class="wf-name">{{ wf.name }}</span>
                  <el-tag
                    :type="getStatusType(wf.status)"
                    size="small"
                    class="wf-status-tag"
                  >
                    {{ getStatusLabel(wf.status) }}
                  </el-tag>
                  <el-tag size="small" type="info">
                    {{ wf.node_count }}个节点
                  </el-tag>
                  <el-tag
                    v-if="wf.create_source === 'AI'"
                    size="small"
                    type="warning"
                  >
                    AI创建
                  </el-tag>
                </div>
                <div class="wf-card-actions">
                  <el-button
                    type="primary"
                    size="small"
                    text
                    @click="handleViewWorkflow(wf)"
                  >
                    查看
                  </el-button>
                  <el-popconfirm
                    title="确定删除此工作流？"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    confirm-button-type="danger"
                    @confirm="handleDelete(wf.workflow_id)"
                  >
                    <template #reference>
                      <el-button type="danger" size="small" text
                        >删除</el-button
                      >
                    </template>
                  </el-popconfirm>
                </div>
              </div>

              <div class="wf-card-meta">
                <span>版本: v{{ wf.version }}</span>
                <span v-if="wf.create_time"> 创建: {{ wf.create_time }} </span>
              </div>

              <div v-if="wf.description" class="wf-card-desc">
                {{ wf.description }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type { WorkflowInfo } from "@/services/agentApi";
import { useWorkflowStore } from "@/stores/workflow";
import { Close, Loading, Refresh, Share } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { storeToRefs } from "pinia";
import { onMounted } from "vue";

defineProps<{
  visible: boolean;
}>();

defineEmits<{
  (e: "close"): void;
}>();

const workflowStore = useWorkflowStore();
const { workflows, loading } = storeToRefs(workflowStore);

onMounted(() => {
  loadWorkflows();
});

function loadWorkflows() {
  workflowStore.loadWorkflows();
}

function handleViewWorkflow(wf: WorkflowInfo) {
  workflowStore.openEditor(wf.workflow_id);
}

async function handleDelete(workflowId: string) {
  try {
    const res = await workflowStore.removeWorkflow(workflowId);
    if (res.success) {
      ElMessage.success("删除成功");
    } else {
      ElMessage.error("删除失败");
    }
  } catch {
    ElMessage.error("删除失败");
  }
}

function getStatusType(
  status: string,
): "success" | "warning" | "info" | "danger" {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "DISABLED":
      return "warning";
    case "DELETED":
      return "danger";
    default:
      return "info";
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case "ACTIVE":
      return "运行中";
    case "DISABLED":
      return "已禁用";
    case "DELETED":
      return "已删除";
    default:
      return status;
  }
}
</script>

<style scoped>
.wf-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-overlay);
  backdrop-filter: blur(4px);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wf-modal {
  width: 70%;
  max-width: 750px;
  height: 75vh;
  background: var(--bg-white);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-modal);
}

.wf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.wf-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-title);
}

.wf-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-divider);
  flex-shrink: 0;
}

.wf-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.wf-loading,
.wf-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.wf-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.wf-card {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.wf-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.wf-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wf-card-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.wf-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-title);
}

.wf-status-tag {
  flex-shrink: 0;
}

.wf-card-actions {
  display: flex;
  gap: 4px;
}

.wf-card-meta {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  flex-wrap: wrap;
}

.wf-card-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-content);
  line-height: 1.5;
}
</style>
