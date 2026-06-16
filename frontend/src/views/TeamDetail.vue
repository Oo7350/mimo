<template>
  <div class="team-detail-page">
      <!-- 团队详情 Hero -->
      <div class="td-hero">
        <div class="td-hero__bg" :style="{ background: avatarGradient(team?.name || 'T', 'team') }">
          <div class="td-hero__overlay" />
        </div>
        <div class="td-hero__body">
          <el-button class="td-hero__back" @click="$router.push('/teams')" text round>
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
          <div class="td-hero__avatar" :style="{ background: avatarGradient(team?.name || 'T', 'team') }">
            {{ (team?.name || '?').charAt(0).toUpperCase() }}
          </div>
        <div class="td-hero__info">
          <h1 class="td-hero__name">{{ team?.name || '加载中...' }}</h1>
          <p class="td-hero__desc">{{ team?.description || '暂无描述' }}</p>
        </div>
        <div class="td-hero__actions">
          <el-tag round effect="dark" type="success">{{ members.length }} 名成员</el-tag>
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="td-hero__badge">
            <el-button type="primary" round @click="scrollToChat">
              <el-icon><ChatDotRound /></el-icon> 群聊
            </el-button>
          </el-badge>
          <el-badge :value="pendingApprovals.length" :hidden="pendingApprovals.length === 0" :max="99" class="td-hero__badge">
            <el-button 
              v-if="isTeamAdmin" 
              type="warning" 
              round 
              @click="showApprovalDialog = true"
            >
              <el-icon><Bell /></el-icon> 审批
            </el-button>
          </el-badge>
          <el-button type="success" plain round @click="showInviteDialog = true">
            <el-icon><Plus /></el-icon> 邀请成员
          </el-button>
          <el-button 
            v-if="!isTeamOwner" 
            type="warning" 
            plain 
            round 
            @click="handleLeaveTeam"
          >
            退出团队
          </el-button>
        </div>
      </div>
    </div>

    <!-- 主内容区：左栏信息 + 右栏聊天 -->
    <div class="td-grid">
      <!-- 左侧：成员 & 项目 -->
      <div class="td-left">
        <!-- 成员列表 -->
        <div class="td-section">
          <div class="td-section__header">
            <div class="td-section__icon td-section__icon--member">
              <el-icon><UserFilled /></el-icon>
            </div>
            <h2>团队成员</h2>
            <span class="td-section__count">{{ members.length }}</span>
          </div>
          <div class="td-member-list">
            <div v-for="m in members" :key="m.userId" class="td-member">
              <div class="td-member__avatar" :style="{ background: avatarGradient(m.username, 'user') }">
                {{ m.username.charAt(0).toUpperCase() }}
              </div>
              <div class="td-member__info">
                <span class="td-member__name">{{ m.username }}</span>
                <span class="td-member__email">{{ m.email }}</span>
              </div>
              <el-tag
                :type="team?.ownerId === m.userId ? 'warning' : (m.role === 'ROLE_ADMIN' ? 'danger' : '')"
                size="small"
                round
                effect="plain"
              >
                {{ team?.ownerId === m.userId ? '群主' : (m.role === 'ROLE_ADMIN' ? '管理员' : '成员') }}
              </el-tag>
              <!-- 群主专属操作 -->
              <template v-if="isTeamOwner && m.userId !== userStore.userInfo?.id">
                <el-button
                  v-if="team?.ownerId !== m.userId"
                  type="primary"
                  size="small"
                  text
                  @click="handleTransferOwner(m)"
                >转让群主</el-button>
                <el-button
                  v-if="m.role === 'ROLE_ADMIN'"
                  type="warning"
                  size="small"
                  text
                  @click="handleUnsetAdmin(m)"
                >取消管理员</el-button>
                <el-button
                  v-else-if="team?.ownerId !== m.userId"
                  type="success"
                  size="small"
                  text
                  @click="handleSetAdmin(m)"
                >设为管理员</el-button>
              </template>
              <!-- 管理员可移除成员（不能移除自己和管理员） -->
              <el-button
                v-if="isTeamAdmin && m.role !== 'ROLE_ADMIN' && team?.ownerId !== m.userId && m.userId !== userStore.userInfo?.id"
                type="danger"
                size="small"
                text
                @click="handleRemove(m.userId)"
              >移除</el-button>
            </div>
            <div v-if="members.length === 0" class="td-member-empty">暂无成员，邀请你的队友加入吧</div>
          </div>
        </div>

        <!-- 项目列表 -->
        <div class="td-section">
          <div class="td-section__header">
            <div class="td-section__icon td-section__icon--project">
              <el-icon><Folder /></el-icon>
            </div>
            <h2>关联项目</h2>
            <el-button size="small" type="success" round @click="showCreateProject = true">
              <el-icon><Plus /></el-icon> 创建项目
            </el-button>
          </div>
          <div v-if="projects.length" class="td-project-grid">
            <div
              v-for="p in projects"
              :key="p.id"
              class="td-project-card"
            >
              <div class="td-project-card__main" @click="$router.push(`/projects/${p.id}/board`)">
                <div class="td-project-card__key">{{ p.key }}</div>
                <div class="td-project-card__name">{{ p.name }}</div>
                <el-icon class="td-project-card__arrow"><ArrowRight /></el-icon>
              </div>
              <el-button
                class="td-project-card__del"
                type="danger"
                size="small"
                text
                :icon="Delete"
                @click.stop="handleDeleteProject(p)"
              >删除</el-button>
            </div>
          </div>
          <div v-else class="td-project-empty">
            <p>团队还没有项目</p>
            <el-button size="small" type="success" round @click="showCreateProject = true">创建第一个项目</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：群聊面板 -->
      <div ref="chatPanelRef" class="td-chat-panel">
        <div class="td-chat-header">
          <div class="td-chat-header__icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div>
            <h3>团队群聊</h3>
            <span class="td-chat-header__online">{{ onlineCount }} 位成员</span>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="chatMsgListRef" class="td-chat-messages" @scroll="onChatScroll">
          <div v-if="messages.length === 0 && !loadingChat" class="td-chat-empty">
            <el-icon :size="36" color="#10b981"><ChatDotRound /></el-icon>
            <p>暂无消息</p>
            <span>发送第一条消息吧~</span>
          </div>
          <template v-for="(msg, idx) in messages" :key="msg.id">
            <!-- 时间分隔 -->
            <div v-if="shouldShowDateDivider(idx)" class="td-chat-divider">
              {{ formatDate(msg.createdAt) }}
            </div>
            <!-- 消息气泡 -->
            <div class="td-msg" :class="{ 'td-msg--mine': msg.isMine, 'td-msg--recalled': msg.recalled }">
              <div
                class="td-msg__avatar"
                :style="{ background: msg.isMine ? undefined : avatarGradient(msg.senderName, 'user') }"
              >
                {{ msg.senderName.charAt(0).toUpperCase() }}
              </div>
              <div class="td-msg__body">
                <span class="td-msg__sender">{{ msg.senderName }}</span>
                <!-- 已撤回消息 -->
                <div v-if="msg.recalled" class="td-msg__bubble td-msg__bubble--recalled">
                  [消息已撤回]
                </div>
                <!-- 正常消息 + 撤回按钮 -->
                <template v-else>
                  <div class="td-msg__bubble td-msg__content-wrap">
                    <span v-html="renderMention(msg.content)"></span>
                    <el-button
                      v-if="msg.isMine && canRecall(msg.createdAt)"
                      class="td-msg__recall-btn"
                      type="info"
                      size="small"
                      text
                      @click.stop="handleRecall(msg)"
                    >撤回</el-button>
                  </div>
                </template>
                <span class="td-msg__time">{{ formatTime(msg.createdAt) }}</span>
              </div>
            </div>
          </template>
          <div v-if="loadingChat" class="td-chat-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="td-chat-input-wrap">
          <el-popover trigger="click" placement="top-start" :width="320" :show-after="0">
            <template #reference>
              <el-button class="td-chat-emoji-btn" circle size="small" text>
                <span style="font-size: 18px">😊</span>
              </el-button>
            </template>
            <div class="emoji-picker">
              <div v-for="group in emojiGroups" :key="group.label" class="emoji-group">
                <div class="emoji-group__label">{{ group.label }}</div>
                <div class="emoji-grid">
                  <span
                    v-for="e in group.items"
                    :key="e"
                    class="emoji-item"
                    @click="insertEmoji(e)"
                  >{{ e }}</span>
                </div>
              </div>
            </div>
          </el-popover>
          <div class="td-chat-input-main">
            <el-input
              ref="chatInputRef"
              v-model="chatInput"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="输入消息... Enter 发送"
              resize="none"
              @input="onChatInput"
              @keydown.enter.exact.prevent="sendChatMessage"
            />
            <!-- @提及成员下拉 -->
            <div v-if="mentionList.length > 0" class="td-mention-dropdown">
              <div
                v-for="(m, i) in mentionList"
                :key="m.userId"
                class="td-mention-item"
                :class="{ 'td-mention-item--active': i === mentionActiveIndex }"
                @click="selectMention(m)"
              >
                <div class="td-mention-item__avatar" :style="{ background: avatarGradient(m.username, 'user') }">
                  {{ m.username.charAt(0).toUpperCase() }}
                </div>
                <span>{{ m.username }}</span>
              </div>
            </div>
          </div>
          <el-button
            type="primary"
            circle
            :disabled="!chatInput.trim()"
            :loading="sendingChat"
            @click="sendChatMessage"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 邀请成员 Dialog -->
    <el-dialog v-model="showInviteDialog" title="邀请成员" width="420px">
      <el-form label-position="top">
        <el-form-item label="搜索用户">
          <el-select
            v-model="inviteUserId"
            filterable
            remote
            reserve-keyword
            placeholder="输入用户名或邮箱搜索..."
            :remote-method="searchUserOptions"
            :loading="searchingUsers"
            style="width: 100%"
          >
            <el-option
              v-for="u in userSearchResults"
              :key="u.id"
              :label="`${u.username} (${u.email})`"
              :value="u.id"
            >
              <div style="display: flex; align-items: center; gap: 8px">
                <div class="td-search-avatar" :style="{ background: avatarGradient(u.username, 'user') }">
                  {{ u.username.charAt(0).toUpperCase() }}
                </div>
                <span>{{ u.username }}</span>
                <span style="color: var(--text-secondary); font-size: 12px">{{ u.email }}</span>
              </div>
            </el-option>
          </el-select>
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
        <el-button type="primary" :disabled="!inviteUserId" @click="handleInvite">邀请</el-button>
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
            <el-option label="Scrum (敏捷迭代)" value="SCRUM">
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>Scrum (敏捷迭代)</span>
                <el-tag size="small" type="primary">Sprint + 故事点</el-tag>
              </div>
            </el-option>
            <el-option label="Kanban (看板)" value="KANBAN">
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>Kanban (看板)</span>
                <el-tag size="small" type="success">持续流动</el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="projectForm.template === 'SCRUM'" style="margin-top: 8px; font-size: 12px; color: var(--text-secondary)">
            <el-icon><InfoFilled /></el-icon> 支持Sprint迭代管理、故事点估算、燃尽图等敏捷特性
          </div>
          <div v-else style="margin-top: 8px; font-size: 12px; color: var(--text-secondary)">
            <el-icon><InfoFilled /></el-icon> 简洁的看板模式，适合持续交付和运维团队
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateProject = false">取消</el-button>
        <el-button type="primary" @click="handleCreateProject">创建</el-button>
      </template>
    </el-dialog>

    <!-- 审批请求 Dialog (仅管理员可见) -->
    <el-dialog v-model="showApprovalDialog" title="待审批请求" width="600px">
      <div v-if="pendingApprovals.length === 0" style="text-align: center; padding: 40px; color: var(--text-secondary)">
        暂无待审批请求
      </div>
      <div v-else class="approval-list">
        <div v-for="item in pendingApprovals" :key="item.id" class="approval-item">
          <div class="approval-item__header">
            <el-tag :type="getApprovalTypeColor(item.targetType)" size="small">{{ getApprovalTypeName(item.targetType) }}</el-tag>
            <span class="approval-item__title">{{ item.title }}</span>
            <span class="approval-item__time">{{ item.createdAt }}</span>
          </div>
          <div class="approval-item__body">
            <span>申请人：{{ item.requesterUsername || '未知' }}</span>
            <span v-if="item.description" class="approval-item__desc">{{ item.description }}</span>
          </div>
          <div class="approval-item__actions">
            <el-button size="small" type="success" @click="handleApprove(item)">通过</el-button>
            <el-button size="small" type="danger" @click="handleReject(item)">拒绝</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from "vue"
