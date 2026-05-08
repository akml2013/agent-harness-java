import {
  createWorkflow,
  DEFAULT_USER_ID,
  deleteWorkflow,
  deleteWorkflowExecution,
  fetchActiveExecutions,
  fetchRecentExecutions,
  fetchRecentExecutionsByAgent,
  fetchWorkflows,
  getWorkflow,
  renameWorkflow,
  updateWorkflow,
  validateWorkflow,
  type WorkflowEdge,
  type WorkflowExecutionDetail,
  type WorkflowInfo,
  type WorkflowNode,
} from "@/services/agentApi";
import { defineStore } from "pinia";
import { computed, ref } from "vue";

export interface FlowNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: {
    label: string;
    nodeType: string;
    toolName?: string;
    toolParams?: string;
    description?: string;
  };
}

export interface FlowEdge {
  id: string;
  source: string;
  target: string;
}

export type NodeExecutionStatus = "idle" | "running" | "completed" | "failed";

export interface ExecutionInfo {
  executionId: string;
  workflowId: string;
  workflowName: string;
  status: string;
  completedNodes: number;
  totalNodes: number;
  startTime: string | null;
  endTime: string | null;
}

export const useWorkflowStore = defineStore("workflow", () => {
  const workflows = ref<WorkflowInfo[]>([]);
  const loading = ref(false);
  const showEditor = ref(false);
  const editingWorkflowId = ref<string | null>(null);
  const editingExecutionId = ref<string | null>(null);
  const editingNodes = ref<FlowNode[]>([]);
  const editingEdges = ref<FlowEdge[]>([]);
  const nodeExecutionStatuses = ref<Record<string, NodeExecutionStatus>>({});
  const executingWorkflowId = ref<string | null>(null);
  const recentExecutions = ref<Record<string, ExecutionInfo[]>>({});
  const currentAgentId = ref<string>("");

  const currentRecentExecutions = computed(() => {
    return recentExecutions.value[currentAgentId.value] || [];
  });

  async function loadWorkflows() {
    loading.value = true;
    try {
      const res = await fetchWorkflows(DEFAULT_USER_ID);
      if (res.success) {
        workflows.value = res.workflows;
      }
    } catch (e) {
      console.error("加载工作流列表失败:", e);
    } finally {
      loading.value = false;
    }
  }

  async function removeWorkflow(workflowId: string, agentId?: string) {
    try {
      const res = await deleteWorkflow(workflowId, DEFAULT_USER_ID, agentId);
      if (res.success) {
        workflows.value = workflows.value.filter(
          (w) => w.workflow_id !== workflowId,
        );
      }
      return res;
    } catch (e) {
      console.error("删除工作流失败:", e);
      return { success: false, message: "删除失败" };
    }
  }

  async function changeWorkflowName(workflowId: string, name: string) {
    try {
      const res = await renameWorkflow(workflowId, name, DEFAULT_USER_ID);
      if (res.success) {
        const wf = workflows.value.find((w) => w.workflow_id === workflowId);
        if (wf) {
          wf.name = name;
        }
      }
      return res;
    } catch (e) {
      console.error("重命名工作流失败:", e);
      return { success: false, error: "重命名失败" };
    }
  }

  async function openEditor(workflowId?: string, executionId?: string) {
    editingWorkflowId.value = workflowId || null;
    editingExecutionId.value = executionId || null;
    if (workflowId) {
      try {
        const res = await getWorkflow(workflowId, DEFAULT_USER_ID);
        if (res.success && res.workflow_nodes && res.workflow_edges) {
          editingNodes.value = res.workflow_nodes.map((n) => ({
            id: n.node_id,
            type:
              n.node_type === "START"
                ? "start"
                : n.node_type === "END"
                  ? "end"
                  : "task",
            position: { x: n.position_x, y: n.position_y },
            data: {
              label: n.node_name,
              nodeType: n.node_type,
              toolName: n.tool_name || undefined,
              toolParams: n.tool_params || undefined,
              description: n.description || undefined,
            },
          }));
          editingEdges.value = res.workflow_edges.map((e) => ({
            id: e.edge_id,
            source: e.source_node_id,
            target: e.target_node_id,
          }));
        } else {
          editingNodes.value = [];
          editingEdges.value = [];
        }
      } catch (e) {
        console.error("加载工作流详情失败:", e);
        editingNodes.value = [];
        editingEdges.value = [];
      }
    } else {
      editingNodes.value = [];
      editingEdges.value = [];
    }

    if (executionId) {
      const execs = recentExecutions.value[currentAgentId.value] || [];
      const exec = execs.find((ex) => ex.executionId === executionId);
      if (exec && exec.status === "RUNNING") {
        executingWorkflowId.value = exec.workflowId;
      }
    }

    showEditor.value = true;
  }

  function closeEditor() {
    showEditor.value = false;
    editingWorkflowId.value = null;
    editingExecutionId.value = null;
    editingNodes.value = [];
    editingEdges.value = [];
  }

  function setNodeStatus(nodeId: string, status: NodeExecutionStatus) {
    nodeExecutionStatuses.value[nodeId] = status;
  }

  function clearNodeStatuses() {
    nodeExecutionStatuses.value = {};
    executingWorkflowId.value = null;
  }

  function setExecutingWorkflow(workflowId: string) {
    executingWorkflowId.value = workflowId;
    nodeExecutionStatuses.value = {};
  }

  function updateExecutionStatus(executionId: string, status: string) {
    for (const agentId of Object.keys(recentExecutions.value)) {
      const exec = recentExecutions.value[agentId]?.find(
        (ex) => ex.executionId === executionId,
      );
      if (exec) {
        exec.status = status;
        if (status === "COMPLETED") {
          exec.completedNodes = exec.totalNodes;
        }
        break;
      }
    }
  }

  function updateExecutionProgress(
    executionId: string,
    completedNodes: number,
  ) {
    for (const agentId of Object.keys(recentExecutions.value)) {
      const exec = recentExecutions.value[agentId]?.find(
        (ex) => ex.executionId === executionId,
      );
      if (exec) {
        exec.completedNodes = completedNodes;
        break;
      }
    }
  }

  async function loadActiveExecutions(sessionId: string) {
    try {
      const res = await fetchActiveExecutions(sessionId);
      if (res.success && res.executions && res.executions.length > 0) {
        const exec = res.executions[0];
        if (
          exec.completed_nodes < exec.total_nodes &&
          exec.status === "RUNNING"
        ) {
          executingWorkflowId.value = exec.workflow_id;
          nodeExecutionStatuses.value = {};
        } else {
          executingWorkflowId.value = null;
          nodeExecutionStatuses.value = {};
        }
      } else {
        executingWorkflowId.value = null;
        nodeExecutionStatuses.value = {};
      }
    } catch (e) {
      console.warn("加载工作流执行状态失败:", e);
      executingWorkflowId.value = null;
      nodeExecutionStatuses.value = {};
    }
  }

  async function loadRecentExecutions(sessionId: string, agentId?: string) {
    try {
      const effectiveAgentId = agentId || currentAgentId.value;
      const res = await fetchRecentExecutions(sessionId, DEFAULT_USER_ID, 5);
      if (res.success && res.executions) {
        recentExecutions.value[effectiveAgentId] = res.executions.map(
          (ex: WorkflowExecutionDetail) => ({
            executionId: ex.execution_id,
            workflowId: ex.workflow_id,
            workflowName: ex.workflow_name,
            status: ex.status,
            completedNodes: ex.completed_nodes,
            totalNodes: ex.total_nodes,
            startTime: ex.start_time,
            endTime: ex.end_time,
          }),
        );

        const runningExec = res.executions.find(
          (ex: WorkflowExecutionDetail) => ex.status === "RUNNING",
        );
        if (runningExec) {
          executingWorkflowId.value = runningExec.workflow_id;
        } else {
          executingWorkflowId.value = null;
        }
      }
    } catch (e) {
      console.warn("加载最近工作流执行记录失败:", e);
    }
  }

  async function loadSessionTasks(agentId: string) {
    try {
      const res = await fetchRecentExecutionsByAgent(agentId, 5);
      if (res.success && res.executions) {
        recentExecutions.value[agentId] = res.executions.map(
          (ex: WorkflowExecutionDetail) => ({
            executionId: ex.execution_id,
            workflowId: ex.workflow_id,
            workflowName: ex.workflow_name,
            status: ex.status,
            completedNodes: ex.completed_nodes,
            totalNodes: ex.total_nodes,
            startTime: ex.start_time,
            endTime: ex.end_time,
          }),
        );

        const runningExec = res.executions.find(
          (ex: WorkflowExecutionDetail) => ex.status === "RUNNING",
        );
        if (runningExec) {
          executingWorkflowId.value = runningExec.workflow_id;
        }
      }
    } catch (e) {
      console.warn("加载会话任务失败:", e);
    }
  }

  function getNodeStatus(nodeId: string): NodeExecutionStatus {
    return nodeExecutionStatuses.value[nodeId] || "idle";
  }

  async function saveWorkflow(
    name: string,
    description: string,
    nodes: FlowNode[],
    edges: FlowEdge[],
  ) {
    const apiNodes: WorkflowNode[] = nodes.map((n, index) => ({
      node_id: n.id,
      node_type: n.data.nodeType,
      node_name: n.data.label,
      position_x: n.position.x,
      position_y: n.position.y,
      tool_name: n.data.toolName || null,
      tool_params: n.data.toolParams || null,
      description: n.data.description || null,
      sort_order: index,
    }));

    const apiEdges: WorkflowEdge[] = edges.map((e) => ({
      edge_id: e.id,
      source_node_id: e.source,
      target_node_id: e.target,
      edge_condition: null,
    }));

    try {
      const validation = await validateWorkflow(apiNodes, apiEdges);
      if (!validation.valid) {
        return { success: false, error: validation.message };
      }

      if (editingWorkflowId.value) {
        const res = await updateWorkflow(editingWorkflowId.value, {
          user_id: DEFAULT_USER_ID,
          name,
          description,
          nodes: apiNodes,
          edges: apiEdges,
        });
        if (res.success) {
          await loadWorkflows();
        }
        return res;
      } else {
        const res = await createWorkflow({
          user_id: DEFAULT_USER_ID,
          name,
          description,
          nodes: apiNodes,
          edges: apiEdges,
          create_source: "MANUAL",
        });
        if (res.success) {
          await loadWorkflows();
        }
        return res;
      }
    } catch (e: any) {
      console.error("保存工作流失败:", e);
      return { success: false, error: e.message || "保存失败" };
    }
  }

  function setCurrentAgentId(agentId: string) {
    currentAgentId.value = agentId;
  }

  async function deleteExecution(
    executionId: string,
    userId: number = DEFAULT_USER_ID,
    agentId?: string,
  ) {
    try {
      const res = await deleteWorkflowExecution(executionId, userId, agentId);
      if (res.success) {
        for (const agentId of Object.keys(recentExecutions.value)) {
          const execs = recentExecutions.value[agentId];
          const idx = execs.findIndex((ex) => ex.executionId === executionId);
          if (idx >= 0) {
            execs.splice(idx, 1);
            break;
          }
        }
      }
      return res;
    } catch (e) {
      console.error("删除工作流执行记录失败:", e);
      return { success: false, message: "删除失败" };
    }
  }

  return {
    workflows,
    loading,
    showEditor,
    editingWorkflowId,
    editingExecutionId,
    editingNodes,
    editingEdges,
    nodeExecutionStatuses,
    executingWorkflowId,
    recentExecutions,
    currentRecentExecutions,
    currentAgentId,
    loadWorkflows,
    removeWorkflow,
    changeWorkflowName,
    openEditor,
    closeEditor,
    saveWorkflow,
    setNodeStatus,
    clearNodeStatuses,
    setExecutingWorkflow,
    getNodeStatus,
    loadActiveExecutions,
    loadRecentExecutions,
    loadSessionTasks,
    updateExecutionStatus,
    updateExecutionProgress,
    setCurrentAgentId,
    deleteExecution,
  };
});
