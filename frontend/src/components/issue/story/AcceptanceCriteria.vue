<template>
  <div class="ac-wrapper">
    <div class="ac-header">
      <span class="ac-title">验收标准</span>
      <span class="ac-progress">{{ doneCount }}/{{ list.length }}</span>
    </div>
    <div class="ac-list">
      <div v-for="item in list" :key="item.id" class="ac-item" :class="{ 'ac-item--done': item.done }">
        <el-checkbox :model-value="item.done" @change="(v: boolean) => toggle(item.id, v)" />
        <span class="ac-item__text">{{ item.text }}</span>
        <el-button size="small" text type="danger" @click="remove(item.id)">
          <el-icon :size="14"><Close /></el-icon>
        </el-button>
      </div>
    </div>
    <div class="ac-input-row">
      <el-input
        v-model="newText"
        size="small"
        placeholder="新增验收标准..."
        @keyup.enter="add"
        :disabled="disabled"
      />
      <el-button size="small" @click="add" :disabled="!newText.trim() || disabled">
        <el-icon><Plus /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Close, Plus } from '@element-plus/icons-vue'
import type { AcceptanceCriterion } from '@/types'

const props = defineProps<{
  modelValue?: AcceptanceCriterion[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: AcceptanceCriterion[]]
  'add': [text: string]
  'toggle': [criteriaId: string, done: boolean]
  'remove': [criteriaId: string]
}>()

const newText = ref('')

const list = computed(() => props.modelValue || [])
const doneCount = computed(() => list.value.filter(a => a.done).length)

function add() {
  if (!newText.value.trim()) return
  emit('add', newText.value.trim())
  newText.value = ''
}

function toggle(id: string, done: boolean) {
  emit('toggle', id, done)
}

function remove(id: string) {
  emit('remove', id)
}
</script>

<style scoped lang="scss">
.ac-wrapper { margin-top: 4px; }
.ac-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.ac-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.ac-progress { font-size: 12px; color: var(--text-secondary); }
.ac-list { margin-bottom: 8px; }
.ac-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border-color-light);
  &--done { .ac-item__text { text-decoration: line-through; color: var(--text-placeholder); } }
  &__text { flex: 1; font-size: 13px; color: var(--text-primary); }
}
.ac-input-row { display: flex; gap: 6px; }
</style>
