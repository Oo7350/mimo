<template>
  <!-- Element Plus 改为按需引入后不再有 app.use(ElementPlus, { locale })，
       中文语言包通过 ElConfigProvider 提供给内部组件（分页/日期选择器等） -->
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <transition name="page-fade" mode="out-in" appear>
        <!-- 不要按 route.path 加 key：那会让 MainLayout 每次路由跳转都整块重挂载，
             导致 WebSocket 重连、审批通知弹窗在每个页面重复弹一次 -->
        <component :is="Component" />
      </transition>
    </router-view>
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
</script>

<style>
/* 全局页面切换动画 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 减少动画偏好支持 */
@media (prefers-reduced-motion: reduce) {
  .page-fade-enter-active,
  .page-fade-leave-active {
    transition: opacity 0.1s ease;
  }
  .page-fade-enter-from,
  .page-fade-leave-to {
    transform: none;
  }
}
</style>
