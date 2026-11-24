<template>
  <div class="empty-guide">
    <el-icon v-if="icon" class="empty-guide__icon"><component :is="icon" /></el-icon>
    <h4 v-if="title" class="empty-guide__title">{{ title }}</h4>
    <p v-if="reason" class="empty-guide__reason">{{ reason }}</p>
    <div v-if="actionLabel || secondaryLabel || $slots.actions" class="empty-guide__actions">
      <slot name="actions">
        <el-button v-if="actionLabel" type="primary" size="default" @click="$emit('action')">
          {{ actionLabel }}
        </el-button>
        <el-button v-if="secondaryLabel" text @click="$emit('secondary')">
          {{ secondaryLabel }}
        </el-button>
      </slot>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * 空状态引导（KNOWN-019）。
 *
 * 起因：多数空列表只有"暂无数据"四个字。新用户面对空页面不知道点哪里，
 * 于是"这功能好像没啥用"。这个组件的约定是——空状态必须说清三件事：
 *   1. 这里本来会出现什么（title）
 *   2. 为什么现在是空的 / 它有什么用（reason）
 *   3. 下一步该点哪里（action，给一个主按钮）
 *
 * 纯展示组件，不猜业务：给了按钮就 emit，由页面决定点完做什么。
 */
defineProps<{
  icon?: any
  title?: string
  reason?: string
  actionLabel?: string
  secondaryLabel?: string
}>()

defineEmits<{ action: []; secondary: [] }>()
</script>

<style scoped>
.empty-guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 36px 20px;
  text-align: center;
}

.empty-guide__icon {
  font-size: 40px;
  color: var(--text-muted);
  margin-bottom: 2px;
}

.empty-guide__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-guide__reason {
  margin: 0;
  max-width: 460px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.empty-guide__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
</style>
