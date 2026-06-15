<template>
  <div class="profile-page">
    <PageHeader title="个人设置" subtitle="管理你的账号信息与偏好" />

    <!-- 个人信息 Hero -->
    <div class="profile-hero">
      <div
        class="profile-hero__avatar profile-hero__avatar--uploadable"
        :style="avatarStyle"
        @click="triggerAvatarUpload"
      >
        <img v-if="avatarPreview" :src="avatarPreview" alt="avatar" />
        <template v-else>{{ (profile?.username || '?').charAt(0).toUpperCase() }}</template>
        <div class="profile-hero__avatar-overlay">
          <el-icon><Camera /></el-icon>
          更换头像
        </div>
      </div>
      <input ref="avatarInput" type="file" accept="image/*" style="display: none" @change="handleAvatarChange" />
      <div>
        <div class="profile-hero__name">{{ profile?.username || '加载中...' }}</div>
        <div class="profile-hero__meta">
          {{ profile?.email }}
          ·
          <el-tag
            size="small"
            :type="profile?.role === 'ROLE_ADMIN' ? 'danger' : 'info'"
            effect="plain"
            style="margin-left: 4px"
          >
            {{ profile?.role === 'ROLE_ADMIN' ? '系统管理员' : '普通成员' }}
          </el-tag>
          <UserBadge v-if="userLevel" :level="userLevel" size="medium" style="margin-left: 6px" />
        </div>
      </div>
      <div class="profile-hero__actions">
        <button class="profile-theme-btn" @click="appStore.toggleDarkMode()">
          <el-icon><component :is="appStore.darkMode ? Sunny : Moon" /></el-icon>
          {{ appStore.darkMode ? '浅色模式' : '深色模式' }}
        </button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :md="12">
        <div class="settings-card">
          <div class="settings-card__header">
            <div class="settings-card__header-icon" style="background: rgba(99,102,241,0.12); color: var(--color-primary)">
              <el-icon><User /></el-icon>
            </div>
            基本资料
          </div>
          <div class="settings-card__body">
            <el-form :model="profileForm" label-position="top">
              <el-form-item label="用户名">
                <el-input v-model="profileForm.username" placeholder="用户名" prefix-icon="User" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" placeholder="邮箱" prefix-icon="Message" />
              </el-form-item>
              <el-form-item label="注册时间">
                <el-input :model-value="profile?.createdAt || '-'" disabled />
              </el-form-item>
              <el-button type="primary" :loading="profileLoading" @click="handleUpdateProfile">
                保存修改
              </el-button>
            </el-form>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :md="12">
        <div class="settings-card">
          <div class="settings-card__header">
            <div class="settings-card__header-icon" style="background: rgba(245,158,11,0.12); color: var(--color-warning)">
              <el-icon><Lock /></el-icon>
            </div>
            修改密码
          </div>
          <div class="settings-card__body">
            <el-form :model="passwordForm" label-position="top">
              <el-form-item label="原密码" required>
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" prefix-icon="Lock" />
              </el-form-item>
              <el-form-item label="新密码" required>
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位" prefix-icon="Lock" />
              </el-form-item>
              <el-form-item label="确认密码" required>
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" prefix-icon="Lock" />
              </el-form-item>
              <el-button type="warning" :loading="passwordLoading" @click="handleChangePassword">
                修改密码
              </el-button>
            </el-form>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from "vue"
import { ElMessage } from "element-plus"
import { Moon, Sunny, Camera } from "@element-plus/icons-vue"
import { getUserProfile, updateUserProfile, changePassword } from "@/api/user"
import { getMyLevel } from "@/api/user-level"
import { useAppStore } from "@/store/app"
import { avatarGradient } from "@/utils/color"
import PageHeader from "@/components/common/PageHeader.vue"
import UserBadge from "@/components/common/UserBadge.vue"

const appStore = useAppStore()
const profile = ref<any>(null)
const userLevel = ref<any>(null)
const profileLoading = ref(false)
const passwordLoading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarPreview = ref<string>("")

const avatarStyle = computed(() => {
  if (avatarPreview.value) return {}
  return { background: avatarGradient(profile?.value?.username || 'U') }
})

