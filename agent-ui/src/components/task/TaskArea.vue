<template>
  <div class="task-area">
    <div class="task-header">
      <h3 class="task-title">任务管理</h3>
      <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
        新建任务
      </el-button>
    </div>

    <div class="task-toolbar">
      <el-radio-group v-model="statusFilter" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="running">执行中</el-radio-button>
        <el-radio-button label="completed">已完成</el-radio-button>
        <el-radio-button label="failed">失败</el-radio-button>
      </el-radio-group>
    </div>

    <div class="task-list">
      <div v-for="task in filteredTasks" :key="task.id" class="task-card">
        <div class="task-card-header">
          <div class="task-type-badge" :class="`type-${task.type}`">
            <el-icon :size="14"
              ><component :is="getTaskTypeIcon(task.type)"
            /></el-icon>
          </div>
          <div class="task-card-info">
            <div class="task-card-title">{{ task.title }}</div>
            <div class="task-card-time">{{ task.createTime }}</div>
          </div>
          <el-tag
            size="small"
            :type="getStatusTagType(task.status)"
            effect="plain"
          >
            {{ getStatusText(task.status) }}
          </el-tag>
        </div>
        <div v-if="task.status === 'running'" class="task-card-progress">
          <el-progress :percentage="task.progress" :stroke-width="6" />
        </div>
        <div class="task-card-footer">
          <el-button text size="small" :icon="View">详情</el-button>
          <el-button
            v-if="task.status === 'failed'"
            text
            size="small"
            type="primary"
            :icon="RefreshRight"
          >
            重试
          </el-button>
          <el-button
            v-if="task.status === 'completed'"
            text
            size="small"
            type="success"
            :icon="Download"
          >
            下载结果
          </el-button>
        </div>
      </div>

      <div v-if="filteredTasks.length === 0" class="task-empty">
        <el-empty description="暂无任务" />
      </div>
    </div>

    <el-dialog
      v-model="showCreateDialog"
      title="新建任务"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="任务名称">
          <el-input v-model="newTask.title" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select
            v-model="newTask.type"
            placeholder="请选择任务类型"
            style="width: 100%"
          >
            <el-option label="文档处理" value="document" />
            <el-option label="报告生成" value="report" />
            <el-option label="数据处理" value="data" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input
            v-model="newTask.description"
            type="textarea"
            :rows="3"
            placeholder="请描述任务需求"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { Task } from "@/stores/task";
import { useTaskStore } from "@/stores/task";
import {
  DataAnalysis,
  Document,
  Download,
  EditPen,
  More,
  Plus,
  RefreshRight,
  View,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, reactive, ref } from "vue";

const taskStore = useTaskStore();
const statusFilter = ref("all");
const showCreateDialog = ref(false);

const newTask = reactive({
  title: "",
  type: "document" as Task["type"],
  description: "",
});

const filteredTasks = computed(() => {
  if (statusFilter.value === "all") return taskStore.tasks;
  return taskStore.tasks.filter((t) => t.status === statusFilter.value);
});

function getTaskTypeIcon(type: string) {
  const map: Record<string, any> = {
    document: Document,
    report: EditPen,
    data: DataAnalysis,
    other: More,
  };
  return map[type] || More;
}

function getStatusTagType(
  status: string,
): "primary" | "success" | "warning" | "info" | "danger" {
  const map: Record<
    string,
    "primary" | "success" | "warning" | "info" | "danger"
  > = {
    pending: "info",
    running: "primary",
    completed: "success",
    failed: "danger",
  };
  return map[status] || "info";
}

function getStatusText(status: string) {
  const map: Record<string, string> = {
    pending: "等待中",
    running: "执行中",
    completed: "已完成",
    failed: "失败",
  };
  return map[status] || status;
}

function handleCreateTask() {
  if (!newTask.title.trim()) {
    ElMessage.warning("请输入任务名称");
    return;
  }
  taskStore.addTask({
    title: newTask.title,
    type: newTask.type,
    status: "pending",
    progress: 0,
    createTime: new Date().toLocaleString("zh-CN"),
  });
  ElMessage.success("任务创建成功");
  showCreateDialog.value = false;
  newTask.title = "";
  newTask.description = "";
}
</script>

<style scoped>
.task-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-white);
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
}

.task-title {
  font-size: 16px;
  font-weight: 500;
}

.task-toolbar {
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-light);
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.task-card {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 12px;
  transition: all var(--transition-fast);
}

.task-card:hover {
  border-color: var(--accent-color);
  box-shadow: var(--shadow-sm);
}

.task-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-type-badge {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.type-document {
  background: var(--bg-mcp-action);
  color: var(--accent-color);
}

.type-report {
  background: var(--bg-ask-user);
  color: var(--warning-color);
}

.type-data {
  background: var(--bg-system-action);
  color: var(--success-color);
}

.type-other {
  background: var(--bg-disabled);
  color: var(--text-secondary);
}

.task-card-info {
  flex: 1;
  min-width: 0;
}

.task-card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.task-card-time {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.task-card-progress {
  margin-top: 12px;
  padding-left: 44px;
}

.task-card-footer {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  padding-left: 36px;
}

.task-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}
</style>
