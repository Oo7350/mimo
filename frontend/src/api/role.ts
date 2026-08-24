import request from "./request";

export interface PermissionVO {
  id: number;
  code: string;
  name: string;
  module: string;
  description?: string;
  sortOrder: number;
}

export interface RoleVO {
  id: number;
  name: string;
  code: string;
  isSystem: number;
  description?: string;
  sortOrder: number;
  createdAt: string;
  permissions: string[];
}

export interface SaveRoleRequest {
  id?: number;
  name: string;
  code: string;
  description?: string;
  sortOrder?: number;
  permissions: string[];
}

export interface AssignRoleRequest {
  userId: number;
  roleId: number;
  scopeType: "GLOBAL" | "TEAM" | "PROJECT";
  scopeId?: number | null;
}

export interface UserRoleAssignmentVO {
  userRoleId: number;
  userId: number;
  id: number;          // role id
  name: string;
  code: string;
  isSystem: number;
  description?: string;
  scopeType: "GLOBAL" | "TEAM" | "PROJECT";
  scopeId: number | null;
  assignedAt: string;
}

/** 列出所有角色 */
export function listRoles() {
  return request.get<RoleVO[]>("/role");
}

export function getRole(id: number) {
  return request.get<RoleVO>(`/role/${id}`);
}

/** 列出所有权限点 */
export function listPermissions() {
  return request.get<PermissionVO[]>("/role/permissions");
}

export function saveRole(data: SaveRoleRequest) {
  return request.post<RoleVO>("/role", data);
}

export function deleteRole(id: number) {
  return request.delete(`/role/${id}`);
}

export function assignRole(data: AssignRoleRequest) {
  return request.post("/role/assign", data);
}

export function revokeRole(
  userId: number,
  roleId: number,
  scopeType: string = "GLOBAL",
  scopeId?: number | null
) {
  return request.delete("/role/assign", {
    params: { userId, roleId, scopeType, scopeId },
  });
}

/** 查询用户拥有的角色（仅 role 维度） */
export function listUserRoles(userId: number) {
  return request.get<RoleVO[]>(`/role/user/${userId}`);
}

/** 查询用户的所有角色分配记录（含 scope 信息） */
export function listUserAssignments(userId: number) {
  return request.get<UserRoleAssignmentVO[]>(`/role/user/${userId}/assignments`);
}

/** 当前登录用户的权限编码列表 */
export function getMyPermissions() {
  return request.get<string[]>("/role/me/permissions");
}
