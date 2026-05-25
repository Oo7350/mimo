<template>
  <div>
    <div style="margin-bottom: 20px">
      <el-button @click="$router.back()" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-size: 18px; font-weight: bold">{{ team?.name }}</span>
              <el-button type="primary" size="small" @click="showInviteDialog = true">邀请成员</el-button>
            </div>
          </template>
          <p style="color: #909399; margin-bottom: 20px">{{ team?.description || '暂无描述' }}</p>

          <el-table :data="members" stripe>
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="email" label="邮箱" />
            <el-table-column prop="role" label="角色" width="120">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ROLE_ADMIN' ? 'danger' : 'info'" size="small">
                  {{ row.role === 'ROLE_ADMIN' ? '管理员' : '成员' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="row.role !== 'ROLE_ADMIN'"
                  type="danger"
                  size="small"
                  text
                  @click="handleRemove(row.userId)"
                >移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span style="font-weight: bold">项目列表</span></template>
          <div v-if="projects.length === 0" style="color: #909399; text-align: center; padding: 20px">
            暂无项目
          </div>
          <div v-for="p in projects" :key="p.id" style="padding: 10px 0; border-bottom: 1px solid #ebeef5; cursor: pointer"
               @click="$router.push(`/projects/${p.id}/board`)">
            <div style="font-weight: bold">{{ p.name }}</div>
            <div style="color: #909399; font-size: 12px">{{ p.key }}</div>
          </div>
          <div style="margin-top: 12px; text-align: center">
            <el-button size="small" @click="showCreateProject = true">创建项目</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 邀请成员 Dialog -->
    <el-dialog v-model="showInviteDialog" title="邀请成员" width="400px">
      <el-form label-position="top">
        <el-form-item label="用户ID">
          <el-input v-model="inviteUserIdInput" style="width: 100%" placeholder="输入用户ID，下方可查已注册用户" />
          <div style="color: var(--text-secondary); font-size: 11px; margin-top: 6px">
            已注册用户：admin(1)、zhangsan(2)、lisi(3)
          </div>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="inviteRole" style="width: 100%">
            <el-option label="成员" value="ROLE_MEMBER" />
            <el-option label="管理员" value="ROLE_ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInviteDialog = false">取消</el-button>
        <el-button type="primary" @click="handleInvite">邀请</el-button>
      </template>
    </el-dialog>

    <!-- 创建项目 Dialog -->
    <el-dialog v-model="showCreateProject" title="创建项目" width="450px">
      <el-form :model="projectForm" label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="projectForm.name" placeholder="如: Mimo敏捷管理" />
        </el-form-item>
        <el-form-item label="项目Key" required>
          <el-input v-model="projectForm.key" placeholder="如: MIMO" style="text-transform: uppercase" />
        </el-form-item>
        <el-form-item label="模板">
          <el-select v-model="projectForm.template" style="width: 100%">
            <el-option label="Scrum" value="SCRUM" />
            <el-option label="Kanban" value="KANBAN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateProject = false">取消</el-button>
        <el-button type="primary" @click="handleCreateProject">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from "vue";
import { useRoute } from "vue-router";
import { getTeamById, getMembers, inviteMember, removeMember } from "@/api/team";
import { getProjectsByTeam, createProject } from "@/api/project";
import { ElMessage, ElMessageBox } from "element-plus";

const route = useRoute();
const teamId = Number(route.params.id);
const team = ref<any>(null);
const members = ref<any[]>([]);
const projects = ref<any[]>([]);
const showInviteDialog = ref(false);
const inviteUserIdInput = ref("");
const inviteRole = ref("ROLE_MEMBER");
const showCreateProject = ref(false);
const projectForm = reactive({ name: "", key: "", template: "SCRUM" });

async function fetchData() {
  const [teamRes, membersRes, projectsRes] = await Promise.all([
    getTeamById(teamId),
    getMembers(teamId),
    getProjectsByTeam(teamId),
  ]);
  team.value = teamRes.data;
  members.value = membersRes.data || [];
  projects.value = projectsRes.data || [];
}

async function handleInvite() {
  const uid = parseInt(inviteUserIdInput.value)
  if (!uid) { ElMessage.warning("请输入有效的用户ID"); return }
  await inviteMember({ teamId, userId: uid, role: inviteRole.value })
  ElMessage.success("邀请成功")
  showInviteDialog.value = false
  inviteUserIdInput.value = ""
  await fetchData()
}

async function handleRemove(userId: number) {
  await ElMessageBox.confirm("确定移除该成员?", "提示", { type: "warning" });
  await removeMember(teamId, userId);
  ElMessage.success("已移除");
  await fetchData();
}

async function handleCreateProject() {
  if (!projectForm.name || !projectForm.key) { ElMessage.warning("请填写项目信息"); return; }
  await createProject({ ...projectForm, teamId });
  ElMessage.success("项目创建成功");
  showCreateProject.value = false;
  projectForm.name = "";
  projectForm.key = "";
  await fetchData();
}

onMounted(fetchData);
</script>
