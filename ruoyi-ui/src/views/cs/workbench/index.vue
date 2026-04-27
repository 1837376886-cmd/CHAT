<template>
  <div class="app-container cs-workbench">
    <!-- 左侧会话列表 -->
    <div class="cs-sidebar">
      <div class="cs-sidebar-header">
        <div class="cs-status-wrap">
          <span class="cs-status-dot" :class="{ online: online }"></span>
          <span class="cs-status-text">{{ online ? '在线' : '离线' }}</span>
        </div>
        <span class="cs-session-count">接待中 {{ activeSessions.length }} / {{ maxSessions }} · 排队 {{ waitingCount }}</span>
        <el-button size="mini" :type="online ? 'danger' : 'success'" @click="toggleOnline">
          {{ online ? '下线' : '上线' }}
        </el-button>
      </div>
      <div class="cs-session-list">
        <div
          v-for="session in activeSessions"
          :key="session.id"
          class="cs-session-card"
          :class="{ active: currentSessionId === session.id }"
          @click="selectSession(session)"
        >
          <div class="cs-session-avatar">访</div>
          <div class="cs-session-info">
            <div class="cs-session-row">
              <span class="cs-session-name">访客 {{ session.visitorId }}</span>
              <span class="cs-session-time">{{ formatTime(session.startTime) }}</span>
            </div>
            <div class="cs-session-preview">{{ lastMessage(session.id) }}</div>
          </div>
          <div v-if="unreadMap[session.id]" class="cs-unread-badge">{{ unreadMap[session.id] }}</div>
          <div class="cs-session-actions">
            <el-button size="mini" type="danger" @click.stop="closeSession(session.id)">结束</el-button>
          </div>
        </div>
        <div v-if="activeSessions.length === 0" class="cs-empty">暂无接待中的访客</div>
      </div>
    </div>

    <!-- 中间聊天区域 -->
    <div class="cs-main">
      <div v-if="currentSession" class="cs-chat-area">
        <div class="cs-chat-header">
          <div class="cs-chat-header-info">
            <span class="cs-chat-header-name">访客 {{ currentSession.visitorId }}</span>
            <span class="cs-chat-header-status">
              <span class="cs-dot active"></span> 对话中
            </span>
          </div>
        </div>
        <div class="cs-chat-messages" ref="messageContainer">
          <div
            v-for="(msg, index) in currentMessages"
            :key="index"
            class="cs-msg"
            :class="[msg.isHistoryDivider ? 'history-divider' : '', msg.isSystem ? 'system' : (msg.isSelf ? 'self' : 'other')]"
          >
            <template v-if="msg.isHistoryDivider">
              <div class="cs-history-divider">{{ msg.content }}</div>
            </template>
            <template v-else-if="msg.isSystem">
              <div class="cs-system-bubble">{{ msg.content }}</div>
            </template>
            <template v-else>
              <div v-if="!msg.isSelf" class="cs-msg-avatar cs-avatar-guest">访</div>
              <div class="cs-msg-bubble">
                <div class="cs-msg-sender">{{ msg.sender }}</div>
                <div class="cs-msg-content">{{ msg.content }}</div>
                <div class="cs-msg-time">{{ msg.time }}</div>
              </div>
              <div v-if="msg.isSelf" class="cs-msg-avatar cs-avatar-cs">我</div>
            </template>
          </div>
        </div>
        <div class="cs-chat-input">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="请输入回复，按 Enter 发送..."
            @keyup.enter.native="sendMessage"
          />
          <el-button type="primary" size="small" @click="sendMessage">发送</el-button>
        </div>
      </div>
      <div v-else class="cs-no-select">
        <div class="cs-no-select-inner">
          <i class="el-icon-chat-dot-square"></i>
          <p>请从左侧选择一个访客开始对话</p>
        </div>
      </div>
    </div>

    <!-- 右侧访客信息 -->
    <div class="cs-info-panel">
      <div class="cs-info-header">访客信息</div>
      <div v-if="currentSession" class="cs-info-body">
        <div class="cs-info-item">
          <div class="cs-info-label">访客ID</div>
          <div class="cs-info-value">{{ currentSession.visitorId }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">会话状态</div>
          <div class="cs-info-value">
            <el-tag size="small" type="success">进行中</el-tag>
          </div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">接入时间</div>
          <div class="cs-info-value">{{ formatFullTime(currentSession.startTime) }}</div>
        </div>
      </div>
      <div v-else class="cs-info-empty">未选择会话</div>
    </div>
  </div>
</template>

<script>
import { getWorkbenchSessions, closeCsSession, csOnline, csOffline, getCsSessionMessages, readCsSession, getWaitingCount, getVisitorHistoryMessages } from '@/api/cs'
import { WS_URL, MessageType } from '@/utils/chatConstants'
import ChatWebSocket from '@/utils/chatWebSocket'
import { getToken } from '@/utils/auth'

export default {
  name: 'CsWorkbench',
  data() {
    return {
      activeSessions: [],
      currentSessionId: null,
      currentMessages: [],
      inputMessage: '',
      online: true,
      maxSessions: 5,
      wsClient: null,
      messageMap: {},
      unreadMap: {},
      waitingCount: 0,
      waitingPollTimer: null
    }
  },
  computed: {
    currentSession() {
      return this.activeSessions.find(s => s.id === this.currentSessionId)
    }
  },
  mounted() {
    this.loadSessions()
    this.initWebSocket()
    this.startWaitingPoll()
  },
  beforeDestroy() {
    if (this.wsClient) {
      this.wsClient.close()
    }
    if (this.waitingPollTimer) {
      clearInterval(this.waitingPollTimer)
    }
  },
  methods: {
    loadSessions() {
      getWorkbenchSessions().then(res => {
        this.activeSessions = res.data || []
        this.activeSessions.forEach(s => {
          if (s.csUnreadCount > 0) {
            this.$set(this.unreadMap, s.id, s.csUnreadCount)
          }
          // 预加载消息，避免左侧预览和点击后显示空
          this.loadMessages(s.id, false)
        })
      })
    },
    selectSession(session) {
      this.currentSessionId = session.id
      this.loadMessagesWithHistory(session.id)
      this.$set(this.unreadMap, session.id, 0)
      readCsSession(session.id)
    },
    loadMessages(sessionId, setCurrent = true) {
      const cached = this.messageMap[sessionId]
      if (cached && cached.length > 0) {
        if (setCurrent) this.currentMessages = cached
        return
      }
      getCsSessionMessages(sessionId).then(res => {
        const list = res.data || []
        const mapped = list.map(item => ({
          sender: item.fromType === 2 ? '我' : (item.fromType === 1 ? '访客' : '系统'),
          content: item.content,
          time: this.formatTime(item.createTime),
          isSelf: item.fromType === 2,
          isSystem: item.fromType === 3
        }))
        this.$set(this.messageMap, sessionId, mapped)
        if (setCurrent) this.currentMessages = mapped
      }).catch(() => {
        if (setCurrent) this.currentMessages = []
      })
    },
    loadMessagesWithHistory(sessionId) {
      const cached = this.messageMap[sessionId]
      if (cached && cached.length > 0 && cached.some(m => m.isHistoryDivider)) {
        this.currentMessages = cached
        return
      }
      const session = this.activeSessions.find(s => s.id === sessionId)
      getCsSessionMessages(sessionId).then(res => {
        const list = res.data || []
        const currentMapped = list.map(item => ({
          sender: item.fromType === 2 ? '我' : (item.fromType === 1 ? '访客' : '系统'),
          content: item.content,
          time: this.formatTime(item.createTime),
          isSelf: item.fromType === 2,
          isSystem: item.fromType === 3
        }))
        if (!session || !session.visitorId) {
          this.$set(this.messageMap, sessionId, currentMapped)
          this.currentMessages = currentMapped
          this.scrollToBottom()
          return
        }
        getVisitorHistoryMessages(session.visitorId).then(histRes => {
          const histList = histRes.data || []
          const histMapped = histList
            .filter(item => item.sessionId !== sessionId)
            .map(item => ({
              sender: item.fromType === 2 ? '客服' : (item.fromType === 1 ? '访客' : '系统'),
              content: item.content,
              time: this.formatTime(item.createTime),
              isSelf: item.fromType === 2,
              isSystem: item.fromType === 3,
              isHistory: true
            }))
          const merged = []
          if (histMapped.length > 0) {
            merged.push({ isHistoryDivider: true, content: '—— 以下为历史会话消息 ——' })
            merged.push(...histMapped)
            merged.push({ isHistoryDivider: true, content: '—— 以上历史会话消息 ——' })
          }
          merged.push(...currentMapped)
          this.$set(this.messageMap, sessionId, merged)
          this.currentMessages = merged
          this.scrollToBottom()
        }).catch(() => {
          this.$set(this.messageMap, sessionId, currentMapped)
          this.currentMessages = currentMapped
          this.scrollToBottom()
        })
      }).catch(() => {
        this.currentMessages = []
      })
    },
    sendMessage() {
      const content = this.inputMessage.trim()
      if (!content || !this.currentSessionId) return

      if (this.wsClient && this.wsClient.ws && this.wsClient.ws.readyState === WebSocket.OPEN) {
        this.wsClient.send({
          type: MessageType.CS_CHAT,
          sessionId: String(this.currentSessionId),
          content: content,
          messageId: 'msg_' + Date.now()
        })
        this.addMessage(this.currentSessionId, {
          sender: '我',
          content: content,
          time: this.formatTime(new Date()),
          isSelf: true
        })
        this.inputMessage = ''
      } else {
        this.$message.warning('连接未就绪，请稍后重试')
      }
    },
    closeSession(sessionId) {
      this.$confirm('确认结束该会话？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        closeCsSession(sessionId).then(() => {
          this.$message.success('会话已结束')
          if (this.currentSessionId === sessionId) {
            this.currentSessionId = null
            this.currentMessages = []
          }
          this.loadSessions()
        })
      })
    },
    toggleOnline() {
      if (this.online) {
        csOffline().then(() => {
          this.online = false
          this.$message.success('已下线')
          this.activeSessions = []
          if (this.currentSessionId) {
            this.currentSessionId = null
            this.currentMessages = []
          }
        })
      } else {
        this.goOnline()
      }
    },
    goOnline() {
      csOnline().then(() => {
        this.online = true
        this.$message.success('已上线')
      }).catch(() => {
        this.online = false
      })
    },
    initWebSocket() {
      const token = getToken()
      const wsUrl = WS_URL
      this.wsClient = new ChatWebSocket(wsUrl, {
        maxReconnectAttempts: 5,
        reconnectInterval: 3000,
        heartbeatInterval: 20000,
        onOpen: () => {
          const userInfo = this.$store.state.user || JSON.parse(localStorage.getItem('userInfo') || '{}')
          const userId = Number(userInfo.id)
          console.log('客服WS认证, userId=', userId, '原始值=', userInfo.id)
          this.wsClient.send({
            type: MessageType.AUTH,
            fromUserId: userId,
            fromUserNickname: userInfo.nickName || userInfo.name,
            fromUserAvatar: userInfo.avatar || ''
          })
        },
        onMessage: (msg) => {
          this.handleWsMessage(msg)
        }
      })
    },
    handleWsMessage(msg) {
      console.log('工作台收到消息:', msg)
      if (msg.type === MessageType.AUTH_SUCCESS) {
        console.log('客服WS认证成功')
        return
      }
      if (msg.type === MessageType.ERROR) {
        this.$message.error(msg.content || '错误')
        return
      }
      if (msg.type === MessageType.CS_CHAT) {
        const sessionId = parseInt(msg.sessionId)
        this.addMessage(sessionId, {
          sender: msg.fromUserNickname || '访客',
          content: msg.content,
          time: this.formatTime(new Date()),
          isSelf: false
        })
        if (!this.activeSessions.find(s => s.id === sessionId)) {
          this.loadSessions()
        }
        if (this.currentSessionId === sessionId) {
          // 客服正在看该会话，立即清零后端未读
          readCsSession(sessionId)
          this.$set(this.unreadMap, sessionId, 0)
        } else {
          const count = this.unreadMap[sessionId] || 0
          this.$set(this.unreadMap, sessionId, count + 1)
        }
        return
      }
      if (msg.type === MessageType.SYSTEM_NOTICE) {
        const sessionId = msg.sessionId ? parseInt(msg.sessionId) : null
        if (sessionId) {
          this.addMessage(sessionId, {
            sender: '系统',
            content: msg.content,
            time: this.formatTime(new Date()),
            isSelf: false,
            isSystem: true
          })
        }
        this.$message.info(msg.content)
        this.loadSessions()
        return
      }
      console.warn('工作台收到未知消息类型:', msg.type)
    },
    addMessage(sessionId, msg) {
      if (!this.messageMap[sessionId]) {
        this.$set(this.messageMap, sessionId, [])
      }
      this.messageMap[sessionId].push(msg)
      if (this.currentSessionId === sessionId) {
        this.currentMessages = [...this.messageMap[sessionId]]
        this.scrollToBottom()
      }
    },
    lastMessage(sessionId) {
      const list = this.messageMap[sessionId]
      if (list && list.length > 0) {
        const last = list[list.length - 1]
        return last.isSystem ? `[系统] ${last.content}` : last.content
      }
      if (this.unreadMap[sessionId] > 0) {
        return '您有新的未读消息'
      }
      return '—'
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messageContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    },
    formatTime(timeStr) {
      if (!timeStr) return ''
      const d = new Date(timeStr)
      const h = String(d.getHours()).padStart(2, '0')
      const m = String(d.getMinutes()).padStart(2, '0')
      return `${h}:${m}`
    },
    startWaitingPoll() {
      this.loadWaitingCount()
      this.waitingPollTimer = setInterval(() => {
        this.loadWaitingCount()
      }, 3000)
    },
    loadWaitingCount() {
      getWaitingCount().then(res => {
        this.waitingCount = res.data || 0
      }).catch(() => {
        this.waitingCount = 0
      })
    },
    formatFullTime(timeStr) {
      if (!timeStr) return '-'
      const d = new Date(timeStr)
      const y = d.getFullYear()
      const mo = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const h = String(d.getHours()).padStart(2, '0')
      const m = String(d.getMinutes()).padStart(2, '0')
      return `${y}-${mo}-${day} ${h}:${m}`
    }
  }
}
</script>

