<template>
  <div
    :class="['ai-chat-panel', { 'is-open': isOpen, 'is-minimized': isMinimized, 'is-dragging': isDragging }]"
    :style="panelStyle"
  >
    <!-- 触发按钮 (折叠状态) -->
    <button
      v-if="!isOpen"
      class="ai-chat-panel__trigger"
      :style="{ cursor: isDragging ? 'grabbing' : 'grab' }"
      @mousedown="onTriggerMouseDown"
      @click="onTriggerClick"
      @touchstart="onTriggerTouchStart"
      title="AI 助手 (可拖动)"
    >
      <el-icon :size="22"><ChatDotRound /></el-icon>
      <span v-if="unreadCount > 0" class="ai-chat-panel__badge">{{ unreadCount }}</span>
    </button>

    <!-- 面板主体 -->
    <div v-else class="ai-chat-panel__body" :style="bodyStyle">
      <!-- 头部（可拖动） -->
      <div
        class="ai-chat-panel__header"
        :style="{ cursor: isDragging ? 'grabbing' : 'grab' }"
        @mousedown="onHeaderMouseDown"
        @touchstart="onHeaderTouchStart"
      >
        <div class="ai-chat-panel__header-left">
          <span class="ai-chat-panel__title">Mimo AI</span>
          <el-tag size="small" effect="plain" type="primary">{{ providerName }}</el-tag>
        </div>
        <div class="ai-chat-panel__header-actions">
          <el-tooltip content="最小化" placement="bottom">
            <el-icon class="ai-chat-panel__icon-btn" @click.stop="isMinimized = true"><Minus /></el-icon>
          </el-tooltip>
          <el-tooltip content="关闭" placement="bottom">
            <el-icon class="ai-chat-panel__icon-btn" @click.stop="handleClose"><Close /></el-icon>
          </el-tooltip>
        </div>
      </div>

      <!-- 最小化状态 -->
      <div v-if="isMinimized" class="ai-chat-panel__mini" @click="isMinimized = false">
        <span>AI 助手</span>
        <span v-if="lastMessage" class="ai-chat-panel__mini-msg">{{ lastMessage }}</span>
      </div>

      <!-- 消息列表 -->
      <div v-else ref="messagesRef" class="ai-chat-panel__messages">
        <div v-if="messages.length === 0" class="ai-chat-panel__welcome">
          <div class="ai-chat-panel__avatar">AI</div>
          <p>你好！我是 Mimo AI 助手，可以帮你：</p>
          <ul>
            <li>分析项目问题</li>
            <li>查询任务状态</li>
            <li>生成测试用例</li>
            <li>优化任务描述</li>
          </ul>
        </div>

        <TransitionGroup name="chat-msg" tag="div">
          <div
            v-for="(msg, i) in messages"
            :key="i"
            :class="['ai-chat-panel__msg', `ai-chat-panel__msg--${msg.role}`]"
          >
            <div v-if="msg.role === 'assistant'" class="ai-chat-panel__msg-avatar">AI</div>
            <div class="ai-chat-panel__msg-bubble">
              <div v-if="msg.loading" class="ai-chat-panel__typing">
                <span></span><span></span><span></span>
              </div>
              <div v-else class="ai-chat-panel__msg-text" v-html="renderMarkdown(msg.content)"></div>
              <div v-if="msg.role === 'assistant' && !msg.loading" class="ai-chat-panel__msg-actions">
                <button title="复制" @click="copyText(msg.content)"><DocumentCopy /></button>
                <button title="重新生成" @click="regenerate(i)"><Refresh /></button>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="ai-chat-panel__msg-avatar">U</div>
          </div>
        </TransitionGroup>
      </div>

      <!-- 输入区 -->
      <div v-if="!isMinimized" class="ai-chat-panel__input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="1"
          placeholder="输入消息... (Enter发送, Shift+Enter换行)"
          resize="none"
          :disabled="streaming"
          @keydown.enter.exact.prevent="handleSend"
          @keydown.enter.shift.exact.stop
        />
        <el-button
          type="primary"
          circle
          :loading="streaming"
          :disabled="!inputText.trim()"
          @click="handleSend"
        >
          <Promotion />
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound, Minus, Close, DocumentCopy, Refresh, Promotion
} from '@element-plus/icons-vue'
import { chatStream, getAiUsage } from '@/api/ai'

