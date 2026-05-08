<template>
  <Teleport to="body">
    <div v-if="visible" class="wf-overlay" @click.self="handleClose">
      <div class="wf-modal">
        <div class="wf-header">
          <div class="wf-header-left">
            <h3>{{ workflowName }}</h3>
            <el-tag
              v-if="isExecutionMode && currentExecution"
              :type="getExecutionStatusType(currentExecution.status)"
              size="small"
              class="wf-status-tag"
            >
              <el-icon
                v-if="currentExecution.status === 'RUNNING'"
                class="is-loading"
              >
                <Loading />
              </el-icon>
              {{ getExecutionStatusText(currentExecution.status) }}
            </el-tag>
            <el-tag
              v-else-if="isExecuting"
              type="warning"
              size="small"
              class="wf-status-tag"
            >
              <el-icon class="is-loading"><Loading /></el-icon>
              执行中
            </el-tag>
          </div>
          <el-button
            :icon="Close"
            text
            circle
            size="small"
            @click="handleClose"
          />
        </div>

        <div class="wf-body">
          <div v-if="workflowDescription" class="wf-info">
            <span class="wf-info-label">描述：</span>
            <span class="wf-info-text">{{ workflowDescription }}</span>
          </div>

          <div v-if="isExecutionMode && currentExecution" class="wf-exec-info">
            <div class="wf-exec-progress-row">
              <span class="wf-exec-label">执行进度</span>
              <el-progress
                :percentage="executionProgress"
                :stroke-width="6"
                :color="getProgressColor(currentExecution.status)"
                style="flex: 1"
              />
              <span class="wf-exec-progress-text"
                >{{ currentExecution.completedNodes }}/{{
                  currentExecution.totalNodes
                }}</span
              >
            </div>
          </div>

          <div class="wf-canvas">
            <VueFlow
              v-model:nodes="nodes"
              v-model:edges="edges"
              :default-viewport="{ zoom: 1, x: 0, y: 0 }"
              :min-zoom="0.3"
              :max-zoom="2"
              :nodes-draggable="false"
              :nodes-connectable="false"
              :edges-updatable="false"
              :zoom-on-scroll="true"
              :pan-on-drag="true"
              fit-view-on-init
            >
              <template #node-start="startNodeProps">
                <Handle type="source" :position="Position.Right" />
                <div
                  class="wf-node wf-node-start"
                  :class="getNodeClass(startNodeProps.id)"
                >
                  <div class="wf-node-icon">
                    <svg
                      viewBox="0 0 24 24"
                      width="16"
                      height="16"
                      fill="currentColor"
                    >
                      <path d="M8 5v14l11-7z" />
                    </svg>
                  </div>
                  <div class="wf-node-label">开始</div>
                  <div
                    v-if="getNodeStatus(startNodeProps.id) === 'running'"
                    class="wf-node-pulse"
                  />
                </div>
              </template>

              <template #node-end="endNodeProps">
                <Handle type="target" :position="Position.Left" />
                <div
                  class="wf-node wf-node-end"
                  :class="getNodeClass(endNodeProps.id)"
                >
                  <div class="wf-node-icon">
                    <svg
                      viewBox="0 0 24 24"
                      width="16"
                      height="16"
                      fill="currentColor"
                    >
                      <rect x="6" y="6" width="12" height="12" rx="1" />
                    </svg>
                  </div>
                  <div class="wf-node-label">结束</div>
                  <div
                    v-if="getNodeStatus(endNodeProps.id) === 'running'"
                    class="wf-node-pulse"
                  />
                </div>
              </template>

              <template #node-task="taskNodeProps">
                <Handle type="target" :position="Position.Left" />
                <div
                  class="wf-node wf-node-task"
                  :class="getNodeClass(taskNodeProps.id)"
                >
                  <div class="wf-node-header">
                    <div class="wf-node-icon">
                      <svg
                        viewBox="0 0 24 24"
                        width="14"
                        height="14"
                        fill="currentColor"
                      >
                        <path
                          d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z"
                        />
                      </svg>
                    </div>
                    <span class="wf-node-title">{{
                      taskNodeProps.data.label
                    }}</span>
                  </div>
                  <div v-if="taskNodeProps.data.toolName" class="wf-node-tool">
                    {{ getToolLabel(taskNodeProps.data.toolName) }}
                  </div>
                  <div
                    v-if="getNodeStatus(taskNodeProps.id) === 'running'"
                    class="wf-node-pulse"
                  />
                  <div
                    v-if="getNodeStatus(taskNodeProps.id) === 'completed'"
                    class="wf-node-check"
                  >
                    <svg
                      viewBox="0 0 24 24"
                      width="14"
                      height="14"
                      class="wf-node-check-svg"
                    >
                      <path
                        d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z"
                      />
                    </svg>
                  </div>
                </div>
                <Handle type="source" :position="Position.Right" />
              </template>

              <Background />
              <Controls />
            </VueFlow>
          </div>
        </div>

        <div class="wf-footer">
          <el-button @click="handleClose">关闭</el-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  useWorkflowStore,
  type FlowEdge,
  type FlowNode,
  type NodeExecutionStatus,
} from "@/stores/workflow";
import { Close, Loading } from "@element-plus/icons-vue";
import { Background } from "@vue-flow/background";
import { Controls } from "@vue-flow/controls";
import "@vue-flow/controls/dist/style.css";
import { Handle, Position, VueFlow } from "@vue-flow/core";
import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
import { computed, ref, watch } from "vue";

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const workflowStore = useWorkflowStore();

