import request from '@/utils/request'

export function createPrivateSession(data) {
  return request({
    url: '/chat/session/private',
    method: 'post',
    data: data
  })
}

export function createGroupSession(data) {
  return request({
    url: '/chat/session/group',
    method: 'post',
    data: data
  })
}

export function getUserSessions() {
  return request({
    url: '/chat/sessions',
    method: 'get'
  })
}

export function getSessionMessages(sessionId, params) {
  return request({
    url: `/chat/session/${sessionId}/messages`,
    method: 'get',
    params: params
  })
}

export function markMessageAsRead(messageId) {
  return request({
    url: `/chat/message/${messageId}/read`,
    method: 'post'
  })
}

export function recallMessage(messageId) {
  return request({
    url: `/chat/message/${messageId}/recall`,
    method: 'post'
  })
}

export function getSessionMembers(sessionId) {
  return request({
    url: `/chat/session/${sessionId}/members`,
    method: 'get'
  })
}

export function joinSession(sessionId) {
  return request({
    url: `/chat/session/${sessionId}/join`,
    method: 'post'
  })
}

export function leaveSession(sessionId) {
  return request({
    url: `/chat/session/${sessionId}/leave`,
    method: 'post'
  })
}
