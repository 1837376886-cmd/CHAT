<template>
  <div class="guest-cs-widget">
    <!-- 悬浮按钮 -->
    <div v-if="!isOpen" class="cs-float-btn" @click="openChat">
      <i class="el-icon-service"></i>
      <span class="cs-label">客服咨询</span>
    </div>

    <!-- 聊天窗口 -->
    <div v-else class="cs-chat-window">
      <div class="cs-chat-header">
        <div class="cs-header-info">
          <span class="cs-title">在线客服</span>
          <span v-if="csNickname" class="cs-subtitle">{{ csNickname }}</span>
          <span v-else-if="waiting" class="cs-status waiting">排队中 {{ waitingPosition ? '（第'+waitingPosition+'位）' : '' }}</span>
          <span v-else-if="waitingForLastCs" class="cs-status waiting">联系上次客服中...</span>
        </div>
        <i class="el-icon-close cs-close" @click="closeChat"></i>
      </div>

      <div class="cs-chat-body" ref="messageContainer">
        <div v-if="!confirmed" class="cs-welcome">
          <i class="el-icon-service"></i>
          <p class="cs-welcome-title">欢迎使用在线客服</p>
          <p class="cs-welcome-desc">如有疑问，请点击下方按钮开始咨询</p>
          <el-button type="primary" size="small" @click="startConsult">开始咨询</el-button>
        </div>
        <template v-else>
          <div v-if="messages.length === 0" class="cs-empty-tip">
            <p>欢迎使用在线客服</p>
            <p v-if="waiting || waitingForLastCs">{{ statusMessage }}</p>
            <p v-if="waiting && waitingPosition">当前排队第 {{ waitingPosition }} 位</p>
          </div>
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="cs-message"
            :class="[msg.isSystem ? 'system' : (msg.isSelf ? 'self' : 'other')]"
          >
            <template v-if="msg.isSystem">
              <div class="cs-system-bubble">{{ msg.content }}</div>
            </template>
            <template v-else>
              <div class="cs-msg-bubble">
                <div class="cs-msg-sender">{{ msg.sender }}</div>
                <div class="cs-msg-content" :class="{ 'emoji-only': isPureEmoji(msg.content) }">{{ msg.content }}</div>
                <div class="cs-msg-time">{{ msg.time }}</div>
              </div>
            </template>
          </div>
        </template>
      </div>

      <div v-if="confirmed" class="cs-chat-footer">
        <div class="cs-input-wrap">
          <div class="cs-toolbar">
            <i class="el-icon-s-grid cs-toolbar-btn" title="表情" @click="toggleEmoji"></i>
          </div>
          <div v-if="emojiVisible" class="cs-emoji-picker">
            <div class="cs-emoji-grid">
              <span
                v-for="(emoji, idx) in emojiList"
                :key="idx"
                class="cs-emoji-item"
                @click="selectEmoji(emoji)"
              >
                {{ emoji }}
              </span>
            </div>
          </div>
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            placeholder="请输入您的问题..."
            :disabled="inputDisabled"
            @keyup.enter.native="handleSend"
          />
        </div>
        <el-button
          type="primary"
          size="small"
          :disabled="inputDisabled || !inputMessage.trim()"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { csConnect, getCsSessionHistory, cancelWaiting } from '@/api/cs'
import { WS_URL, MessageType } from '@/utils/chatConstants'
import ChatWebSocket from '@/utils/chatWebSocket'
import { emojiList, isPureEmoji } from '@/utils/emoji'

