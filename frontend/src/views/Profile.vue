<template>
  <div class="profile-page">
    <!-- 左侧：个人信息 + 仪表盘 -->
    <aside class="profile-sidebar">
      <!-- Hero 卡片 -->
      <div class="sidebar-hero">
        <div
          class="sidebar-hero__avatar"
          :style="avatarStyle"
          @click="triggerAvatarUpload"
        >
          <img v-if="avatarPreview" :src="avatarPreview" alt="avatar" />
          <template v-else>{{ (profile?.username || '?').charAt(0).toUpperCase() }}</template>
          <div class="sidebar-hero__avatar-overlay">
            <el-icon><Camera /></el-icon>
            更换
          </div>
        </div>
        <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="handleAvatarChange" />

        <h2 class="sidebar-hero__name">{{ profile?.username || '加载中...' }}</h2>
        <p class="sidebar-hero__email">{{ profile?.email }}</p>

        <div class="sidebar-hero__badges">
          <el-tag
            size="small"
            :type="profile?.role === 'ROLE_ADMIN' ? 'danger' : 'info'"
            effect="plain"
            round
          >
            {{ profile?.role === 'ROLE_ADMIN' ? '管理员' : '成员' }}
          </el-tag>
          <UserBadge v-if="userLevel" :level="userLevel" size="small" />
        </div>

        <div class="sidebar-hero__meta">
          <span>加入于 {{ formatDate(profile?.createdAt) }}</span>
        </div>
      </div>

      <!-- 迷你仪表盘 -->
      <div class="sidebar-dashboard">
        <div class="sidebar-dashboard__title">个人概览</div>
        <div class="sidebar-dashboard__grid">
          <div class="dash-stat">
            <div class="dash-stat__icon" style="background: rgba(99,102,241,0.12); color: #6366f1">
              <el-icon><FolderOpened /></el-icon>
            </div>
            <div class="dash-stat__info">
              <span class="dash-stat__value">{{ stats.projectCount }}</span>
              <span class="dash-stat__label">参与项目</span>
            </div>
          </div>
          <div class="dash-stat">
            <div class="dash-stat__icon" style="background: rgba(16,185,129,0.12); color: #10b981">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="dash-stat__info">
              <span class="dash-stat__value">{{ stats.completedCount }}</span>
              <span class="dash-stat__label">已完成</span>
            </div>
          </div>
          <div class="dash-stat">
            <div class="dash-stat__icon" style="background: rgba(245,158,11,0.12); color: #f59e0b">
              <el-icon><Loading /></el-icon>
            </div>
            <div class="dash-stat__info">
              <span class="dash-stat__value">{{ stats.inProgressCount }}</span>
              <span class="dash-stat__label">进行中</span>
            </div>
          </div>
          <div class="dash-stat">
            <div class="dash-stat__icon" style="background: rgba(239,68,68,0.12); color: #ef4444">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="dash-stat__info">
              <span class="dash-stat__value">{{ stats.bugCount }}</span>
              <span class="dash-stat__label">缺陷数</span>
            </div>
          </div>
          <div class="dash-stat dash-stat--wide">
            <div class="dash-stat__icon" style="background: rgba(139,92,246,0.12); color: #8b5cf6">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="dash-stat__info">
              <span class="dash-stat__value">{{ stats.sprintProgress }}%</span>
              <span class="dash-stat__label">Sprint 完成率</span>
            </div>
            <div class="dash-stat__bar">
              <div class="dash-stat__bar-fill" :style="{ width: stats.sprintProgress + '%' }"></div>
            </div>
          </div>
          <div class="dash-stat dash-stat--wide">
            <div class="dash-stat__icon" style="background: rgba(6,182,212,0.12); color: #06b6d4">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="dash-stat__info">
              <span class="dash-stat__value">{{ stats.thisWeekActivity }}</span>
              <span class="dash-stat__label">本周活跃</span>
            </div>
            <div class="dash-stat__sparkline">
              <svg viewBox="0 0 40 14" preserveAspectRatio="none"><polyline points="0,12 7,10 14,6 21,8 28,3 35,5 40,2" fill="none" stroke="#06b6d4" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </div>
          </div>
        </div>
      </div>

      <!-- 主题切换 -->
      <button class="sidebar-theme-btn" @click="appStore.toggleDarkMode()">
        <el-icon><component :is="appStore.darkMode ? Sunny : Moon" /></el-icon>
        {{ appStore.darkMode ? '浅色模式' : '深色模式' }}
      </button>
    </aside>

    <!-- 右侧：Tab 内容区 -->
    <main class="profile-main">
      <!-- Tab 导航 -->
      <div class="profile-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="profile-tabs__item"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          {{ tab.label }}
        </button>
        <div class="profile-tabs__indicator" :style="indicatorStyle"></div>
      </div>

      <!-- Tab: 基本信息 -->
      <div v-show="activeTab === 'basic'" class="tab-panel">
        <div class="settings-card">
          <div class="settings-card__header">
            <div class="settings-card__header-icon" style="background: rgba(99,102,241,0.12); color: var(--color-primary)">
              <el-icon><User /></el-icon>
            </div>
            基本信息
          </div>
          <div class="settings-card__body">
            <el-form :model="profileForm" label-position="top" class="profile-form">
              <div class="form-row">
                <el-form-item label="用户名">
                  <el-input v-model="profileForm.username" placeholder="用户名">
                    <template #prefix><el-icon><User /></el-icon></template>
                  </el-input>
                </el-form-item>
                <el-form-item label="邮箱地址">
                  <el-input v-model="profileForm.email" placeholder="邮箱">
                    <template #prefix><el-icon><Message /></el-icon></template>
                  </el-input>
                </el-form-item>
              </div>
              <div class="form-row form-row--single">
                <el-form-item label="注册时间">
                  <el-input :model-value="formatDate(profile?.createdAt)" disabled>
                    <template #prefix><el-icon><Calendar /></el-icon></template>
                  </el-input>
                </el-form-item>
                <el-form-item label="角色">
                  <el-input :model-value="profile?.role === 'ROLE_ADMIN' ? '系统管理员' : '普通成员'" disabled>
                    <template #prefix><el-icon><UserFilled /></el-icon></template>
                  </el-input>
                </el-form-item>
              </div>
              <el-button type="primary" :loading="profileLoading" round @click="handleUpdateProfile">
                保存修改
              </el-button>
            </el-form>
          </div>
        </div>
      </div>

      <!-- Tab: 安全设置 -->
      <div v-show="activeTab === 'security'" class="tab-panel">
        <div class="settings-card">
          <div class="settings-card__header">
            <div class="settings-card__header-icon" style="background: rgba(245,158,11,0.12); color: var(--color-warning)">
              <el-icon><Lock /></el-icon>
            </div>
            安全设置
          </div>
          <div class="settings-card__body">
            <div class="security-tip">
              <el-icon><InfoFilled /></el-icon>
              定期更换密码可有效保障账号安全，建议使用 12 位以上混合字符密码。
            </div>
            <el-form :model="passwordForm" label-position="top" class="profile-form">
              <el-form-item label="当前密码" required>
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码">
                  <template #prefix><el-icon><Key /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item label="新密码" required>
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位字符" @input="calcStrength">
                  <template #prefix><el-icon><Key /></el-icon></template>
                </el-input>
                <div class="pwd-strength">
                  <span class="pwd-strength__label">{{ strengthLabel }}</span>
                  <div class="pwd-strength__bar">
                    <div
                      v-for="i in 4"
                      :key="i"
                      class="pwd-strength__seg"
                      :class="[strengthClass, { 'is-filled': i <= strengthLevel }]"
                    ></div>
                  </div>
                </div>
              </el-form-item>
              <el-form-item label="确认新密码" required>
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码">
                  <template #prefix><el-icon><Key /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-button type="warning" :loading="passwordLoading" round @click="handleChangePassword">
                修改密码
              </el-button>
            </el-form>
          </div>
        </div>
      </div>

      <!-- Tab: 我的动态 -->
      <div v-show="activeTab === 'activity'" class="tab-panel">
        <div class="settings-card settings-card--full">
          <div class="settings-card__header">
            <div class="settings-card__header-icon" style="background: rgba(16,185,129,0.12); color: #10b981">
              <el-icon><Clock /></el-icon>
            </div>
            最近动态
          </div>
          <div class="settings-card__body activity-list">
            <div v-if="activities.length" class="activity-timeline">
              <div v-for="(item, i) in activities" :key="i" class="activity-item">
                <div class="activity-item__dot" :style="{ background: item.color }"></div>
                <div class="activity-item__body">
                  <p class="activity-item__text" v-html="item.text"></p>
                  <span class="activity-item__time">{{ item.time }}</span>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">
              <el-icon :size="48"><Box /></el-icon>
              <p>暂无动态记录</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab: 通知偏好 -->
      <div v-show="activeTab === 'notifications'" class="tab-panel">
        <div class="settings-card">
          <div class="settings-card__header">
            <div class="settings-card__header-icon" style="background: rgba(236,72,153,0.12); color: #ec4899">
              <el-icon><Bell /></el-icon>
            </div>
            通知偏好
          </div>
          <div class="settings-card__body">
            <div class="pref-section">
              <h4 class="pref-section__title">邮件通知</h4>
              <div class="pref-item">
                <div class="pref-item__text">
                  <strong>任务分配给我时</strong>
                  <span>当有新任务指派给你时发送邮件提醒</span>
                </div>
                <el-switch v-model="prefs.emailOnAssign" />
              </div>
              <div class="pref-item">
                <div class="pref-item__text">
                  <strong>任务状态变更时</strong>
                  <span>关注任务的状态发生变化时通知你</span>
                </div>
                <el-switch v-model="prefs.emailOnStatusChange" />
              </div>
              <div class="pref-item">
                <div class="pref-item__text">
                  <strong>缺陷指派给我时</strong>
                  <span>收到新缺陷分配时立即通知</span>
                </div>
                <el-switch v-model="prefs.emailOnBugAssign" />
              </div>
              <div class="pref-item">
                <div class="pref-item__text">
                  <strong>审批需要我处理时</strong>
                  <span>有待审批事项需要你操作时提醒</span>
                </div>
                <el-switch v-model="prefs.emailOnApproval" />
              </div>
            </div>
            <div class="pref-section">
              <h4 class="pref-section__title">浏览器通知</h4>
              <div class="pref-item">
                <div class="pref-item__text">
                  <strong>允许桌面通知</strong>
                  <span>在浏览器中显示实时推送通知</span>
                </div>
                <el-switch v-model="prefs.browserPush" />
              </div>
              <div class="pref-item">
                <div class="pref-item__text">
                  <strong>提示音效</strong>
                  <span>收到通知时播放提示音</span>
                </div>
                <el-switch v-model="prefs.soundEffect" />
              </div>
            </div>
            <el-button type="primary" round @click="savePrefs">保存偏好</el-button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue"
