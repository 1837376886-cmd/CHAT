import request from '@/utils/request'

export function csConnect(data) {
  return request({
    url: '/cs/connect',
    method: 'post',
    data: data
  })
}

export function cancelWaiting(visitorToken) {
  return request({
    url: '/cs/waiting/cancel',
    method: 'post',
    data: { visitorToken }
  })
}

export function csSendMessage(data) {
  return request({
    url: '/cs/cs/message/send',
    method: 'post',
    data: data
  })
}

export function getCsSessionHistory(sessionId, visitorToken) {
  return request({
    url: `/cs/session/history/${sessionId}`,
    method: 'get',
    params: { visitorToken }
  })
}

export function getCsSessionMessages(sessionId) {
  return request({
    url: `/cs/session/${sessionId}/messages`,
    method: 'get'
  })
}

export function getWorkbenchSessions() {
  return request({
    url: '/cs/workbench/sessions',
    method: 'get'
  })
}

export function closeCsSession(sessionId) {
  return request({
    url: `/cs/session/close/${sessionId}`,
    method: 'post'
  })
}

export function readCsSession(sessionId) {
  return request({
    url: `/cs/session/read/${sessionId}`,
    method: 'post'
  })
}

export function csStaffList() {
  return request({
    url: '/cs/staff/list',
    method: 'get'
  })
}

export function setCsStaff(data) {
  return request({
    url: '/cs/staff/set',
    method: 'post',
    data: data
  })
}

export function getCsConfig(userId) {
  return request({
    url: `/cs/config/${userId}`,
    method: 'get'
  })
}

export function saveCsConfig(data) {
  return request({
    url: '/cs/config/save',
    method: 'post',
    data: data
  })
}

export function getMyCsHistory() {
  return request({
    url: '/cs/my/history',
    method: 'get'
  })
}

export function getCsMyHistory() {
  return request({
    url: '/cs/my/csHistory',
    method: 'get'
  })
}

export function confirmBindHistory() {
  return request({
    url: '/cs/bind/confirm',
    method: 'post'
  })
}

export function csOnline() {
  return request({
    url: '/cs/online',
    method: 'post'
  })
}

export function csOffline() {
  return request({
    url: '/cs/offline',
    method: 'post'
  })
}

export function getWaitingCount() {
  return request({
    url: '/cs/waiting/count',
    method: 'get'
  })
}

export function getMyCsStatus() {
  return request({
    url: '/cs/my/status',
    method: 'get'
  })
}

export function getVisitorHistoryMessages(visitorId) {
  return request({
    url: `/cs/visitor/${visitorId}/messages`,
    method: 'get'
  })
}