const isOpen = ref(false)
const isMinimized = ref(false)
const inputText = ref('')
const streaming = ref(false)
const messagesRef = ref<HTMLElement | null>(null)
const providerName = ref('DeepSeek')
const unreadCount = ref(0)
let abortController: AbortController | null = null

// ====== 可拖动：位置持久化 ======
const STORAGE_KEY = 'ai-panel-pos'
interface PanelPos { right: number; bottom: number }
const pos = ref<PanelPos>({ right: 24, bottom: 24 })
const isDragging = ref(false)
let dragStartX = 0
let dragStartY = 0
let dragStartRight = 0
let dragStartBottom = 0
let dragMoved = false
let dragPointerId: number | null = null
let dragMoveHandler: ((e: PointerEvent) => void) | null = null
let dragUpHandler: (() => void) | null = null

const panelStyle = computed(() => ({
  right: `${pos.value.right}px`,
  bottom: `${pos.value.bottom}px`,
  left: 'auto',
  top: 'auto',
  transition: isDragging.value ? 'none' : undefined,
}))

// 展开后 body 仍然吸附在 panel 容器，宽度由容器决定
const bodyStyle = computed(() => ({}))

function loadPos() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as PanelPos
      if (typeof parsed.right === 'number' && typeof parsed.bottom === 'number') {
        pos.value = clampPos(parsed)
      }
    }
  } catch {}
}

function savePos() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(pos.value))
  } catch {}
}

function clampPos(p: PanelPos): PanelPos {
  const w = window.innerWidth
  const h = window.innerHeight
  const W = isOpen.value ? 380 : 52
  const H = isOpen.value ? 560 : 52
  return {
    right: Math.max(0, Math.min(p.right, w - W)),
    bottom: Math.max(0, Math.min(p.bottom, h - H)),
  }
}

function beginDrag(clientX: number, clientY: number, pointerId?: number) {
  dragStartX = clientX
  dragStartY = clientY
  dragStartRight = pos.value.right
  dragStartBottom = pos.value.bottom
  isDragging.value = true
  dragMoved = false
  dragPointerId = pointerId ?? null

  dragMoveHandler = (e: PointerEvent) => {
    if (dragPointerId !== null && e.pointerId !== dragPointerId) return
    const dx = e.clientX - dragStartX
    const dy = e.clientY - dragStartY
    if (Math.abs(dx) + Math.abs(dy) > 3) dragMoved = true
    pos.value = clampPos({
      right: dragStartRight - dx,
      bottom: dragStartBottom - dy,
    })
  }
  dragUpHandler = () => {
    isDragging.value = false
    savePos()
    if (dragMoveHandler) window.removeEventListener('pointermove', dragMoveHandler)
    if (dragUpHandler) {
      window.removeEventListener('pointerup', dragUpHandler)
      window.removeEventListener('pointercancel', dragUpHandler)
    }
    dragMoveHandler = null
    dragUpHandler = null
  }
  window.addEventListener('pointermove', dragMoveHandler)
  window.addEventListener('pointerup', dragUpHandler)
  window.addEventListener('pointercancel', dragUpHandler)
}

function onTriggerMouseDown(e: MouseEvent) {
  // 区分点击与拖动：mousedown 时记录，按 click 时检测是否移动过
  beginDrag(e.clientX, e.clientY)
}

function onTriggerClick(_e: MouseEvent) {
  // 拖动过程中不应该触发 click
  if (dragMoved) {
    dragMoved = false
    return
  }
  isOpen.value = true
  isMinimized.value = false
}

function onTriggerTouchStart(e: TouchEvent) {
  const t = e.touches[0]
  if (!t) return
  beginDrag(t.clientX, t.clientY)
}

function onHeaderMouseDown(e: MouseEvent) {
  // 点击 header 上的按钮时不应触发拖动
  const target = e.target as HTMLElement
  if (target.closest('.ai-chat-panel__icon-btn')) return
  e.preventDefault()
  beginDrag(e.clientX, e.clientY)
}

function onHeaderTouchStart(e: TouchEvent) {
  const target = e.target as HTMLElement
  if (target.closest('.ai-chat-panel__icon-btn')) return
  const t = e.touches[0]
  if (!t) return
  beginDrag(t.clientX, t.clientY)
}

