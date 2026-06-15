import request from "./request";

export function getDashboard() {
  return request.get("/dashboard");
}
