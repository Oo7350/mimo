import request from "./request";

export function getBoard(projectId: number) {
  return request.get(`/board/${projectId}`);
}

export function createColumn(data: { projectId: number; name: string; color?: string; sortOrder?: number }) {
  return request.post("/board/column", data);
}

export function updateColumn(data: { id: number; name?: string; color?: string; sortOrder?: number }) {
  return request.put("/board/column", data);
}

export function deleteColumn(id: number) {
  return request.delete(`/board/column/${id}`);
}

export function sortColumns(columnIds: number[]) {
  return request.put("/board/column/sort", { columnIds });
}

export function moveIssue(data: { issueId: number; targetColumnId: number; sortOrder: number }) {
  return request.put("/board/issue/move", data);
}