<style scoped>
.cs-workbench {
  display: flex;
  height: calc(100vh - 84px);
  padding: 0;
  background: #fff;
}
.cs-sidebar {
  width: 300px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
}
.cs-sidebar-header {
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
}
.cs-status-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cs-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.cs-status-dot.online {
  background: #67c23a;
}
.cs-status-text {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.cs-session-count {
  font-size: 12px;
  color: #909399;
  flex: 1;
  text-align: center;
}
.cs-session-list {
  flex: 1;
  overflow-y: auto;
}
.cs-session-card {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  position: relative;
  transition: background 0.2s;
}
.cs-session-card:hover {
  background: #f5f7fa;
}
.cs-session-card.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 13px;
}
.cs-session-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e6f2ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  margin-right: 12px;
  flex-shrink: 0;
}
.cs-session-info {
  flex: 1;
  min-width: 0;
}
.cs-session-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.cs-session-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.cs-session-time {
  font-size: 12px;
  color: #c0c4cc;
}
.cs-session-preview {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cs-unread-badge {
  position: absolute;
  right: 60px;
  top: 50%;
  transform: translateY(-50%);
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}
.cs-session-actions {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  display: none;
}
.cs-session-card:hover .cs-session-actions {
  display: block;
}
.cs-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-size: 13px;
}
.cs-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.cs-chat-area {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.cs-chat-header {
  padding: 14px 20px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}
.cs-chat-header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cs-chat-header-name {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}
.cs-chat-header-status {
  font-size: 12px;
  color: #67c23a;
  display: flex;
  align-items: center;
  gap: 4px;
}
.cs-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
}
.cs-dot.active {
  background: #67c23a;
}
.cs-chat-messages {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
  background: #f5f7fa;
}
.cs-msg {
  margin-bottom: 16px;
  display: flex;
  align-items: flex-start;
}
.cs-msg.self {
  justify-content: flex-end;
}
.cs-msg.other {
  justify-content: flex-start;
}
.cs-msg.system {
  justify-content: center;
  align-items: center;
}
.cs-msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
  margin: 0 10px;
}
.cs-avatar-guest {
  background: #e6f2ff;
  color: #409eff;
}
.cs-avatar-cs {
  background: #409eff;
  color: #fff;
}
.cs-msg-bubble {
  max-width: 60%;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.cs-msg.self .cs-msg-bubble {
  background: #409eff;
  color: #fff;
}
.cs-msg-sender {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.cs-msg.self .cs-msg-sender {
  color: rgba(255,255,255,0.85);
}
.cs-msg-content {
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}
.cs-msg-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 6px;
  text-align: right;
}
.cs-msg.self .cs-msg-time {
  color: rgba(255,255,255,0.7);
}
.cs-system-bubble {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 12px;
  background: #e4e7ed;
  color: #606266;
  font-size: 12px;
  max-width: 70%;
  text-align: center;
}
.cs-msg.history-divider {
  justify-content: center;
  align-items: center;
  margin: 20px 0;
}
.cs-history-divider {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
}
.cs-chat-input {
  padding: 12px 20px;
  border-top: 1px solid #ebeef5;
  background: #fff;
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.cs-chat-input .el-textarea {
  flex: 1;
}
.cs-no-select {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.cs-no-select-inner {
  text-align: center;
  color: #c0c4cc;
}
.cs-no-select-inner i {
  font-size: 48px;
  margin-bottom: 12px;
}
.cs-no-select-inner p {
  font-size: 14px;
}
.cs-info-panel {
  width: 260px;
  border-left: 1px solid #ebeef5;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
}
.cs-info-header {
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  background: #fff;
}
.cs-info-body {
  padding: 16px;
}
.cs-info-item {
  margin-bottom: 16px;
}
.cs-info-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.cs-info-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.cs-info-empty {
  padding: 40px 16px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}
</style>