import { useRoute, useRouter } from "vue-router"
import { getTeamById, getMembers, inviteMember, removeMember, getTeamRole, leaveTeam, transferOwner, setMemberAdmin, unsetMemberAdmin } from "@/api/team"
import { getProjectsByTeam, createProject, deleteProject } from "@/api/project"
import { searchUsers } from "@/api/user"
import { sendChatMessage as apiSendChat, getChatHistory, recallMessage } from "@/api/chat"
import { createApproval, getPendingApprovals, approveRequest, rejectRequest } from "@/api/approval"
import { useWebSocket } from "@/composables/useWebSocket"
import { useUserStore } from "@/store/user"
import { ElMessage, ElMessageBox } from "element-plus"
import {
  Plus, ArrowLeft, UserFilled, Folder, ArrowRight,
  ChatDotRound, Promotion, Loading, Delete, Bell, InfoFilled,
} from "@element-plus/icons-vue"
import { avatarGradient } from "@/utils/color"

const route = useRoute()
const router = useRouter()
const teamId = Number(route.params.id)

// ========== 基础数据 ==========
const team = ref<any>(null)
const members = ref<any[]>([])
const projects = ref<any[]>([])
const showInviteDialog = ref(false)
const inviteUserId = ref<number | null>(null)
const inviteRole = ref("ROLE_MEMBER")
const showCreateProject = ref(false)
const projectForm = reactive({ name: "", key: "", template: "SCRUM" })
const userSearchResults = ref<any[]>([])
const searchingUsers = ref(false)

