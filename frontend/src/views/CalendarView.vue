<template>
  <div class="calendar-page">
    <!-- Header -->
    <div class="calendar-header">
      <div class="calendar-header__left">
        <h2>日历</h2>
        <div class="calendar-nav">
          <el-button text @click="goToday">今天</el-button>
          <el-button-group>
            <el-button :icon="ArrowLeft" @click="prev" />
            <el-button :icon="ArrowRight" @click="next" />
          </el-button-group>
          <span class="calendar-nav__title">{{ currentTitle }}</span>
        </div>
      </div>
      <div class="calendar-header__right">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="month">月</el-radio-button>
          <el-radio-button value="week">周</el-radio-button>
          <el-radio-button value="day">日</el-radio-button>
        </el-radio-group>
        <el-select v-model="filterProjectId" placeholder="全部项目" clearable size="small" style="width:160px">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新建事件
        </el-button>
      </div>
    </div>

    <!-- Month View -->
    <div v-if="viewMode === 'month'" class="calendar-body">
      <div class="month-grid">
        <div class="month-grid__header">
          <span v-for="d in weekDays" :key="d">{{ d }}</span>
        </div>
        <div class="month-grid__body">
          <div
            v-for="(cell, idx) in monthCells"
            :key="idx"
            class="month-cell"
            :class="{ 'month-cell--other': !cell.isCurrentMonth, 'month-cell--today': cell.isToday }"
            @click="openCreateForDate(cell.date)"
          >
            <span class="month-cell__day">{{ cell.day }}</span>
            <div class="month-cell__events">
              <div
                v-for="evt in cell.events.slice(0, 3)"
                :key="evt.id"
                class="month-event-dot"
                :style="{ backgroundColor: evt.color }"
                :title="evt.title + (evt.allDay ? '' : ` ${formatTime(evt.startTime)}`)"
                @click.stop="openDetail(evt)"
              />
              <span v-if="cell.events.length > 3" class="month-cell__more">
                +{{ cell.events.length - 3 }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Week View -->
    <div v-else-if="viewMode === 'week'" class="calendar-body calendar-week">
      <div class="week-header">
        <div class="week-time-col"></div>
        <div v-for="d in weekDates" :key="d.key" class="week-day-header" :class="{ 'is-today': d.isToday }">
          <span class="week-day-name">{{ d.weekDay }}</span>
          <span class="week-day-num">{{ d.day }}</span>
        </div>
      </div>
      <div class="week-body" ref="weekBodyRef">
        <div class="week-time-col">
          <div v-for="h in 24" :key="h" class="hour-label">{{ formatHour(h - 1) }}</div>
        </div>
        <div v-for="d in weekDates" :key="d.key" class="week-day-col" :class="{ 'is-today': d.isToday }">
          <div v-for="h in 24" :key="h" class="hour-cell" @click="openCreateForDateTime(d.date, h - 1)" />
          <!-- Events -->
          <div
            v-for="evt in getWeekEvents(d.date)"
            :key="evt.id"
            class="week-event-bar"
            :class="{ 'all-day': evt.allDay }"
            :style="getWeekEventStyle(evt, d.date)"
            @click="openDetail(evt)"
          >
            {{ evt.title }}
          </div>
        </div>
      </div>
    </div>

    <!-- Day View -->
    <div v-else class="calendar-body calendar-day">
      <div class="day-header">
        <span class="day-date">{{ formatDate(currentDate) }} {{ weekDays[currentDate.getDay()] }}</span>
      </div>
      <div class="day-body">
        <div class="day-time-col">
          <div v-for="h in 24" :key="h" class="hour-label">{{ formatHour(h - 1) }}</div>
        </div>
        <div class="day-content">
          <div v-for="h in 24" :key="h" class="hour-cell" @click="openCreateForDateTime(currentDate, h - 1)" />
          <div
            v-for="evt in dayEvents"
            :key="evt.id"
            class="day-event-block"
            :style="getDayEventStyle(evt)"
            @click="openDetail(evt)"
          >
            <div class="day-event-block__color" :style="{ background: evt.color }"></div>
            <div class="day-event-block__info">
              <strong>{{ evt.title }}</strong>
              <span v-if="!evt.allDay">{{ formatTime(evt.startTime) }} - {{ formatTime(evt.endTime) }}</span>
              <span class="day-event-block__type">{{ eventTypeName(evt.eventType) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Event Form Dialog -->
    <EventForm
      v-model:visible="dialogVisible"
      :event="editingEvent"
      :default-date="defaultDate"
      :projects="projects"
      @saved="onSaved"
    />

    <!-- Event Detail Dialog -->
    <el-dialog v-model="detailVisible" :title="detailEvent?.title || '事件详情'" width="480px" destroy-on-close>
      <template v-if="detailEvent">
        <div class="event-detail">
          <div class="event-detail__row">
            <el-icon><Clock /></el-icon>
            <span>{{ detailEvent.allDay ? '全天' : `${formatTime(detailEvent.startTime)} - ${formatTime(detailEvent.endTime)}` }}</span>
            <span class="event-detail__date">{{ formatDate(detailEvent.startTime) }}</span>
          </div>
          <div v-if="detailEvent.description" class="event-detail__row">
            <el-icon><Document /></el-icon>
            <span>{{ detailEvent.description }}</span>
          </div>
          <div v-if="detailEvent.location" class="event-detail__row">
            <el-icon><Location /></el-icon>
            <span>{{ detailEvent.location }}</span>
          </div>
          <div class="event-detail__row">
            <el-icon><CollectionTag /></el-icon>
            <el-tag :color="detailEvent.color" effect="dark" size="small" style="color:#fff;border:none">
              {{ eventTypeName(detailEvent.eventType) }}
            </el-tag>
            <span v-if="detailEvent.projectName" class="event-detail__project">{{ detailEvent.projectName }}</span>
          </div>
          <div v-if="detailEvent.relatedType === 'ISSUE'" class="event-detail__row">
            <el-icon><Tickets /></el-icon>
            <span>关联任务: {{ detailEvent.relatedTitle }}</span>
          </div>
          <div v-if="detailEvent.participantNames?.length" class="event-detail__row">
            <el-icon><User /></el-icon>
            <span>{{ detailEvent.participantNames.join(', ') }}</span>
          </div>
          <div v-if="detailEvent.readonly" class="event-detail__readonly">
            <el-icon><InfoFilled /></el-icon>
            此为任务截止日期自动同步事件，请通过修改任务截止日期来更新
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detailEvent && !detailEvent.readonly" type="primary" @click="editFromDetail">编辑</el-button>
        <el-popconfirm v-if="detailEvent && !detailEvent.readonly" title="确定删除此事件？" @confirm="handleDelete(detailEvent!.id)">
          <template #reference>
            <el-button type="danger">删除</el-button>
          </template>
        </el-popconfirm>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ArrowLeft, ArrowRight, Plus, Clock, Document, Location, CollectionTag, Tickets, User, InfoFilled } from '@element-plus/icons-vue'
import { getCalendarEvents, deleteCalendarEvent, type CalendarEvent } from '@/api/calendar'
import { getProjects } from '@/api/project'
import EventForm from './EventForm.vue'

const viewMode = ref<'month' | 'week' | 'day'>('month')
const filterProjectId = ref<number | null>(null)
const currentDate = ref(new Date())
const events = ref<CalendarEvent[]>([])
const projects = ref<{ id: number; name: string }[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingEvent = ref<CalendarEvent | null>(null)
const detailEvent = ref<CalendarEvent | null>(null)
const defaultDate = ref<Date | null>(null)

const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

// ---- Title ----
const currentTitle = computed(() => {
  const y = currentDate.value.getFullYear()
  const m = currentDate.value.getMonth()
  if (viewMode.value === 'month') return `${y}年${m + 1}月`
  if (viewMode.value === 'week') {
    const startOfWeek = getWeekStart(currentDate.value)
    const endOfWeek = new Date(startOfWeek)
    endOfWeek.setDate(endOfWeek.getDate() + 6)
    return `${startOfWeek.getMonth()+1}/${startOfWeek.getDate()} - ${endOfWeek.getMonth()+1}/${endOfWeek.getDate()}`
  }
  return `${y}年${m + 1}月${currentDate.value.getDate()}日`
})

// ---- Navigation ----
function goToday() { currentDate.value = new Date(); fetchEvents() }
function prev() {
  if (viewMode.value === 'month') currentDate.value = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() - 1, 1)
  else if (viewMode.value === 'week') { const d = new Date(currentDate.value); d.setDate(d.getDate() - 7); currentDate.value = d }
  else { const d = new Date(currentDate.value); d.setDate(d.getDate() - 1); currentDate.value = d }
  fetchEvents()
}
function next() {
  if (viewMode.value === 'month') currentDate.value = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() + 1, 1)
  else if (viewMode.value === 'week') { const d = new Date(currentDate.value); d.setDate(d.getDate() + 7); currentDate.value = d }
  else { const d = new Date(currentDate.value); d.setDate(d.getDate() + 1); currentDate.value = d }
  fetchEvents()
}

// ---- Data fetching ----
async function fetchEvents() {
  const start = getViewStartDate()
  const end = getViewEndDate()
  try {
    const res = await getCalendarEvents(
      start.toISOString().replace('Z', ''),
      end.toISOString().replace('Z', ''),
      filterProjectId.value ?? undefined
    )
    events.value = res.data || []
  } catch {}
}

function getViewStartDate(): Date {
  if (viewMode.value === 'month') {
    const first = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth(), 1)
    const dow = first.getDay()
    first.setDate(first.getDate() - dow)
    return first
  }
  if (viewMode.value === 'week') return getWeekStart(currentDate.value)
  // day
  const d = new Date(currentDate.value)
  d.setHours(0, 0, 0, 0)
  return d
}

