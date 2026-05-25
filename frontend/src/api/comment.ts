import request from "./request";

export function createComment(data: { issueId: number; content: string }) {
  return request.post("/comments", data);
}

export function getCommentsByIssue(issueId: number) {
  return request.get(`/comments/issue/${issueId}`);
}