// ========== 权限控制 ==========
const myTeamRole = ref<string | null>(null)
const isTeamAdmin = computed(() => {
  // 系统管理员
  if (userStore.userInfo?.role === 'ROLE_ADMIN') return true
  // 团队管理员
  return myTeamRole.value === 'ROLE_ADMIN'
})

// 是否为群主（团队创建者）
const isTeamOwner = computed(() => {
  return team.value && team.value.ownerId === userStore.userInfo?.id
})

// ========== 审批请求 ==========
const pendingApprovals = ref<any[]>([])
const showApprovalDialog = ref(false)

// 获取待审批列表
async function fetchPendingApprovals() {
  if (!isTeamAdmin.value) return
  try {
    const res = await getPendingApprovals(teamId)
    pendingApprovals.value = res.data || []
  } catch { /* ignore */ }
}

// 创建审批请求（非管理员操作项目时调用）
async function requestApproval(targetType: string, title: string, dataJson: any) {
  try {
    await createApproval({
      teamId,
      targetType,
      title,
      description: `用户 ${userStore.username} 申请${title}`,
      dataJson: JSON.stringify(dataJson),
    })
    ElMessage.success('审批请求已提交，等待管理员审核')
  } catch { /* handled by interceptor */ }
}

// 审批通过
async function handleApprove(approval: any) {
  try {
    await approveRequest(approval.id)
    ElMessage.success(`已通过: ${approval.title}`)
    await fetchPendingApprovals()
  } catch { /* handled */ }
}

