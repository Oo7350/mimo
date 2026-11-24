<template>
  <!--
    管理员身份徽标（v2.19.8）。

    为什么单独做一个组件：以前"是不是管理员"在界面上完全没有痕迹 —— 顶栏只看得到用户名，
    管理员和普通成员长得一模一样，于是没人知道自己手里有哪些额外能力（全员任务流、验收、授权）。
    这里用一个可复用的小徽标把它标出来，尺寸可调，语言统一。

    设计：盾牌 = 权限，渐变描边 = 与普通标签区分，微光扫过只在 hover/首次入场时出现一次，
    不做常驻动画（满屏闪烁的管理界面属于噪音）。
  -->
  <span
    :class="['admin-badge', `admin-badge--${size}`, { 'admin-badge--quiet': quiet }]"
    :title="title"
  >
    <svg class="admin-badge__icon" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path
        d="M8 1.4 3 3.3v4.2c0 3 2 5.6 5 7.1 3-1.5 5-4.1 5-7.1V3.3L8 1.4Z"
        stroke="currentColor"
        stroke-width="1.3"
        stroke-linejoin="round"
      />
      <path d="m5.9 7.9 1.5 1.5 2.7-3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
    <span v-if="!iconOnly" class="admin-badge__text">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 徽标文字，默认「管理员」；团队管理员建议传「团队管理员」 */
    label?: string
    size?: 'small' | 'medium' | 'large'
    /** 只显示图标（顶栏窄屏、头像角标场景） */
    iconOnly?: boolean
    /** 低调模式：不带渐变底，只留描边色，适合列表里大量出现 */
    quiet?: boolean
  }>(),
  { label: '管理员', size: 'small', iconOnly: false, quiet: false },
)

const size = computed(() => props.size)
const label = computed(() => props.label)
const iconOnly = computed(() => props.iconOnly)
const quiet = computed(() => props.quiet)
const title = computed(() => `${label.value}：可以查看全员任务流、处理验收与审批`)
</script>

<style scoped lang="scss">
.admin-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  font-weight: 700;
  letter-spacing: 0.02em;
  white-space: nowrap;
  color: #7c2d12;
  background: linear-gradient(135deg, #fde68a, #fbbf24 55%, #f59e0b);
  border: 1px solid rgba(180, 83, 9, 0.35);
  box-shadow: 0 1px 2px rgba(180, 83, 9, 0.25);
  position: relative;
  overflow: hidden;

  /* 入场时一道微光扫过，然后停住 —— 只提示一次，不常驻 */
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(100deg, transparent 20%, rgba(255, 255, 255, 0.75) 50%, transparent 80%);
    transform: translateX(-120%);
    animation: admin-badge-sheen 1.1s ease-out 0.15s 1 forwards;
    pointer-events: none;
  }

  &__icon {
    width: 1em;
    height: 1em;
    flex-shrink: 0;
  }

  &--small {
    font-size: 12px;
    padding: 1px 7px 1px 5px;
  }

  &--medium {
    font-size: 12px;
    padding: 2px 9px 2px 6px;
  }

  &--large {
    font-size: 13px;
    padding: 4px 12px 4px 9px;
  }

  /* 低调模式：只在浅底上给个金色描边，不抢正文注意力。
     文字色用 --color-warning 而不是写死 #b45309 —— 暗色下那个值压在同色浅底上只有 2.6:1。 */
  &--quiet {
    background: var(--bg-warning-soft);
    border-color: rgba(180, 83, 9, 0.28);
    color: var(--color-warning);
    box-shadow: none;

    &::after { display: none; }
  }
}

@keyframes admin-badge-sheen {
  to {
    transform: translateX(120%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-badge::after {
    animation: none;
    display: none;
  }
}
</style>
