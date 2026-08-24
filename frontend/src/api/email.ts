import request from "./request";

export interface EmailAccountVO {
  id: number;
  emailAddress: string;
  imapHost: string;
  imapPort: number;
  imapUsername: string;
  isDefault: number;
  lastSyncedAt?: string;
  createdAt: string;
  // v2.13.3：邮件通知偏好
  smtpHost?: string;
  smtpPort?: number;
  notifyTypes?: string;
  notifyEnabled: number;
}

export interface EmailPresetVO {
  provider: string;
  imapHost: string;
  imapPort: number;
  hint: string;
}

export interface BindEmailReq {
  emailAddress: string;
  imapHost: string;
  imapPort?: number;
  imapUsername: string;
  imapPassword: string;
  isDefault?: number;
}

/** v2.13.3：更新邮件通知偏好请求体 */
export interface NotifySettingsReq {
  enabled?: number;
  types?: string;
  smtpHost?: string;
  smtpPort?: number;
}

export interface MailSummaryVO {
  messageSeq: number;
  subject: string;
  from: string;
  fromPersonal?: string;
  sentDate?: string;
  snippet: string;
  seen: boolean;
  size: number;
}

export interface MailDetailVO {
  subject: string;
  from: string;
  fromPersonal?: string;
  to?: string;
  sentDate?: string;
  textBody?: string;
  htmlBody?: string;
}

export interface InboxVO {
  account?: EmailAccountVO;
  messages: MailSummaryVO[];
}

export function getEmailPresets() {
  return request.get("/email/presets");
}

export function listEmailAccounts() {
  return request.get("/email/accounts");
}

export function bindEmailAccount(data: BindEmailReq) {
  return request.post("/email/accounts", data);
}

export function unbindEmailAccount(id: number) {
  return request.delete(`/email/accounts/${id}`);
}

export function testEmailConnection(data: BindEmailReq) {
  return request.post("/email/accounts/test", data);
}

export function getInbox(accountId?: number, limit = 20) {
  // 邮件列表可能较慢，单独设置 30s 超时
  return request.get("/email/inbox", { params: { accountId, limit }, timeout: 30000 });
}

export function getMailDetail(accountId: number, seq: number) {
  return request.get(`/email/accounts/${accountId}/messages/${seq}`);
}

/** v2.13.3：更新邮件通知偏好 */
export function updateNotifySettings(accountId: number, data: NotifySettingsReq) {
  return request.put(`/email/accounts/${accountId}/notification-settings`, data);
}

/** v2.13.3：发送测试邮件 */
export function sendTestNotification(accountId: number): Promise<any> {
  // SMTP 发送可能较慢，单独设置 30s 超时
  return request.post(`/email/accounts/${accountId}/test-notification`, {}, { timeout: 30000 });
}
