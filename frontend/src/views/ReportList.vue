<template>
  <div>
    <el-button @click="$router.back()" text><el-icon><ArrowLeft /></el-icon> 返回看板</el-button>

    <div style="display: flex; justify-content: space-between; align-items: center; margin: 16px 0">
      <h2>日报/周报</h2>
      <div style="display: flex; gap: 8px">
        <el-select v-model="filterType" style="width: 120px" @change="fetchReports">
          <el-option label="全部" value="" />
          <el-option label="日报" value="DAILY" />
          <el-option label="周报" value="WEEKLY" />
        </el-select>
        <el-button type="primary" @click="showGenerate = true">生成报告</el-button>
      </div>
    </div>

    <el-table :data="reports" stripe>
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.type === 'DAILY' ? '' : 'warning'" size="small">
            {{ row.type === 'DAILY' ? '日报' : '周报' }}
          </el-tag>
        </template>
    </el-table-column>
      <el-table-column prop="reportDate" label="日期" width="130" />
      <el-table-column prop="projectName" label="项目" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'info'" size="small">
            {{ row.status === 'SUBMITTED' ? '已提交' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="viewReport(row)">查看</el-button>
          <el-button v-if="row.status !== 'SUBMITTED'" size="small" type="success" @click="handleSubmit(row.id)">
            提交
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="reports.length === 0" description="暂无报告" />

    <!-- 生成报告 Dialog -->
    <el-dialog v-model="showGenerate" title="生成报告" width="400px">
      <el-form label-position="top">
        <el-form-item label="类型">
          <el-select v-model="genType" style="width: 100%">
            <el-option label="日报" value="DAILY" />
            <el-option label="周报" value="WEEKLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="genDate" type="date" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerate = false">取消</el-button>
        <el-button type="primary" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 查看报告 Dialog -->
    <el-dialog v-model="showViewDialog" title="报告详情" width="600px">
      <div style="white-space: pre-wrap; line-height: 1.8">{{ viewingContent }}</div>
      <template #footer v-if="viewingReport?.status !== 'SUBMITTED'">
        <el-button type="primary" @click="handleUpdateContent">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { generateReport, getReports, submitReport, updateReport } from "@/api/report";
import { ElMessage } from "element-plus";

const route = useRoute();
const projectId = Number(route.params.id);
const reports = ref<any[]>([]);
const filterType = ref("");
const showGenerate = ref(false);
const genType = ref("DAILY");
const genDate = ref("");
const showViewDialog = ref(false);
const viewingReport = ref<any>(null);
const viewingContent = ref("");

async function fetchReports() {
  const res = await getReports({ projectId, type: filterType.value || undefined });
  reports.value = res.data || [];
}

async function handleGenerate() {
  await generateReport({ projectId, type: genType.value, reportDate: genDate.value || undefined });
  ElMessage.success("报告已生成");
  showGenerate.value = false;
  await fetchReports();
}

function viewReport(report: any) {
  viewingReport.value = report;
  viewingContent.value = report.content || "";
  showViewDialog.value = true;
}

async function handleSubmit(id: number) {
  await submitReport(id);
  ElMessage.success("已提交");
  await fetchReports();
}

async function handleUpdateContent() {
  if (!viewingReport.value) return;
  await updateReport({ id: viewingReport.value.id, content: viewingContent.value });
  ElMessage.success("已保存");
  showViewDialog.value = false;
  await fetchReports();
}

onMounted(fetchReports);
</script>
