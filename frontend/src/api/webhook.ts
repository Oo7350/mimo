import request from "./request";

/** Webhook 配置 */
export interface WebhookConfig {
  id?: number;
  projectId?: number | null;
  teamId?: number | null;
  name: string;
  url: string;
  secret?: string | null;
  events: string; // 逗号分隔：ISSUE_CREATED,ISSUE_UPDATED,...
  enabled?: number; // 1=启用 0=禁用
  createdBy?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 投递日志 */
export interface WebhookDeliveryLog {
  id: number;
  webhookId: number;
  eventType: string;
  payload?: string;
  statusCode?: number;
  responseBody?: string;
  success: number;
  durationMs?: number;
  error?: string;
  createdAt: string;
}

/** 可订阅事件类型 */
export const WEBHOOK_EVENTS = [
  { value: "ISSUE_CREATED", label: "任务创建" },
  { value: "ISSUE_UPDATED", label: "任务更新" },
  { value: "ISSUE_DELETED", label: "任务删除" },
  { value: "WEBHOOK_TEST", label: "测试投递（不通过订阅过滤）" },
];

export function listWebhooks(params: {
  page?: number;
  size?: number;
  projectId?: number;
  teamId?: number;
  includeDisabled?: boolean;
}) {
  return request.get("/webhooks", { params });
}

export function getWebhook(id: number) {
  return request.get(`/webhooks/${id}`);
}

export function createWebhook(data: WebhookConfig) {
  return request.post("/webhooks", data);
}

export function updateWebhook(id: number, data: Partial<WebhookConfig>) {
  return request.put(`/webhooks/${id}`, data);
}

export function deleteWebhook(id: number) {
  return request.delete(`/webhooks/${id}`);
}

/** 测试投递，立即返回投递结果 */
export function testWebhook(id: number) {
  return request.post(`/webhooks/${id}/test`);
}

/** 查询投递日志 */
export function listDeliveries(id: number, params?: { page?: number; size?: number }) {
  return request.get(`/webhooks/${id}/deliveries`, { params });
}
