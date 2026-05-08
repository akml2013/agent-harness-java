<template>
  <Teleport to="body">
    <div v-if="visible" class="kb-overlay" @click.self="$emit('close')">
      <div class="kb-modal">
        <div class="kb-header">
          <h3>知识库管理</h3>
          <el-button
            :icon="Close"
            text
            circle
            size="small"
            @click="$emit('close')"
          />
        </div>

        <div class="kb-toolbar">
          <el-button
            type="primary"
            :icon="Upload"
            @click="showUploadDialog = true"
          >
            上传知识库
          </el-button>
          <el-button :icon="Refresh" @click="() => loadKnowledgeBases()"
            >刷新</el-button
          >
        </div>

        <div class="kb-content">
          <div v-if="loading" class="kb-loading">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div v-else-if="knowledgeBases.length === 0" class="kb-empty">
            <el-icon :size="48" color="var(--text-placeholder)"
              ><FolderOpened
            /></el-icon>
            <span>暂无知识库，点击上方按钮上传</span>
          </div>

          <div v-else class="kb-list">
            <div v-for="kb in knowledgeBases" :key="kb.kbId" class="kb-card">
              <div class="kb-card-header">
                <div class="kb-card-info">
                  <span class="kb-name">{{ kb.name }}</span>
                  <el-tag
                    :type="getStatusType(kb.status)"
                    size="small"
                    class="kb-status-tag"
                  >
                    {{ getStatusLabel(kb.status) }}
                  </el-tag>
                </div>
                <div class="kb-card-actions">
                  <el-button
                    v-if="kb.status === 'ERROR'"
                    type="warning"
                    size="small"
                    text
                    @click="handleRetry(kb.kbId)"
                  >
                    重试
                  </el-button>
                  <el-popconfirm
                    title="确定删除此知识库？"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    confirm-button-type="danger"
                    @confirm="handleDelete(kb.kbId)"
                  >
                    <template #reference>
                      <el-button type="danger" size="small" text
                        >删除</el-button
                      >
                    </template>
                  </el-popconfirm>
                </div>
              </div>

              <div class="kb-card-meta">
                <span>{{ kb.fileName }}</span>
                <span
                  >{{ kb.fileType.toUpperCase() }} ·
                  {{ formatFileSize(kb.fileSize) }}</span
                >
                <span>分块: {{ kb.chunkCount }}</span>
                <span>策略: {{ getStrategyLabel(kb.chunkStrategy) }}</span>
              </div>

              <div v-if="kb.description" class="kb-card-desc">
                {{ kb.description }}
              </div>

              <div
                v-if="kb.status === 'CHUNKING' || kb.status === 'EMBEDDING'"
                class="kb-progress"
              >
                <el-progress
                  :percentage="kb.progress"
                  :stroke-width="6"
                  :format="() => getStatusLabel(kb.status)"
                />
              </div>

              <div
                v-if="kb.status === 'ERROR' && kb.errorMessage"
                class="kb-error"
              >
                <el-icon><WarningFilled /></el-icon>
                <span>{{ kb.errorMessage }}</span>
              </div>
            </div>
          </div>
        </div>

        <el-dialog
          v-model="showUploadDialog"
          title="上传知识库"
          width="520px"
          :close-on-click-modal="false"
          append-to-body
        >
          <el-form label-width="80px" label-position="left">
            <el-form-item label="选择文件" required>
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                accept=".docx,.txt,.pdf,.xlsx"
                :on-change="handleFileChange"
                :on-remove="handleFileRemove"
                drag
                class="kb-upload-area"
              >
                <el-icon :size="36" style="color: var(--text-placeholder)"
                  ><UploadFilled
                /></el-icon>
                <div class="kb-upload-text">
                  拖拽文件到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                  <div class="upload-tip">支持 docx/txt/pdf/xlsx 格式</div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="知识库名称">
              <el-input
                v-model="uploadForm.name"
                placeholder="默认取文件名"
                clearable
              />
            </el-form-item>

            <div class="kb-form-row">
              <el-form-item label="分块策略" class="kb-form-row-item">
                <el-radio-group v-model="uploadForm.chunkStrategy" size="small">
                  <el-radio-button value="PARAGRAPH">按段落</el-radio-button>
                  <el-radio-button value="FIXED_SIZE">固定大小</el-radio-button>
                  <el-radio-button value="CHAPTER">按章节</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </div>

            <div class="kb-form-row">
              <el-form-item label="分块大小" class="kb-form-row-item">
                <el-slider
                  v-model="uploadForm.chunkSize"
                  :min="200"
                  :max="2000"
                  :step="100"
                  show-input
                  :show-input-controls="false"
                  style="padding-right: 8px"
                />
              </el-form-item>
              <el-form-item label="分块重叠" class="kb-form-row-item">
                <el-slider
                  v-model="uploadForm.chunkOverlap"
                  :min="0"
                  :max="200"
                  :step="10"
                  show-input
                  :show-input-controls="false"
                  style="padding-right: 8px"
                />
              </el-form-item>
            </div>
          </el-form>

          <template #footer>
            <el-button @click="showUploadDialog = false">取消</el-button>
            <el-button
              type="primary"
              :loading="uploading"
              :disabled="!uploadForm.file"
              @click="handleUpload"
            >
              {{ uploading ? "上传中..." : "确认上传" }}
            </el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  type KnowledgeBaseInfo,
  deleteKnowledgeBase,
  fetchKnowledgeBases,
  retryKnowledgeBaseIndex,
  uploadKnowledgeBase,
} from "@/services/agentApi";
import {
  Close,
  FolderOpened,
  Loading,
  Refresh,
  Upload,
  UploadFilled,
  WarningFilled,
} from "@element-plus/icons-vue";
import type { UploadFile } from "element-plus";
import { ElMessage } from "element-plus";
import { onMounted, onUnmounted, reactive, ref } from "vue";

