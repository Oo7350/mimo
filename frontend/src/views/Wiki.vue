<template>
  <div class="wiki">
    <!-- 左栏：目录树 -->
    <aside class="wiki__sidebar">
      <div class="wiki__sidebar-head">
        <el-input v-model="keyword" placeholder="搜索页面..." clearable size="small" @keyup.enter="doSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="small" @click="openCreateDialog()">
          <el-icon><Plus /></el-icon>新建
        </el-button>
      </div>

      <!-- 搜索结果 -->
      <div v-if="searching" class="wiki__search-results">
        <div v-for="r in searchResults" :key="r.id" class="wiki__search-item" @click="openPage(r.id)">
          <div class="wiki__search-title">{{ r.title }}</div>
          <div class="wiki__search-meta">v{{ r.version }} · {{ r.authorName || "-" }} · {{ formatTime(r.updatedAt) }}</div>
        </div>
        <el-empty v-if="searchResults.length === 0" description="无匹配结果" :image-size="60" />
        <div class="wiki__search-back" @click="clearSearch">
          <el-icon><ArrowLeft /></el-icon>返回目录
        </div>
      </div>

      <!-- 目录树 -->
      <el-scrollbar v-else class="wiki__tree-scroll">
        <el-tree
          :data="tree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :props="{ label: 'title', children: 'children' }"
          highlight-current
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="wiki__tree-node" @contextmenu.prevent.stop="openContextMenu($event, data)">
              <span class="wiki__tree-label">{{ data.title }}</span>
              <el-dropdown trigger="click" @command="(cmd: string) => handleTreeCommand(cmd, data as WikiTreeVO)">
                <el-icon class="wiki__tree-more"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="create">新建子页面</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除页面</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
      </el-scrollbar>
    </aside>

    <!-- 右栏：内容区 -->
    <main class="wiki__content">
      <el-empty v-if="!currentPage && !editing" description="从左侧选择一个页面，或点击「新建」创建第一篇文章">
        <el-button type="primary" @click="openCreateDialog()">
          <el-icon><Plus /></el-icon>新建页面
        </el-button>
      </el-empty>

      <template v-else>
        <!-- 查看模式 -->
        <template v-if="!editing && currentPage">
          <div class="wiki__page-head">
            <div>
              <h2 class="wiki__page-title">{{ currentPage.title }}</h2>
              <div class="wiki__page-meta">
                <span>版本 v{{ currentPage.version }}</span>
                <span v-if="currentPage.authorName">作者 {{ currentPage.authorName }}</span>
                <span v-if="currentPage.updatedAt">更新于 {{ formatTime(currentPage.updatedAt) }}</span>
                <span>浏览 {{ currentPage.viewCount }}</span>
              </div>
            </div>
            <div class="wiki__page-actions">
              <el-button size="small" @click="startEdit">
                <el-icon><Edit /></el-icon>编辑
              </el-button>
              <el-button size="small" @click="openVersionsDrawer">
                <el-icon><Clock /></el-icon>历史
              </el-button>
              <el-button size="small" @click="uploadRef?.click()">
                <el-icon><Upload /></el-icon>附件
              </el-button>
              <input ref="uploadRef" type="file" hidden @change="handleUpload" />
              <el-button size="small" type="danger" plain @click="handleDeletePage">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <div class="wiki__page-body markdown-body" v-html="renderedContent"></div>

          <!-- 附件区 -->
          <div v-if="attachments.length" class="wiki__attachments">
            <div class="wiki__attachments-title">
              <el-icon><Paperclip /></el-icon>附件 ({{ attachments.length }})
            </div>
            <div v-for="a in attachments" :key="a.id" class="wiki__attachment">
              <div class="wiki__attachment-info">
                <span class="wiki__attachment-name">{{ a.fileName }}</span>
                <span class="wiki__attachment-meta">{{ formatSize(a.fileSize) }} · {{ a.uploaderName || "-" }} · {{ formatTime(a.createdAt) }}</span>
              </div>
              <div class="wiki__attachment-actions">
                <el-button size="small" text type="primary" @click="downloadAttachment(a.id, a.fileName)">
                  <el-icon><Download /></el-icon>下载
                </el-button>
                <el-button size="small" text type="danger" @click="removeAttachment(a.id)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </template>

        <!-- 编辑模式 -->
        <div v-else-if="editing" class="wiki__editor">
          <el-input v-model="editForm.title" placeholder="页面标题" size="large" class="wiki__editor-title" />
          <div class="wiki__editor-tip">支持 Markdown 语法</div>
          <el-input
            v-model="editForm.content"
            type="textarea"
            :rows="18"
            placeholder="使用 Markdown 编写内容... 支持标题、列表、引用、表格、代码块"
          />
          <el-input v-model="editForm.changeSummary" placeholder="变更摘要（可选，会记录在版本历史中）" size="small" />
          <div class="wiki__editor-actions">
            <el-button type="primary" :loading="saving" @click="saveEdit">
              <el-icon><Check /></el-icon>保存
            </el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </div>
        </div>
      </template>
    </main>

    <!-- 新建页面弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建页面" width="420px" destroy-on-close>
      <el-form label-width="70px">
        <el-form-item label="父页面">
          <el-select v-model="createForm.parentId" clearable placeholder="无（顶级页面）" style="width: 100%">
            <el-option v-for="n in flatTree" :key="n.id" :label="'　'.repeat(depthMap[n.id] ?? 0) + n.title" :value="n.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" placeholder="页面标题" maxlength="200" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="createForm.content" type="textarea" :rows="5" placeholder="可选，创建后也可再编辑" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉 -->
    <el-drawer v-model="versionsDrawer" title="版本历史" size="400px">
      <div v-if="currentVersion" class="wiki__version-preview">
        <div class="wiki__version-preview-head">
          <span>v{{ currentVersion.version }} · {{ currentVersion.title }}</span>
          <div>
            <el-button size="small" @click="currentVersion = null">返回列表</el-button>
            <el-button size="small" type="primary" :loading="restoring" @click="handleRestore(currentVersion)">
              回滚到此版本
            </el-button>
          </div>
        </div>
        <div class="wiki__version-preview-meta">
          {{ currentVersion.editorName || "-" }} · {{ formatTime(currentVersion.createdAt) }}
          <span v-if="currentVersion.changeSummary"> · {{ currentVersion.changeSummary }}</span>
        </div>
        <div class="markdown-body" v-html="renderMarkdown(currentVersion.content)"></div>
      </div>
      <div v-else class="wiki__versions">
        <div v-for="v in versions" :key="v.id" class="wiki__version-item" @click="previewVersion(v)">
          <div class="wiki__version-item-title">v{{ v.version }} · {{ v.title }}</div>
          <div class="wiki__version-item-meta">
            {{ v.editorName || "-" }} · {{ formatTime(v.createdAt) }}
            <span v-if="v.changeSummary"> · {{ v.changeSummary }}</span>
          </div>
        </div>
        <el-empty v-if="versions.length === 0" description="暂无历史版本" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from "vue"
