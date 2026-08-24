import request from "./request";

export interface AnnouncementVO {
  id: number;
  title: string;
  content: string;
  pinned: number;
  createdBy: number;
  createdByName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AnnouncementCreateReq {
  title: string;
  content: string;
  pinned?: number;
}

export function listAnnouncements(page = 1, size = 20) {
  return request.get("/announcements", { params: { page, size } });
}

export function getAnnouncement(id: number) {
  return request.get(`/announcements/${id}`);
}

export function createAnnouncement(data: AnnouncementCreateReq) {
  return request.post("/announcements", data);
}

export function updateAnnouncement(id: number, data: Partial<AnnouncementCreateReq>) {
  return request.put(`/announcements/${id}`, data);
}

export function deleteAnnouncement(id: number) {
  return request.delete(`/announcements/${id}`);
}