const nodes = ref<FlowNode[]>([]);
const edges = ref<FlowEdge[]>([]);

const isExecutionMode = computed(() => !!workflowStore.editingExecutionId);

const currentExecution = computed(() => {
  if (!workflowStore.editingExecutionId) return null;
  return workflowStore.currentRecentExecutions.find(
    (ex) => ex.executionId === workflowStore.editingExecutionId,
  );
});

const isExecuting = computed(
  () => workflowStore.executingWorkflowId === workflowStore.editingWorkflowId,
);

const executionProgress = computed(() => {
  if (!currentExecution.value || currentExecution.value.totalNodes === 0)
    return 0;
  return Math.round(
    (currentExecution.value.completedNodes /
      currentExecution.value.totalNodes) *
      100,
  );
});

const workflowName = computed(() => {
  if (currentExecution.value) return currentExecution.value.workflowName;
  if (!workflowStore.editingWorkflowId) return "工作流详情";
  const wf = workflowStore.workflows.find(
    (w) => w.workflow_id === workflowStore.editingWorkflowId,
  );
  return wf?.name || "工作流详情";
});

const workflowDescription = computed(() => {
  if (!workflowStore.editingWorkflowId) return "";
  const wf = workflowStore.workflows.find(
    (w) => w.workflow_id === workflowStore.editingWorkflowId,
  );
  return wf?.description || "";
});

const toolLabelMap: Record<string, string> = {
  generate_word: "生成Word",
  generate_excel: "生成Excel",
  generate_pdf: "生成PDF",
  modify_word: "修改Word",
  modify_excel: "修改Excel",
  web_search: "网络搜索",
  calculate: "数据计算",
  send_email: "发送邮件",
  query_knowledge_base: "查询知识库",
  generate_chart: "生成图表",
  read_word: "读取Word",
  read_excel: "读取Excel",
  read_pdf: "读取PDF",
  read_txt: "读取文本",
};

function getToolLabel(toolName: string): string {
  return toolLabelMap[toolName] || toolName;
}

function getNodeStatus(nodeId: string): NodeExecutionStatus {
  return workflowStore.getNodeStatus(nodeId);
}

function getNodeClass(nodeId: string): Record<string, boolean> {
  const status = getNodeStatus(nodeId);
  const showExecutionStatus = isExecutionMode.value || isExecuting.value;
  return {
    "wf-node-completed": status === "completed",
    "wf-node-running": status === "running",
    "wf-node-failed": status === "failed",
    "wf-node-idle": status === "idle" && showExecutionStatus,
  };
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
  () => props.visible,
  (val) => {
    if (val) {
      if (workflowStore.editingNodes.length > 0) {
        const rawNodes = JSON.parse(JSON.stringify(workflowStore.editingNodes));
        const rawEdges = JSON.parse(JSON.stringify(workflowStore.editingEdges));

        nodes.value = layoutNodesLeftToRight(rawNodes, rawEdges);
        edges.value = rawEdges;
      } else {
        nodes.value = [];
        edges.value = [];
      }
    }
  },
);