// 审批拒绝
async function handleReject(approval: any) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝审批', {
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '可选填',
    })
    await rejectRequest(approval.id, reason || '')
    ElMessage.success(`已拒绝: ${approval.title}`)
    await fetchPendingApprovals()
  } catch (e: any) {
    if (e !== 'cancel') throw e
  }
}

// 审批类型名称映射
function getApprovalTypeName(type: string): string {
  const map: Record<string, string> = {
    'PROJECT_CREATE': '创建项目',
    'PROJECT_UPDATE': '修改项目',
    'PROJECT_DELETE': '删除项目',
    'PROJECT_MEMBER_ADD': '添加成员',
  }
  return map[type] || type
}

// 审批类型颜色映射
function getApprovalTypeColor(type: string): string {
  const map: Record<string, string> = {
    'PROJECT_CREATE': 'success',
    'PROJECT_UPDATE': 'warning',
    'PROJECT_DELETE': 'danger',
    'PROJECT_MEMBER_ADD': 'primary',
  }
  return map[type] || 'info'
}

// ========== 团队管理（群主专属功能） ==========

// 退出团队
async function handleLeaveTeam() {
  try {
    await ElMessageBox.confirm('确定要退出该团队吗？退出后需要重新邀请才能加入。', '退出团队', {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await leaveTeam(teamId)
    ElMessage.success('已退出团队')
    // 跳转到团队列表
    router.push('/teams')
  } catch (e: any) {
    if (e !== 'cancel') throw e
  }
}

// 转让群主
async function handleTransferOwner(member: any) {
  try {
    const { value: confirmed } = await ElMessageBox.confirm(
      `确定将群主身份转让给「${member.username}」？转让后你将变为普通管理员。`,
      '转让群主',
      { confirmButtonText: '确定转让', cancelButtonText: '取消', type: 'warning' }
    )
    if (confirmed) {
      await transferOwner(teamId, member.userId)
      ElMessage.success('群主转让成功')
      await fetchData()
    }
  } catch (e: any) {
    if (e !== 'cancel') throw e
  }
}

// 设置管理员
async function handleSetAdmin(member: any) {
  try {
    await setMemberAdmin(teamId, member.userId)
    ElMessage.success(`已将 ${member.username} 设为管理员`)
    await fetchData()
  } catch { /* handled by interceptor */ }
}

// 取消管理员
async function handleUnsetAdmin(member: any) {
  try {
    await unsetMemberAdmin(teamId, member.userId)
    ElMessage.success(`已取消 ${member.username} 的管理员权限`)
    await fetchData()
  } catch { /* handled by interceptor */ }
}

// ========== 聊天 ==========
interface ChatMsg {
  id: number; teamId: number; senderId: number;
  senderName: string; senderAvatar: string; content: string;
  createdAt: string; isMine: boolean; recalled?: boolean;
}
const userStore = useUserStore()
// 立即获取当前用户 ID（确保 WS 消息到达时能正确判断是否为自己）
function getMyUserId(): number | null {
  // 优先从 Pinia store 获取
  const storeId = userStore.userInfo?.id
  if (storeId) return storeId
  // 其次从 localStorage 获取
  const stored = localStorage.getItem("user")
  if (stored) {
    try { return JSON.parse(stored).id } catch { /* ignore */ }
  }
  return null
}
let myUserId = getMyUserId()

const messages = ref<ChatMsg[]>([])
const chatInput = ref("")
const sendingChat = ref(false)
const loadingChat = ref(true)
const unreadCount = ref(0)
const onlineCount = computed(() => members.value.length)
const chatPanelRef = ref<HTMLElement>()
const chatMsgListRef = ref<HTMLElement>()
const chatInputRef = ref<any>()
let lastReadTime = Date.now()

// @提及
const mentionList = ref<any[]>([])
const mentionActiveIndex = ref(0)
let mentionStartPos = -1  // @ 符号在输入框中的位置

// Emoji 数据
const emojiGroups = [
  { label: '表情', items: ['😀','😃','😄','😁','😆','😅','🤣','😂','🙂','🙃','😉','😊','😇','🥰','😍','🤩','😘','😗','😚','😙','🥲','😋','😛','😜','🤪','😝','🤑','🤗','🤭','🫢','🫣','🤫','🤔','🫡','🤐','🤨','😐','😑','😶','🫥','😏','😒','🙄','😬','🤥','😌','😔','😪','🤤','😴','😷','🤒','🤕','🤢','🤮','🥵','🥶','🥴','😵','🤯','🤠','🥳','🥸','😎','🤓','🧐','😕','🫤','😟','🙁','☹️','😮','😯','😲','😳','🥺','🥹','😦','😧','😨','😰','😥','😢','😭','😱','😖','😣','😞','😓','😩','😫','🥱','😤','😡','😠','🤬','😈','👿','💀','☠️','💩','🤡','👹','👺','👻','👽','👾','🤖'] },
  { label: '手势', items: ['👋','🤚','🖐️','✋','🖖','👌','🤏','✌️','🤞','🤟','🤘','🤙','👈','👉','👆','🖕','👇','☝️','👍','👎','✊','👊','🤛','🤜','👏','🙌','👐','🤲','🤝','🙏','✍️','💪','🦾','🦿','🦵','🦶','👂','🦻','👃','🧠','🫀','🫁','🦷','🦴','👀','👁️','👅','👄'] },
  { label: '物品', items: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❤️‍🔥','❤️‍🩹','💯','💢','💥','💫','💦','💨','🕳️','💣','💬','👁️‍🗨️','🗨️','🗯️','💭','💤','👋','🤚','✋','🖖','👌','🤞','🤟','🤘','🤙','👈','👉','👆','🖕','👇','☝️','👍','👎','✊','👊','🤛','🤜','👏','🙌','👐','🤲','🤝','🙏','✍️','💪','🦾','🦿','🦵','🦶','👂','🦻','👃','🧠','🫀','🫁','🦷','🦴','👀','👁️','👅','👄'] },
  { label: '符号', items: ['⭐','🌟','✨','⚡','🔥','💥','🎉','🎊','🎈','🎁','🏆','🥇','🥈','🥉','👑','💎','🔔','📢','📣','🎯','🚀','💡','🔧','🛠️','📝','📋','📌','📍','🔒','🔓','🔑','⚠️','❓','❗','✅','❌','➕','➖','✖️','➗','♾️','💯','🔄','↔️','⬆️','⬇️','➡️','⬅️','↩️','↪️','⤴️','⤵️','#️⃣','*️⃣','0️⃣','1️⃣','2️⃣','3️⃣','4️⃣','5️⃣','6️⃣','7️⃣','8️⃣','9️⃣','🔟','🔠','🔡','🔢','🔣','🔤','🅰️','🆎','🅱️','🆑','🆒','🆓','ℹ️','🆗','🆙','🆘','🆔','🚫','🔞','📵','🚯','🚱','🚳','🚷','🚼','🚽','🚾','🛂','🛃','🛄','🛅','🚹','🚺','🚻','🚼','🚽','🚾','🛂','🛃','🛄','🛅','🚹','🚺','🚻'] },
]

function insertEmoji(emoji: string) {
  const textarea = chatInputRef.value?.$el?.querySelector('textarea')
  if (textarea) {
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    chatInput.value = chatInput.value.slice(0, start) + emoji + chatInput.value.slice(end)
    // 恢复光标位置
    nextTick(() => {
      textarea.focus()
      textarea.setSelectionRange(start + emoji.length, start + emoji.length)
    })
  } else {
    chatInput.value += emoji
  }
}

// WebSocket 实时接收消息
const ws = useWebSocket()
ws.subscribe(`/topic/team-chat/${teamId}`, (data: any) => {
  if (data.type === "CHAT_MESSAGE" && data.message) {
    const msg: ChatMsg = data.message
    msg.isMine = msg.senderId === myUserId
    messages.value.push(msg)
    if (!msg.isMine) unreadCount.value++
    nextTick(scrollToBottom)
  }
  // 处理撤回事件：更新本地消息列表中对应的消息
  if (data.type === "CHAT_RECALL" && data.message) {
    const idx = messages.value.findIndex((m: ChatMsg) => m.id === data.message.id)
    if (idx >= 0) {
      messages.value[idx].recalled = true
      messages.value[idx].content = "[消息已撤回]"
    }
  }
})

// 加载历史消息
async function loadChatHistory() {
  loadingChat.value = true
  try {
    const res = await getChatHistory(teamId)
    const list: ChatMsg[] = res.data || []
    // 确保有 myUserId（可能 store 已在页面加载期间完成 hydration）
    if (!myUserId) myUserId = getMyUserId()
    messages.value = list.map((m: ChatMsg) => ({
      ...m,
      isMine: m.senderId === myUserId,
    }))
    lastReadTime = Date.now()
  } finally {
    loadingChat.value = false
    nextTick(scrollToBottom)
  }
}

// 发送消息
async function sendChatMessage() {
  const text = chatInput.value.trim()
  if (!text) return
  sendingChat.value = true
  try {
    await apiSendChat({ teamId, content: text })
    chatInput.value = ""
    mentionList.value = []
    nextTick(scrollToBottom)
  } catch {
    // 错误由拦截器处理
  } finally {
    sendingChat.value = false
  }
}

// 撤回消息（2分钟内）
function canRecall(createdAt: string): boolean {
  if (!createdAt) return false
  const msgTime = new Date(createdAt).getTime()
  return Date.now() - msgTime < 2 * 60 * 1000
}

async function handleRecall(msg: ChatMsg) {
  try {
    await recallMessage(msg.id)
    // 本地更新
    msg.recalled = true
    msg.content = "[消息已撤回]"
    ElMessage.success("消息已撤回")
  } catch { /* 错误由拦截器处理 */ }
}

// @提及：输入时检测 @ 并弹出成员列表
function onChatInput(val: string) {
  const textarea = chatInputRef.value?.$el?.querySelector('textarea')
  if (!textarea) { mentionList.value = []; return }

  const cursorPos = textarea.selectionStart
  // 找光标前最近的 @ 符号
  let atIdx = -1
  for (let i = cursorPos - 1; i >= 0; i--) {
    if (val[i] === '@') { atIdx = i; break }
    if (val[i] === ' ' && i < cursorPos - 1) break  // 遇到空格且不是紧跟@后，停止搜索
  }

  if (atIdx >= 0) {
    const query = val.substring(atIdx + 1, cursorPos).toLowerCase()
    mentionStartPos = atIdx
    mentionActiveIndex.value = 0
    mentionList.value = members.value.filter((m: any) =>
      m.username.toLowerCase().includes(query)
    ).slice(0, 8)
  } else {
    mentionList.value = []
  }
}

// 选择 @ 成员
function selectMention(m: any) {
  if (mentionStartPos < 0) return
  const before = chatInput.value.slice(0, mentionStartPos)
  const after = chatInput.value.slice(mentionStartPos + 1)  // 跳过 @
  // 找到 @ 后面到光标位置的内容并替换
  const cursorEnd = chatInputRef.value?.$el?.querySelector('textarea')?.selectionStart ?? chatInput.value.length
  const afterAt = chatInput.value.slice(cursorEnd)
  chatInput.value = before + `@${m.username} ` + afterAt
  mentionList.value = []
  mentionStartPos = -1
}

// 滚动到底部
function scrollToBottom() {
  if (!chatMsgListRef.value) return
  chatMsgListRef.value.scrollTop = chatMsgListRef.value.scrollHeight
}

// 滚动到聊天区域
function scrollToChat() {
  chatPanelRef.value?.scrollIntoView({ behavior: "smooth", block: "start" })
  unreadCount.value = 0
  lastReadTime = Date.now()
}

// 滚动时清除未读
function onChatScroll() {
  const el = chatMsgListRef.value
  if (!el) return
  const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40
  if (atBottom && unreadCount.value > 0) {
    unreadCount.value = 0
    lastReadTime = Date.now()
  }
}

// 时间分隔线判断
function shouldShowDateDivider(idx: number): boolean {
  if (idx === 0) return true
  const prev = messages.value[idx - 1]?.createdAt?.split(" ")[0]
  const curr = messages.value[idx]?.createdAt?.split(" ")[0]
  return prev !== curr
}

// 格式化日期/时间
function formatDate(dt?: string) {
  if (!dt) return ""
  return dt.split(" ")[0] || ""
}
function formatTime(dt?: string) {
  if (!dt) return ""
  return dt.split(" ")[1]?.substring(0, 5) || ""
}

// 渲染消息中的 @提及（高亮显示）
function renderMention(content: string): string {
  if (!content) return ""
  // 先转义 HTML 防止 XSS，再做 @mention 高亮
  const escaped = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
  return escaped.replace(/@(\S+)/g, '<span class="td-mention-highlight">@$1</span>')
}

// ========== 基础操作 ==========
async function searchUserOptions(query: string) {
  if (!query || query.length < 1) { userSearchResults.value = []; return }
  searchingUsers.value = true
  try {
    const res = await searchUsers(query)
    userSearchResults.value = res.data || []
  } finally {
    searchingUsers.value = false
  }
}

async function fetchData() {
  const [teamRes, membersRes, projectsRes, roleRes] = await Promise.all([
    getTeamById(teamId),
    getMembers(teamId),
    getProjectsByTeam(teamId),
    getTeamRole(teamId).catch(() => ({ data: null })), // 获取用户角色，失败则忽略
  ])
  team.value = teamRes.data
  members.value = membersRes.data || []
  projects.value = projectsRes.data || []
  myTeamRole.value = roleRes.data // 当前用户在团队中的角色
}

async function handleInvite() {
  if (!inviteUserId.value) { ElMessage.warning("请选择要邀请的用户"); return }
  await inviteMember({ teamId, userId: inviteUserId.value, role: inviteRole.value })
  ElMessage.success("邀请成功")
  showInviteDialog.value = false
  inviteUserId.value = null
  await fetchData()
}

async function handleRemove(userId: number) {
  await ElMessageBox.confirm("确定移除该成员?", "提示", { type: "warning" })
  await removeMember(teamId, userId)
  ElMessage.success("已移除")
  await fetchData()
}

async function handleCreateProject() {
  if (!projectForm.name || !projectForm.key) { ElMessage.warning("请填写项目信息"); return }

  // 非管理员需要走审批流程
  const isAdminUser = isTeamAdmin.value
  if (!isAdminUser) {
    try {
      await requestApproval('PROJECT_CREATE', `创建项目: ${projectForm.name}`, {
        name: projectForm.name,
        key: projectForm.key,
        template: projectForm.template,
      })
    } catch (e) {
      console.error('创建审批请求失败:', e)
      ElMessage.error('提交审批请求失败，请稍后重试')
      return
    }
    showCreateProject.value = false
    projectForm.name = ""
    projectForm.key = ""
    return
  }

  // 管理员直接创建（如果后端返回403/500则自动降级为审批）
  try {
    await createProject({ ...projectForm, teamId })
    ElMessage.success("项目创建成功")
  } catch (e: any) {
    // 后端权限拦截时自动降级为审批请求
    const status = e?.response?.status
    if (status === 403 || status >= 500) {
      await requestApproval('PROJECT_CREATE', `创建项目: ${projectForm.name}`, {
        name: projectForm.name,
        key: projectForm.key,
        template: projectForm.template,
      })
      showCreateProject.value = false
      projectForm.name = ""
      projectForm.key = ""
      return
    }
    throw e
  }
  showCreateProject.value = false
  projectForm.name = ""
  projectForm.key = ""
  await fetchData()
}

async function handleDeleteProject(p: any) {
  try {
    await ElMessageBox.confirm(`确定删除项目「${p.name}」？此操作不可恢复。`, '确认删除', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })

    // 非管理员需要走审批流程
    if (!isTeamAdmin.value) {
      try {
        await requestApproval('PROJECT_DELETE', `删除项目: ${p.name}`, { projectId: p.id })
      } catch (e) {
        console.error('创建审批请求失败:', e)
        ElMessage.error('提交审批请求失败，请稍后重试')
        return
      }
      return
    }

    // 管理员直接删除（如果后端返回403/500则自动降级为审批）
    try {
      await deleteProject(p.id)
      ElMessage.success('项目已删除')
      await fetchData()
    } catch (e: any) {
      const status = e?.response?.status
      if (status === 403 || status >= 500) {
        await requestApproval('PROJECT_DELETE', `删除项目: ${p.name}`, { projectId: p.id })
        return
      }
      throw e
    }
  } catch (e: any) {
    if (e !== 'cancel') throw e
  }
}

onMounted(() => {
  fetchData()
  loadChatHistory()
  fetchPendingApprovals()
})
</script>

<style scoped lang="scss">
.team-detail-page {
  max-width: 1100px;
  margin: 0 auto;
}

// ========== Hero ==========
.td-hero {
  position: relative;
  margin-bottom: 24px;
  border-radius: 18px;
  overflow: hidden;

  &__bg {
    position: absolute;
    inset: 0;
    opacity: 0.08;
  }

  &__overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(6,95,70,0.92), rgba(4,120,87,0.85));
  }

  &__top-bar {
    display: none; /* 已移除，返回按钮移入 body 内 */
  }

  &__back {
    color: rgba(255,255,255,0.8);
    font-weight: 600;
    align-self: flex-start;

    &:hover { color: #fff; }
  }

  &__body {
    position: relative;
    z-index: 1;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 16px;
    padding: 20px 24px 24px;
  }

  &__avatar {
    width: 56px;
    height: 56px;
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 800;
    font-size: 22px;
    flex-shrink: 0;
    box-shadow: 0 8px 24px rgba(0,0,0,0.2);
  }

  &__info {
    flex: 1;
    min-width: 160px;
  }

  &__name {
    font-size: 20px;
    font-weight: 800;
    color: #fff;
    margin: 0 0 4px;
    letter-spacing: -0.3px;
  }

  &__desc {
    font-size: 13px;
    color: rgba(255,255,255,0.7);
    margin: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  &__badge {
    margin-right: 4px;
  }
}

// ========== 网格布局 ==========
.td-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 18px;
  align-items: start;
}

.td-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// ========== Section 通用 ==========
.td-section {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 14px 18px;
    border-bottom: 1px solid var(--border-color);

    h2 {
      font-size: 14px;
      font-weight: 700;
      margin: 0;
      color: var(--text-primary);
      flex: 1;
    }
  }

  &__count {
    font-size: 12px;
    color: var(--text-secondary);
    background: var(--bg-hover);
    padding: 2px 8px;
    border-radius: 10px;
  }

  &__icon {
    width: 30px;
    height: 30px;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    flex-shrink: 0;

    &--member {
      background: rgba(16,185,129,0.12);
      color: #059669;
    }

    &--project {
      background: rgba(245,158,11,0.12);
      color: #d97706;
    }
  }
}

