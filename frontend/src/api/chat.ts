import request from "./request";

export function sendChatMessage(data: { teamId: number; content: string }) {
  return request.post("/chat/send", data);
}

export function getChatHistory(teamId: number) {
  return request.get(`/chat/history/${teamId}`);
}

export function recallMessage(messageId: number) {
  return request.put(`/chat/recall/${messageId}`);
}