import { ElMessage } from "element-plus"
import {
  Moon, Sunny, Camera, User, Lock, Message, Calendar,
  UserFilled, Key, InfoFilled, Clock, Bell, Box,
  FolderOpened, CircleCheck, Loading, Warning,
  TrendCharts, Timer
} from "@element-plus/icons-vue"
import { getUserProfile, updateUserProfile, changePassword } from "@/api/user"
import { getMyLevel } from "@/api/user-level"
import request from "@/api/request"
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
const avatarPreview = ref("")
const activeTab = ref("basic")

const avatarStyle = computed(() => {
  if (avatarPreview.value) return {}
  return { background: avatarGradient(profile?.value?.username || 'U', 'user') }
})

// Tabs
const tabs = [
  { key: "basic", label: "基本信息", icon: "User" },
  { key: "security", label: "安全设置", icon: "Lock" },
  { key: "activity", label: "我的动态", icon: "Clock" },
  { key: "notifications", label: "通知偏好", icon: "Bell" },
]

// Indicator position
const indicatorStyle = computed(() => {
  const idx = tabs.findIndex(t => t.key === activeTab.value)
  return { transform: `translateX(${idx * 100}%)` }
})

// Stats (mock data - replace with real API calls later)
const stats = reactive({
  projectCount: 0,
  completedCount: 0,
  inProgressCount: 0,
  bugCount: 0,
  sprintProgress: 0,
  thisWeekActivity: 0,
})

