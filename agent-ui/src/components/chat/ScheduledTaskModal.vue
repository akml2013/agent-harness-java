<template>
  <Teleport to="body">
    <div v-if="visible" class="st-overlay" @click.self="$emit('close')">
      <div class="st-modal">
        <div class="st-header">
          <h3>定时任务</h3>
          <el-button
            :icon="Close"
            text
            circle
            size="small"
            @click="$emit('close')"
          />
        </div>

        <div class="st-toolbar">
          <el-button :icon="Refresh" @click="loadTasks">刷新</el-button>
        </div>

        <div class="st-content">
          <div v-if="loading" class="st-loading">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div v-else-if="tasks.length === 0" class="st-empty">
            <el-icon :size="48" color="var(--text-placeholder)"
              ><AlarmClock
            /></el-icon>
            <span>暂无定时任务，可让AI创建</span>
          </div>

          <div v-else class="st-list">
            <div v-for="task in tasks" :key="task.taskId" class="st-card">
              <div class="st-card-header">
                <div class="st-card-info">
                  <span class="st-name">{{ task.taskName }}</span>
                  <el-tag
                    :type="getStatusType(task.status)"
                    size="small"
                    class="st-status-tag"
                  >
                    {{ getStatusLabel(task.status) }}
                  </el-tag>
                  <el-tag size="small" type="info">
                    {{ getRepeatTypeLabel(task.repeatType) }}
                  </el-tag>
                </div>
              </div>

              <div class="st-card-meta">
                <span v-if="task.nextExecuteTime">
                  下次执行: {{ task.nextExecuteTime }}
                </span>
                <span v-if="task.lastExecuteTime">
                  上次执行: {{ task.lastExecuteTime }}
                </span>
                <span>已执行: {{ task.executeCount }}次</span>
              </div>

              <div v-if="task.taskDescription" class="st-card-desc">
                {{ task.taskDescription }}
              </div>

              <div v-if="task.taskInput" class="st-card-input">
                <span class="st-label">任务输入:</span>
                {{ task.taskInput }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { useScheduledTaskStore } from "@/stores/scheduledTask";
import { AlarmClock, Close, Loading, Refresh } from "@element-plus/icons-vue";
import { storeToRefs } from "pinia";
import { onMounted } from "vue";

defineProps<{
  visible: boolean;
}>();

defineEmits<{
  (e: "close"): void;
}>();

const scheduledTaskStore = useScheduledTaskStore();
const { tasks, loading } = storeToRefs(scheduledTaskStore);

onMounted(() => {
  loadTasks();
});

function loadTasks() {
  scheduledTaskStore.loadTasks(scheduledTaskStore.currentAgentId);
}

function getStatusType(
  status: string,
): "success" | "warning" | "info" | "danger" {
  return scheduledTaskStore.getStatusType(status);
}

function getStatusLabel(status: string): string {
  return scheduledTaskStore.getStatusLabel(status);
}

function getRepeatTypeLabel(repeatType: string): string {
  return scheduledTaskStore.getRepeatTypeLabel(repeatType);
}
</script>

<style scoped>
.st-overlay {
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

.st-modal {
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

.st-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.st-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-title);
}

.st-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-divider);
  flex-shrink: 0;
}

.st-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.st-loading,
.st-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.st-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.st-card {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.st-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.st-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.st-card-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.st-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-title);
}

.st-status-tag {
  flex-shrink: 0;
}

.st-card-meta {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  flex-wrap: wrap;
}

.st-card-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-content);
  line-height: 1.5;
}

.st-card-input {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 6px 10px;
  background: var(--bg-primary);
  border-radius: 4px;
}

.st-label {
  font-weight: 500;
  color: var(--text-content);
  margin-right: 4px;
}
</style>
