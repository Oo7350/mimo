<template>
  <div
    :class="['avatar', { 'avatar--clickable': clickable }]"
    :style="avatarStyle"
    :title="name"
  >
    <span class="avatar__letter">{{ initial }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  size?: number
  clickable?: boolean
}>(), {
  size: 28,
  clickable: false,
})

const PALETTE = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C',
  '#5470C6', '#91CC75', '#FAC858', '#EE6666',
]

function hashColor(str: string): string {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

const initial = computed(() => props.name?.charAt(0)?.toUpperCase() || '?')
const bgColor = computed(() => hashColor(props.name))

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  backgroundColor: bgColor.value,
  fontSize: `${props.size * 0.42}px`,
  lineHeight: `${props.size}px`,
}))
</script>

<style scoped lang="scss">
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 600;
  user-select: none;
  flex-shrink: 0;

  &--clickable {
    cursor: pointer;
    transition: transform var(--transition-fast);
    &:hover {
      transform: scale(1.1);
    }
  }

  &__letter {
    display: block;
    text-align: center;
  }
}
</style>
