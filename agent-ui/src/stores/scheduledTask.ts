import {
  DEFAULT_USER_ID,
  deleteScheduledTask,
  fetchScheduledTasks,
  type ScheduledTaskInfo,
} from "@/services/agentApi";
import { defineStore } from "pinia";
import { ref } from "vue";

export interface ScheduledTask {
  taskId: string;
  taskName: string;
  taskDescription: string | null;
  repeatType: string;
  repeatRule: string;
  taskInput: string | null;
  status: string;
  nextExecuteTime: string | null;
  lastExecuteTime: string | null;
  executeCount: number;
  createTime: string | null;
  updateTime: string | null;
}

function mapTask(t: ScheduledTaskInfo): ScheduledTask {
  return {
    taskId: t.task_id,
    taskName: t.task_name,
    taskDescription: t.task_description,
    repeatType: t.repeat_type,
    repeatRule: t.repeat_rule,
    taskInput: t.task_input,
    status: t.status,
    nextExecuteTime: t.next_execute_time,
    lastExecuteTime: t.last_execute_time,
    executeCount: t.execute_count,
    createTime: t.create_time,
    updateTime: t.update_time || null,
  };
}

export const useScheduledTaskStore = defineStore("scheduledTask", () => {
  const tasks = ref<ScheduledTask[]>([]);
  const loading = ref(false);
  const currentAgentId = ref<string>("");

  async function loadTasks(agentId: string) {
    if (!agentId) return;
    currentAgentId.value = agentId;
    loading.value = true;
    try {
      const res = await fetchScheduledTasks(agentId);
      if (res.success) {
        tasks.value = res.tasks.map(mapTask);
      }
    } catch (e) {
      console.error("加载定时任务列表失败:", e);
    } finally {
      loading.value = false;
    }
  }

  function addTask(task: ScheduledTaskInfo) {
    const existing = tasks.value.find((t) => t.taskId === task.task_id);
    if (existing) {
      Object.assign(existing, mapTask(task));
    } else {
      tasks.value.unshift(mapTask(task));
    }
  }

  function updateTask(taskId: string, updates: Partial<ScheduledTask>) {
    const task = tasks.value.find((t) => t.taskId === taskId);
    if (task) {
      Object.assign(task, updates);
    }
  }

  function removeTask(taskId: string) {
    tasks.value = tasks.value.filter((t) => t.taskId !== taskId);
  }

  async function cancelTask(
    agentId: string,
    taskId: string,
    userId: number = DEFAULT_USER_ID,
  ) {
    try {
      const res = await deleteScheduledTask(agentId, taskId, userId);
      if (res.success) {
        removeTask(taskId);
      }
      return res;
    } catch (e) {
      console.error("取消定时任务失败:", e);
      return { success: false, message: "取消失败" };
    }
  }

  function clearTasks() {
    tasks.value = [];
    currentAgentId.value = "";
  }

  function getRepeatTypeLabel(repeatType: string): string {
    switch (repeatType) {
      case "ONCE":
        return "一次性";
      case "HOURLY":
        return "每小时";
      case "DAILY":
        return "每天";
      case "WEEKLY":
        return "每周";
      case "MONTHLY":
        return "每月";
      case "YEARLY":
        return "每年";
      default:
        return repeatType;
    }
  }

  function getStatusLabel(status: string): string {
    switch (status) {
      case "ACTIVE":
        return "运行中";
      case "PAUSED":
        return "已暂停";
      case "COMPLETED":
        return "已完成";
      case "CANCELLED":
        return "已取消";
      default:
        return status;
    }
  }

  function getStatusType(
    status: string,
  ): "success" | "warning" | "info" | "danger" {
    switch (status) {
      case "ACTIVE":
        return "success";
      case "PAUSED":
        return "warning";
      case "COMPLETED":
        return "info";
      case "CANCELLED":
        return "danger";
      default:
        return "info";
    }
  }

  return {
    tasks,
    loading,
    currentAgentId,
    loadTasks,
    addTask,
    updateTask,
    removeTask,
    cancelTask,
    clearTasks,
    getRepeatTypeLabel,
    getStatusLabel,
    getStatusType,
  };
});
