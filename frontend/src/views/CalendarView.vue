<template>
  <div class="calendar-page">
    <!-- ===== HEADER ===== -->
    <div class="cal-header">
      <div class="cal-header__left">
        <h2 class="cal-title">
          <ShinyText
            text="日历"
            color="#0f172a"
            shine-color="#06b6d4"
            :speed="3.5"
          />
        </h2>
        <div class="cal-nav">
          <el-button text size="small" @click="goToday">今天</el-button>
          <el-button-group size="small">
            <el-button :icon="ArrowLeft" @click="prev" />
            <el-button :icon="ArrowRight" @click="next" />
          </el-button-group>
          <span class="cal-nav__title">{{ currentTitle }}</span>
        </div>
      </div>
      <div class="cal-header__right">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="month">月</el-radio-button>
          <el-radio-button value="week">周</el-radio-button>
          <el-radio-button value="day">日</el-radio-button>
        </el-radio-group>
        <el-select v-model="filterTeamId" placeholder="全部团队" clearable size="small" style="width:120px" @change="onTeamFilterChange">
          <el-option v-for="t in teams" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filterEventType" placeholder="全部类型" clearable size="small" style="width:120px">
          <el-option label="会议" value="MEETING" />
          <el-option label="提醒" value="REMINDER" />
          <el-option label="截止日期" value="TASK_DEADLINE" />
          <el-option label="Sprint" value="SPRINT" />
          <el-option label="自定义" value="CUSTOM" />
        </el-select>
        <el-popover trigger="click" placement="bottom-end" :width="300" popper-class="notif-popover">
          <template #reference>
            <div class="notif-bell" :class="{ 'has-unread': unreadCount > 0 }">
              <el-icon :size="18"><Bell /></el-icon>
              <span v-if="unreadCount > 0" class="notif-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </div>
          </template>
          <div class="notif-dropdown">
            <div class="notif-dropdown__header">
              <strong>通知</strong>
              <el-button text size="small" type="primary" @click="markAllNotifsRead">全部已读</el-button>
            </div>
            <div v-if="notifications.length === 0" class="notif-empty">
              <el-icon><Bell /></el-icon><span>暂无通知</span>
            </div>
            <div v-else class="notif-list">
              <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }" @click="handleNotifClick(n)">
                <div class="notif-dot" :class="{ active: !n.isRead }"></div>
                <div class="notif-body">
                  <strong>{{ n.title }}</strong>
                  <p>{{ n.content }}</p>
                  <span class="notif-time">{{ formatNotifTime(n.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-popover>
        <RippleButton variant="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新建
        </RippleButton>
      </div>
    </div>

    <!-- ===== BODY ===== -->
    <div class="cal-body">
      <!-- LEFT SIDEBAR -->
      <div class="cal-sidebar">
        <div class="mini-cal">
          <div class="mini-cal__header">{{ miniCalYear }}年{{ miniCalMonth }}月</div>
          <div class="mini-cal__grid">
            <span v-for="d in ['日','一','二','三','四','五','六']" :key="d" class="mini-cal__wd">{{ d }}</span>
            <span v-for="(cell, i) in miniCalCells" :key="i" class="mini-cal__cell"
              :class="{ other: cell.otherMonth, today: cell.isToday, selected: isSelectedDate(cell.date) }"
              @click="jumpToDate(cell.date)">
              {{ cell.day }}
              <span v-if="cell.eventCount > 0 && !cell.otherMonth" class="mini-dot"></span>
            </span>
          </div>
        </div>
        <div class="today-summary">
          <h4>今日概览</h4>
          <div class="today-date-big">{{ todayStr.day }}<span>{{ todayStr.month }}</span></div>
          <p class="today-weekday">{{ todayStr.weekday }}</p>
          <div v-if="todayEvents.length > 0" class="today-event-list">
            <div v-for="evt in todayEvents.slice(0, 5)" :key="evt.id" class="today-event-chip" :style="{ borderLeftColor: evt.color }" @click="openDetail(evt)">
              <span class="chip-time">{{ evt.allDay ? '全天' : formatTime(evt.startTime) }}</span>
              <span class="chip-title">{{ evt.title }}</span>
            </div>
            <span v-if="todayEvents.length > 5" class="today-more">+{{ todayEvents.length - 5 }} 更多</span>
          </div>
          <div v-else class="today-empty"><el-icon><Calendar /></el-icon><span>今日暂无日程</span></div>
        </div>
      </div>

      <!-- CENTER: Calendar -->
      <div class="cal-main">
        <!-- MONTH VIEW -->
        <div v-if="viewMode === 'month'" class="view-month">
          <div class="month-grid">
            <div class="month-grid__header">
              <span v-for="d in weekDays" :key="d">{{ d }}</span>
            </div>
            <div class="month-grid__body">
              <div v-for="(cell, idx) in monthCells" :key="idx" class="month-cell"
                :class="{ other: !cell.isCurrentMonth, today: cell.isToday, selected: isDayPanelDate(cell.date) }"
                @click="openDayPanel(cell.date)">
                <span class="month-cell__num">{{ cell.day }}</span>
                <div class="month-cell__events">
                  <div v-for="evt in getVisibleCellEvents(cell.events)" :key="evt.id" class="month-event-bar"
                    :style="{ backgroundColor: evt.color }" @click.stop="openDetail(evt)">
                    {{ evt.title }}
                  </div>
                  <span v-if="cell.events.length > 3" class="month-more" @click.stop="openDayPanel(cell.date)">+{{ cell.events.length - 3 }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- WEEK VIEW -->
        <div v-else-if="viewMode === 'week'" class="view-week">
          <div class="week-grid">
            <div class="week-grid__header">
              <div class="time-col-head"></div>
              <div v-for="d in weekDates" :key="d.key" class="day-col-head"
                :class="{ isToday: d.isToday }" @click="openDayPanel(d.date)">
                <span class="dch-wd">{{ d.weekDay }}</span>
                <span class="dch-day">{{ d.day }}</span>
              </div>
            </div>
            <div class="week-grid__body">
              <div class="time-col">
                <div v-for="h in 24" :key="h" class="time-label">{{ pad(h-1) }}:00</div>
              </div>
              <div v-for="d in weekDates" :key="d.key" class="day-col" :class="{ isToday: d.isToday }">
                <div v-for="h in 24" :key="h" class="hour-slot" @click="openCreateForDateTime(d.date, h-1)" />
                <div v-for="evt in getWeekDayEvents(d.date)" :key="evt.id" class="week-event-block"
                  :style="getWeekEventStyle(evt)" @click="openDetail(evt)">
                  {{ evt.title }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- DAY VIEW -->
        <div v-else class="view-day">
          <div class="day-top-bar">
            <span class="dtb-date">{{ formatDateFull(currentDate) }}</span>
            <el-tag v-if="isToday(currentDate)" size="small" type="primary">今天</el-tag>
          </div>
          <div class="day-grid">
            <div class="time-col">
              <div v-for="h in 24" :key="h" class="time-label">{{ pad(h-1) }}:00</div>
            </div>
            <div class="day-content-col">
              <div v-for="h in 24" :key="h" class="hour-slot" @click="openCreateForDateTime(currentDate, h-1)" />
              <div v-for="evt in dayViewEvents" :key="evt.id" class="day-event-card" :style="getDayEventStyle(evt)" @click="openDetail(evt)">
                <div class="dec-color-bar" :style="{ background: evt.color }"></div>
                <div class="dec-body">
                  <strong>{{ evt.title }}</strong>
                  <span>{{ evt.allDay ? '全天' : `${formatTime(evt.startTime)} - ${formatTime(evt.endTime)}` }}</span>
                  <el-tag size="small" :type="eventTypeTagType(evt.eventType)">{{ eventTypeName(evt.eventType) }}</el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- RIGHT PANEL -->
      <transition name="panel-slide">
        <div v-if="dayPanelVisible" class="detail-panel">
          <div class="dp-header">
            <div class="dp-header__top">
              <h3>{{ panelInfo.weekday }}</h3>
              <el-button text circle :icon="Close" @click="closeDayPanel" />
            </div>
            <div class="dp-header__date">
              {{ panelInfo.fullDate }}
              <el-tag v-if="panelInfo.isToday" size="small" type="primary">今天</el-tag>
            </div>
          </div>
          <div class="dp-tip">
            <el-icon><Sunny /></el-icon>
            <div><strong>今日建议</strong><p>{{ panelInfo.tip }}</p></div>
          </div>
          <div class="dp-section">
            <div class="dp-section__head">
              <span>当日日程</span>
              <el-tag size="small" round>{{ panelEvents.length }}</el-tag>
            </div>
            <div v-if="panelEvents.length" class="dp-events">
              <div v-for="evt in panelEvents" :key="evt.id" class="dp-event-card" @click="openDetail(evt)">
                <div class="dpec-left" :style="{ background: evt.color }"></div>
                <div class="dpec-body">
                  <div class="dpec-row">
                    <strong>{{ evt.title }}</strong>
                    <el-tag size="small" :type="eventTypeTagType(evt.eventType)">{{ eventTypeName(evt.eventType) }}</el-tag>
                  </div>
                  <div class="dpec-meta"><el-icon><Clock /></el-icon><span>{{ evt.allDay ? '全天' : `${formatTime(evt.startTime)} - ${formatTime(evt.endTime)}` }}</span></div>
                  <div v-if="evt.teamName" class="dpec-meta"><el-icon><User /></el-icon><span>{{ evt.teamName }}</span></div>
                  <div v-if="evt.projectName" class="dpec-meta"><el-icon><CollectionTag /></el-icon><span>{{ evt.projectName }}</span></div>
                  <div v-if="evt.location" class="dpec-meta"><el-icon><Location /></el-icon><span>{{ evt.location }}</span></div>
                </div>
              </div>
            </div>
            <div v-else class="dp-empty"><el-icon><Calendar /></el-icon><span>当天没有安排</span></div>
          </div>
          <div class="dp-actions">
            <el-button type="primary" class="dp-btn" @click="openCreateFromPanel('CUSTOM')"><el-icon><Plus /></el-icon> 新增日程</el-button>
            <el-button class="dp-btn" @click="openCreateFromPanel('REMINDER')"><el-icon><EditPen /></el-icon> 新增待办</el-button>
          </div>
        </div>
      </transition>
    </div>

    <!-- EVENT FORM -->
    <EventForm v-model:visible="dialogVisible" :event="editingEvent" :default-date="defaultDate"
      :default-event-type="pendingEventType" :teams="teams" :projects="filteredProjects" @saved="onSaved" />

    <!-- EVENT DETAIL -->
    <el-dialog v-model="detailVisible" :title="detailEvent?.title || '事件详情'" width="520px" destroy-on-close>
      <template v-if="detailEvent">
        <div class="edl">
          <div class="edl__banner" :style="{ background: detailEvent.color + '15', borderLeftColor: detailEvent.color }">
            <el-tag :color="detailEvent.color" effect="dark" size="small" style="color:#fff;border:none">{{ eventTypeName(detailEvent.eventType) }}</el-tag>
            <span v-if="detailEvent.teamName" class="edl__tag">{{ detailEvent.teamName }}</span>
            <span v-if="detailEvent.projectName" class="edl__tag">{{ detailEvent.projectName }}</span>
          </div>
          <div class="edl__body">
            <div class="edl-row"><el-icon><Clock /></el-icon><span>{{ detailEvent.allDay ? '全天' : `${formatTime(detailEvent.startTime)} - ${formatTime(detailEvent.endTime)}` }}</span><span class="edl-date">{{ formatDate(detailEvent.startTime) }}</span></div>
            <div v-if="detailEvent.description" class="edl-row desc"><el-icon><Document /></el-icon><span>{{ detailEvent.description }}</span></div>
            <div v-if="detailEvent.location" class="edl-row"><el-icon><Location /></el-icon><span>{{ detailEvent.location }}</span></div>
            <div v-if="detailEvent.participantNames?.length" class="edl-row"><el-icon><User /></el-icon><span>{{ detailEvent.participantNames.join('、') }}</span></div>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detailEvent && !detailEvent.readonly" type="primary" @click="editFromDetail">编辑</el-button>
        <el-popconfirm v-if="detailEvent && !detailEvent.readonly" title="确定删除？" @confirm="handleDelete(detailEvent!.id)">
          <template #reference><el-button type="danger">删除</el-button></template>
        </el-popconfirm>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ArrowLeft, ArrowRight, Plus, Clock, Document, Location, CollectionTag, User, Close, Sunny, Calendar, EditPen, Bell } from '@element-plus/icons-vue'
import { getCalendarEvents, deleteCalendarEvent, type CalendarEvent } from '@/api/calendar'
import { getMyProjects, getProjectsByTeam } from '@/api/project'
import { getMyTeams } from '@/api/team'
import { getNotifications, markAllRead as apiMarkAllRead, createNotification, markRead } from '@/api/notification'
import EventForm from './EventForm.vue'
import ShinyText from '@/components/common/ShinyText.vue'
import RippleButton from '@/components/common/RippleButton.vue'

const viewMode = ref<'month'|'week'|'day'>('month')
const filterTeamId = ref<number|null>(null)
const filterEventType = ref<string|null>(null)
const currentDate = ref(new Date())
const events = ref<CalendarEvent[]>([])
const projects = ref<{id:number;name:string}[]>([])
const teams = ref<{id:number;name:string}[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingEvent = ref<CalendarEvent|null>(null)
const detailEvent = ref<CalendarEvent|null>(null)
const defaultDate = ref<Date|null>(null)
const pendingEventType = ref<string|undefined>()
const dayPanelVisible = ref(false)
const selectedDate = ref(new Date())
const notifications = ref<any[]>([])
const unreadCount = ref(0)
let notifTimer: ReturnType<typeof setInterval>|null = null
const weekDays = ['周日','周一','周二','周三','周四','周五','周六']

// Computed
const currentTitle = computed(() => {
  const y = currentDate.value.getFullYear(), m = currentDate.value.getMonth()
  if (viewMode.value==='month') return `${y}年${m+1}月`
  if (viewMode.value==='week') { const s=weekStart(currentDate.value),e=new Date(s);e.setDate(e.getDate()+6); return `${s.getMonth()+1}月${s.getDate()}日 - ${e.getMonth()+1}月${e.getDate()}日` }
  return `${y}年${m+1}月${currentDate.value.getDate()}日 ${weekDays[currentDate.value.getDay()]}`
})

const filteredProjects = computed(() => projects.value)

const todayStr = computed(() => { const n=new Date(); return { day:n.getDate(), month:n.getMonth()+1+'月', weekday:weekDays[n.getDay()] } })
const todayEvents = computed(() => { const ds=toDS(new Date()); return events.value.filter(e=>toDS(parseISO(e.startTime))===ds).sort((a,b)=>parseISO(a.startTime).getTime()-parseISO(b.startTime).getTime()) })

const miniCalYear = computed(()=>currentDate.value.getFullYear())
const miniCalMonth = computed(()=>currentDate.value.getMonth()+1)
const miniCalCells = computed(()=>{
  const cells:any[]=[], first=new Date(miniCalYear.value,miniCalMonth.value-1,1), start=new Date(first); start.setDate(start.getDate()-start.getDay())
  const today=new Date(); today.setHours(0,0,0,0)
  for(let i=0;i<42;i++){const d=new Date(start);d.setDate(start.getDate()+i);const ds=toDS(d);cells.push({date:d,day:d.getDate(),otherMonth:d.getMonth()!==miniCalMonth.value-1,isToday:toDS(today)===ds,eventCount:events.value.filter(e=>toDS(parseISO(e.startTime))===ds).length})}
  return cells
})

// Month cells
interface MCell{date:Date;day:number;isCurrentMonth:boolean;isToday:boolean;events:CalendarEvent[]}
const monthCells = computed(():MCell[]=>{
  const cells:MCell[]=[], first=new Date(currentDate.value.getFullYear(),currentDate.value.getMonth(),1), start=new Date(first);start.setDate(start.getDate()-start.getDay())
  const today=new Date();today.setHours(0,0,0,0)
  for(let i=0;i<42;i++){const d=new Date(start);d.setDate(start.getDate()+i);const ds=toDS(d);cells.push({date:d,day:d.getDate(),isCurrentMonth:d.getMonth()===currentDate.value.getMonth(),isToday:toDS(today)===ds,events:events.value.filter(e=>toDS(parseISO(e.startTime))===ds)})}
  return cells
})
function getVisibleCellEvents(evts:CalendarEvent[]):CalendarEvent[]{return evts.slice(0,3)}

// Week
interface WDate{key:string;date:Date;day:number;weekDay:string;isToday:boolean}
const weekDates = computed(():WDate[]=>{
  const dates:WDate[]=[],s=weekStart(currentDate.value),today=new Date()
  for(let i=0;i<7;i++){const d=new Date(s);d.setDate(s.getDate()+i);dates.push({key:toDS(d),date:d,day:d.getDate(),weekDay:weekDays[d.getDay()],isToday:toDS(today)===toDS(d)})}
  return dates
})
function getWeekDayEvents(date:Date):CalendarEvent[]{const ds=toDS(date);return events.value.filter(e=>toDS(parseISO(e.startTime))===ds&&!e.allDay)}
function getWeekEventStyle(evt:CalendarEvent):Record<string,any>{const s=parseISO(evt.startTime),e=parseISO(evt.endTime);const top=((s.getHours()*60+s.getMinutes())/1440)*100;const dur=Math.max((e.getTime()-s.getTime())/60000,30);return{top:`${top}%`,height:`${Math.max((dur/1440)*100,2.5)}%`,backgroundColor:evt.color+'22',borderLeft:`3px solid ${evt.color}`}}

// Day view
const dayViewEvents = computed(()=>{const ds=toDS(currentDate.value);return events.value.filter(e=>toDS(parseISO(e.startTime))===ds)})
function getDayEventStyle(evt:CalendarEvent):Record<string,any>{const s=parseISO(evt.startTime),e=parseISO(evt.endTime);const top=((s.getHours()*60+s.getMinutes())/1440)*100;const dur=Math.max((e.getTime()-s.getTime())/60000,30);return{top:`${top}%`,minHeight:`${Math.max((dur/1440)*100,4)}%`}}

// Panel
const panelInfo = computed(()=>{
  const d=selectedDate.value,tips:Record<string,string>={'0':'适合休息放松、回顾本周成果','1':'适合规划本周目标、启动新任务','2':'适合深度工作、攻克技术难题','3':'适合团队协作、会议沟通','4':'适合推进进度、检查里程碑','5':'适合收尾工作、整理文档','6':'适合学习提升、技术分享'}
  const wd=['星期日','星期一','星期二','星期三','星期四','星期五','星期六'],mo=['一月','二月','三月','四月','五月','六月','七月','八月','九月','十月','十一月','十二月']
  return{weekday:wd[d.getDay()],fullDate:`${d.getFullYear()}年${mo[d.getMonth()]}${d.getDate()}日`,isToday:toDS(d)===toDS(new Date()),tip:tips[String(d.getDay())]||'保持专注，高效完成今日目标'}
})
const panelEvents = computed(()=>{const ds=toDS(selectedDate.value);return events.value.filter(e=>toDS(parseISO(e.startTime))===ds)})

// Helpers
function toDS(d:Date):string{return`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
function parseISO(s:string):Date{return new Date(s)}
function formatTime(s:string):string{const d=new Date(s);return`${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`}
function formatDate(s:string):string{const d=new Date(s);return`${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日`}
function formatDateFull(d:Date):string{return`${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 ${weekDays[d.getDay()]}`}
function pad(n:number):string{return String(n).padStart(2,'0')}
function eventTypeName(t:string):string{return({TASK_DEADLINE:'截止日期',MEETING:'会议',REMINDER:'提醒',SPRINT:'Sprint',CUSTOM:'自定义'})[t]||t}
function eventTypeTagType(t:string):''|'success'|'warning'|'danger'|'info'{
  const map:{[k:string]:''|'success'|'warning'|'danger'|'info'}={
    TASK_DEADLINE:'danger',MEETING:'',REMINDER:'warning',SPRINT:'success',CUSTOM:'info'
  }
  return map[t]||'info'
}
function formatNotifTime(s:string):string{const d=new Date(s),now=new Date(),diff=now.getTime()-d.getTime();if(diff<60000)return'刚刚';if(diff<3600000)return`${Math.floor(diff/60000)}分钟前`;if(diff<86400000)return`${Math.floor(diff/3600000)}小时前`;return`${d.getMonth()+1}/${d.getDate()}`}
function isDayPanelDate(date:Date):boolean{return dayPanelVisible.value&&toDS(date)===toDS(selectedDate.value)}
function isSelectedDate(date:Date):boolean{return toDS(date)===toDS(selectedDate.value)}
function isToday(d:Date):boolean{return toDS(d)===toDS(new Date())}
function weekStart(date:Date):Date{const d=new Date(date);d.setDate(d.getDate()-d.getDay());d.setHours(0,0,0,0);return d}

// Navigation
function goToday(){currentDate.value=new Date();fetchEvents()}
function prev(){if(viewMode.value==='month')currentDate.value=new Date(currentDate.value.getFullYear(),currentDate.value.getMonth()-1,1);else if(viewMode.value==='week'){const d=new Date(currentDate.value);d.setDate(d.getDate()-7);currentDate.value=d}else{const d=new Date(currentDate.value);d.setDate(d.getDate()-1);currentDate.value=d}fetchEvents()}
function next(){if(viewMode.value==='month')currentDate.value=new Date(currentDate.value.getFullYear(),currentDate.value.getMonth()+1,1);else if(viewMode.value==='week'){const d=new Date(currentDate.value);d.setDate(d.getDate()+7);currentDate.value=d}else{const d=new Date(currentDate.value);d.setDate(d.getDate()+1);currentDate.value=d}fetchEvents()}
function jumpToDate(date:Date){selectedDate.value=date;currentDate.value=new Date(date);dayPanelVisible.value=true;fetchEvents()}

// Data
async function fetchEvents(){
  const start=getViewStart(),end=getViewEnd()
  try{const res=await getCalendarEvents(start.toISOString().replace('Z',''),end.toISOString().replace('Z',''),filterTeamId.value??undefined,undefined,filterEventType.value??undefined);events.value=res.data||[]}catch{}
}
async function fetchNotifs(){try{const res=await getNotifications(10);notifications.value=res.data||[];unreadCount.value=notifications.value.filter(n=>!n.isRead).length}catch{}}

async function onTeamFilterChange(tid:number|null){
  if(tid){try{projects.value=(await getProjectsByTeam(tid)).data||[]}catch{projects.value=[]}}
  else{try{projects.value=(await getMyProjects()).data||[]}catch{projects.value=[]}}
  fetchEvents()
}

function getViewStart():Date{if(viewMode.value==='month'){const f=new Date(currentDate.value.getFullYear(),currentDate.value.getMonth(),1);f.setDate(f.getDate()-f.getDay());return f}if(viewMode.value==='week')return weekStart(currentDate.value);const d=new Date(currentDate.value);d.setHours(0,0,0,0);return d}
function getViewEnd():Date{if(viewMode.value==='month'){const l=new Date(currentDate.value.getFullYear(),currentDate.value.getMonth()+1,0);l.setDate(l.getDate()+(6-l.getDay()));l.setHours(23,59,59);return l}if(viewMode.value==='week'){const s=weekStart(currentDate.value);const e=new Date(s);e.setDate(e.getDate()+6);e.setHours(23,59,59);return e}const d=new Date(currentDate.value);d.setHours(23,59,59);return d}

// Actions
function openDayPanel(date:Date){selectedDate.value=date;dayPanelVisible.value=true;checkAndNotifyUpcoming(date)}
function closeDayPanel(){dayPanelVisible.value=false}
function openCreateDialog(){pendingEventType.value=undefined;editingEvent.value=null;defaultDate.value=new Date();dialogVisible.value=true}
function openCreateFromPanel(type?:string){pendingEventType.value=type;editingEvent.value=null;defaultDate.value=selectedDate.value;dialogVisible.value=true}
function openCreateForDateTime(date:Date,hour:number){pendingEventType.value=undefined;editingEvent.value=null;defaultDate.value=new Date(date.getFullYear(),date.getMonth(),date.getDate(),hour,0);dialogVisible.value=true}
function openDetail(evt:CalendarEvent){detailEvent.value=evt;detailVisible.value=true}
function editFromDetail(){detailVisible.value=false;editingEvent.value=detailEvent.value;dialogVisible.value=true}
async function handleDelete(id:number){await deleteCalendarEvent(id);detailVisible.value=false;fetchEvents()}
function onSaved(){dialogVisible.value=false;fetchEvents()}

// Notifications
async function handleNotifClick(n:any){if(!n.isRead){try{await markRead(n.id);n.isRead=1;unreadCount.value=Math.max(0,unreadCount.value-1)}catch{}}}
async function markAllNotifsRead(){try{await apiMarkAllRead();notifications.value.forEach(n=>n.isRead=1);unreadCount.value=0}catch{}}
async function checkAndNotifyUpcoming(date:Date){const now=new Date(),today=toDS(now),target=toDS(date);if(target<today)return;const tomorrow=new Date(now);tomorrow.setDate(tomorrow.getDate()+1);if(date>tomorrow)return;const ds=toDS(date),upcoming=events.value.filter(e=>toDS(parseISO(e.startTime))===ds);for(const evt of upcoming){if(evt.participants?.length){for(const pid of evt.participants){try{await createNotification({userId:pid,type:'CALENDAR_REMINDER',title:`日程提醒：${evt.title}`,content:evt.allDay?`您有一个全天日程「${evt.title}」将在今天进行`:`您有一个日程「${evt.title}」将于 ${formatTime(evt.startTime)} 开始`,relatedId:evt.id,relatedType:'CALENDAR_EVENT'})}catch{}}}}}

// Lifecycle
watch([viewMode,filterEventType],()=>fetchEvents())

onMounted(async()=>{
  try{teams.value=(await getMyTeams()).data||[]}catch{}
  try{projects.value=(await getMyProjects()).data||[]}catch{}
  fetchEvents();fetchNotifs()
  notifTimer=setInterval(fetchNotifs,30000)
})
onUnmounted(()=>{if(notifTimer)clearInterval(notifTimer)})
</script>

<style scoped lang="scss">
$primary:#409eff;$border:#e4e7ed;$text:#303133;$text2:#909399;$text3:#c0c4cc;$bg:#f5f7fa;$card:#fff;$r:10px;$sh:0 1px 4px rgba(0,0,0,.06);

.calendar-page{display:flex;flex-direction:column;height:100%;padding:16px 20px;overflow:hidden;background:$bg;box-sizing:border-box}

/* HEADER */
.cal-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;flex-shrink:0;gap:12px;flex-wrap:wrap;
  &__left{display:flex;align-items:center;gap:14px;.cal-title{margin:0;font-size:20px;font-weight:800;color:$text}}}
.cal-nav{display:flex;align-items:center;gap:6px;&__title{font-size:14px;font-weight:600;color:$text2;min-width:120px;text-align:center}}
.cal-header__right{display:flex;align-items:center;gap:8px;flex-wrap:wrap}

/* NOTIF BELL */
.notif-bell{position:relative;cursor:pointer;padding:6px 8px;border-radius:8px;color:$text2;transition:all .2s;&:hover{background:rgba(0,0,0,.04);color:$text}&.has-unread{color:$primary}}
.notif-badge{position:absolute;top:2px;right:2px;min-width:16px;height:16px;line-height:16px;text-align:center;font-size:10px;font-weight:700;color:#fff;background:#f56c6c;border-radius:8px;padding:0 4px}

/* BODY */
.cal-body{flex:1;display:flex;gap:12px;overflow:hidden;min-height:0}

/* SIDEBAR */
.cal-sidebar{width:220px;flex-shrink:0;display:flex;flex-direction:column;gap:10px;overflow-y:auto}
.mini-cal{background:$card;border-radius:$r;padding:10px;box-shadow:$sh;
  &__header{text-align:center;font-weight:600;font-size:13px;margin-bottom:6px;color:$text}
  &__grid{display:grid;grid-template-columns:repeat(7,1fr);gap:1px}
  &__wd{text-align:center;font-size:10px;color:$text3;padding:2px 0}
  &__cell{aspect-ratio:1;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:11px;border-radius:50%;cursor:pointer;position:relative;color:$text2;transition:all .15s;
    &:hover{background:rgba($primary,.08)}&.other{color:$text3;opacity:.4}&.today{background:$primary;color:#fff;font-weight:700}&.selected:not(.today){outline:2px solid $primary;outline-offset:-2px}
    .mini-dot{position:absolute;bottom:1px;width:3px;height:3px;border-radius:50%;background:$primary}}}
.today-summary{background:$card;border-radius:$r;padding:12px;box-shadow:$sh;flex:1;overflow-y:auto;h4{margin:0 0 8px;font-size:12px;color:$text2}}
.today-date-big{font-size:32px;font-weight:800;line-height:1;color:$text;span{font-size:13px;font-weight:500;color:$text2;margin-left:4px}}
.today-weekday{font-size:12px;color:$text3;margin:4px 0 10px}
.today-event-list{display:flex;flex-direction:column;gap:4px}
.today-event-chip{display:flex;align-items:center;gap:6px;padding:6px 8px;border-radius:6px;background:$bg;border-left:3px solid transparent;cursor:pointer;transition:background .15s;&:hover{background:darken($bg,3%)}.chip-time{font-size:10px;color:$text3;white-space:nowrap}.chip-title{font-size:11px;font-weight:500;color:$text;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
.today-more{font-size:10px;color:$primary;text-align:center;cursor:pointer;padding:2px}
.today-empty{display:flex;flex-direction:column;align-items:center;gap:4px;padding:16px 0;color:$text3;.el-icon{font-size:24px}span{font-size:11px}}

/* MAIN */
.cal-main{flex:1;min-width:0;background:$card;border-radius:$r;box-shadow:$sh;display:flex;flex-direction:column;overflow:hidden}

/* MONTH */
.view-month{flex:1;overflow:auto}
.month-grid{width:100%;height:100%;display:flex;flex-direction:column;
  &__header{display:grid;grid-template-columns:repeat(7,1fr);border-bottom:1px solid $border;span{padding:8px;text-align:center;font-size:12px;font-weight:700;color:$text2}}
  &__body{display:grid;grid-template-columns:repeat(7,1fr);grid-auto-rows:1fr;flex:1}}
.month-cell{border-right:1px solid #f0f0f0;border-bottom:1px solid #f0f0f0;padding:3px;cursor:pointer;position:relative;min-height:60px;transition:background .15s;
  &:nth-child(7n){border-right:none}&:hover{background:rgba($primary,.03)}&.other{background:#fafbfc}&.today{background:#ecf5ff}&.selected{outline:2px solid $primary;outline-offset:-2px;z-index:2}
  &__num{display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:50%;font-size:12px;font-weight:500;color:$text}
  &.today &__num{background:$primary;color:#fff;font-weight:700}
  &__events{display:flex;flex-direction:column;gap:1px;margin-top:1px;overflow:hidden}}
.month-event-bar{font-size:10px;color:#fff;padding:0 4px;border-radius:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;cursor:pointer;line-height:1.6;transition:opacity .15s;&:hover{opacity:.85}}
.month-more{font-size:10px;color:$primary;cursor:pointer;padding:0 2px;&:hover{text-decoration:underline}}

/* WEEK */
.view-week{flex:1;display:flex;flex-direction:column;overflow:hidden}
.week-grid{display:flex;flex-direction:column;height:100%;&__header{display:flex;border-bottom:1px solid $border;flex-shrink:0}&__body{flex:1;display:flex;overflow-y:auto}}
.time-col-head{width:48px;flex-shrink:0}
.day-col-head{flex:1;text-align:center;padding:6px 0;border-right:1px solid #f0f0f0;cursor:pointer;transition:background .15s;&:hover{background:rgba($primary,.03)}&.isToday{background:#ecf5ff;.dch-day{color:$primary;font-weight:700}}.dch-wd{font-size:10px;color:$text3;display:block}.dch-day{font-size:14px;font-weight:600;color:$text}}
.time-col{width:48px;flex-shrink:0}.day-col{flex:1;position:relative;border-right:1px solid #f0f0f0;&.isToday{background:rgba($primary,.01)}}
.time-label{height:40px;font-size:9px;color:$text3;text-align:right;padding-right:4px;line-height:40px}
.hour-slot{height:40px;border-bottom:1px solid #f9f9f9;cursor:pointer;transition:background .1s;&:hover{background:rgba($primary,.05)}}
.week-event-block{position:absolute;left:2px;right:2px;padding:1px 4px;font-size:10px;border-radius:2px;cursor:pointer;z-index:2;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;transition:opacity .15s;&:hover{opacity:.85}}

/* DAY */
.view-day{flex:1;display:flex;flex-direction:column;overflow:hidden}
.day-top-bar{padding:10px 14px;border-bottom:1px solid $border;flex-shrink:0;display:flex;align-items:center;gap:6px;.dtb-date{font-size:15px;font-weight:700;color:$text}}
.day-grid{flex:1;display:flex;overflow-y:auto}
.day-content-col{flex:1;position:relative}
.day-event-card{position:absolute;left:4px;right:4px;display:flex;gap:6px;padding:6px 8px;background:$card;border-radius:6px;box-shadow:$sh;cursor:pointer;z-index:2;transition:box-shadow .15s;&:hover{box-shadow:0 2px 8px rgba(0,0,0,.1)}.dec-color-bar{width:3px;border-radius:2px;align-self:stretch}.dec-body{display:flex;flex-direction:column;gap:1px;flex:1;strong{font-size:12px}span{font-size:10px;color:$text2}}}

/* DETAIL PANEL */
.detail-panel{width:300px;flex-shrink:0;background:$card;border-radius:$r;padding:16px;overflow-y:auto;display:flex;flex-direction:column;gap:12px;box-shadow:0 2px 8px rgba(0,0,0,.08)}
.dp-header{&__top{display:flex;align-items:center;justify-content:space-between;h3{margin:0;font-size:18px;font-weight:800}}&__date{font-size:12px;color:$text2;margin-top:2px;display:flex;align-items:center;gap:4px}}
.dp-tip{display:flex;align-items:flex-start;gap:8px;padding:10px 12px;background:linear-gradient(135deg,#667eea08,#764ba208);border-radius:8px;border:1px solid #667eea15;.el-icon{color:#e6a23c;font-size:18px;flex-shrink:0;margin-top:1px}strong{font-size:12px;color:$text;display:block;margin-bottom:2px}p{font-size:11px;color:$text2;margin:0;line-height:1.4}}
.dp-section{flex:1;display:flex;flex-direction:column;&__head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;span{font-size:13px;font-weight:700;color:$text}}}
.dp-events{display:flex;flex-direction:column;gap:6px}
.dp-event-card{display:flex;gap:8px;padding:10px 12px;background:$bg;border-radius:8px;cursor:pointer;transition:background .15s;&:hover{background:darken($bg,3%)}.dpec-left{width:3px;border-radius:2px;align-self:stretch}.dpec-body{flex:1;display:flex;flex-direction:column;gap:3px;.dpec-row{display:flex;align-items:center;justify-content:space-between;gap:6px;strong{font-size:12px}}.dpec-meta{display:flex;align-items:center;gap:4px;font-size:10px;color:$text2;.el-icon{font-size:12px}}}}
.dp-empty{display:flex;flex-direction:column;align-items:center;gap:6px;padding:20px 0;color:$text3;.el-icon{font-size:28px}span{font-size:12px}}
.dp-actions{display:flex;flex-direction:column;gap:6px;padding-top:10px;border-top:1px solid #f0f0f0;.dp-btn{border-radius:8px;font-weight:500}}

/* ANIMATION */
.panel-slide-enter-active,.panel-slide-leave-active{transition:all .3s cubic-bezier(.4,0,.2,1)}
.panel-slide-enter-from,.panel-slide-leave-to{transform:translateX(16px);opacity:0}

/* EVENT DETAIL DIALOG */
.edl{&__banner{padding:12px 14px;border-radius:8px;border-left:4px solid;display:flex;align-items:center;gap:8px;margin-bottom:12px;.edl__tag{font-size:11px;color:$text2;padding:2px 6px;background:$bg;border-radius:4px}}&__body{display:flex;flex-direction:column;gap:10px}}
.edl-row{display:flex;align-items:flex-start;gap:6px;font-size:13px;color:$text;.el-icon{color:$text2;margin-top:2px;flex-shrink:0}&.desc{flex-direction:column;gap:2px}.edl-date{margin-left:auto;font-size:11px;color:$text3}}

/* NOTIF DROPDOWN */
.notif-dropdown{&__header{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid #f0f0f0;strong{font-size:13px}}}
.notif-empty{display:flex;flex-direction:column;align-items:center;gap:6px;padding:20px 0;color:$text3;.el-icon{font-size:28px}span{font-size:12px}}
.notif-list{display:flex;flex-direction:column;gap:2px;max-height:300px;overflow-y:auto}
.notif-item{display:flex;gap:8px;padding:8px 10px;border-radius:6px;cursor:pointer;transition:background .12s;&:hover{background:#f5f7fa}&.unread{background:#ecf5ff33}.notif-dot{width:6px;height:6px;border-radius:50%;margin-top:5px;flex-shrink:0;background:#e4e7ed;&.active{background:$primary}}.notif-body{flex:1;min-width:0;strong{font-size:12px;display:block}p{font-size:11px;color:$text2;margin:2px 0 0;line-height:1.3;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.notif-time{font-size:10px;color:$text3}}}
</style>
