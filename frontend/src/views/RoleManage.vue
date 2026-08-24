<template>
  <div class="role-manage">
    <PageHeader title="角色权限管理" subtitle="细粒度 RBAC — 用户/角色/权限三层模型" />

    <div class="role-manage__tabs">
      <el-tabs v-model="activeTab" class="role-tabs">
        <!-- 角色列表 -->
        <el-tab-pane label="角色列表" name="roles">
          <div class="role-toolbar">
            <el-input
              v-model="roleSearch"
              placeholder="搜索角色名称或编码"
              :prefix-icon="Search"
              clearable
              style="width: 280px"
            />
            <el-button
              v-permission="'role.manage'"
              type="primary"
              :icon="Plus"
              @click="openCreate"
            >
              新建角色
            </el-button>
          </div>

          <el-table
            :data="filteredRoles"
            v-loading="loading"
            stripe
            class="role-table"
            row-key="id"
          >
            <el-table-column label="角色" min-width="220">
              <template #default="{ row }">
                <div class="role-cell">
                  <div class="role-cell__avatar" :style="{ background: roleColor(row.code) }">
                    {{ row.name.charAt(0) }}
                  </div>
                  <div>
                    <div class="role-cell__name">
                      {{ row.name }}
                      <el-tag v-if="row.isSystem" type="warning" size="small" effect="plain">系统</el-tag>
                    </div>
                    <span class="role-cell__code">{{ row.code }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="描述" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="role-desc">{{ row.description || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="权限数" width="100" align="center">
              <template #default="{ row }">
                <el-tag type="info" effect="plain">{{ row.permissions.length }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170" align="center">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  size="small"
                  @click="openEdit(row)"
                  v-permission="'role.manage'"
                >编辑</el-button>
                <el-button
                  text
                  type="primary"
                  size="small"
                  @click="openPermDrawer(row)"
                >查看权限</el-button>
                <el-button
                  v-if="!row.isSystem"
                  v-permission="'role.manage'"
                  text
                  type="danger"
                  size="small"
                  @click="handleDelete(row)"
                >删除</el-button>
                <span v-else class="role-readonly">系统内置</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 权限点 -->
        <el-tab-pane label="权限点" name="permissions">
          <div class="perm-toolbar">
            <span class="perm-toolbar__count">共 {{ permissions.length }} 个权限点</span>
          </div>
          <div class="perm-groups">
            <div v-for="(perms, mod) in groupedPermissions" :key="mod" class="perm-group">
              <div class="perm-group__header">
                <h4>{{ moduleLabel(mod) }}</h4>
                <span class="perm-group__count">{{ perms.length }} 个</span>
              </div>
              <div class="perm-group__grid">
                <div v-for="p in perms" :key="p.code" class="perm-card">
                  <div class="perm-card__head">
                    <code class="perm-card__code">{{ p.code }}</code>
                    <span class="perm-card__name">{{ p.name }}</span>
                  </div>
                  <p class="perm-card__desc">{{ p.description || '无说明' }}</p>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 用户角色分配 -->
        <el-tab-pane label="用户角色分配" name="assign">
          <div class="assign-section">
            <!-- 用户搜索 -->
            <div class="assign-search">
              <el-select
                v-model="selectedUserId"
                filterable
                remote
                :remote-method="searchUser"
                :loading="userLoading"
                placeholder="搜索用户名/邮箱"
                style="width: 320px"
                @change="onUserChange"
              >
                <el-option
                  v-for="u in userOptions"
                  :key="u.id"
                  :label="`${u.username} (${u.email || '无邮箱'})`"
                  :value="u.id"
                />
              </el-select>
              <span v-if="selectedUser" class="assign-search__info">
                当前：{{ selectedUser.username }}
              </span>
            </div>

            <template v-if="selectedUserId">
              <!-- 已分配角色列表 -->
              <div class="assign-block">
                <div class="assign-block__header">
                  <h4>已分配角色</h4>
                  <span class="assign-block__count">{{ userRoleRows.length }} 条</span>
                </div>
                <el-table
                  :data="userRoleRows"
                  v-loading="userRoleLoading"
                  stripe
                  empty-text="该用户暂无任何角色分配"
                >
                  <el-table-column label="角色" min-width="160">
                    <template #default="{ row }">
                      <el-tag :type="row.isSystem ? 'warning' : 'info'" effect="plain">
                        {{ row.name }}
                      </el-tag>
                      <code class="assign-code">{{ row.code }}</code>
                    </template>
                  </el-table-column>
                  <el-table-column label="Scope" width="120">
                    <template #default="{ row }">
                      <el-tag :type="scopeTagType(row.scopeType)" size="small">
                        {{ row.scopeType }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="Scope ID" min-width="180" align="center">
                    <template #default="{ row }">
                      <span class="assign-scopeid">{{ scopeIdLabel(row) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="120" align="center">
                    <template #default="{ row }">
                      <el-button
                        text
                        type="danger"
                        size="small"
                        @click="handleRevoke(row)"
                      >撤销</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <!-- 分配新角色 -->
              <div class="assign-block">
                <div class="assign-block__header">
                  <h4>分配新角色</h4>
                </div>
                <el-form :model="newAssign" label-width="100px" class="assign-form">
                  <el-form-item label="角色">
                    <el-select
                      v-model="newAssign.roleId"
                      placeholder="选择角色"
                      style="width: 280px"
                    >
                      <el-option
                        v-for="r in roles"
                        :key="r.id"
                        :label="`${r.name} (${r.code})`"
                        :value="r.id"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="Scope 类型">
                    <el-radio-group v-model="newAssign.scopeType">
                      <el-radio-button label="GLOBAL">全局</el-radio-button>
                      <el-radio-button label="TEAM">团队</el-radio-button>
                      <el-radio-button label="PROJECT">项目</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item v-if="newAssign.scopeType !== 'GLOBAL'" :label="newAssign.scopeType === 'TEAM' ? '团队' : '项目'">
                    <el-select
                      v-model="newAssign.scopeId"
                      filterable
                      placeholder="请选择"
                      style="width: 320px"
                    >
                      <el-option
                        v-for="t in (newAssign.scopeType === 'TEAM' ? teamOptions : projectOptions)"
                        :key="t.id"
                        :label="`${t.name} (id=${t.id})`"
                        :value="t.id"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item>
                    <el-button
                      type="primary"
                      :loading="assigning"
                      :disabled="!canSubmitAssign"
                      @click="handleAssign"
                    >分配</el-button>
                    <span class="assign-form__tip">
                      说明：TEAM scope 仅对该组所属项目生效；PROJECT scope 仅对该项目生效。
                    </span>
                  </el-form-item>
                </el-form>
              </div>
            </template>
            <el-empty v-else description="请先搜索并选择一个用户" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 角色 编辑/新建 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing.id ? '编辑角色' : '新建角色'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form :model="editing" label-width="100px" label-position="right">
        <el-form-item label="角色名称" required>
          <el-input v-model="editing.name" placeholder="如：项目经理" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input
            v-model="editing.code"
            placeholder="大写英文，如 PROJECT_MANAGER"
            :disabled="!!editing.id && editing.isSystem === 1"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editing.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="权限分配">
          <div class="perm-selector">
            <el-checkbox-group v-model="editing.permissions">
              <div v-for="(perms, mod) in groupedAllPermissions" :key="mod" class="perm-group--select">
                <div class="perm-group__header">
                  <el-checkbox
                    :model-value="isGroupAllChecked(mod)"
                    :indeterminate="isGroupIndeterminate(mod)"
                    @change="toggleGroup(mod, $event)"
                  >{{ moduleLabel(mod) }}</el-checkbox>
                </div>
                <el-checkbox
                  v-for="p in perms"
                  :key="p.code"
                  :value="p.code"
                  class="perm-checkbox"
                >
                  <code>{{ p.code }}</code>
                  <span class="perm-checkbox__name">{{ p.name }}</span>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限查看抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`角色权限：${drawerRole?.name || ''}`"
      size="420px"
    >
      <div v-if="drawerRole" class="drawer-perms">
        <el-tag
          v-for="code in drawerRole.permissions"
          :key="code"
          class="drawer-perm-tag"
          type="info"
          effect="plain"
        >{{ code }}</el-tag>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus } from "@element-plus/icons-vue";
import PageHeader from "@/components/common/PageHeader.vue";
import {
  listRoles,
  listPermissions,
  saveRole,
  deleteRole,
  assignRole,
  revokeRole,
  listUserAssignments,
  type RoleVO,
  type PermissionVO,
  type SaveRoleRequest,
  type AssignRoleRequest,
  type UserRoleAssignmentVO,
} from "@/api/role";
import { searchUsers, type UserSearchResult } from "@/api/user";
import { getMyTeams } from "@/api/team";
import { getMyProjects } from "@/api/project";

interface IdName {
  id: number;
  name: string;
}

const activeTab = ref("roles");
const loading = ref(false);
const saving = ref(false);
const roleSearch = ref("");

const roles = ref<RoleVO[]>([]);
const permissions = ref<PermissionVO[]>([]);

const dialogVisible = ref(false);
const drawerVisible = ref(false);
const drawerRole = ref<RoleVO | null>(null);

const editing = ref<SaveRoleRequest & { isSystem?: number }>({
  id: undefined,
  name: "",
  code: "",
  description: "",
  permissions: [],
});

const filteredRoles = computed(() => {
  if (!roleSearch.value) return roles.value;
  const kw = roleSearch.value.toLowerCase();
  return roles.value.filter(
    (r) =>
      r.name.toLowerCase().includes(kw) ||
      r.code.toLowerCase().includes(kw)
  );
});

const groupedPermissions = computed(() => groupByModule(permissions.value));
const groupedAllPermissions = computed(() => groupByModule(permissions.value));

function groupByModule(list: PermissionVO[]): Record<string, PermissionVO[]> {
  const out: Record<string, PermissionVO[]> = {};
  for (const p of list) {
    if (!out[p.module]) out[p.module] = [];
    out[p.module].push(p);
  }
  return out;
}

function moduleLabel(mod: string): string {
  const map: Record<string, string> = {
    project: "项目",
    issue: "任务",
    sprint: "Sprint",
    board: "看板",
    report: "报告",
    team: "团队",
    system: "系统管理",
    wiki: "Wiki",
  };
  return map[mod] || mod;
}

function roleColor(code: string): string {
  const colors: Record<string, string> = {
    SUPER_ADMIN: "linear-gradient(135deg, #F56C6C, #E6A23C)",
    PROJECT_MANAGER: "linear-gradient(135deg, #409EFF, #67C23A)",
    PRODUCT_OWNER: "linear-gradient(135deg, #67C23A, #95D475)",
    DEVELOPER: "linear-gradient(135deg, #909399, #C0C4CC)",
    TESTER: "linear-gradient(135deg, #E6A23C, #F56C6C)",
    VIEWER: "linear-gradient(135deg, #C0C4CC, #E4E7ED)",
    REPORTER: "linear-gradient(135deg, #9B59B6, #8E44AD)",
  };
  return colors[code] || "linear-gradient(135deg, #409EFF, #67C23A)";
}

function formatDate(iso?: string): string {
  if (!iso) return "-";
  return new Date(iso).toLocaleString("zh-CN", { hour12: false });
}

// 分组全选/半选
function isGroupAllChecked(mod: string): boolean {
  const perms = groupedAllPermissions.value[mod] || [];
  return perms.every((p) => editing.value.permissions.includes(p.code));
}
function isGroupIndeterminate(mod: string): boolean {
  const perms = groupedAllPermissions.value[mod] || [];
  const checked = perms.filter((p) => editing.value.permissions.includes(p.code));
  return checked.length > 0 && checked.length < perms.length;
}
function toggleGroup(mod: string, checked: boolean) {
  const perms = groupedAllPermissions.value[mod] || [];
  const codes = perms.map((p) => p.code);
  if (checked) {
    const set = new Set([...editing.value.permissions, ...codes]);
    editing.value.permissions = [...set];
  } else {
    editing.value.permissions = editing.value.permissions.filter(
      (c) => !codes.includes(c)
    );
  }
}

async function load() {
  loading.value = true;
  try {
    const [r, p] = await Promise.all([listRoles(), listPermissions()]);
    roles.value = r.data;
    permissions.value = p.data;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = {
    id: undefined,
    name: "",
    code: "",
    description: "",
    permissions: [],
  };
  dialogVisible.value = true;
}

function openEdit(row: RoleVO) {
  editing.value = {
    id: row.id,
    name: row.name,
    code: row.code,
    description: row.description || "",
    sortOrder: row.sortOrder,
    permissions: [...row.permissions],
    isSystem: row.isSystem,
  };
  dialogVisible.value = true;
}

async function handleSave() {
  if (!editing.value.name || !editing.value.code) {
    ElMessage.warning("请填写名称和编码");
    return;
  }
  saving.value = true;
  try {
    await saveRole(editing.value);
    ElMessage.success("保存成功");
    dialogVisible.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: RoleVO) {
  await ElMessageBox.confirm(
    `确定删除角色「${row.name}」？`,
    "删除确认",
    { type: "warning" }
  );
  await deleteRole(row.id);
  ElMessage.success("删除成功");
  load();
}

function openPermDrawer(row: RoleVO) {
  drawerRole.value = row;
  drawerVisible.value = true;
}

// ==================== 用户角色分配 ====================
const selectedUserId = ref<number | null>(null);
const selectedUser = ref<UserSearchResult | null>(null);
const userOptions = ref<UserSearchResult[]>([]);
const userLoading = ref(false);
const userRoleRows = ref<UserRoleAssignmentVO[]>([]);
const userRoleLoading = ref(false);
const assigning = ref(false);

const teamOptions = ref<IdName[]>([]);
const projectOptions = ref<IdName[]>([]);

const newAssign = ref<AssignRoleRequest>({
  userId: 0,
  roleId: undefined as unknown as number,
  scopeType: "GLOBAL",
  scopeId: null,
});

const canSubmitAssign = computed(() => {
  if (!selectedUserId.value || !newAssign.value.roleId) return false;
  if (newAssign.value.scopeType !== "GLOBAL" && !newAssign.value.scopeId) return false;
  return true;
});

function scopeTagType(scope: string): "primary" | "success" | "warning" | "info" {
  return scope === "PROJECT" ? "warning" : scope === "TEAM" ? "success" : "primary";
}

function scopeIdLabel(row: UserRoleAssignmentVO): string {
  if (row.scopeType === "GLOBAL" || row.scopeId == null) return "—";
  if (row.scopeType === "TEAM") {
    const t = teamOptions.value.find((x) => x.id === row.scopeId);
    return t ? `${t.name} (#${row.scopeId})` : `#${row.scopeId}`;
  }
  const p = projectOptions.value.find((x) => x.id === row.scopeId);
  return p ? `${p.name} (#${row.scopeId})` : `#${row.scopeId}`;
}

async function searchUser(kw: string) {
  if (!kw) {
    userOptions.value = [];
    return;
  }
  userLoading.value = true;
  try {
    const res = await searchUsers(kw);
    userOptions.value = res.data || [];
  } finally {
    userLoading.value = false;
  }
}

async function onUserChange(userId: number | null) {
  selectedUser.value = userOptions.value.find((u) => u.id === userId) || null;
  userRoleRows.value = [];
  if (!userId) return;
  userRoleLoading.value = true;
  try {
    const res = await listUserAssignments(userId);
    userRoleRows.value = res.data || [];
  } finally {
    userRoleLoading.value = false;
  }
}

async function loadScopeOptions() {
  try {
    const [t, p] = await Promise.all([getMyTeams(), getMyProjects()]);
    teamOptions.value = ((t.data as IdName[]) || []).map((x: IdName) => ({ id: x.id, name: x.name }));
    projectOptions.value = ((p.data as IdName[]) || []).map((x: IdName) => ({ id: x.id, name: x.name }));
  } catch {
    /* 静默 */
  }
}

async function handleAssign() {
  if (!selectedUserId.value) return;
  assigning.value = true;
  try {
    await assignRole({
      userId: selectedUserId.value,
      roleId: newAssign.value.roleId,
      scopeType: newAssign.value.scopeType,
      scopeId: newAssign.value.scopeType === "GLOBAL" ? null : newAssign.value.scopeId,
    });
    ElMessage.success("分配成功");
    newAssign.value.roleId = undefined as unknown as number;
    newAssign.value.scopeId = null;
    onUserChange(selectedUserId.value);
  } finally {
    assigning.value = false;
  }
}

async function handleRevoke(row: UserRoleAssignmentVO) {
  if (!selectedUserId.value) return;
  await ElMessageBox.confirm(
    `撤销「${row.name}」(${row.scopeType}${row.scopeId ? "/" + row.scopeId : ""}) 角色？`,
    "撤销确认",
    { type: "warning" }
  );
  await revokeRole(
    selectedUserId.value,
    row.id,
    row.scopeType,
    row.scopeId
  );
  ElMessage.success("已撤销");
  onUserChange(selectedUserId.value);
}

onMounted(async () => {
  await load();
  loadScopeOptions();
});
</script>

<style scoped>
.role-manage {
  padding: 24px;
}
.role-manage__tabs {
  margin-top: 16px;
}
.role-toolbar,
.perm-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.role-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.role-cell__avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 18px;
}
.role-cell__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.role-cell__code {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}
.role-readonly {
  font-size: 12px;
  color: #909399;
}
.perm-groups {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.perm-group {
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: 12px;
  padding: 16px;
}
.perm-group__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.perm-group__header h4 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}
.perm-group__count {
  font-size: 12px;
  color: #909399;
}
.perm-group__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.perm-card {
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  padding: 12px;
}
.perm-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.perm-card__code {
  font-family: monospace;
  font-size: 13px;
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}
.perm-card__name {
  font-weight: 600;
  font-size: 13px;
}
.perm-card__desc {
  margin: 0;
  font-size: 12px;
  color: #909399;
}
.perm-selector {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  padding: 16px;
  width: 100%;
}
.perm-group--select {
  margin-bottom: 16px;
}
.perm-checkbox {
  display: flex;
  margin: 6px 0;
  width: 100%;
}
.perm-checkbox__name {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
.drawer-perms {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.drawer-perm-tag {
  font-family: monospace;
}
.assign-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin-top: 8px;
}
.assign-search {
  display: flex;
  align-items: center;
  gap: 16px;
}
.assign-search__info {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}
.assign-block {
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: 12px;
  padding: 16px 20px;
}
.assign-block__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.assign-block__header h4 {
  margin: 0;
  font-size: 15px;
}
.assign-block__count {
  font-size: 12px;
  color: #909399;
}
.assign-code {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}
.assign-scopeid {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}
.assign-form {
  max-width: 640px;
}
.assign-form__tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