// ========== 成员列表 ==========
.td-member-list {
  padding: 6px 0;
}

.td-member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 18px;
  transition: background 0.15s;

  &:hover { background: var(--bg-hover); }

  &__avatar {
    width: 32px;
    height: 32px;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 700;
    font-size: 13px;
    flex-shrink: 0;
  }

  &__info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  &__name {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__email {
    font-size: 11px;
    color: var(--text-secondary);
  }
}

.td-member-empty {
  padding: 20px 18px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}

// ========== 项目卡片 ==========
.td-project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
  padding: 14px 18px;
}

.td-project-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  transition: all 0.2s;

  &:hover {
    border-color: rgba(16,185,129,0.35);
    box-shadow: 0 2px 12px rgba(16,185,129,0.08);

    .td-project-card__arrow { color: #10b981; transform: translateX(2px); }
    .td-project-card__del { opacity: 1; }
  }

  &__main {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
    cursor: pointer;
  }

  &__del {
    opacity: 0;
    transition: opacity 0.2s;
    flex-shrink: 0;
  }

  &__key {
    font-family: monospace;
    font-weight: 700;
    font-size: 11px;
    color: #059669;
    background: rgba(16,185,129,0.1);
    padding: 2px 7px;
    border-radius: 5px;
    flex-shrink: 0;
  }

  &__name {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__arrow {
    margin-left: auto;
    color: var(--text-placeholder);
    transition: all 0.2s;
    flex-shrink: 0;
  }
}

.td-project-empty {
  padding: 20px 18px;
  text-align: center;
  p {
    color: var(--text-secondary);
    font-size: 13px;
    margin: 0 0 10px;
  }
}

// ========== 群聊面板 ==========
.td-chat-panel {
  position: sticky;
  top: 72px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  min-height: 480px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.td-chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
  background: linear-gradient(135deg, rgba(16,185,129,0.06), rgba(245,158,11,0.04));

  h3 {
    font-size: 14px;
    font-weight: 700;
    margin: 0;
    color: var(--text-primary);
  }

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: 10px;
    background: rgba(16,185,129,0.12);
    color: #059669;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    flex-shrink: 0;
  }

  &__online {
    font-size: 11px;
    color: var(--text-secondary);
  }
}

