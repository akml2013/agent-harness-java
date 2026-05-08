import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Task {
  id: string
  title: string
  type: 'document' | 'report' | 'data' | 'other'
  status: 'pending' | 'running' | 'completed' | 'failed'
  progress: number
  createTime: string
  sessionId?: string
}

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<Task[]>([
    {
      id: 'task-1',
      title: 'Q1销售数据分析报告',
      type: 'report',
      status: 'completed',
      progress: 100,
      createTime: '2026-04-19 10:28'
    },
    {
      id: 'task-2',
      title: '周报自动生成',
      type: 'report',
      status: 'completed',
      progress: 100,
      createTime: '2026-04-18 16:20'
    },
    {
      id: 'task-3',
      title: 'Excel数据清洗',
      type: 'data',
      status: 'running',
      progress: 65,
      createTime: '2026-04-18 14:00'
    }
  ])

  const activeTaskCount = ref(1)

  function addTask(task: Omit<Task, 'id'>) {
    const id = `task-${Date.now()}`
    tasks.value.unshift({ ...task, id })
    if (task.status === 'running') {
      activeTaskCount.value++
    }
  }

  function updateTaskStatus(taskId: string, status: Task['status'], progress?: number) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (task) {
      task.status = status
      if (progress !== undefined) {
        task.progress = progress
      }
    }
  }

  return {
    tasks,
    activeTaskCount,
    addTask,
    updateTaskStatus
  }
})