function getViewEndDate(): Date {
  if (viewMode.value === 'month') {
    const last = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() + 1, 0)
    const dow = last.getDay()
    last.setDate(last.getDate() + (6 - dow))
    last.setHours(23, 59, 59)
    return last
  }
  if (viewMode.value === 'week') {
    const start = getWeekStart(currentDate.value)
    const end = new Date(start)
    end.setDate(end.getDate() + 6)
    end.setHours(23, 59, 59)
    return end
  }
  const d = new Date(currentDate.value)
  d.setHours(23, 59, 59)
  return d
}

function getWeekStart(date: Date): Date {
  const d = new Date(date)
  const dow = d.getDay()
  d.setDate(d.getDate() - dow)
  d.setHours(0, 0, 0, 0)
  return d
}

// ---- Month View ----
interface MonthCell {
  date: Date
  day: number
  isCurrentMonth: boolean
  isToday: boolean
  events: CalendarEvent[]
}

const monthCells = computed((): MonthCell[] => {
  const cells: MonthCell[] = []
  const first = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth(), 1)
  const startDate = new Date(first)
  startDate.setDate(startDate.getDate() - startDate.getDay()) // Start from Sunday

  const today = new Date()
  today.setHours(0, 0, 0, 0)

  for (let i = 0; i < 42; i++) {
    const d = new Date(startDate)
    d.setDate(startDate.getDate() + i)
    const dStr = toDateString(d)
    cells.push({
      date: d,
      day: d.getDate(),
      isCurrentMonth: d.getMonth() === currentDate.value.getMonth(),
      isToday: toDateString(today) === dStr,
      events: events.value.filter(e => toDateString(parseISO(e.startTime)) === dStr),
    })
  }
  return cells
})