function layoutNodesLeftToRight(
  rawNodes: FlowNode[],
  rawEdges: FlowEdge[],
): FlowNode[] {
  const result: FlowNode[] = [];
  const nodeMap = new Map<string, FlowNode>();
  const inDegree = new Map<string, number>();
  const adjacency = new Map<string, string[]>();

  for (const n of rawNodes) {
    nodeMap.set(n.id, { ...n });
    inDegree.set(n.id, 0);
    adjacency.set(n.id, []);
  }

  for (const e of rawEdges) {
    adjacency.get(e.source)?.push(e.target);
    inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
  }

  const layers: string[][] = [];
  const queue: string[] = [];
  for (const [id, deg] of inDegree) {
    if (deg === 0) queue.push(id);
  }

  const visited = new Set<string>();
  while (queue.length > 0) {
    const layer: string[] = [];
    const nextQueue: string[] = [];
    for (const id of queue) {
      if (visited.has(id)) continue;
      visited.add(id);
      layer.push(id);
      for (const next of adjacency.get(id) || []) {
        inDegree.set(next, (inDegree.get(next) || 0) - 1);
        if (inDegree.get(next) === 0) {
          nextQueue.push(next);
        }
      }
    }
    layers.push(layer);
    queue.length = 0;
    queue.push(...nextQueue);
  }

  for (const id of nodeMap.keys()) {
    if (!visited.has(id)) {
      layers.push([id]);
    }
  }

  const xSpacing = 220;
  const ySpacing = 100;
  const startY = 120;

  for (let layerIdx = 0; layerIdx < layers.length; layerIdx++) {
    const layer = layers[layerIdx];
    const centerY = startY + (Math.max(1, layer.length) - 1) * ySpacing * 0.5;
    for (let nodeIdx = 0; nodeIdx < layer.length; nodeIdx++) {
      const node = nodeMap.get(layer[nodeIdx]);
      if (node) {
        node.position = {
          x: 60 + layerIdx * xSpacing,
          y: centerY - ((layer.length - 1) * ySpacing) / 2 + nodeIdx * ySpacing,
        };
        result.push(node);
      }
    }
  }

  return result;
}

function handleClose() {
  workflowStore.closeEditor();
  emit("close");
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
  z-index: 2100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wf-modal {
  width: 900px;
  height: 550px;
  background: var(--bg-white);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.wf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.wf-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wf-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-title);
}

.wf-status-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.wf-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.wf-info {
  padding: 10px 20px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
  color: var(--text-content);
  flex-shrink: 0;
}

.wf-info-label {
  color: var(--text-secondary);
}

.wf-info-text {
  color: var(--text-title);
}

.wf-exec-info {
  padding: 10px 20px;
  background: var(--bg-light);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.wf-exec-progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wf-exec-label {
  font-size: 13px;
  color: var(--text-content);
  white-space: nowrap;
}

.wf-exec-progress-text {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.wf-canvas {
  flex: 1;
  position: relative;
}

.wf-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
}

.wf-node {
  min-width: 130px;
  max-width: 180px;
  border-radius: 8px;
  overflow: visible;
  font-size: 13px;
  position: relative;
  transition: box-shadow 0.3s ease;
}

.wf-node-start {
  background: var(--bg-node-teal);
  border: 2px solid var(--border-node-teal);
}

.wf-node-end {
  background: var(--bg-context-danger-hover);
  border: 2px solid var(--danger-color);
}

.wf-node-task {
  background: var(--bg-mcp-action);
  border: 2px solid var(--accent-color);
}

.wf-node-idle {
  background: var(--bg-light) !important;
  border-color: var(--text-placeholder) !important;
  color: var(--text-secondary);
}

.wf-node-idle .wf-node-icon {
  color: var(--text-placeholder);
}

.wf-node-completed {
  border-color: var(--success-color) !important;
  background: var(--bg-system-action) !important;
}

.wf-node-completed .wf-node-icon {
  color: var(--success-color);
}

.wf-node-running {
  border-color: var(--warning-color) !important;
  background: var(--bg-ask-user) !important;
  box-shadow: var(--shadow-running-glow);
}

.wf-node-running .wf-node-icon {
  color: var(--warning-color);
}

.wf-node-failed {
  border-color: var(--danger-color) !important;
  background: var(--bg-context-danger-hover) !important;
}

.wf-node-failed .wf-node-icon {
  color: var(--danger-color);
}

.wf-node-header {
  padding: 8px 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wf-node-icon {
  display: flex;
  align-items: center;
  color: inherit;
  flex-shrink: 0;
}

.wf-node-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-node-label {
  padding: 6px 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wf-node-tool {
  padding: 2px 12px 8px;
  font-size: 11px;
  color: var(--text-secondary);
  border-top: 1px solid var(--border-subtle);
  padding-top: 6px;
  margin-top: 2px;
}

.wf-node-pulse {
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  border-radius: 10px;
  border: 2px solid var(--warning-color);
  animation: pulse-ring 1.5s ease-out infinite;
  pointer-events: none;
}

.wf-node-check {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  background: var(--bg-white);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-small);
}

.wf-node-check-svg {
  fill: var(--success-color);
}

@keyframes pulse-ring {
  0% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0;
    transform: scale(1.15);
  }
}
</style>