import { useRoute } from "vue-router"
import { ElMessage, ElMessageBox } from "element-plus"
import {
  Search,
  Plus,
  MoreFilled,
  Edit,
  Clock,
  Upload,
  Delete,
  Download,
  Paperclip,
  Check,
  ArrowLeft,
} from "@element-plus/icons-vue"
import {
  getWikiTree,
  getWikiPage,
  createWikiPage,
  updateWikiPage,
  deleteWikiPage,
  searchWiki,
  getWikiVersions,
  getWikiVersion,
  restoreWikiVersion,
  getWikiAttachments,
  uploadWikiAttachment,
  deleteWikiAttachment,
  downloadWikiAttachment,
  type WikiTreeVO,
  type WikiPageVO,
  type WikiVersionVO,
  type WikiAttachmentVO,
} from "@/api/wiki"
import { renderMarkdown } from "@/utils/markdown"

const route = useRoute()
const projectId = Number(route.params.id)

const tree = ref<WikiTreeVO[]>([])
const flatTree = ref<WikiTreeVO[]>([])
const depthMap = ref<Record<number, number>>({})
const currentPage = ref<WikiPageVO | null>(null)
const attachments = ref<WikiAttachmentVO[]>([])
const editing = ref(false)
const saving = ref(false)
const creating = ref(false)
const restoring = ref(false)
const keyword = ref("")
const searching = ref(false)
const searchResults = ref<WikiPageVO[]>([])
const createDialogVisible = ref(false)
const versionsDrawer = ref(false)
const versions = ref<WikiVersionVO[]>([])
const currentVersion = ref<WikiVersionVO | null>(null)
const uploadRef = ref<HTMLInputElement>()

