<template>
  <div class="ann-page">
    <div class="ann-header">
      <h2>系统公告</h2>
      <div class="ann-header__actions">
        <el-button v-if="userStore.isAdmin" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon> 发布公告
        </el-button>
        <el-button @click="loadList" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="ann-list">
      <el-empty v-if="!loading && list.length === 0" description="暂无公告" />
      <el-card
        v-for="a in list"
        :key="a.id"
        class="ann-item"
        shadow="hover"
      >
        <div class="ann-item__header">
          <el-tag v-if="a.pinned === 1" type="danger" size="small" effect="dark">置顶</el-tag>
          <span class="ann-item__title" @click="openDetail(a)">{{ a.title }}</span>
        </div>
        <div class="ann-item__meta">
          <span>{{ a.createdByName || '匿名' }}</span>
          <span>{{ formatTime(a.createdAt) }}</span>
        </div>
        <div class="ann-item__content" @click="openDetail(a)">
          {{ stripMarkdown(a.content).slice(0, 200) }}{{ stripMarkdown(a.content).length > 200 ? '…' : '' }}
        </div>
        <div v-if="userStore.isAdmin" class="ann-item__actions">
          <el-button text type="primary" @click="openEdit(a)">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(a)">删除</el-button>
        </div>
      </el-card>
    </div>

    <!-- 编辑/新建弹窗 -->
    <el-dialog
      v-model="editorVisible"
      :title="editing.id ? '编辑公告' : '发布公告'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form :model="editing" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="editing.title" placeholder="公告标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="editing.content"
            type="textarea"
            :rows="10"
            placeholder="支持 Markdown 语法"
          />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="editing.pinned" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="current?.title || '公告详情'" width="640px">
      <div v-if="current" class="ann-detail">
        <div class="ann-detail__meta">
          <el-tag v-if="current.pinned === 1" type="danger" size="small">置顶</el-tag>
          <span>{{ current.createdByName || '匿名' }}</span>
          <span>{{ formatTime(current.createdAt) }}</span>
        </div>
        <div class="ann-detail__body" v-html="renderMarkdown(current.content)"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  listAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  type AnnouncementVO,
} from '@/api/announcement'

const userStore = useUserStore()
const loading = ref(false)
const list = ref<AnnouncementVO[]>([])
const editorVisible = ref(false)
const detailVisible = ref(false)
const saving = ref(false)
const editing = ref<{ id?: number; title: string; content: string; pinned: number }>({
  title: '', content: '', pinned: 0,
})
const current = ref<AnnouncementVO | null>(null)

async function loadList() {
  loading.value = true
  try {
    const res = await listAnnouncements(1, 50)
    list.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = { title: '', content: '', pinned: 0 }
  editorVisible.value = true
}

function openEdit(a: AnnouncementVO) {
  editing.value = { id: a.id, title: a.title, content: a.content, pinned: a.pinned }
  editorVisible.value = true
}

async function openDetail(a: AnnouncementVO) {
  current.value = a
  detailVisible.value = true
}

async function handleSave() {
  if (!editing.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!editing.value.content.trim()) {
    ElMessage.warning('请输入正文')
    return
  }
  saving.value = true
  try {
    if (editing.value.id) {
      await updateAnnouncement(editing.value.id, {
        title: editing.value.title,
        content: editing.value.content,
        pinned: editing.value.pinned,
      })
      ElMessage.success('已更新')
    } else {
      await createAnnouncement({
        title: editing.value.title,
        content: editing.value.content,
        pinned: editing.value.pinned,
      })
      ElMessage.success('已发布')
    }
    editorVisible.value = false
    await loadList()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

async function handleDelete(a: AnnouncementVO) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${a.title}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAnnouncement(a.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message || ''))
  }
}

function formatTime(s?: string): string {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  return d.toLocaleString('zh-CN', { hour12: false })
}

function stripMarkdown(s: string): string {
  return s.replace(/[#*`>\-\[\]\(\)!_~]/g, '').replace(/\s+/g, ' ').trim()
}

function renderMarkdown(s: string): string {
  // 简单 Markdown 渲染：标题 / 段落 / 代码块 / 粗体
  const escape = (str: string) => str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return s
    .split('\n')
    .map(line => {
      let l = escape(line)
      if (/^### /.test(l)) return `<h4>${l.slice(4)}</h4>`
      if (/^## /.test(l)) return `<h3>${l.slice(3)}</h3>`
      if (/^# /.test(l)) return `<h2>${l.slice(2)}</h2>`
      if (/^- /.test(l)) return `<li>${l.slice(2)}</li>`
      if (/^> /.test(l)) return `<blockquote>${l.slice(2)}</blockquote>`
      l = l.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      l = l.replace(/`(.+?)`/g, '<code>$1</code>')
      if (l.trim() === '') return '<br/>'
      return `<p>${l}</p>`
    })
    .join('')
}

onMounted(loadList)
</script>

<style scoped>
.ann-page { padding: 24px; max-width: 960px; margin: 0 auto; }
.ann-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ann-header h2 { margin: 0; }
.ann-header__actions { display: flex; gap: 8px; }
.ann-list { display: flex; flex-direction: column; gap: 12px; }
.ann-item { cursor: pointer; }
.ann-item__header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ann-item__title { font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.ann-item__meta { display: flex; gap: 16px; color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 8px; }
.ann-item__content { color: var(--el-text-color-regular); font-size: 14px; line-height: 1.6; }
.ann-item__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; border-top: 1px dashed var(--el-border-color-lighter); padding-top: 8px; }
.ann-detail__meta { display: flex; gap: 12px; color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 16px; }
.ann-detail__body { line-height: 1.7; }
.ann-detail__body :deep(h2) { font-size: 18px; margin: 12px 0 8px; }
.ann-detail__body :deep(h3) { font-size: 16px; margin: 10px 0 6px; }
.ann-detail__body :deep(h4) { font-size: 14px; margin: 8px 0 4px; }
.ann-detail__body :deep(blockquote) { border-left: 3px solid var(--el-color-primary); padding-left: 12px; color: var(--el-text-color-secondary); margin: 8px 0; }
.ann-detail__body :deep(code) { background: var(--el-fill-color-light); padding: 2px 6px; border-radius: 4px; font-family: 'Consolas', monospace; }
.ann-detail__body :deep(li) { margin-left: 20px; }
</style>