onMounted(() => {
  loadPos()
  window.addEventListener('resize', () => {
    pos.value = clampPos(pos.value)
  })
})

onBeforeUnmount(() => {
  if (dragMoveHandler) window.removeEventListener('pointermove', dragMoveHandler)
  if (dragUpHandler) {
    window.removeEventListener('pointerup', dragUpHandler)
    window.removeEventListener('pointercancel', dragUpHandler)
  }
})

watch(isOpen, (v) => {
  // 重新计算位置以适配展开后尺寸
  pos.value = clampPos(pos.value)
  if (v) unreadCount.value = 0
})

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
}
const messages = ref<ChatMessage[]>([])

// 最后一条消息（用于最小化预览）
const lastMessage = computed(() => {
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'assistant') return ''
  return last.content.length > 30 ? last.content.slice(0, 30) + '...' : last.content
})

// 初始化时获取Provider名称
getAiUsage().then((u) => { providerName.value = u.provider }).catch(() => {})

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  // 终止上一个未完成的请求，避免 ERR_ABORTED 噪音
  if (abortController) {
    try { abortController.abort() } catch {}
    abortController = null
  }

  // 添加用户消息
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  scrollToBottom()

  // 添加AI占位消息
  const aiIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', loading: true })
  streaming.value = true

  let fullContent = ''

  abortController = chatStream(
    text,
    (chunk) => {
      fullContent += chunk
      // 实时更新最后一条AI消息
      const msg = messages.value[aiIndex]
      if (msg) {
        msg.content = fullContent
        msg.loading = false
      }
      scrollToBottom()
    },
    '你是Mimo项目管理AI助手。基于项目上下文回答用户问题，使用中文输出。保持回答简洁实用。',
    () => {
      streaming.value = false
      const msg = messages.value[aiIndex]
      if (msg && !msg.content) {
        msg.content = '(无响应)'
        msg.loading = false
      }
    },
    (err) => {
      streaming.value = false
      const msg = messages.value[aiIndex]
      if (msg) {
        const errMsg = err?.message || '请求失败'
        msg.content = `(请求失败: ${errMsg})`
        msg.loading = false
      }
    }
  )
}

function handleClose() {
  isOpen.value = false
  isMinimized.value = false
  unreadCount.value = 0
}

function copyText(text: string) {
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制'))
}

function regenerate(index: number) {
  // 找到对应的用户消息重新发送
  let userMsgIndex = index - 1
  while (userMsgIndex >= 0 && messages.value[userMsgIndex].role !== 'user') {
    userMsgIndex--
  }
  if (userMsgIndex >= 0) {
    const userMsg = messages.value[userMsgIndex]
    // 移除从用户消息之后的所有消息
    messages.value.splice(userMsgIndex)
    inputText.value = userMsg.content
    handleSend()
  }
}

/** 简单的 Markdown 渲染 (支持加粗/代码/列表) */
function renderMarkdown(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^[-*] (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>\n?)/g, '<ul>$1</ul>')
    .replace(/\n/g, '<br/>')
}
</script>

<style scoped>
.ai-chat-panel {
  position: fixed;
  z-index: 9999;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
}
.ai-chat-panel.is-dragging,
.ai-chat-panel.is-dragging * {
  cursor: grabbing !important;
  user-select: none !important;
  -webkit-user-select: none !important;
}

/* 触发按钮 */
.ai-chat-panel__trigger {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(99,102,241,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s;
}
.ai-chat-panel__trigger:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(99,102,241,0.5);
}
.ai-chat-panel__badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 18px;
  height: 18px;
  font-size: 11px;
  background: #ef4444;
  color: white;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

