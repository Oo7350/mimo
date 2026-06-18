<!--
  RippleButton — 点击涟漪按钮
  点击时从点击位置扩散圆形涟漪，克制、轻量、无障碍友好
-->
<template>
  <button class="ripple-btn" :class="[`ripple-btn--${variant}`, `ripple-btn--${size}`]" @click="onClick" :disabled="disabled">
    <slot />
  </button>
</template>

<script setup lang="ts">
interface Props {
  variant?: 'primary' | 'default' | 'success'
  size?: 'small' | 'default' | 'large'
  disabled?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'default',
  disabled: false,
})

function onClick(e: MouseEvent) {
  const btn = e.currentTarget as HTMLElement
  const rect = btn.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const size = Math.max(rect.width, rect.height)

  const ripple = document.createElement('span')
  ripple.className = 'ripple-effect'
  ripple.style.cssText = `
    width: ${size}px;
    height: ${size}px;
    left: ${x - size / 2}px;
    top: ${y - size / 2}px;
  `
  btn.appendChild(ripple)
  ripple.addEventListener('animationend', () => ripple.remove())
}
</script>

<style scoped lang="scss">
.ripple-btn {
  position: relative;
  overflow: hidden;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  font-weight: 500;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;

  /* ===== 尺寸 ===== */
  &--small {
    padding: 6px 14px;
    font-size: 13px;
  }
  &--default {
    padding: 9px 20px;
    font-size: 14px;
  }
  &--large {
    padding: 12px 28px;
    font-size: 15px;
  }

  /* ===== 变体 ===== */
  &--primary {
    background: #4f46e5;
    color: #fff;
    &:hover { background: #4338ca; }
    &:active { background: #3730a3; transform: scale(0.98); }
    ::v-deep(.ripple-effect) { background: rgba(255,255,255,0.25); }
  }
  &--default {
    background: #f1f5f9;
    color: #334155;
    &:hover { background: #e2e8f0; }
    &:active { background: #cbd5e1; transform: scale(0.98); }
    ::v-deep(.ripple-effect) { background: rgba(0,0,0,0.08); }
  }
  &--success {
    background: #10b981;
    color: #fff;
    &:hover { background: #059669; }
    &:active { background: #047857; transform: scale(0.98); }
    ::v-deep(.ripple-effect) { background: rgba(255,255,255,0.2); }
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none !important;
  }
}

:deep(.ripple-effect) {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  animation: ripple-wave 0.5s ease-out forwards;
  transform: scale(0);
}

@keyframes ripple-wave {
  to {
    transform: scale(2.5);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.ripple-effect) { animation: none; opacity: 0; }
  .ripple-btn:active { transform: none !important; }
}
</style>