defineProps<{
  visible: boolean;
}>();

defineEmits<{
  (e: "close"): void;
}>();

const knowledgeBases = ref<KnowledgeBaseInfo[]>([]);
const loading = ref(false);
const showUploadDialog = ref(false);
const uploading = ref(false);
const uploadRef = ref();

const uploadForm = reactive({
  file: null as File | null,
  name: "",
  chunkStrategy: "PARAGRAPH",
  chunkSize: 500,
  chunkOverlap: 50,
});

let pollTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  loadKnowledgeBases();
  startPolling();
});

onUnmounted(() => {
  stopPolling();
});

function startPolling() {
  pollTimer = setInterval(() => {
    const hasProcessing = knowledgeBases.value.some(
      (kb) =>
        kb.status === "UPLOADING" ||
        kb.status === "CHUNKING" ||
        kb.status === "EMBEDDING",
    );
    if (hasProcessing) {
      loadKnowledgeBases(true);
    }
  }, 5000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function loadKnowledgeBases(silent = false) {
  if (!silent) loading.value = true;
  try {
    const res = await fetchKnowledgeBases();
    if (res.success) {
      knowledgeBases.value = res.knowledge_bases;
    }
  } catch {
    if (!silent) ElMessage.error("获取知识库列表失败");
  } finally {
    loading.value = false;
  }
}

function handleFileChange(file: UploadFile) {
  if (file.raw) {
    uploadForm.file = file.raw;
    if (!uploadForm.name) {
      uploadForm.name = file.name.replace(/\.[^.]+$/, "");
    }
  }
}

function handleFileRemove() {
  uploadForm.file = null;
}

async function handleUpload() {
  if (!uploadForm.file) {
    ElMessage.warning("请选择文件");
    return;
  }

  uploading.value = true;
  try {
    const res = await uploadKnowledgeBase(uploadForm.file, undefined, {
      name: uploadForm.name || undefined,
      chunkStrategy: uploadForm.chunkStrategy,
      chunkSize: uploadForm.chunkSize,
      chunkOverlap: uploadForm.chunkOverlap,
    });

    if (res.success) {
      ElMessage.success(res.message || "知识库创建成功");
      showUploadDialog.value = false;
      resetUploadForm();
      loadKnowledgeBases();
    } else {
      ElMessage.error(res.error || "上传失败");
    }
  } catch {
    ElMessage.error("上传失败");
  } finally {
    uploading.value = false;
  }
}

function resetUploadForm() {
  uploadForm.file = null;
  uploadForm.name = "";
  uploadForm.chunkStrategy = "PARAGRAPH";
  uploadForm.chunkSize = 500;
  uploadForm.chunkOverlap = 50;
  if (uploadRef.value) {
    uploadRef.value.clearFiles();
  }
}

async function handleDelete(kbId: string) {
  try {
    const res = await deleteKnowledgeBase(kbId);
    if (res.success) {
      ElMessage.success("删除成功");
      loadKnowledgeBases();
    } else {
      ElMessage.error("删除失败");
    }
  } catch {
    ElMessage.error("删除失败");
  }
}

async function handleRetry(kbId: string) {
  try {
    const res = await retryKnowledgeBaseIndex(kbId);
    if (res.success) {
      ElMessage.success("已重新开始索引");
      loadKnowledgeBases();
    } else {
      ElMessage.error(res.message || "重试失败");
    }
  } catch {
    ElMessage.error("重试失败");
  }
}

function getStatusType(status: string) {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "ERROR":
      return "danger";
    case "UPLOADING":
    case "CHUNKING":
    case "EMBEDDING":
      return "warning";
    default:
      return "info";
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case "UPLOADING":
      return "上传中";
    case "CHUNKING":
      return "分块中";
    case "EMBEDDING":
      return "向量化中";
    case "ACTIVE":
      return "已就绪";
    case "ERROR":
      return "错误";
    default:
      return status;
  }
}

function getStrategyLabel(strategy: string) {
  switch (strategy) {
    case "PARAGRAPH":
      return "按段落";
    case "FIXED_SIZE":
      return "固定大小";
    case "CHAPTER":
      return "按章节";
    default:
      return strategy;
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}
</script>

<style scoped>
.kb-overlay {
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

.kb-modal {
  width: 80%;
  max-width: 900px;
  height: 80vh;
  background: var(--bg-white);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-modal);
}

.kb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.kb-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-title);
}

