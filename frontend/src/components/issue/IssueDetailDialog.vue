<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="mode === 'edit' ? '编辑任务' : '新建任务'"
    width="650px"
    destroy-on-close
  >
    <el-form :model="form" label-position="top" ref="formRef">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="任务标题" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="故事" value="STORY" />
              <el-option label="任务" value="TASK" />
              <el-option label="缺陷" value="BUG" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="优先级">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option label="最高" value="HIGHEST" />
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
              <el-option label="最低" value="LOWEST" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%" v-if="mode === 'edit'">
              <el-option label="待办" value="TODO" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="已完成" value="DONE" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="所属看板列">
            <el-select v-model="form.columnId" style="width: 100%" clearable>
              <el-option v-for="c in columns" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="指派人ID">
            <el-input-number v-model="form.assigneeId" :min="1" style="width: 100%" placeholder="用户ID" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="故事点">
            <el-input-number v-model="form.storyPoints" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="截止日期">
        <el-date-picker v-model="form.dueDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="4" placeholder="详细描述..." />
      </el-form-item>
      <el-form-item v-if="form.type === 'BUG'" label="严重程度">
        <el-select v-model="form.severity" style="width: 100%">
          <el-option label="阻塞" value="BLOCKER" />
          <el-option label="严重" value="CRITICAL" />
          <el-option label="主要" value="MAJOR" />
          <el-option label="次要" value="MINOR" />
          <el-option label="轻微" value="TRIVIAL" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.type === 'BUG'" label="复现步骤">
        <el-input v-model="form.stepsToRepro" type="textarea" :rows="3" placeholder="描述复现步骤..." />
      </el-form-item>
      <el-form-item label="标签">
        <el-input v-model="labelInput" placeholder="输入标签后回车添加" @keyup.enter="addLabel" />
        <div style="margin-top: 8px; display: flex; gap: 4px; flex-wrap: wrap">
          <el-tag v-for="(l, i) in form.labels" :key="i" closable @close="form.labels.splice(i, 1)">
            {{ l }}
          </el-tag>
        </div>
      </el-form-item>
    </el-form>

    <!-- 附件管理（仅编辑模式） -->
    <div v-if="mode === 'edit' && issueId" style="margin-top: 16px">
      <el-divider />
      <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center">
        <span style="font-weight: bold">附件</span>
        <input
          ref="fileInput"
          type="file"
          style="display: none"
          @change="onFileChange"
        />
        <el-button size="small" type="primary" text @click="($refs.fileInput as HTMLInputElement)?.click()">
          <el-icon><Upload /></el-icon> 上传附件
        </el-button>
      </div>
      <div v-if="attachments.length === 0" style="color: #909399; font-size: 13px">暂无附件</div>
      <div v-for="att in attachments" :key="att.id"
           style="display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid #ebeef5">
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon><Document /></el-icon>
          <div>
            <div style="font-size: 13px">{{ att.fileName }}</div>
            <div style="font-size: 11px; color: #909399">
              {{ formatSize(att.fileSize) }} · {{ att.uploaderName }} · {{ att.createdAt }}
            </div>
          </div>
        </div>
        <div>
          <el-button size="small" text @click="handleDownload(att.id)">
            <el-icon><Download /></el-icon>
          </el-button>
          <el-button size="small" text type="danger" @click="handleDeleteAttachment(att.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button v-if="mode === 'edit'" type="danger" @click="handleDelete" :loading="loading">删除</el-button>
      <el-button type="primary" @click="handleSave" :loading="loading">
        {{ mode === 'edit' ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { createIssue, getIssueById, updateIssue, deleteIssue } from "@/api/issue";
import { getIssueAttachments, uploadAttachment, downloadAttachment, deleteAttachment } from "@/api/attachment";
import { ElMessage } from "element-plus";

const props = defineProps<{
  visible: boolean;
  projectId: number;
  columns: any[];
  issueId?: number;
  mode?: string;
}>();

const emit = defineEmits(["update:visible", "created", "updated"]);

const formRef = ref();
const loading = ref(false);
const labelInput = ref("");
const attachments = ref<any[]>([]);

const form = reactive<any>({
  title: "", type: "TASK", priority: "MEDIUM", status: "TODO",
  columnId: null, assigneeId: null, storyPoints: null,
  dueDate: null, description: "", severity: null, stepsToRepro: null,
  labels: [],
});

async function loadIssue() {
  if (!props.issueId) return;
  const res = await getIssueById(props.issueId);
  const issue = res.data;
  Object.assign(form, {
    id: issue.id,
    title: issue.title,
    type: issue.type,
    priority: issue.priority,
    status: issue.status,
    columnId: issue.columnId,
    assigneeId: issue.assigneeId,
    storyPoints: issue.storyPoints,
    dueDate: issue.dueDate,
    description: issue.description,
    severity: issue.severity,
    stepsToRepro: issue.stepsToRepro,
    labels: (issue.labels || []).map((l: any) => l.label),
  });
  await fetchAttachments();
}

async function fetchAttachments() {
  if (!props.issueId) return;
  const res = await getIssueAttachments(props.issueId);
  attachments.value = res.data || [];
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !props.issueId) return;
  try {
    await uploadAttachment(props.issueId, file);
    ElMessage.success("附件上传成功");
    await fetchAttachments();
  } finally {
    input.value = ""; // reset input
  }
}

async function handleDownload(attId: number) {
  const res = await downloadAttachment(attId);
  const blob = res.data as Blob;
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  const disposition = (res as any).headers?.["content-disposition"];
  const match = disposition?.match(/filename="(.+)"/);
  link.download = match?.[1] || "file";
  link.click();
  window.URL.revokeObjectURL(url);
}

async function handleDeleteAttachment(attId: number) {
  await deleteAttachment(attId);
  ElMessage.success("附件已删除");
  await fetchAttachments();
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

watch(() => props.visible, async (v) => {
  if (v) {
    Object.assign(form, {
      id: null, title: "", type: "TASK", priority: "MEDIUM", status: "TODO",
      columnId: null, assigneeId: null, storyPoints: null,
      dueDate: null, description: "", severity: null, stepsToRepro: null,
      labels: [],
    });
    if (props.mode === "edit") await loadIssue();
  }
});

function addLabel() {
  if (labelInput.value && !form.labels.includes(labelInput.value)) {
    form.labels.push(labelInput.value);
    labelInput.value = "";
  }
}

async function handleSave() {
  if (!form.title) { ElMessage.warning("请输入标题"); return; }
  loading.value = true;
  try {
    const data = { ...form, projectId: props.projectId };
    if (props.mode === "edit") {
      await updateIssue(data);
      emit("updated");
    } else {
      await createIssue(data);
      emit("created");
    }
    ElMessage.success(props.mode === "edit" ? "保存成功" : "创建成功");
    emit("update:visible", false);
  } finally {
    loading.value = false;
  }
}

async function handleDelete() {
  await deleteIssue(form.id);
  ElMessage.success("已删除");
  emit("update:visible", false);
  emit("updated");
}
</script>