/* 面板 */
.ai-chat-panel__body {
  width: 380px;
  height: 560px;
  border-radius: 16px;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-color, #e4e7ed);
  box-shadow: 0 8px 32px rgba(0,0,0,0.12), 0 2px 8px rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 头部 */
.ai-chat-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
  flex-shrink: 0;
}
.ai-chat-panel__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-chat-panel__title {
  font-weight: 600;
  font-size: 15px;
}
.ai-chat-panel__header-actions {
  display: flex;
  gap: 4px;
}
.ai-chat-panel__icon-btn {
  font-size: 16px;
  cursor: pointer;
  padding: 3px;
  color: var(--text-secondary, #909399);
  border-radius: 4px;
  transition: all 0.15s;
}
.ai-chat-panel__icon-btn:hover { color: var(--text-primary, #303133); background: rgba(0,0,0,0.04); }

/* 最小化 */
.ai-chat-panel__mini {
  padding: 10px 16px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ai-chat-panel__mini > span:first-child { font-weight: 600; font-size: 13px; }
.ai-chat-panel__mini-msg {
  font-size: 11px;
  color: var(--text-secondary, #94a3b8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 消息区 */
.ai-chat-panel__messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-chat-panel__welcome {
  text-align: center;
  padding: 20px 0;
  color: var(--text-secondary, #94a3b8);
}
.ai-chat-panel__welcome p { margin-bottom: 8px; font-size: 14px; font-weight: 500; }
.ai-chat-panel__welcome ul {
  list-style: none;
  padding: 0;
  text-align: left;
  max-width: 200px;
  margin: 0 auto;
}
.ai-chat-panel__welcome li {
  padding: 4px 0;
  font-size: 13px;
  position: relative;
  padding-left: 16px;
}
.ai-chat-panel__welcome li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 11px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6366f1;
}

/* 消息气泡 */
.ai-chat-panel__msg {
  display: flex;
  gap: 8px;
  max-width: 88%;
}
.ai-chat-panel__msg--user { align-self: flex-end; flex-direction: row-reverse; }
.ai-chat-panel__msg--assistant { align-self: flex-start; }

.ai-chat-panel__msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.ai-chat-panel__msg--assistant .ai-chat-panel__msg-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
}
.ai-chat-panel__msg--user .ai-chat-panel__msg-avatar {
  background: #e4e7ed;
  color: #606266;
}

.ai-chat-panel__msg-bubble {
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
}
.ai-chat-panel__msg--assistant .ai-chat-panel__msg-bubble {
  background: rgba(99,102,241,0.06);
  border: 1px solid rgba(99,102,241,0.12);
  border-top-left-radius: 4px;
}
.ai-chat-panel__msg--user .ai-chat-panel__msg-bubble {
  background: #6366f1;
  color: white;
  border-top-right-radius: 4px;
}

.ai-chat-panel__msg-text :deep(code) {
  background: rgba(0,0,0,0.08);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}
.ai-chat-panel__msg--user .ai-chat-panel__msg-text :deep(code) {
  background: rgba(255,255,255,0.2);
}

.ai-chat-panel__msg-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}
.ai-chat-panel__msg-bubble:hover .ai-chat-panel__msg-actions { opacity: 1; }
.ai-chat-panel__msg-actions button {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-tertiary, #c0c4cc);
  padding: 2px;
}
.ai-chat-panel__msg-actions button:hover { color: #6366f1; }

/* 打字动画 */
.ai-chat-panel__typing {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.ai-chat-panel__typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6366f1;
  animation: typing-bounce 1.2s infinite;
}
.ai-chat-panel__typing span:nth-child(2) { animation-delay: 0.15s; }
.ai-chat-panel__typing span:nth-child(3) { animation-delay: 0.3s; }
@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* 输入区 */
.ai-chat-panel__input {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--border-color, #f0f0f0);
  flex-shrink: 0;
  align-items: flex-end;
}
.ai-chat-panel__input :deep(.el-textarea__inner) {
  min-height: 34px !important;
  max-height: 80px;
  font-size: 13px;
  padding: 7px 10px;
}
.ai-chat-panel__input .el-button { flex-shrink: 0; }

/* 消息过渡 */
.chat-msg-enter-active { transition: all 0.25s ease-out; }
.chat-msg-leave-active { transition: all 0.15s ease-in; }
.chat-msg-enter-from { opacity: 0; transform: translateY(8px); }
.chat-msg-leave-to { opacity: 0; transform: translateY(-8px); scale: 0.95; }

/* 暗色模式适配 */
[data-theme='dark'] .ai-chat-panel__body {
  background: #1e1e2d;
  border-color: #2d2d44;
}
[data-theme='dark'] .ai-chat-panel__msg--assistant .ai-chat-panel__msg-bubble {
  background: rgba(99,102,241,0.12);
  border-color: rgba(99,102,241,0.2);
}
[data-theme='dark'] .ai-chat-panel__msg--user .ai-chat-panel__msg-bubble {
  background: #6366f1;
}
</style>
