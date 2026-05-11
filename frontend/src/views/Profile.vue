<template>
  <div>
    <h2 style="margin-bottom: 20px">个人信息</h2>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span style="font-weight: bold">基本资料</span></template>
          <el-form :model="profileForm" label-position="top" ref="profileFormRef">
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" placeholder="用户名" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="邮箱" />
            </el-form-item>
            <el-form-item label="角色">
              <el-tag :type="profile?.role === 'ROLE_ADMIN' ? 'danger' : 'info'">
                {{ profile?.role === 'ROLE_ADMIN' ? '管理员' : '成员' }}
              </el-tag>
            </el-form-item>
            <el-form-item label="注册时间">
              <span style="color: #909399">{{ profile?.createdAt }}</span>
            </el-form-item>
            <el-button type="primary" @click="handleUpdateProfile" :loading="profileLoading">保存修改</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span style="font-weight: bold">修改密码</span></template>
          <el-form :model="passwordForm" label-position="top" ref="passwordFormRef">
            <el-form-item label="原密码" required>
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" required>
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
            </el-form-item>
            <el-form-item label="确认密码" required>
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-button type="warning" @click="handleChangePassword" :loading="passwordLoading">修改密码</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from "vue";
import { ElMessage } from "element-plus";
import request from "@/api/request";
import { getUserProfile, updateUserProfile, changePassword } from "@/api/user";

const profile = ref<any>(null);
const profileLoading = ref(false);
const passwordLoading = ref(false);

const profileForm = reactive({ username: "", email: "" });
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirmPassword: "" });

async function fetchProfile() {
  const res = await getUserProfile();
  profile.value = res.data;
  profileForm.username = profile.value?.username || "";
  profileForm.email = profile.value?.email || "";
}

async function handleUpdateProfile() {
  profileLoading.value = true;
  try {
    await updateUserProfile({ username: profileForm.username, email: profileForm.email });
    ElMessage.success("资料更新成功");
    await fetchProfile();
  } finally {
    profileLoading.value = false;
  }
}

async function handleChangePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning("请填写完整密码信息"); return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning("两次输入的新密码不一致"); return;
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning("新密码至少6位"); return;
  }
  passwordLoading.value = true;
  try {
    await changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword });
    ElMessage.success("密码修改成功");
    passwordForm.oldPassword = "";
    passwordForm.newPassword = "";
    passwordForm.confirmPassword = "";
  } finally {
    passwordLoading.value = false;
  }
}

onMounted(fetchProfile);
</script>
