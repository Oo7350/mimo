<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <div>
        <el-button @click="$router.back()" text><el-icon><ArrowLeft /></el-icon></el-button>
        <span style="font-size: 18px; font-weight: bold">{{ projectName }}</span>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button @click="$router.push(`/projects/${projectId}/sprints`)">
          <el-icon><DataAnalysis /></el-icon>Sprint
        </el-button>
        <el-button @click="$router.push(`/projects/${projectId}/reports`)">
          <el-icon><Document /></el-icon>报告
        </el-button>
        <el-button type="primary" @click="showCreateIssue = true">
          <el-icon><Plus /></el-icon>新建任务
        </el-button>
      </div>
    </div>

    <div style="display: flex; gap: 16px; overflow-x: auto; min-height: 70vh; padding-bottom: 16px">
      <div v-for="col in columns" :key="col.id"
           style="flex: 0 0 300px; background: #ebeef5; border-radius: 8px; padding: 12px">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
          <div style="display: flex; align-items: center; gap: 8px">
            <span :style="{ width: '10px', height: '10px', borderRadius: '50%', background: col.color, display: 'inline-block' }"></span>
            <span style="font-weight: bold">{{ col.name }}</span>
            <el-tag size="small" round>{{ col.issues?.length || 0 }}</el-tag>
          </div>
          <el-dropdown trigger="click">
            <el-icon style="cursor: pointer"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="editColumn(col)">编辑</el-dropdown-item>
                <el-dropdown-item @click="handleDeleteColumn(col.id)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <draggable
          :list="col.issues"
          group="issues"
          itemKey="id"
          @change="(evt: any) => handleDragChange(evt, col.id)"
          style="min-height: 60px"
        >
          <template #item="{ element }">
            <div class="issue-card" @click="openIssue(element)">
              <div style="display: flex; justify-content: space-between; align-items: flex-start">
                <span style="font-size: 11px; color: #909399">{{ element.issueKey }}</span>
                <el-tag size="small" :type="typeColor(element.type)">{{ typeLabel(element.type) }}</el-tag>
              </div>
              <div style="font-weight: bold; margin: 6px 0; font-size: 14px">{{ element.title }}</div>
              <div style="display: flex; justify-content: space-between; align-items: center; font-size: 12px">
                <div style="display: flex; gap: 4px">
                  <el-tag v-for="lbl in (element.labels || [])" :key="lbl.id"
                          size="small" :color="lbl.color" effect="dark" style="border: none">
                    {{ lbl.label }}
                  </el-tag>
                </div>
                <div v-if="element.assigneeName" style="color: #409EFF">
                  <el-icon><User /></el-icon> {{ element.assigneeName }}
                </div>
              </div>
              <div v-if="element.priority" style="margin-top: 4px">
                <el-tag size="small" :type="priorityColor(element.priority)">
                  {{ element.priority }}
                </el-tag>
              </div>
            </div>
          </template>
        </draggable>
      </div>

      <!-- 添加列 -->
      <div style="flex: 0 0 200px">
        <el-button style="width: 100%" @click="showAddColumn = true">
          <el-icon><Plus /></el-icon> 添加列
        </el-button>
      </div>
    </div>

    <!-- 新建任务 Dialog -->
    <IssueDetailDialog
      v-model:visible="showCreateIssue"
      :project-id="projectId"
      :columns="columns"
      @created="fetchBoard"
    />

    <!-- 编辑任务 Dialog -->
    <IssueDetailDialog
      v-model:visible="showEditIssue"
      :project-id="projectId"
      :columns="columns"
      :issue-id="editingIssueId"
      mode="edit"
      @updated="fetchBoard"
    />

    <!-- 添加列 Dialog -->
    <el-dialog v-model="showAddColumn" title="添加看板列" width="400px">
      <el-form label-position="top">
        <el-form-item label="列名称" required>
          <el-input v-model="newColumnName" placeholder="如: 测试中" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="newColumnColor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddColumn = false">取消</el-button>
        <el-button type="primary" @click="handleAddColumn">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑列 Dialog -->
    <el-dialog v-model="showEditColumn" title="编辑看板列" width="400px">
      <el-form label-position="top">
        <el-form-item label="列名称" required>
          <el-input v-model="editingColName" placeholder="列名称" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="editingColColor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditColumn = false">取消</el-button>
        <el-button type="primary" @click="handleEditColumn">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { getBoard, createColumn, updateColumn, deleteColumn, moveIssue } from "@/api/board";
import { getProjectById } from "@/api/project";
import draggable from "vuedraggable";
import IssueDetailDialog from "@/components/issue/IssueDetailDialog.vue";
import { ElMessage, ElMessageBox } from "element-plus";

const route = useRoute();
const projectId = Number(route.params.id);
const projectName = ref("");
const columns = ref<any[]>([]);
const showCreateIssue = ref(false);
const showEditIssue = ref(false);
const editingIssueId = ref<number>(0);
const showAddColumn = ref(false);
const newColumnName = ref("");
const newColumnColor = ref("#409EFF");
const showEditColumn = ref(false);
const editingColId = ref(0);
const editingColName = ref("");
const editingColColor = ref("");

async function fetchBoard() {
  const [boardRes, projRes] = await Promise.all([
    getBoard(projectId),
    getProjectById(projectId),
  ]);
  columns.value = boardRes.data?.columns || [];
  projectName.value = projRes.data?.name || "";
}

async function handleAddColumn() {
  if (!newColumnName.value) { ElMessage.warning("请输入列名称"); return; }
  await createColumn({ projectId, name: newColumnName.value, color: newColumnColor.value });
  ElMessage.success("列已添加");
  showAddColumn.value = false;
  newColumnName.value = "";
  await fetchBoard();
}

function editColumn(col: any) {
  editingColId.value = col.id;
  editingColName.value = col.name;
  editingColColor.value = col.color;
  showEditColumn.value = true;
}

async function handleEditColumn() {
  if (!editingColName.value) { ElMessage.warning("请输入列名称"); return; }
  await updateColumn({ id: editingColId.value, name: editingColName.value, color: editingColColor.value });
  ElMessage.success("列已更新");
  showEditColumn.value = false;
  await fetchBoard();
}

async function handleDeleteColumn(colId: number) {
  try {
    await ElMessageBox.confirm("确定删除此列?", "提示", { type: "warning" });
    await deleteColumn(colId);
    ElMessage.success("已删除");
    await fetchBoard();
  } catch { /* cancelled */ }
}

async function handleDragChange(evt: any, targetColId: number) {
  if (evt.added) {
    const issue = evt.added.element;
    const newIndex = evt.added.newIndex;
    await moveIssue({ issueId: issue.id, targetColumnId: targetColId, sortOrder: newIndex });
  }
}

function openIssue(issue: any) {
  editingIssueId.value = issue.id;
  showEditIssue.value = true;
}

function typeColor(type: string) {
  const map: Record<string, string> = { STORY: "success", TASK: "", BUG: "danger" };
  return map[type] || "";
}

function typeLabel(type: string) {
  const map: Record<string, string> = { STORY: "故事", TASK: "任务", BUG: "缺陷" };
  return map[type] || type;
}

function priorityColor(p: string) {
  const map: Record<string, string> = { HIGHEST: "danger", HIGH: "warning", MEDIUM: "", LOW: "info", LOWEST: "info" };
  return map[p] || "";
}

onMounted(fetchBoard);
</script>

<style scoped>
.issue-card {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transition: box-shadow 0.2s;
}
.issue-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
</style>