// ---- Week View ----
interface WeekDate {
  key: string
  date: Date
  day: number
  weekDay: string
  isToday: boolean
}

const weekDates = computed((): WeekDate[] => {
  const dates: WeekDate[] = []
  const start = getWeekStart(currentDate.value)
  const today = new Date()
  for (let i = 0; i < 7; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    dates.push({
      key: toDateString(d),
      date: d,
      day: d.getDate(),
      weekDay: weekDays[d.getDay()],
      isToday: toDateString(today) === toDateString(d),
    })
  }
  return dates
})

function getWeekEvents(date: Date): CalendarEvent[] {
  const dStr = toDateString(date)
  return events.value.filter(e => {
    const s = parseISO(e.startTime)
    return toDateString(s) === dStr && !e.allDay
  })
}

function getWeekEventStyle(evt: CalendarEvent, colDate: Date): Record<string, any> {
  const start = parseISO(evt.startTime)
  const end = parseISO(evt.endTime)
  const topPct = ((start.getHours() * 60 + start.getMinutes()) / (24 * 60)) * 100
  const durationMin = (end.getTime() - start.getTime()) / 60000
  const heightPct = Math.max((durationMin / (24 * 60)) * 100, 2.5)
  return {
    top: `${topPct}%`,
    height: `${heightPct}%`,
    backgroundColor: evt.color + '33',
    borderLeft: `3px solid ${evt.color}`,
  }
}

