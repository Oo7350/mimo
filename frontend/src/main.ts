import { createApp } from "vue";
import { createPinia } from "pinia";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
// Element Plus 组件改为按需引入（见 vite.config.ts 的 unplugin-vue-components）。
// 这里只保留全量样式：组件 style 由 importStyle:false 关闭，统一由此处加载。
import "element-plus/dist/index.css";
// Element Plus 暗色主题变量：需要 <html class="dark"> 才会生效（见 store/app.ts 的 applyTheme）
import "element-plus/theme-chalk/dark/css-vars.css";

import App from "./App.vue";
import router from "./router";
import "driver.js/dist/driver.css";
import "./styles/variables.css";
import "./styles/global.css";
import { useAppStore } from "./store/app";
import permissionDirective from "./directives/permission";

const app = createApp(App);

// 全局注册 Element Plus 图标。
// 部分页面（如 LevelManage / Profile）把图标名当字符串放进配置后用 <component :is="'Bell'" /> 渲染，
// 依赖运行时按名字解析，所以这里仍需全量注册；直接 import 的图标会被打包器 tree-shake。
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(createPinia());
app.use(router);

// 全局指令
app.directive("permission", permissionDirective);

useAppStore().initTheme();
app.mount("#app");
