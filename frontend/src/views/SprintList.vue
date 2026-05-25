<template>
  <div>
    <el-button @click="$router.back()" text><el-icon><ArrowLeft /></el-icon> 返回看板</el-button>

    <div style="display: flex; justify-content: space-between; align-items: center; margin: 16px 0">
      <h2>Sprint 管理</h2>
      <div style="display: flex; gap: 8px">
        <el-button type="success" @click="handleQuickStart" :loading="quickStarting">
          <el-icon><Lightning /></el-icon>一键开始迭代
        </el-button>
        <el-button type="primary" @click="showCreateDialog = true">创建 Sprint</el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col v-for="s in sprints" :key="s.id" :span="8">
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: bold">{{ s.name }}</span>
              <el-tag :type="s.isActive ? 'success' : s.status === 'COMPLETED' ? 'info' : ''" size="small">
                {{ s.isActive ? '进行中' : s.status === 'COMPLETED' ? '已完成' : '计划中' }}
              </el-tag>
            </div>
          </template>
          <p style="color: #909399; margin-bottom: 8px" v-if="s.goal">{{ s.goal }}</p>
          <div style="font-size: 13px; color: #606266; margin-bottom: 8px">
            {{ s.startDate }} ~ {{ s.endDate }}
          </div>
          <div style="margin-bottom: 12px">
            <el-progress :percentage="s.totalIssues ? Math.round(s.completedIssues / s.totalIssues * 100) : 0"
                         :stroke-width="8" />
            <div style="font-size: 12px; color: #909399; margin-top: 4px">
              {{ s.completedIssues }} / {{ s.totalIssues }} 个任务
            </div>
          </div>
          <div style="display: flex; gap: 8px">
            <el-button size="small" @click="$router.push(`/projects/${projectId}/sprints/${s.id}/burndown`)">
              燃尽图
            </el-button>
            <el-button v-if="s.status === 'PLANNING'" size="small" type="success" @click="handleStart(s.id)">
              启动
            </el-button>
            <el-button v-if="s.isActive" size="small" type="warning" @click="showCompleteDialog(s)">
              完成
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="sprints.length === 0" description="暂无 Sprint" />

    <!-- 创建 Sprint Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建 Sprint" width="450px">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如: Sprint 1" />
        </el-form-item>
        <el-form-item label="目标">
          <el-input v-model="createForm.goal" placeholder="Sprint 目标" />
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="createForm.startDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="createForm.endDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 完成 Sprint Dialog（处理未完成任务） -->
    <el-dialog v-model="showComplete" title="完成 Sprint" width="480px">
      <p style="margin-bottom: 16px; color: var(--text-secondary)">
        此 Sprint 中还有 {{ completingSprint?.totalIssues - completingSprint?.completedIssues || 0 }} 个未完成任务，请选择处理方式：
      </p>
      <el-radio-group v-model="migrationChoice" style="display: flex; flex-direction: column; gap: 12px">
        <el-radio value="backlog">移回看板待办列</el-radio>
        <el-radio value="migrate">移至下一个 Sprint</el-radio>
      </el-radio-group>
      <el-select
        v-if="migrationChoice === 'migrate'"
        v-model="targetSprintId"
        placeholder="选择目标 Sprint"
        style="width: 100%; margin-top: 12px"
      >
        <el-option
          v-for="s in otherSprints"
          :key="s.id"
          :label="s.name"
          :value="s.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="showComplete = false">取消</el-button>
        <el-button type="primary" @click="handleCompleteWithMigration">确定完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from "vue";
import { useRoute } from "vue-router";
import {
  createSprint, getSprintsByProject, startSprint,
  quickStartSprint, completeSprintWithMigration
} from "@/api/sprint";
import { ElMessage } from "element-plus";

const route = useRoute();
const projectId = Number(route.params.id);
const sprints = ref<any[]>([]);
const showCreateDialog = ref(false);
const quickStarting = ref(false);
const createForm = reactive({ name: "", goal: "", startDate: "", endDate: "" });

const showComplete = ref(false);
const completingSprint = ref<any>(null);
const migrationChoice = ref("backlog");
const targetSprintId = ref<number | null>(null);

const otherSprints = computed(() =>
  sprints.value.filter(s => s.id !== completingSprint.value?.id && s.status !== 'COMPLETED')
);

async function fetchSprints() {
  const res = await getSprintsByProject(projectId);
  sprints.value = res.data || [];
}

async function handleCreate() {
  if (!createForm.name || !createForm.startDate || !createForm.endDate) {
    ElMessage.warning("请填写完整信息"); return;
  }
  await createSprint({ ...createForm, projectId });
  ElMessage.success("Sprint 创建成功");
  showCreateDialog.value = false;
  await fetchSprints();
}

async function handleStart(id: number) {
  await startSprint(id);
  ElMessage.success("Sprint 已启动");
  await fetchSprints();
}

async function handleQuickStart() {
  quickStarting.value = true;
  try {
    await quickStartSprint(projectId);
    ElMessage.success("2 周迭代已创建并启动，前 5 个待办任务已加入");
    await fetchSprints();
  } catch { /* handled */ }
  finally { quickStarting.value = false; }
}

function showCompleteDialog(sprint: any) {
  completingSprint.value = sprint;
  migrationChoice.value = "backlog";
  targetSprintId.value = null;
  showComplete.value = true;
}

async function handleCompleteWithMigration() {
  if (!completingSprint.value) return;
  const tid = migrationChoice.value === 'migrate' ? targetSprintId.value : undefined;
  await completeSprintWithMigration(completingSprint.value.id, tid || undefined);
  ElMessage.success("Sprint 已完成");
  showComplete.value = false;
  await fetchSprints();
}

onMounted(fetchSprints);
</script>
