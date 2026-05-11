import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";

const request = axios.create({
  baseURL: "/api",
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

// 请求拦截器 - 加 JWT
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // 文件下载等 blob 响应直接返回原始 response
    if (response.config.responseType === "blob") {
      return response;
    }
    const res = response.data;
    if (res.code !== 200) {
      ElMessage.error(res.message || "请求失败");
      return Promise.reject(new Error(res.message));
    }
    return res;
  },
  (error) => {
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        localStorage.removeItem("token");
        router.push("/login");
        ElMessage.error("登录已过期，请重新登录");
      } else if (status === 403) {
        ElMessage.error("无权限操作");
      } else if (status >= 500) {
        ElMessage.error("服务器错误");
      }
    } else {
      ElMessage.error("网络连接异常");
    }
    return Promise.reject(error);
  }
);

export default request;