// Activities (mock data)
const activities = ref<any[]>([])

// Notification prefs
const prefs = reactive({
  emailOnAssign: true,
  emailOnStatusChange: true,
  emailOnBugAssign: true,
  emailOnApproval: true,
  browserPush: false,
  soundEffect: true,
})

// Forms
const profileForm = reactive({ username: "", email: "" })
const passwordForm = reactive({ oldPassword: "", newPassword: "", confirmPassword: "" })

// Password strength
const strengthLevel = ref(0)
const strengthLabel = computed(() => ["极弱", "弱", "一般", "强", "很强"][strengthLevel.value])
const strengthClass = computed(() => ["is-weak", "is-weak", "is-fair", "is-good", "is-strong"][strengthLevel.value])

function calcStrength(val: string) {
  let s = 0
  if (val.length >= 6) s++
  if (val.length >= 10) s++
  if (/[A-Z]/.test(val)) s++
  if (/[0-9]/.test(val)) s++
  if (/[^A-Za-z0-9]/.test(val)) s++
  strengthLevel.value = Math.min(4, Math.floor(s / 1.25))
}

function triggerAvatarUpload() { avatarInput.value?.click() }

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning("图片大小不能超过 2MB"); return }
  const reader = new FileReader()
  reader.onload = async () => {
    const base64 = reader.result as string
    avatarPreview.value = base64
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
  try {
    const res = await getUserProfile()
    profile.value = res.data
    profileForm.username = profile.value?.username || ""
    profileForm.email = profile.value?.email || ""
    avatarPreview.value = profile.value?.avatar || ""
  } catch { /* */ }

  // Fetch level badge
  try { const lr = await getMyLevel(); userLevel.value = lr.data } catch { /* */ }

  // 从 /dashboard 获取真实统计数据
  try {
    const res = await request.get("/dashboard")
    const d = res.data
    Object.assign(stats, {
      projectCount: d.myProjects?.length || 0,
      completedCount: d.doneIssues || 0,
      inProgressCount: d.inProgressIssues || 0,
      bugCount: d.bugCount || 0,
      sprintProgress: d.activeSprints?.length
        ? Math.round((d.activeSprints.reduce((s: number, a: any) => s + (a.completedIssues || 0), 0) /
            d.activeSprints.reduce((s: number, a: any) => s + (a.totalIssues || 1), 0)) * 100)
        : 0,
      thisWeekActivity: d.thisWeekActivity || 0,
    })
    // 真实动态
    activities.value = (d.recentActivities || []).slice(0, 5).map((a: any) => {
      const colorMap: Record<string, string> = {
        CREATE: '#10b981', UPDATE: '#f59e0b', MOVE: '#6366f1',
        DELETE: '#ef4444', COMMENT: '#06b6d4', ASSIGN: '#8b5cf6',
      }
      return {
        text: `${a.action === 'CREATE' ? '创建了' : a.action === 'UPDATE' ? '更新了' : a.action === 'MOVE' ? '移动了' : a.action === 'DELETE' ? '删除了' : a.action === 'COMMENT' ? '评论了' : a.action === 'ASSIGN' ? '指派了' : a.action} <strong>${a.detail || ''}</strong>`,
        time: formatRelativeTime(a.createdAt),
        color: colorMap[a.action] || '#6366f1',
      }
    })
  } catch { /* 保持默认值 0 */ }
}

