<template>
  <el-breadcrumb v-if="items.length > 1" separator="/" class="breadcrumb">
    <el-breadcrumb-item v-for="item in items" :key="item.path || item.label">
      <span
        v-if="item.path"
        class="breadcrumb__link"
        @click="$router.push(item.path)"
      >
        {{ item.label }}
      </span>
      <span v-else class="breadcrumb__current">{{ item.label }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

interface Crumb {
  label: string
  path?: string
}

const route = useRoute()

const ROUTE_LABELS: Record<string, string> = {
  '': '工作台',
  projects: '项目',
  board: '看板',
  reports: '报告',
  teams: '团队',
  profile: '个人信息',
}

const items = computed<Crumb[]>(() => {
  const crumbs: Crumb[] = []
  const parts = route.path.split('/').filter(Boolean)

  crumbs.push({ label: '工作台', path: '/' })

  if (parts[0] === 'teams') {
    crumbs.push({ label: '团队', path: '/teams' })
    if (parts[1]) {
      crumbs.push({ label: '团队详情', path: `/teams/${parts[1]}` })
    }
  } else if (parts[0] === 'projects' && parts[1]) {
    crumbs.push({ label: '项目', path: `/projects/${parts[1]}/board` })
    const sub = parts[2]
    if (sub === 'board') {
      crumbs.push({ label: '看板' })
    } else if (sub === 'reports') {
      crumbs.push({ label: '报告' })
    }
  } else if (parts[0] === 'profile') {
    crumbs.push({ label: '个人信息' })
  }

  return crumbs
})
</script>

<style scoped lang="scss">
.breadcrumb {
  &__link {
    cursor: pointer;
    font-weight: normal;
  }

  &__current {
    font-weight: 500;
  }
}
</style>
