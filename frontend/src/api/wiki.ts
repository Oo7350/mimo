import request from "./request"

export interface WikiTreeVO {
  id: number
  projectId: number
  parentId: number | null
  title: string
  version: number
  authorName?: string
  editorName?: string
  isPinned: number
  viewCount: number
  sortOrder: number
  updatedAt?: string
  children: WikiTreeVO[]
}

export interface WikiPageVO {
  id: number
  projectId: number
  parentId: number | null
  title: string
  content?: string
  version: number
  authorId: number
  authorName?: string
  editorId?: number
  editorName?: string
  isPinned: number
  viewCount: number
  sortOrder: number
  createdAt?: string
  updatedAt?: string
}

export interface WikiVersionVO {
  id: number
  pageId: number
  version: number
  title: string
  content: string
  changeSummary?: string
  editorName?: string
  createdAt?: string
}

export interface WikiAttachmentVO {
  id: number
  pageId: number
  fileName: string
  fileSize: number
  mimeType?: string
  uploaderName?: string
  createdAt?: string
}

export interface WikiCreateRequest {
  projectId: number
  parentId?: number | null
  title: string
  content?: string
  changeSummary?: string
}

export interface WikiUpdateRequest {
  parentId?: number | null
  title?: string
  content?: string
  changeSummary?: string
}

export function getWikiTree(projectId: number) {
  return request.get<WikiTreeVO[]>(`/wiki/tree?projectId=${projectId}`)
}

export function getWikiPage(id: number) {
  return request.get<WikiPageVO>(`/wiki/pages/${id}`)
}

export function createWikiPage(data: WikiCreateRequest) {
  return request.post<WikiPageVO>("/wiki/pages", data)
}

export function updateWikiPage(id: number, data: WikiUpdateRequest) {
  return request.put<WikiPageVO>(`/wiki/pages/${id}`, data)
}

export function deleteWikiPage(id: number) {
  return request.delete(`/wiki/pages/${id}`)
}

export function searchWiki(projectId: number, q: string) {
  return request.get<WikiPageVO[]>(`/wiki/search?projectId=${projectId}&q=${encodeURIComponent(q)}`)
}

export function moveWikiPage(id: number, params: { parentId?: number | null; targetId?: number | null; position: "before" | "after" | "inner" }) {
  return request.put<WikiPageVO>(`/wiki/pages/${id}/move`, null, { params })
}

export function getWikiVersions(pageId: number) {
  return request.get<WikiVersionVO[]>(`/wiki/pages/${pageId}/versions`)
}

export function getWikiVersion(pageId: number, version: number) {
  return request.get<WikiVersionVO>(`/wiki/pages/${pageId}/versions/${version}`)
}

export function restoreWikiVersion(pageId: number, version: number) {
  return request.post<WikiPageVO>(`/wiki/pages/${pageId}/versions/${version}/restore`)
}

export function getWikiAttachments(pageId: number) {
  return request.get<WikiAttachmentVO[]>(`/wiki/pages/${pageId}/attachments`)
}

export function uploadWikiAttachment(pageId: number, file: File) {
  const form = new FormData()
  form.append("file", file)
  return request.post<WikiAttachmentVO>(`/wiki/pages/${pageId}/attachments`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  })
}

export function deleteWikiAttachment(attachmentId: number) {
  return request.delete(`/wiki/attachments/${attachmentId}`)
}

export function downloadWikiAttachment(attachmentId: number) {
  return request.get<Blob>(`/wiki/attachments/${attachmentId}/download`, { responseType: "blob" })
}