// 消息列表
.td-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: transparent; border-radius: 4px; }
  &:hover::-webkit-scrollbar-thumb { background: var(--border-color); }
}

.td-chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 0;
  color: var(--text-secondary);

  p {
    font-size: 14px;
    font-weight: 600;
    margin: 0;
  }

  span {
    font-size: 12px;
  }
}

.td-chat-loading {
  display: flex;
  justify-content: center;
  padding: 8px;
  color: var(--text-placeholder);
}

// 时间分隔线
.td-chat-divider {
  text-align: center;
  font-size: 11px;
  color: var(--text-placeholder);
  padding: 8px 0;
  position: relative;

  &::before, &::after {
    content: "";
    position: absolute;
    top: 50%;
    width: 28%;
    height: 1px;
    background: var(--border-color);
  }
  &::before { left: 0; }
  &::after { right: 0; }
}

// 消息气泡
.td-msg {
  display: flex;
  gap: 8px;
  max-width: 85%;
  padding: 4px 0;

  &--mine {
    align-self: flex-end;
    margin-left: auto;  // 确保右对齐
    flex-direction: row-reverse;

    .td-msg__bubble {
      background: linear-gradient(135deg, #059669, #10b981);
      color: #fff;
      border-bottom-right-radius: 4px;
    }
    .td-msg__sender { display: none; }
    .td-msg__time { text-align: right; }
  }

  &--recalled {
    opacity: 0.6;

    .td-msg__bubble--recalled {
      font-style: italic;
      color: var(--text-secondary);
      background: transparent;
      padding: 4px 8px;
      font-size: 12px;
    }
  }

  &__avatar {
    width: 30px;
    height: 30px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 700;
    font-size: 12px;
    flex-shrink: 0;
    margin-top: 2px;
  }

  &__body {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  &__sender {
    font-size: 11px;
    font-weight: 600;
    color: var(--text-secondary);
    margin-bottom: 2px;
    padding-left: 2px;
  }

  &__bubble {
    padding: 8px 12px;
    border-radius: 12px;
    background: var(--bg-hover);
    color: var(--text-primary);
    font-size: 13px;
    line-height: 1.5;
    word-break: break-word;
    border-bottom-left-radius: 4px;
  }

  &__content-wrap {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__recall-btn {
    opacity: 0;
    transition: opacity 0.2s;
    font-size: 11px;
    flex-shrink: 0;
    padding: 2px 6px;
  }

  &:hover &__recall-btn {
    opacity: 1;
  }

  &__time {
    font-size: 10px;
    color: var(--text-placeholder);
    margin-top: 2px;
    padding: 0 4px;
  }
}

// 输入框
.td-chat-input-wrap {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);

  .el-textarea {
    flex: 1;
  }

  .el-button {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
  }

  &__emoji-btn {
    flex-shrink: 0;
    width: 36px;
    height: 36px;

    &:hover { background: var(--bg-hover); }
  }
}

.td-chat-input-main {
  position: relative;
  flex: 1;
}

// Emoji 选择器
.emoji-picker {
  max-height: 280px;
  overflow-y: auto;
}

.emoji-group {
  &:not(:last-child) {
    margin-bottom: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--border-color);
  }

  &__label {
    font-size: 11px;
    color: var(--text-secondary);
    font-weight: 600;
    margin-bottom: 4px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 2px;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 1;
  font-size: 18px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s, transform 0.1s;

  &:hover {
    background: rgba(16,185,129,0.12);
    transform: scale(1.2);
  }

  &:active {
    transform: scale(0.9);
  }
}

// 搜索头像
.td-search-avatar {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 10px;
  flex-shrink: 0;
}

// 响应式：窄屏时聊天面板下移
@media (max-width: 900px) {
  .td-grid {
    grid-template-columns: 1fr;
  }
  .td-chat-panel {
    position: static;
    height: 420px;
    min-height: unset;
  }
}

// @提及下拉
.td-mention-dropdown {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  max-height: 200px;
  overflow-y: auto;
  z-index: 10;
  margin-bottom: 4px;
}

.td-mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover, &--active {
    background: rgba(16,185,129,0.1);
  }

  &__avatar {
    width: 22px;
    height: 22px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 700;
    font-size: 10px;
    flex-shrink: 0;
  }

  span {
    font-size: 13px;
  }
}

// @提及高亮（消息内容中）
.td-mention-highlight {
  color: #059669;
  font-weight: 600;
  background: rgba(16,185,129,0.1);
  padding: 0 2px;
  border-radius: 3px;
}

// ========== 审批列表 ==========
.approval-list {
  max-height: 400px;
  overflow-y: auto;
}

.approval-item {
  padding: 16px;
  border-bottom: 1px solid var(--border-color-light);
  
  &:last-child { border-bottom: none; }
  
  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
    
    .approval-item__title {
      font-weight: 600;
      color: var(--text-primary);
      flex: 1;
    }
    
    .approval-item__time {
      font-size: 12px;
      color: var(--text-placeholder);
      flex-shrink: 0;
    }
  }
  
  &__body {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 13px;
    color: var(--text-secondary);
    margin-bottom: 12px;
    
    .approval-item__desc {
      font-size: 12px;
      color: var(--text-placeholder);
      margin-top: 4px;
    }
  }
  
  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>