.kb-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-divider);
  flex-shrink: 0;
}

.kb-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.kb-loading,
.kb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kb-card {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;
}

.kb-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.kb-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-card-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kb-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-title);
}

.kb-status-tag {
  flex-shrink: 0;
}

.kb-card-actions {
  display: flex;
  gap: 4px;
}

.kb-card-meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  flex-wrap: wrap;
}

.kb-card-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-content);
  line-height: 1.5;
}

.kb-progress {
  margin-top: 10px;
}

.kb-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--bg-context-danger-hover);
  border-radius: 4px;
  font-size: 12px;
  color: var(--danger-color);
}

.kb-error .el-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.upload-tip {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.kb-upload-area {
  width: 100%;
}

.kb-upload-area :deep(.el-upload) {
  width: 100%;
}

.kb-upload-area :deep(.el-upload-dragger) {
  width: 100%;
  padding: 16px;
  box-sizing: border-box;
}

.kb-upload-area :deep(.el-upload-list) {
  max-width: 100%;
  overflow: hidden;
}

.kb-upload-area :deep(.el-upload-list__item) {
  max-width: 100%;
}

.kb-upload-area :deep(.el-upload-list__item-name) {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-upload-text {
  font-size: 13px;
  color: var(--text-content);
}

.kb-form-row {
  display: flex;
  gap: 16px;
}

.kb-form-row-item {
  flex: 1;
  min-width: 0;
}
</style>
