import request from './request'

export function getMyLevel() {
  return request.get('/api/user-levels/me')
}

export function getAllUserLevels() {
  return request.get('/api/user-levels/all')
}

export function setUserLevel(userId: number, level: number) {
  return request.put(`/api/user-levels/${userId}`, { level, levelName: 'L' + level })
}