const editForm = ref({ title: "", content: "", changeSummary: "" })
const createForm = ref<{ parentId: number | null; title: string; content: string }>({
  parentId: null,
  title: "",
  content: "",
})

const renderedContent = computed(() => renderMarkdown(currentPage.value?.content ?? ""))

async function loadTree() {
  const res = await getWikiTree(projectId)
  tree.value = res.data
  flatTree.value = []
  depthMap.value = {}
  const walk = (nodes: WikiTreeVO[], depth: number) => {
    for (const n of nodes) {
      flatTree.value.push(n)
      depthMap.value[n.id] = depth
      walk(n.children ?? [], depth + 1)
    }
  }
  walk(res.data, 0)
}

async function handleNodeClick(data: WikiTreeVO) {
  await openPage(data.id)
}

async function openPage(id: number) {
  currentPage.value = (await getWikiPage(id)).data
  editing.value = false
  attachments.value = (await getWikiAttachments(id)).data
}

function openCreateDialog(parent?: WikiTreeVO) {
  createForm.value = { parentId: parent?.id ?? null, title: "", content: "" }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.title.trim()) {
    ElMessage.warning("请输入页面标题")
    return
  }
  creating.value = true
  try {
    const page = (await createWikiPage({
      projectId,
      parentId: createForm.value.parentId,
      title: createForm.value.title.trim(),
      content: createForm.value.content,
    })).data
    createDialogVisible.value = false
    ElMessage.success("页面创建成功")
    await loadTree()
    await openPage(page.id)
  } finally {
    creating.value = false
  }
}

function startEdit() {
  if (!currentPage.value) return
  editForm.value = {
    title: currentPage.value.title,
    content: currentPage.value.content ?? "",
    changeSummary: "",
  }
  editing.value = true
}

async function saveEdit() {
  if (!currentPage.value) return
  if (!editForm.value.title.trim()) {
    ElMessage.warning("标题不能为空")
    return
  }
  saving.value = true
  try {
    const page = (await updateWikiPage(currentPage.value.id, {
      title: editForm.value.title.trim(),
      content: editForm.value.content,
      changeSummary: editForm.value.changeSummary || undefined,
    })).data
    ElMessage.success("已保存")
    editing.value = false
    await loadTree()
    await openPage(page.id)
  } finally {
    saving.value = false
  }
}

function cancelEdit() {
  editing.value = false
}

async function handleDeletePage() {
  if (!currentPage.value) return
  await ElMessageBox.confirm("删除后子页面与历史版本将一并删除，确定删除该页面？", "删除页面", {
    type: "warning",
    confirmButtonText: "删除",
    cancelButtonText: "取消",
  })
  await deleteWikiPage(currentPage.value.id)
  ElMessage.success("页面已删除")
  currentPage.value = null
  attachments.value = []
  await loadTree()
}

function handleTreeCommand(cmd: string, node: WikiTreeVO) {
  if (cmd === "create") openCreateDialog(node)
  if (cmd === "delete") {
    currentPage.value = { id: node.id } as WikiPageVO
    handleDeletePage()
  }
}

function openContextMenu(_e: Event, _data: WikiTreeVO) {
  // 右键已通过 dropdown 处理，此函数保留防默认菜单
}

// ============ 搜索 ============

async function doSearch() {
  if (!keyword.value.trim()) return
  searching.value = true
  searchResults.value = (await searchWiki(projectId, keyword.value.trim())).data
}