const profileForm = reactive({ username: "", email: "" })
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirmPassword: "" })

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  // 限制大小 2MB
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning("图片大小不能超过 2MB")
    return
  }
  // 转为 base64 预览并存储
  const reader = new FileReader()
  reader.onload = async () => {
    const base64 = reader.result as string
    avatarPreview.value = base64
    // 自动保存头像
    try {
      await updateUserProfile({ avatar: base64 })
      ElMessage.success("头像已更新")
    } catch {
      ElMessage.error("头像保存失败")
      avatarPreview.value = ""
    }
  }
  reader.readAsDataURL(file)
}

async function fetchProfile() {
  const res = await getUserProfile()
  profile.value = res.data
  profileForm.username = profile.value?.username || ""
  profileForm.email = profile.value?.email || ""
  avatarPreview.value = profile.value?.avatar || ""
  // 获取铭牌
  try { const lr = await getMyLevel(); userLevel.value = lr.data } catch { /* */ }
}

async function handleUpdateProfile() {
  profileLoading.value = true
  try {
    await updateUserProfile({ username: profileForm.username, email: profileForm.email })
    ElMessage.success("资料更新成功")
    await fetchProfile()
  } finally {
    profileLoading.value = false
  }
}

async function handleChangePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning("请填写完整密码信息"); return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning("两次输入的新密码不一致"); return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning("新密码至少6位"); return
  }
  passwordLoading.value = true
  try {
    await changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success("密码修改成功")
    passwordForm.oldPassword = ""
    passwordForm.newPassword = ""
    passwordForm.confirmPassword = ""
  } finally {
    passwordLoading.value = false
  }
}

onMounted(fetchProfile)
</script>

<style scoped lang="scss">
.profile-page {
  max-width: 960px;
  margin: 0 auto;
}

.profile-hero {
  position: relative;

  &__avatar--uploadable {
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);

    &:hover {
      transform: scale(1.05);
      box-shadow: 0 8px 28px rgba(0, 0, 0, 0.2);
    }

    img { width: 100%; height: 100%; object-fit: cover; }
    &:hover .profile-hero__avatar-overlay { opacity: 1; }
  }

  &__avatar-overlay {
    position: absolute; inset: 0;
    background: rgba(0, 0, 0, 0.55);
    backdrop-filter: blur(6px);
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    gap: 5px;
    color: #fff;
    font-size: 12.5px;
    font-weight: 700;
    opacity: 0;
    transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  }

  &__actions { margin-left: auto; flex-shrink: 0; }
}

.profile-theme-btn {
  display: flex; align-items: center; gap: 7px;
  padding: 9px 18px;
  border-radius: 11px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

  &:hover {
    background: rgba(255, 255, 255, 0.22);
    transform: translateY(-2px) scale(1.03);
    box-shadow: 0 4px 14px rgba(255, 255, 255, 0.15);
  }
  &:active { transform: translateY(0) scale(0.97); }
}

.settings-card {
  margin-bottom: 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.3s ease;

  &:hover { box-shadow: var(--shadow-md); }

  &__header {
    display: flex; align-items: center; gap: 12px;
    padding: 20px 24px;
    font-size: 16px;
    font-weight: 750;
    color: var(--text-primary);
    letter-spacing: -0.2px;
    border-bottom: 1px solid var(--border-color-light);
  }

  &__header-icon {
    width: 38px; height: 38px;
    border-radius: 11px;
    display: flex; align-items: center; justify-content: center;
    font-size: 17px;
    flex-shrink: 0;
  }

  &__body { padding: 22px 24px; }

  :deep(.el-form-item__label) {
    font-weight: 650;
    font-size: 13px;
    color: var(--text-primary);
    letter-spacing: -0.1px;
    padding-bottom: 8px;
  }

  :deep(.el-input) {
    .el-input__wrapper {
      border-radius: var(--border-radius-sm);
      box-shadow: 0 0 0 1px var(--border-color);
      transition: all 0.25s ease;
      &:hover { box-shadow: 0 0 0 1px rgba(79, 70, 229, 0.3); }
      &.is-focus {
        box-shadow:
          0 0 0 2px rgba(79, 70, 229, 0.25),
          0 0 20px rgba(79, 70, 229, 0.12);
        border-color: transparent;
      }
    }
  }

  :deep(.el-button--primary),
  :deep(.el-button--warning) {
    margin-top: 10px;
    border-radius: 12px;
    padding: 11px 26px;
    font-weight: 700;
    font-size: 14px;
    letter-spacing: 0.5px;
    transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px currentColor * 0.3;
    }
    &:active { transform: translateY(0) scale(0.97); }
  }
}
</style>
