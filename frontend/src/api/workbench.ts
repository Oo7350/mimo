import request from './request'
import type { WorkbenchData } from '@/types/workbench'

export function getMyWorkbench() {
  return request.get<WorkbenchData>('/workbench/my')
}
