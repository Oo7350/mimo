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
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // SSE 流式响应不压缩，避免缓冲
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              delete proxyRes.headers['content-encoding']
            }
          })
        },
      },
    },
  },
});
