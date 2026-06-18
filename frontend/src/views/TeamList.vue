<template>
  <div class="team-page">
    <!-- 团队专属 Hero 区域 -->
    <div class="team-hero">
      <Aurora
        color1="#10b981"
        color2="#059669"
        color3="#a7f3d0"
        :opacity="0.55"
        speed="normal"
      />
      <div class="team-hero__content">
        <div class="team-hero__text">
          <h1 class="team-hero__title">
            <ShinyText
              text="我的团队"
              color="#ffffff"
              shine-color="#a7f3d0"
              :speed="3"
            />
          </h1>
          <p class="team-hero__desc">管理团队成员，协作推进项目</p>
        </div>
        <RippleButton variant="primary" @click="showCreateDialog = true" size="large">
          <el-icon><Plus /></el-icon> 创建团队
        </RippleButton>
      </div>
    </div>

    <!-- 团队列表 -->
    <div v-if="teams.length" class="team-grid">
      <div
        v-for="team in teams"
        :key="team.id"
        class="team-card"
        @click="goToTeam(team.id)"
      >
        <div class="team-card__accent" />
        <div class="team-card__body">
          <div class="team-card__top">
            <div class="team-card__avatar-ring" :style="{ borderColor: avatarGradient(team.name, 'team') }">
              <div class="team-card__avatar" :style="{ background: avatarGradient(team.name, 'team') }">
                {{ team.name.charAt(0).toUpperCase() }}
              </div>
            </div>
            <div class="team-card__info">
              <h3 class="team-card__name">{{ team.name }}</h3>
              <p class="team-card__desc">{{ team.description || '暂无描述' }}</p>
            </div>
          </div>
          <div class="team-card__footer">
            <div class="team-card__meta">
              <el-icon><User /></el-icon>
              <span>{{ team.memberCount }} 名成员</span>
              <span class="team-card__meta-dot">·</span>
              <span>{{ team.ownerName }}</span>
            </div>
            <div class="team-card__actions">
              <el-button
                type="danger"
                size="small"
                text
                :icon="Delete"
                @click.stop="handleDelete(team)"
              >删除</el-button>
              <el-icon class="team-card__arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="team-empty">
      <div class="team-empty__icon">
        <el-icon :size="48" color="#10b981"><UserFilled /></el-icon>
      </div>
      <h3 class="team-empty__title">还没有团队</h3>
      <p class="team-empty__desc">创建你的第一个团队，开始邀请成员协作</p>
      <el-button type="primary" @click="showCreateDialog = true" round>
        <el-icon><Plus /></el-icon> 创建第一个团队
      </el-button>
    </div>

    <!-- 创建团队 Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建团队" width="450px" :close-on-click-modal="false">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="团队名称" required>
          <el-input v-model="createForm.name" placeholder="如：前端开发组" />
        </el-form-item>
        <el-form-item label="团队描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="简要描述团队的定位和目标" />
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
import { ref, reactive, onMounted } from "vue"
import { useRouter } from "vue-router"
import { createTeam, getMyTeams, deleteTeam } from "@/api/team"
import { ElMessage, ElMessageBox } from "element-plus"
import { Plus, User, ArrowRight, UserFilled, Delete } from "@element-plus/icons-vue"
import { avatarGradient } from "@/utils/color"
import type { TeamVO } from "@/types"
import ShinyText from "@/components/common/ShinyText.vue"
import Aurora from "@/components/common/Aurora.vue"
import RippleButton from "@/components/common/RippleButton.vue"

const router = useRouter()
const teams = ref<TeamVO[]>([])
const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = reactive({ name: "", description: "" })

async function fetchTeams() {
  const res = await getMyTeams()
  teams.value = res.data || []
}

async function handleCreate() {
  if (!createForm.name) {
    ElMessage.warning("请输入团队名称")
    return
  }
  
  creating.value = true
  try {
    await createTeam(createForm)
    ElMessage.success("团队创建成功")
    showCreateDialog.value = false
    createForm.name = ""
    createForm.description = ""
    await fetchTeams()
  } finally {
    creating.value = false
  }
}

function goToTeam(teamId: number) {
  router.push(`/teams/${teamId}`)
}

async function handleDelete(team: TeamVO) {
  try {
    await ElMessageBox.confirm(`确定删除团队「${team.name}」？此操作不可恢复。`, '确认删除', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteTeam(team.id)
    ElMessage.success('团队已删除')
    await fetchTeams()
  } catch (e: any) {
    if (e !== 'cancel') throw e
  }
}

onMounted(fetchTeams)
</script>

<style scoped lang="scss">
.team-page {
  max-width: 960px;
  margin: 0 auto;
}

// ========== 团队专属 Hero ==========
.team-hero {
  position: relative;
  margin-bottom: 32px;
  padding: 40px 32px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(135deg, #064e3b 0%, #065f46 40%, #047857 100%);

  &__content {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: 28px;
    font-weight: 800;
    color: #fff;
    margin: 0 0 6px;
    letter-spacing: -0.5px;
  }

  &__desc {
    font-size: 14px;
    color: rgba(255,255,255,0.75);
    margin: 0;
  }

  &__btn {
    flex-shrink: 0;
    background: #fff !important;
    color: #047857 !important;
    border: none !important;
    font-weight: 700;
    padding: 10px 24px;

    &:hover {
      background: #ecfdf5 !important;
    }
  }
}

// ========== 团队卡片网格 ==========
.team-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.team-card {
  display: flex;
  align-items: stretch;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.25s ease;
  overflow: hidden;

  &:hover {
    border-color: rgba(16,185,129,0.4);
    box-shadow: 0 4px 20px rgba(16,185,129,0.1);
    transform: translateY(-1px);

    .team-card__accent {
      opacity: 1;
    }

    .team-card__arrow {
      color: #10b981;
      transform: translateX(3px);
    }
  }

  &__accent {
    width: 4px;
    background: linear-gradient(180deg, #10b981, #059669);
    opacity: 0.6;
    transition: opacity 0.25s;
    flex-shrink: 0;
  }

  &__body {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 18px 20px;
    gap: 16px;
    flex: 1;
    min-width: 0;
  }

  &__top {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
    flex: 1;
  }

  &__avatar-ring {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    border: 2px solid;
    padding: 2px;
    flex-shrink: 0;
  }

  &__avatar {
    width: 100%;
    height: 100%;
    border-radius: 11px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 800;
    font-size: 18px;
  }

  &__info {
    min-width: 0;
  }

  &__name {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0 0 3px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__desc {
    font-size: 12px;
    color: var(--text-secondary);
    margin: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: var(--text-placeholder);
  }

  &__meta-dot {
    color: var(--border-color);
  }

  &__arrow {
    color: var(--text-placeholder);
    transition: all 0.25s;
    font-size: 16px;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

// ========== 空状态 ==========
.team-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  text-align: center;

  &__icon {
    width: 88px;
    height: 88px;
    border-radius: 24px;
    background: rgba(16,185,129,0.08);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 20px;
  }

  &__title {
    font-size: 18px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0 0 8px;
  }

  &__desc {
    font-size: 13px;
    color: var(--text-secondary);
    margin: 0 0 24px;
  }
}
</style>
