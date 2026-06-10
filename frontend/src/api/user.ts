import request from "./request";

export interface LoginParams {
  username: string;
  password: string;
}

export interface RegisterParams {
  username: string;
  password: string;
  email: string;
}

export function login(data: LoginParams) {
  return request.post("/auth/login", data);
}

export function register(data: RegisterParams) {
  return request.post("/auth/register", data);
}

export function getUserProfile() {
  return request.get("/user/profile");
}

export function updateUserProfile(data: { username?: string; email?: string; avatar?: string }) {
  return request.put("/user/profile", data);
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put("/user/password", data);
}

export interface UserSearchResult {
  id: number
  username: string
  email: string
  avatar: string | null
}

export function searchUsers(keyword?: string) {
  return request.get<UserSearchResult[]>("/user/search", { params: { keyword } });
}
