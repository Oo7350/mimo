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
          <!-- 用户等级铭牌 (L1-L4) -->
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

  // 获取用户等级
  try {
    const levelRes = await getMyLevel()
    userLevel.value = levelRes.data
  } catch { /* ignore */ }
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

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    &:hover .profile-hero__avatar-overlay {
      opacity: 1;
    }
  }

  &__avatar-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    opacity: 0;
    transition: opacity var(--transition-fast);
  }

  &__actions {
    margin-left: auto;
    flex-shrink: 0;
  }
}

.profile-theme-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--transition-fast);

  &:hover { background: rgba(255, 255, 255, 0.25); }
}

.settings-card {
  margin-bottom: 20px;
}
</style>