async function handleUpdateProfile() {
  profileLoading.value = true
  try {
    await updateUserProfile({ username: profileForm.username, email: profileForm.email })
    ElMessage.success("资料更新成功")
    await fetchProfile()
  } finally { profileLoading.value = false }
}

async function handleChangePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) { ElMessage.warning("请填写完整密码信息"); return }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) { ElMessage.warning("两次输入的新密码不一致"); return }
  if (passwordForm.newPassword.length < 6) { ElMessage.warning("新密码至少6位"); return }
  passwordLoading.value = true
  try {
    await changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success("密码修改成功")
    passwordForm.oldPassword = ""
    passwordForm.newPassword = ""
    passwordForm.confirmPassword = ""
    strengthLevel.value = 0
  } finally { passwordLoading.value = false }
}

function savePrefs() { ElMessage.success("偏好已保存") }

function formatDate(date?: string) {
  if (!date) return "-"
  return new Date(date).toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric" })
}

function formatRelativeTime(dateStr?: string): string {
  if (!dateStr) return ""
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return "刚刚"
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return formatDate(dateStr)
}

onMounted(fetchProfile)
</script>

<style scoped lang="scss">
.profile-page {
  display: flex;
  gap: 28px;
  max-width: 1160px;
  margin: 0 auto;
  padding-top: 8px;
  align-items: flex-start;
}

