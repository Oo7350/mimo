import request from './request'

export interface CalendarEvent {
  id: number
  userId: number
  projectId: number | null
  projectName: string | null
  title: string
  description: string | null
  startTime: string
  endTime: string
  allDay: boolean
  eventType: 'TASK_DEADLINE' | 'MEETING' | 'REMINDER' | 'SPRINT' | 'CUSTOM'
  relatedId: number | null
  relatedType: 'ISSUE' | 'SPRINT' | null
  relatedTitle: string | null
  color: string
  location: string | null
  participants: number[] | null
  participantNames: string[] | null
  reminderMinutes: number | null
  createdAt: string
  readonly: boolean
}

export interface CreateCalendarEvent {
  projectId?: number
  title: string
  description?: string
  startTime: string
  endTime: string
  allDay?: boolean
  eventType?: CalendarEvent['eventType']
  relatedId?: number
  relatedType?: CalendarEvent['relatedType']
  color?: string
  location?: string
  participants?: number[]
  reminderMinutes?: number
}

export function getCalendarEvents(start: string, end: string, projectId?: number) {
  const params = new URLSearchParams({ start, end })
  if (projectId) params.set('projectId', String(projectId))
  return request.get(`/calendar?${params.toString()}`)
}

export function getCalendarEventById(id: number) {
  return request.get(`/calendar/${id}`)
}

export function createCalendarEvent(data: CreateCalendarEvent) {
  return request.post('/calendar', data)
}

export function updateCalendarEvent(id: number, data: Partial<CreateCalendarEvent>) {
  return request.put(`/calendar/${id}`, data)
}

export function deleteCalendarEvent(id: number) {
  return request.delete(`/calendar/${id}`)
}
