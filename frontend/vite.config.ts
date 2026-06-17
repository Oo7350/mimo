import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
    },
  },
  define: {
    global: "globalThis",
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        // SSE 需要禁用缓冲和压缩
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            // 对 SSE 请求禁用缓冲
            if (proxyReq.getHeader('accept')?.toString().includes('text/event-stream')) {
              proxyReq.setHeader('cache-control', 'no-cache')
            }
          })
          proxy.on('proxyRes', (proxyRes) => {
            // SSE 流式响应不压缩，避免缓冲
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              delete proxyRes.headers['content-encoding']
              delete proxyRes.headers['content-length']
            }
          })
          proxy.on('error', (err) => {
            console.error('[Vite Proxy] error:', err.message)
          })
        },
      },
    },
  },
});
