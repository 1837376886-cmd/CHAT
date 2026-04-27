<template>
  <div class="app-container cs-workbench">
    <div class="cs-sidebar">
      <div class="cs-sidebar-header">
        <span>当前接待：{{ activeSessions.length }} / {{ maxSessions }}</span>
        <el-button size="mini" :type="online ? 'success' : 'info'" @click="toggleOnline">
          {{ online ? '在线' : '离线' }}
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
          <div class="cs-session-name">访客 {{ session.visitorId }}</div>
          <div class="cs-session-time">{{ formatTime(session.startTime) }}</div>
          <div class="cs-session-actions">
            <el-button size="mini" type="danger" @click.stop="closeSession(session.id)">结束</el-button>
          </div>
        </div>
        <div v-if="activeSessions.length === 0" class="cs-empty">暂无接待中的访客</div>
      </div>
    </div>

    <div class="cs-main">
      <div v-if="currentSession" class="cs-chat-area">
        <div class="cs-chat-header">
          <span>访客 {{ currentSession.visitorId }}</span>
        </div>
        <div class="cs-chat-messages" ref="messageContainer">
          <div
            v-for="(msg, index) in currentMessages"
            :key="index"
            class="cs-msg"
            :class="msg.isSelf ? 'self' : 'other'"
          >
            <div class="cs-msg-bubble">
              <div class="cs-msg-sender">{{ msg.sender }}</div>
              <div class="cs-msg-content">{{ msg.content }}</div>
              <div class="cs-msg-time">{{ msg.time }}</div>
            </div>
          </div>
        </div>
        <div class="cs-chat-input">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            placeholder="请输入回复..."
            @keyup.enter.native="sendMessage"
          />
          <el-button type="primary" size="small" @click="sendMessage">发送</el-button>
        </div>
      </div>
      <div v-else class="cs-no-select">请从左侧选择一个访客</div>
    </div>
  </div>
</template>

<script>
import { getWorkbenchSessions, closeCsSession, csOnline, csOffline, getCsSessionMessages } from '@/api/cs'
import { csSendMessage } from '@/api/cs'
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
      messageMap: {}
    }
  },
  computed: {
    currentSession() {
      return this.activeSessions.find(s => s.id === this.currentSessionId)
    }
  },
  mounted() {
    this.goOnline()
    this.loadSessions()
    this.initWebSocket()
  },
  beforeDestroy() {
    if (this.wsClient) {
      this.wsClient.close()
    }
  },
  methods: {
    loadSessions() {
      getWorkbenchSessions().then(res => {
        this.activeSessions = res.data || []
      })
    },
    selectSession(session) {
      this.currentSessionId = session.id
      this.loadMessages(session.id)
    },
    loadMessages(sessionId) {
      const cached = this.messageMap[sessionId]
      if (cached && cached.length > 0) {
        this.currentMessages = cached
        return
      }
      getCsSessionMessages(sessionId).then(res => {
        const list = res.data || []
        const mapped = list.map(item => ({
          sender: item.fromType === 2 ? '我' : (item.fromType === 1 ? '访客' : '系统'),
          content: item.content,
          time: this.formatTime(item.createTime),
          isSelf: item.fromType === 2
        }))
        this.messageMap[sessionId] = mapped
        this.currentMessages = mapped
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
        return
      }
      if (msg.type === MessageType.SYSTEM_NOTICE) {
        this.$message.info(msg.content)
        this.loadSessions()
        return
      }
      console.warn('工作台收到未知消息类型:', msg.type)
    },
    addMessage(sessionId, msg) {
      if (!this.messageMap[sessionId]) {
        this.messageMap[sessionId] = []
      }
      this.messageMap[sessionId].push(msg)
      if (this.currentSessionId === sessionId) {
        this.currentMessages = [...this.messageMap[sessionId]]
        this.scrollToBottom()
      }
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
    }
  }
}
</script>

<style scoped>
.cs-workbench {
  display: flex;
  height: calc(100vh - 84px);
  padding: 0;
}
.cs-sidebar {
  width: 280px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}
.cs-sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cs-session-list {
  flex: 1;
  overflow-y: auto;
}
.cs-session-card {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  position: relative;
}
.cs-session-card:hover,
.cs-session-card.active {
  background: #f5f7fa;
}
.cs-session-name {
  font-weight: bold;
  margin-bottom: 4px;
}
.cs-session-time {
  font-size: 12px;
  color: #999;
}
.cs-session-actions {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  display: none;
}
.cs-session-card:hover .cs-session-actions {
  display: block;
}
.cs-empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
}
.cs-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.cs-chat-area {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.cs-chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  font-weight: bold;
}
.cs-chat-messages {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  background: #f5f5f5;
}
.cs-msg {
  margin-bottom: 12px;
  display: flex;
}
.cs-msg.self {
  justify-content: flex-end;
}
.cs-msg.other {
  justify-content: flex-start;
}
.cs-msg-bubble {
  max-width: 60%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff;
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
  color: rgba(255,255,255,0.8);
}
.cs-msg-content {
  font-size: 14px;
  line-height: 1.5;
}
.cs-msg-time {
  font-size: 10px;
  color: #ccc;
  margin-top: 4px;
  text-align: right;
}
.cs-chat-input {
  padding: 12px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 8px;
}
.cs-no-select {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}
</style>