function clearSearch() {
  searching.value = false
  searchResults.value = []
  keyword.value = ""
}

// ============ 版本历史 ============

async function openVersionsDrawer() {
  if (!currentPage.value) return
  currentVersion.value = null
  versions.value = (await getWikiVersions(currentPage.value.id)).data
  versionsDrawer.value = true
}

async function previewVersion(v: WikiVersionVO) {
  if (!currentPage.value) return
  currentVersion.value = (await getWikiVersion(currentPage.value.id, v.version)).data
}

async function handleRestore(v: WikiVersionVO) {
  if (!currentPage.value) return
  await ElMessageBox.confirm(`确定回滚到版本 v${v.version}？当前内容会先保存为历史版本。`, "版本回滚", {
    type: "warning",
    confirmButtonText: "回滚",
    cancelButtonText: "取消",
  })
  restoring.value = true
  try {
    const page = (await restoreWikiVersion(currentPage.value.id, v.version)).data
    ElMessage.success("已回滚")
    versionsDrawer.value = false
    await loadTree()
    await openPage(page.id)
  } finally {
    restoring.value = false
  }
}

// ============ 附件 ============

async function handleUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ""
  if (!file || !currentPage.value) return
  const pageId = currentPage.value.id
  await uploadWikiAttachment(pageId, file)
  ElMessage.success("附件上传成功")
  attachments.value = (await getWikiAttachments(pageId)).data
}

async function downloadAttachment(id: number, fileName: string) {
  const blob = (await downloadWikiAttachment(id)).data
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
}

async function removeAttachment(id: number) {
  await ElMessageBox.confirm("确定删除该附件？", "删除附件", { type: "warning" })
  await deleteWikiAttachment(id)
  ElMessage.success("附件已删除")
  attachments.value = (await getWikiAttachments(id)).data
}

// ============ 工具 ============

function formatTime(t?: string) {
  if (!t) return "-"
  return t.replace("T", " ").slice(0, 16)
}

function formatSize(bytes?: number) {
  if (bytes == null) return "-"
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(async () => {
  await loadTree()
})
</script>

<style scoped>
.wiki {
  display: flex;
  height: calc(100vh - 130px);
  min-height: 480px;
  background: var(--el-bg-color);
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
}

/* 左栏 */
.wiki__sidebar {
  width: 280px;
  min-width: 280px;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
}

.wiki__sidebar-head {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.wiki__tree-scroll {
  flex: 1;
  padding: 8px;
}

.wiki__tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}

.wiki__tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki__tree-more {
  opacity: 0;
  transition: opacity 0.15s;
}

.wiki__tree-node:hover .wiki__tree-more {
  opacity: 1;
}

.wiki__search-results {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.wiki__search-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
}

.wiki__search-item:hover {
  background: var(--el-fill-color-light);
}

.wiki__search-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.wiki__search-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.wiki__search-back {
  padding: 8px 10px;
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 右栏 */
.wiki__content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

.wiki__page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 16px;
  margin-bottom: 20px;
}

.wiki__page-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--el-text-color-primary);
}

.wiki__page-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.wiki__page-body {
  line-height: 1.75;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

/* 附件 */
.wiki__attachments {
  margin-top: 28px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 16px;
}

.wiki__attachments-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}

.wiki__attachment {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
}

.wiki__attachment:hover {
  background: var(--el-fill-color-light);
}

.wiki__attachment-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.wiki__attachment-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 10px;
}

/* 编辑器 */
.wiki__editor-title {
  margin-bottom: 8px;
}

.wiki__editor-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.wiki__editor-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

/* 版本抽屉 */
.wiki__version-preview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}

.wiki__version-preview-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
}

.wiki__version-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.wiki__version-item:hover {
  border-color: var(--el-color-primary);
}

.wiki__version-item-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.wiki__version-item-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.markdown-body :deep(pre) {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.markdown-body :deep(code) {
  background: var(--el-fill-color-light);
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 0.92em;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--el-color-primary);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border-radius: 0 6px 6px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 6px 12px;
}

.markdown-body :deep(img) {
  max-width: 100%;
}
</style>
