import request from './request'

export function getMyLevel() {
  return request.get('/user-levels/me')
}

export function getAllUserLevels() {
  return request.get('/user-levels/all')
}

export function setUserLevel(userId: number, level: number) {
  return request.put(`/user-levels/${userId}`, { level, levelName: 'L' + level })
}
