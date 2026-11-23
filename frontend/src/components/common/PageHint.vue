<template>
  <p class="page-hint">
    <el-icon v-if="icon" class="page-hint__icon"><component :is="icon" /></el-icon>
    <span><slot /></span>
  </p>
</template>

<script setup lang="ts">
/**
 * 一句话定位：这一页解决什么问题。
 *
 * 起因是一次真实反馈——"不知道一些功能有什么用，也没有提示"。
 * 很多页面只有标题（"Webhook 集成""操作审计日志"），没说明它在整条链路里是干什么的、
 * 什么时候该来这里。这个组件就是那小半行字，放在标题下面。
 */
defineProps<{ icon?: any }>()
</script>

<style scoped>
.page-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.page-hint__icon {
  margin-top: 3px;
  flex-shrink: 0;
}
</style>