/* ===== 左侧边栏 ===== */
.profile-sidebar {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 80px;
}

/* Hero 卡片 */
.sidebar-hero {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 26px 22px;
  text-align: center;

  &__avatar {
    width: 88px; height: 88px;
    border-radius: 22px;
    margin: 0 auto 16px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    font-size: 32px;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: opacity 0.15s;
    background: #f7b955;
    color: #fff;

    &:hover { opacity: 0.85; }

    img { width: 100%; height: 100%; object-fit: cover; }
  }

  &__avatar-overlay {
    position: absolute; inset: 0;
    background: rgba(0,0,0,0.55);
    backdrop-filter: blur(8px);
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    gap: 4px;
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    opacity: 0;
    transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  }

  &__avatar:hover &__avatar-overlay { opacity: 1; }

  &__name {
    font-size: 20px;
    font-weight: 800;
    letter-spacing: -0.4px;
    margin: 0 0 4px;
  }

  &__email {
    font-size: 13px;
    color: var(--text-secondary);
    margin: 0 0 12px;
  }

  &__badges {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-bottom: 14px;
  }

  &__meta {
    font-size: 11.5px;
    color: var(--text-muted);
    padding-top: 12px;
    border-top: 1px solid var(--border-color-light);
  }
/* 迷你仪表盘 */
.sidebar-dashboard {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 18px 16px;
}
  &__title {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-secondary);
    margin-bottom: 14px;
    letter-spacing: 0.3px;
    text-transform: uppercase;
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
}

.dash-stat {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--bg-subtle);
  transition: border-color 0.15s;
  border: 1px solid transparent;

  &:hover { border-color: var(--border-color); }

  &--wide {
    grid-column: span 2;
    flex-wrap: wrap;
  }

  &__icon {
    width: 34px; height: 34px;
    border-radius: 9px;
    display: flex; align-items: center; justify-content: center;
    font-size: 16px;
    flex-shrink: 0;
  }

  &__info {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  &__value {
    font-size: 19px;
    font-weight: 800;
    line-height: 1.2;
    letter-spacing: -0.5px;
  }

  &__label {
    font-size: 11px;
    color: var(--text-muted);
    font-weight: 550;
  }

  &__bar {
    width: 100%;
    height: 4px;
    background: var(--border-color);
    border-radius: 2px;
    overflow: hidden;
    margin-top: 4px;

    &-fill {
      height: 100%;
      background: var(--text-muted);
      border-radius: 2px;
      transition: width 0.4s ease;
    }
  }

  &__sparkline {
    width: 50px; height: 14px;
    margin-left: auto;
  }
}

/* 主题按钮 */
.sidebar-theme-btn {
  display: flex; align-items: center; justify-content: center;
  gap: 7px;
  padding: 11px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: opacity 0.15s;

  &:hover { opacity: 0.8; }
  &:active { transform: scale(0.97); }
}

/* ===== 右侧主区 ===== */
.profile-main {
  flex: 1;
  min-width: 0;
}

/* Tab 导航 */
.profile-tabs {
  display: flex;
  position: relative;
  background: var(--bg-subtle);
  border-radius: 14px;
  padding: 4px;
  margin-bottom: 22px;
  border: 1px solid var(--border-color-light);

  &__item {
    flex: 1;
    display: flex; align-items: center; justify-content: center;
    gap: 6px;
    padding: 11px 0;
    border-radius: 11px;
    font-size: 13.5px;
    font-weight: 650;
    color: var(--text-secondary);
    cursor: pointer;
    border: none;
    background: transparent;
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
    position: relative;
    z-index: 1;

    .el-icon { font-size: 15px; }

    &.is-active {
      color: var(--text-primary);
      background: var(--bg-card);
      font-weight: 700;
    }

    &:hover:not(.is-active) {
      color: var(--text-primary);
    }
  }

  &__indicator {
    position: absolute;
    top: 4px; left: 4px;
    width: calc(25% - 4px);
    height: calc(100% - 8px);
    background: var(--bg-card);
    border-radius: 11px;
    transition: transform 0.3s ease;
    z-index: 0;
  }
}

