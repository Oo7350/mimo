import request from "./request";

export function generateReport(data: { projectId: number; type?: string; reportDate?: string }) {
  return request.post("/reports/generate", data);
}

export function updateReport(data: { id: number; content: string }) {
  return request.put("/reports", data);
}

export function submitReport(id: number) {
  return request.put(`/reports/${id}/submit`);
}

export function getReports(params?: { projectId?: number; type?: string }) {
  return request.get("/reports", { params });
}

export function getReportById(id: number) {
  return request.get(`/reports/${id}`);
}

export function getProjectStats(projectId: number) {
  return request.get(`/reports/stats/${projectId}`);
}
