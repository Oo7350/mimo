<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>我的团队</h2>
      <el-button type="primary" @click="showCreateDialog = true">创建团队</el-button>
    </div>

    <el-row :gutter="20">
      <el-col v-for="team in teams" :key="team.id" :span="8">
        <el-card shadow="hover" style="margin-bottom: 20px; cursor: pointer" @click="goToTeam(team.id)">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: bold">{{ team.name }}</span>
              <el-tag size="small" type="info">{{ team.memberCount }} 人</el-tag>
            </div>
          </template>
          <p style="color: #909399; min-height: 40px">{{ team.description || '暂无描述' }}</p>
          <div style="color: #c0c4cc; font-size: 12px">创建者: {{ team.ownerName }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="teams.length === 0" description="暂无团队" />

    <!-- 创建团队 Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建团队" width="450px">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="团队名称" required>
          <el-input v-model="createForm.name" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="团队描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from "vue";
import { useRouter } from "vue-router";
import { createTeam, getMyTeams } from "@/api/team";
import { ElMessage } from "element-plus";

const router = useRouter();
const teams = ref<TeamVO[]>([]);
const showCreateDialog = ref(false);
const creating = ref(false);
const createForm = reactive({ name: "", description: "" });

async function fetchTeams() {
  const res = await getMyTeams();
  teams.value = res.data || [];
}

async function handleCreate() {
  if (!createForm.name) {
    ElMessage.warning("请输入团队名称");
    return;
  }
  creating.value = true;
  try {
    await createTeam(createForm);
    ElMessage.success("团队创建成功");
    showCreateDialog.value = false;
    createForm.name = "";
    createForm.description = "";
    await fetchTeams();
  } finally {
    creating.value = false;
  }
}

function goToTeam(teamId: number) {
  router.push(`/teams/${teamId}`);
}

onMounted(fetchTeams);
</script>