// ---- Day View ----
const dayEvents = computed(() => {
  const dStr = toDateString(currentDate.value)
  return events.value.filter(e => toDateString(parseISO(e.startTime)) === dStr)
})

function getDayEventStyle(evt: CalendarEvent): Record<string, any> {
  const start = parseISO(evt.startTime)
  const end = parseISO(evt.endTime)
  const topPct = ((start.getHours() * 60 + start.getMinutes()) / (24 * 60)) * 100
  const durationMin = (end.getTime() - start.getTime()) / 60000
  const heightPct = Math.max((durationMin / (24 * 60)) * 100, 4)
  return {
    top: `${topPct}%`,
    minHeight: `${heightPct}%`,
  }
}

// ---- Helpers ----
function toDateString(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function parseISO(s: string): Date {
  return new Date(s)
}

function formatTime(s: string): string {
  const d = new Date(s)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDate(s: string): string {
  const d = new Date(s)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function formatHour(h: number): string {
  return `${String(h).padStart(2, '0')}:00`
}

function eventTypeName(type: string): string {
  const map: Record<string, string> = {
    TASK_DEADLINE: '截止日期',
    MEETING: '会议',
    REMINDER: '提醒',
    SPRINT: 'Sprint',
    CUSTOM: '自定义',
  }
  return map[type] || type
}

// ---- Dialog actions ----
function openCreateDialog() {
  editingEvent.value = null
  defaultDate.value = new Date()
  dialogVisible.value = true
}

function openCreateForDate(date: Date) {
  editingEvent.value = null
  defaultDate.value = date
  dialogVisible.value = true
}

function openCreateForDateTime(date: Date, hour: number) {
  editingEvent.value = null
  defaultDate.value = new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour, 0)
  dialogVisible.value = true
}

function openDetail(evt: CalendarEvent) {
  detailEvent.value = evt
  detailVisible.value = true
}

function editFromDetail() {
  detailVisible.value = false
  editingEvent.value = detailEvent.value
  dialogVisible.value = true
}

async function handleDelete(id: number) {
  await deleteCalendarEvent(id)
  detailVisible.value = false
  fetchEvents()
}

function onSaved() {
  dialogVisible.value = false
  fetchEvents()
}

// ---- Lifecycle ----
watch([viewMode, filterProjectId], () => fetchEvents())
onMounted(async () => {
  const res = await getProjects()
  projects.value = res.data?.records || res.data || []
  fetchEvents()
})
</script>

<style scoped lang="scss">
.calendar-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px;
  overflow: hidden;
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;

  &__left {
    display: flex;
    align-items: center;
    gap: 16px;
    h2 { margin: 0; font-size: 22px; font-weight: 700; }
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.calendar-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  &__title { font-size: 15px; font-weight: 600; min-width: 140px; text-align: center; }
}

.calendar-body {
  flex: 1;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

// --- Month Grid ---
.month-grid {
  width: 100%;
  height: 100%;

  &__header {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    border-bottom: 1px solid #ebeef5;
    span {
      padding: 10px;
      text-align: center;
      font-size: 12px;
      font-weight: 600;
      color: #909399;
    }
  }

  &__body {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    grid-auto-rows: 1fr;
  }
}

.month-cell {
  border-right: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
  padding: 4px;
  cursor: pointer;
  min-height: 80px;
  position: relative;
  &:nth-child(7n) { border-right: none; }

  &--other { background: #fafafa; }
  &--today { background: #ecf5ff; }

  &__day {
    font-size: 13px;
    font-weight: 500;
    color: #303133;
    display: inline-block;
    width: 24px;
    height: 24px;
    line-height: 24px;
    text-align: center;
    border-radius: 50%;
  }
  &--today &__day {
    background: #409eff;
    color: #fff;
  }

  &__events {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
    margin-top: 2px;
  }

  &__more {
    font-size: 11px;
    color: #909399;
  }
}

.month-event-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  cursor: pointer;
  transition: transform 0.15s;
  &:hover { transform: scale(1.6); }
}

// --- Week View ---
.calendar-week {
  display: flex;
  flex-direction: column;
}

.week-header, .week-body {
  display: flex;
}

.week-time-col {
  width: 56px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
}

.week-day-header {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  border-right: 1px solid #f0f0f0;
  border-bottom: 1px solid #ebeef5;
  .week-day-name { font-size: 11px; color: #909399; display: block; }
  .week-day-num { font-size: 16px; font-weight: 600; }
  &.is-today { background: #ecf5ff; .week-day-num { color: #409eff; } }
}

.week-body {
  flex: 1;
  position: relative;
  overflow-y: auto;
}

.hour-label {
  height: 52px;
  font-size: 11px;
  color: #c0c4cc;
  text-align: right;
  padding-right: 4px;
  line-height: 52px;
}

.hour-cell {
  height: 52px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  &:hover { background: #f5f7fa; }
}

.week-day-col {
  flex: 1;
  position: relative;
  border-right: 1px solid #f0f0f0;
  &.is-today { background: rgba(64,158,255,0.02); }
}

.week-event-bar {
  position: absolute;
  left: 2px;
  right: 2px;
  padding: 2px 6px;
  font-size: 11px;
  border-radius: 3px;
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  z-index: 2;
  transition: opacity 0.15s;
  &:hover { opacity: 0.85; }
  &.all-day {
    top: 0;
    height: auto;
    background: none;
    border-left: none;
    color: inherit;
  }
}

// --- Day View ---
.calendar-day {
  display: flex;
  flex-direction: column;
}

.day-header {
  padding: 10px 16px;
  border-bottom: 1px solid #ebeef5;
  .day-date { font-size: 16px; font-weight: 600; }
}

.day-body {
  display: flex;
  flex: 1;
  overflow-y: auto;
}

.day-time-col {
  width: 56px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
}

.day-content {
  flex: 1;
  position: relative;
}

.day-event-block {
  position: absolute;
  left: 4px;
  right: 4px;
  display: flex;
  gap: 8px;
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  cursor: pointer;
  z-index: 2;
  transition: box-shadow 0.15s;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }

  &__color {
    width: 4px;
    border-radius: 2px;
    flex-shrink: 0;
  }

  &__info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    strong { font-size: 13px; }
    span { font-size: 11px; color: #909399; }
  }

  &__type {
    font-size: 11px;
    align-self: flex-start;
  }
}

// --- Event Detail ---
.event-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;

  &__row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    font-size: 14px;
    color: #606266;
    .el-icon { color: #909399; margin-top: 2px; }
  }

  &__date {
    margin-left: auto;
    font-size: 13px;
    color: #909399;
  }

  &__project {
    margin-left: 8px;
    font-size: 12px;
    color: #909399;
  }

  &__readonly {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 12px;
    background: #fdf6ec;
    border-radius: 6px;
    font-size: 13px;
    color: #e6a23c;
    .el-icon { color: #e6a23c; }
  }
}
</style>