export default {
  name: 'GuestChatWidget',
  data() {
    return {
      isOpen: false,
      confirmed: false,
      visitorToken: localStorage.getItem('cs_visitor_token') || '',
      deviceFingerprint: localStorage.getItem('cs_device_fp') || '',
      sessionId: null,
      csUserId: null,
      csNickname: null,
      waiting: false,
      waitingForLastCs: false,
      waitingPosition: null,
      statusMessage: '',
      inputMessage: '',
      messages: [],
      wsClient: null,
      wsConnected: false,
      reconnectTimer: null,
      emojiVisible: false,
      emojiList: emojiList
    }
  },
  computed: {
    inputDisabled() {
      return this.waiting || this.waitingForLastCs
    }
  },
  beforeDestroy() {
    if (this.wsClient) {
      this.wsClient.close()
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }
  },
  methods: {
    openChat() {
      this.isOpen = true
      if (!this.deviceFingerprint) {
        this.deviceFingerprint = this.generateFingerprint()
        localStorage.setItem('cs_device_fp', this.deviceFingerprint)
      }
      if (!this.visitorToken) {
        this.visitorToken = this.generateToken()
        localStorage.setItem('cs_visitor_token', this.visitorToken)
      }
    },
    closeChat() {
      if (this.waiting || this.waitingForLastCs) {
        this.$confirm('您正在排队中，关闭后将退出排队，是否继续？', '提示', {
          confirmButtonText: '退出排队',
          cancelButtonText: '继续排队',
          type: 'warning'
        }).then(() => {
          cancelWaiting(this.visitorToken).then(() => {
            this.isOpen = false
            this.confirmed = false
            this.waiting = false
            this.waitingForLastCs = false
            this.waitingPosition = null
            if (this.reconnectTimer) {
              clearTimeout(this.reconnectTimer)
              this.reconnectTimer = null
            }
          })
        }).catch(() => {
          // 用户选择继续排队，不做任何操作
        })
        return
      }
      this.isOpen = false
      if (!this.sessionId) {
        this.confirmed = false
      }
    },
    async startConsult() {
      this.confirmed = true
      this.messages = []
      await this.doConnect()
    },
    generateToken() {
      return 'v_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
    },
    generateFingerprint() {
      const { generateFingerprint } = require('@/utils/deviceFingerprint')
      return generateFingerprint()
    },
    async doConnect() {
      try {
        const res = await csConnect({
          visitorToken: this.visitorToken,
          deviceFingerprint: this.deviceFingerprint,
          sourcePage: window.location.href
        })
        const data = res.data
        // 后端可能按IP复用了旧token，需要同步更新
        if (data.visitorToken) {
          this.visitorToken = data.visitorToken
          localStorage.setItem('cs_visitor_token', this.visitorToken)
        }
        this.waiting = data.waiting || false
        this.waitingForLastCs = data.waitingForLastCs || false
        this.waitingPosition = data.waitingPosition || null
        this.statusMessage = data.message || ''

        if (data.success) {
          this.sessionId = data.sessionId
          this.csUserId = data.csUserId
          this.csNickname = data.csNickname
          this.initWebSocket()
          this.loadHistory()
        } else if (this.waiting || this.waitingForLastCs) {
          this.initWebSocket()
          // 等待状态下定时重试分配
          this.scheduleReconnect()
        }
      } catch (e) {
        this.statusMessage = '连接失败，请稍后重试'
        console.error(e)
      }
    },
    scheduleReconnect() {
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
      }
      this.reconnectTimer = setTimeout(() => {
        if (this.isOpen && !this.sessionId) {
          this.doConnect()
        }
      }, 5000)
    },
    initWebSocket() {
      if (this.wsClient) {
        this.wsClient.close()
      }
      this.wsClient = new ChatWebSocket(WS_URL, {
        maxReconnectAttempts: 5,
        reconnectInterval: 3000,
        heartbeatInterval: 20000,
        onOpen: () => {
          this.wsConnected = true
          this.wsClient.send({
            type: MessageType.GUEST_AUTH,
            visitorToken: this.visitorToken
          })
        },
        onMessage: (msg) => {
          this.handleWsMessage(msg)
        },
        onClose: () => {
          this.wsConnected = false
        }
      })
    },
    handleWsMessage(msg) {
      if (msg.type === MessageType.AUTH_SUCCESS) {
        console.log('访客WS认证成功')
        return
      }
      if (msg.type === MessageType.CS_CHAT) {
        this.messages.push({
          sender: msg.fromUserNickname || '客服',
          content: msg.content,
          time: this.formatTime(new Date()),
          isSelf: false
        })
        this.scrollToBottom()
        return
      }
      if (msg.type === MessageType.SYSTEM_NOTICE) {
        this.messages.push({
          sender: '系统',
          content: msg.content,
          time: this.formatTime(new Date()),
          isSelf: false,
          isSystem: true
        })
        this.scrollToBottom()

        if (msg.content === '会话已结束' || msg.content === '客服已下线，会话已结束') {
          this.sessionId = null
          this.csNickname = null
          this.csUserId = null
          this.waiting = false
          this.waitingForLastCs = false
          // 保留聊天界面和消息记录，仅禁用输入，让用户看到结束提示后可重新咨询
        }
      }
      if (msg.type === MessageType.ERROR) {
        if (msg.content === '会话不存在或已结束') {
          this.sessionId = null
        }
        this.$message.error(msg.content || '发送失败')
      }
      if (msg.type === MessageType.AUTH_SUCCESS) {
        console.log('访客WS认证成功')
      }
    },
    async loadHistory() {
      if (!this.sessionId) return
      try {
        const res = await getCsSessionHistory(this.sessionId, this.visitorToken)
        const list = res.data || []
        this.messages = list.map(item => ({
          sender: item.fromType === 1 ? '我' : (item.fromType === 2 ? '客服' : '系统'),
          content: item.content,
          time: this.formatTime(item.createTime),
          isSelf: item.fromType === 1,
          isSystem: item.fromType === 3
        }))
        this.scrollToBottom()
      } catch (e) {
        console.error(e)
      }
    },
    async handleSend() {
      const content = this.inputMessage.trim()
      if (!content || this.inputDisabled) return

      if (!this.sessionId) {
        await this.doConnect()
        if (!this.sessionId) {
          this.$message.warning('暂时无法连接客服，请稍后重试')
          return
        }
      }

      if (this.wsClient && this.wsConnected) {
        this.wsClient.send({
          type: MessageType.CS_CHAT,
          sessionId: String(this.sessionId),
          visitorToken: this.visitorToken,
          content: content,
          messageId: 'msg_' + Date.now()
        })
      }

      this.messages.push({
        sender: '我',
        content: content,
        time: this.formatTime(new Date()),
        isSelf: true
      })
      this.inputMessage = ''
      this.scrollToBottom()
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messageContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    },
    toggleEmoji() {
      this.emojiVisible = !this.emojiVisible
    },
    selectEmoji(emoji) {
      this.inputMessage += emoji
      this.emojiVisible = false
    },
    isPureEmoji(content) {
      return isPureEmoji(content)
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
.guest-cs-widget {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 9999;
}
.cs-float-btn {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
  transition: all 0.3s;
}
.cs-float-btn:hover {
  transform: scale(1.05);
}
.cs-float-btn i {
  font-size: 24px;
}
.cs-label {
  font-size: 10px;
  margin-top: 2px;
}
.cs-chat-window {
  width: 360px;
  height: 500px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.cs-chat-header {
  padding: 12px 16px;
  background: #409eff;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cs-title {
  font-weight: bold;
  font-size: 16px;
}
.cs-subtitle {
  margin-left: 8px;
  font-size: 12px;
  opacity: 0.9;
}
.cs-status {
  margin-left: 8px;
  font-size: 12px;
}
.cs-status.waiting {
  color: #ffe58f;
}
.cs-close {
  cursor: pointer;
  font-size: 18px;
}
.cs-chat-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  background: #f5f5f5;
}
.cs-empty-tip {
  text-align: center;
  color: #999;
  margin-top: 40px;
}
.cs-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #606266;
}
.cs-welcome i {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 16px;
}
.cs-welcome-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}
.cs-welcome-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 20px;
}
.cs-message {
  margin-bottom: 12px;
  display: flex;
}
.cs-message.self {
  justify-content: flex-end;
}
.cs-message.other {
  justify-content: flex-start;
}
.cs-message.system {
  justify-content: center;
}
.cs-system-bubble {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 12px;
  background: #dcdfe6;
  color: #606266;
  font-size: 12px;
  max-width: 80%;
  text-align: center;
}
.cs-msg-bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff;
  word-break: break-word;
}
.cs-message.self .cs-msg-bubble {
  background: #409eff;
  color: #fff;
}
.cs-msg-sender {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.cs-message.self .cs-msg-sender {
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
.cs-chat-footer {
  padding: 8px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.cs-chat-footer .el-textarea {
  flex: 1;
}
.cs-input-wrap {
  flex: 1;
  position: relative;
}
.cs-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.cs-toolbar-btn {
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}
.cs-toolbar-btn:hover {
  color: #409eff;
  background: #f5f7fa;
}
.cs-emoji-picker {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  width: 260px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  padding: 8px;
  z-index: 99999;
}
.cs-emoji-grid {
  display: grid;
  grid-template-columns: repeat(7, 34px);
  gap: 2px;
  justify-content: center;
  max-height: 180px;
  overflow-y: auto;
  padding-right: 4px;
}
.cs-emoji-item {
  font-size: 20px;
  cursor: pointer;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  user-select: none;
}
.cs-emoji-item:hover {
  background-color: #f0f0f0;
}
.cs-msg-content.emoji-only {
  font-size: 28px;
  line-height: 1.2;
}
</style>
