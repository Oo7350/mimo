import request from "./request";

export function getNotifications(limit = 20) {
  return request.get("/notifications", { params: { limit } });
}

export function getUnreadCount() {
  return request.get("/notifications/unread-count");
}

export function markRead(id: number) {
  return request.put(`/notifications/${id}/read`);
}

export function markAllRead() {
  return request.put("/notifications/read-all");
}
