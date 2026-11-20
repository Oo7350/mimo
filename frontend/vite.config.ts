import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { resolve } from "path";

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板里的 el-xxx 与 v-loading 等指令在使用处才 import，
    // 不再走 main.ts 的 app.use(ElementPlus) 全量注册（入口 chunk 1.25 MB → 见文档实测）。
    // importStyle: false —— 组件样式仍由 main.ts 统一引入 element-plus/dist/index.css，
    // 避免每个组件各自引入一份 style 造成重复。
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: "src/components.d.ts",
    }),
  ],
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
