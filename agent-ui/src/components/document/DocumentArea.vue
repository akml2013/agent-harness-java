<template>
  <div class="document-area">
    <div class="doc-header">
      <h3 class="doc-title">文档管理</h3>
      <el-button
        type="primary"
        :icon="UploadFilled"
        @click="showUploadDialog = true"
      >
        上传文档
      </el-button>
    </div>

    <div class="doc-toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索文档..."
        prefix-icon="Search"
        clearable
        style="width: 260px"
      />
      <div class="doc-filters">
        <el-radio-group v-model="fileTypeFilter" size="small">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="pdf">PDF</el-radio-button>
          <el-radio-button label="docx">Word</el-radio-button>
          <el-radio-button label="xlsx">Excel</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="doc-list">
      <div v-for="doc in filteredDocuments" :key="doc.id" class="doc-card">
        <div class="doc-icon" :class="`doc-icon-${doc.fileType}`">
          <el-icon :size="28">
            <component :is="getFileIcon(doc.fileType)" />
          </el-icon>
        </div>
        <div class="doc-info">
          <div class="doc-name">{{ doc.name }}</div>
          <div class="doc-meta">
            <span>{{ doc.fileType.toUpperCase() }}</span>
            <span>{{ doc.fileSize }}</span>
            <span>{{ doc.uploadTime }}</span>
          </div>
          <div class="doc-tags">
            <el-tag
              v-for="tag in doc.tags"
              :key="tag"
              size="small"
              effect="plain"
              class="doc-tag"
            >
              {{ tag }}
            </el-tag>
          </div>
        </div>
        <div class="doc-actions">
          <el-tooltip content="查看详情">
            <el-button :icon="View" text circle size="small" />
          </el-tooltip>
          <el-tooltip content="基于文档对话">
            <el-button :icon="ChatDotRound" text circle size="small" />
          </el-tooltip>
          <el-dropdown trigger="click">
            <el-button :icon="MoreFilled" text circle size="small" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>下载</el-dropdown-item>
                <el-dropdown-item>重新解析</el-dropdown-item>
                <el-dropdown-item divided class="danger-item"
                  >删除</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div v-if="filteredDocuments.length === 0" class="doc-empty">
        <el-empty description="暂无文档">
          <el-button type="primary" @click="showUploadDialog = true"
            >上传文档</el-button
          >
        </el-empty>
      </div>
    </div>

    <el-dialog
      v-model="showUploadDialog"
      title="上传文档"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-upload
        class="doc-upload"
        drag
        multiple
        accept=".pdf,.docx,.xlsx,.pptx,.txt"
        :auto-upload="false"
      >
        <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、Word、Excel、PPT、TXT 格式，单文件不超过 50MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload">开始上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  ChatDotRound,
  DataAnalysis,
  Document,
  MoreFilled,
  UploadFilled,
  View,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { computed, ref } from "vue";

interface DocItem {
  id: string;
  name: string;
  fileType: string;
  fileSize: string;
  uploadTime: string;
  status: string;
  tags: string[];
}

const searchKeyword = ref("");
const fileTypeFilter = ref("all");
const showUploadDialog = ref(false);

const documents = ref<DocItem[]>([
  {
    id: "doc-1",
    name: "2026年Q1销售数据报告.xlsx",
    fileType: "xlsx",
    fileSize: "2.3 MB",
    uploadTime: "2026-04-19",
    status: "parsed",
    tags: ["销售数据", "Q1报告"],
  },
  {
    id: "doc-2",
    name: "产品需求规格说明书V2.1.pdf",
    fileType: "pdf",
    fileSize: "5.1 MB",
    uploadTime: "2026-04-18",
    status: "parsed",
    tags: ["产品需求", "规格说明"],
  },
  {
    id: "doc-3",
    name: "员工手册2026版.docx",
    fileType: "docx",
    fileSize: "1.8 MB",
    uploadTime: "2026-04-17",
    status: "parsed",
    tags: ["人力资源", "制度"],
  },
  {
    id: "doc-4",
    name: "客户合同模板.pdf",
    fileType: "pdf",
    fileSize: "0.9 MB",
    uploadTime: "2026-04-16",
    status: "parsed",
    tags: ["合同", "模板"],
  },
]);

const filteredDocuments = computed(() => {
  let list = documents.value;
  if (fileTypeFilter.value !== "all") {
    list = list.filter((d) => d.fileType === fileTypeFilter.value);
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase();
    list = list.filter(
      (d) =>
        d.name.toLowerCase().includes(keyword) ||
        d.tags.some((t) => t.toLowerCase().includes(keyword)),
    );
  }
  return list;
});

function getFileIcon(fileType: string) {
  const map: Record<string, any> = {
    pdf: Document,
    docx: Document,
    xlsx: DataAnalysis,
    pptx: Document,
    txt: Document,
  };
  return map[fileType] || Document;
}

function handleUpload() {
  ElMessage.success("文档上传功能开发中");
  showUploadDialog.value = false;
}
</script>

<style scoped>
.document-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-white);
}

.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
}

.doc-title {
  font-size: 16px;
  font-weight: 500;
}

.doc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border-light);
}

.doc-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.doc-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
  transition: all var(--transition-fast);
}

.doc-card:hover {
  border-color: var(--accent-color);
  box-shadow: var(--shadow-sm);
}

.doc-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-icon-pdf {
  background: var(--bg-context-danger-hover);
  color: var(--danger-color);
}

.doc-icon-docx {
  background: var(--bg-mcp-action);
  color: var(--accent-color);
}

.doc-icon-xlsx {
  background: var(--bg-system-action);
  color: var(--success-color);
}

.doc-icon-pptx {
  background: var(--bg-ask-user);
  color: var(--warning-color);
}

.doc-icon-txt {
  background: var(--bg-disabled);
  color: var(--text-secondary);
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.doc-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.doc-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.doc-tag {
  font-size: 11px;
}

.doc-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.doc-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}

.doc-upload {
  width: 100%;
}

.danger-item {
  color: var(--danger-color) !important;
}
</style>