.tab-panel {
  animation: fadeInUp 0.35s ease both;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 设置卡片 */
.settings-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-lg);
  overflow: hidden;
  transition: border-color 0.15s;

  &:hover { border-color: rgba(0,0,0,0.12); }

  &--full { min-height: 400px; }

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

  &__body { padding: 24px; }
}

/* 表单样式 */
.profile-form {
  :deep(.el-form-item) { margin-bottom: 20px; }
  :deep(.el-form-item__label) {
    font-weight: 650;
    font-size: 13px;
    color: var(--text-primary);
    letter-spacing: -0.1px;
    padding-bottom: 8px;
  }
  :deep(.el-input) {
    .el-input__wrapper {
      border-radius: 10px;
      box-shadow: 0 0 0 1px var(--border-color);
      transition: border-color 0.15s;
      &:hover { border-color: var(--text-muted); }
      &.is-focus { border-color: var(--text-primary); box-shadow: none; }
    }
  }
  :deep(.el-button--primary),
  :deep(.el-button--warning) {
    border-radius: 12px;
    padding: 11px 28px;
    font-weight: 700;
    font-size: 14px;
    letter-spacing: 0.5px;
    transition: opacity 0.15s;

    &:hover { opacity: 0.85; }
    &:active { transform: scale(0.97); }
  }
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;

  &--single {
    :deep(.el-form-item:first-child) { grid-column: span 1; }
  }
}

/* 密码强度指示器 */
.pwd-strength {
  display: flex; align-items: center; gap: 10px;
  margin-top: 8px;

  &__label {
    font-size: 12px;
    font-weight: 650;
    min-width: 38px;
  }

  &__bar {
    display: flex; gap: 4px; flex: 1;
  }

  &__seg {
    flex: 1; height: 4px;
    border-radius: 2px;
    background: var(--border-color);
    transition: all 0.35s ease;

    &.is-filled {
      &.is-weak { background: #ef4444; height: 5px; }
      &.is-fair { background: #f59e0b; height: 6px; }
      &.is-good { background: #10b981; height: 6px; }
      &.is-strong { background: #10b981; height: 7px; }
    }
  }
}

/* 安全提示 */
.security-tip {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 12px 16px;
  border-radius: 10px;
  background: rgba(245,158,11,0.08);
  border: 1px solid rgba(245,158,11,0.15);
  color: #92400e;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 20px;

  .el-icon { margin-top: 1px; font-size: 16px; flex-shrink: 0; }
}

/* 动态时间线 */
.activity-list { min-height: 280px; }

.activity-timeline {
  position: relative;
  padding-left: 24px;

  &::before {
    content: '';
    position: absolute;
    left: 7px; top: 8px; bottom: 8px;
    width: 2px;
    background: var(--border-color);
    border-radius: 1px;
  }
}

.activity-item {
  position: relative;
  padding-bottom: 22px;

  &:last-child { padding-bottom: 0; }

  &__dot {
    position: absolute;
    left: -24px; top: 4px;
    width: 14px; height: 14px;
    border-radius: 50%;
    border: 2.5px solid var(--bg-card);
    z-index: 1;
  }

  &__body {
    padding-left: 4px;
  }

  &__text {
    margin: 0 0 4px;
    font-size: 14px;
    line-height: 1.5;
    color: var(--text-primary);

    strong { color: var(--color-primary); font-weight: 700; }
  }

  &__time {
    font-size: 12px;
    color: var(--text-muted);
  }

  &:hover &__text { color: var(--text-primary); }
}

.empty-state {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12px;
  height: 260px;
  color: var(--text-muted);

  p { margin: 0; font-size: 14px; font-weight: 600; }
}

/* 通知偏好 */
.pref-section {
  margin-bottom: 24px;

  &:last-of-type { margin-bottom: 0; }

  &__title {
    font-size: 14px;
    font-weight: 750;
    margin: 0 0 14px;
    color: var(--text-primary);
    letter-spacing: -0.2px;
  }
}

.pref-item {
  display: flex; align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color-light);

  &:last-child { border-bottom: none; }

  &__text {
    display: flex; flex-direction: column; gap: 3px;

    strong { font-size: 14px; font-weight: 650; }
    span { font-size: 12px; color: var(--text-muted); }
  }
}
</style>
