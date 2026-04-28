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
              <span class="cs-session-name">{{ session.visitorNickname || '访客 ' + session.visitorId }}</span>
              <span class="cs-session-time">{{ formatTime(session.startTime) }}</span>
            </div>
            <div class="cs-session-preview">{{ lastMessage(session.id) }}</div>
          </div>
          <div v-if="unreadMap[session.id]" class="cs-unread-badge">{{ unreadMap[session.id] }}</div>
          <div class="cs-session-actions">
            <el-button size="mini" type="warning" @click.stop="openTransferDialog(session)">转接</el-button>
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
            <span class="cs-chat-header-name">{{ currentSession.visitorNickname || '访客 ' + currentSession.visitorId }}</span>
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
                <div class="cs-msg-content" :class="{ 'emoji-only': isPureEmoji(msg.content) }">{{ msg.content }}</div>
                <div class="cs-msg-time">{{ msg.time }}</div>
              </div>
              <div v-if="msg.isSelf" class="cs-msg-avatar cs-avatar-cs">{{ msg.isMe ? '我' : (msg.sender ? msg.sender.charAt(0) : '客') }}</div>
            </template>
          </div>
        </div>
        <div class="cs-chat-input">
          <div class="cs-input-wrap">
            <div class="cs-toolbar">
              <i class="el-icon-orange cs-toolbar-btn" title="表情" @click="toggleEmoji"></i>
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
              :rows="3"
              placeholder="请输入回复，按 Enter 发送..."
              @keyup.enter.native="sendMessage"
            />
          </div>
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

    <!-- 转接弹窗 -->
    <el-dialog title="转接会话" :visible.sync="transferDialogVisible" width="400px" @closed="resetTransferDialog">
      <el-form label-width="80px">
        <el-form-item label="目标客服">
          <el-select v-model="transferTargetId" placeholder="请选择客服" style="width: 100%">
            <el-option
              v-for="staff in onlineStaffList"
              :key="staff.userId"
              :label="staff.nickName + ' (' + staff.activeCount + '/' + staff.maxSessions + ')'"
              :value="staff.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转接原因">
          <el-input
            v-model="transferReason"
            type="textarea"
            :rows="3"
            placeholder="请输入转接原因（对方客服可见）"
          />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferLoading" @click="submitTransfer">确定转接</el-button>
      </span>
    </el-dialog>

    <!-- 右侧访客信息 -->
    <div class="cs-info-panel">
      <div class="cs-info-header">访客信息</div>
      <div v-if="currentSession" class="cs-info-body">
        <div class="cs-info-item">
          <div class="cs-info-label">访客ID</div>
          <div class="cs-info-value">{{ currentSession.visitorId }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">访客昵称</div>
          <div class="cs-info-value">{{ visitorDetail ? visitorDetail.nickname : '-' }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">绑定用户</div>
          <div class="cs-info-value">
            <el-tag v-if="visitorDetail && visitorDetail.boundUserId" size="small" type="success">{{ visitorDetail.boundUserNickName || visitorDetail.boundUserId }}</el-tag>
            <span v-else class="cs-info-muted">未绑定</span>
          </div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">IP地址</div>
          <div class="cs-info-value">{{ visitorDetail ? visitorDetail.ip : '-' }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">设备指纹</div>
          <div class="cs-info-value cs-info-ellipsis" :title="visitorDetail ? visitorDetail.deviceFingerprint : ''">{{ visitorDetail ? visitorDetail.deviceFingerprint : '-' }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">浏览器 / 设备</div>
          <div class="cs-info-value cs-info-ellipsis" :title="visitorDetail ? visitorDetail.userAgent : ''">{{ visitorDetail ? visitorDetail.userAgent : '-' }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">来源页面</div>
          <div class="cs-info-value cs-info-ellipsis" :title="visitorDetail ? visitorDetail.sourcePage : ''">{{ visitorDetail ? visitorDetail.sourcePage : '-' }}</div>
        </div>
        <div class="cs-info-item">
          <div class="cs-info-label">首次访问</div>
          <div class="cs-info-value">{{ visitorDetail ? formatFullTime(visitorDetail.createTime) : '-' }}</div>
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
import { getWorkbenchSessions, closeCsSession, csOnline, csOffline, getCsSessionMessages, readCsSession, getWaitingCount, getVisitorHistoryMessages, getMyCsStatus, getVisitorDetail, getOnlineStaff, requestTransfer, acceptTransfer, rejectTransfer } from '@/api/cs'
import { WS_URL, MessageType } from '@/utils/chatConstants'
import ChatWebSocket from '@/utils/chatWebSocket'
import { getToken } from '@/utils/auth'
import { emojiList, isPureEmoji } from '@/utils/emoji'

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
      waitingPollTimer: null,
      emojiVisible: false,
      emojiList: emojiList,
      visitorDetail: null,
      transferDialogVisible: false,
      transferTargetId: null,
      transferReason: '',
      onlineStaffList: [],
      transferSessionId: null,
      transferLoading: false
    }
  },
  computed: {
    currentSession() {
      return this.activeSessions.find(s => s.id === this.currentSessionId)
    }
  },
  mounted() {
    this.$store.dispatch('csNotice/setWorkbenchVisible', true)
    this.initWorkbench()
  },
  activated() {
    this.$store.dispatch('csNotice/setWorkbenchVisible', true)
    this.initWorkbench()
  },
  deactivated() {
    this.$store.dispatch('csNotice/setWorkbenchVisible', false)
    this.destroyWorkbench()
  },
  beforeDestroy() {
    this.$store.dispatch('csNotice/setWorkbenchVisible', false)
    this.destroyWorkbench()
  },
  methods: {
    initWorkbench() {
      this.loadMyStatus().then(() => {
        if (this.online) {
          this.loadSessions()
          if (!this.wsClient) {
            this.initWebSocket()
          }
        } else {
          // 客服不在线，关闭WS、清空会话列表，但保留排队轮询
          if (this.wsClient) {
            this.wsClient.close()
            this.wsClient = null
          }
          this.activeSessions = []
          if (this.currentSessionId) {
            this.currentSessionId = null
            this.currentMessages = []
            this.visitorDetail = null
          }
        }
        // 无论在线与否都启动排队轮询，让客服实时看到排队情况
        if (!this.waitingPollTimer) {
          this.startWaitingPoll()
        }
        this.$store.dispatch('csNotice/clearUnread')
      })
    },
    destroyWorkbench() {
      if (this.wsClient) {
        this.wsClient.close()
        this.wsClient = null
      }
      if (this.waitingPollTimer) {
        clearInterval(this.waitingPollTimer)
        this.waitingPollTimer = null
      }
    },
    loadMyStatus() {
      return getMyCsStatus().then(res => {
        const data = res.data || {}
        this.online = !!data.online
        this.maxSessions = data.maxSessions || 5
      }).catch(() => {
        this.online = false
      })
    },
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
      this.loadVisitorDetail(session.visitorId)
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
          sender: item.senderName || (item.fromType === 2 ? '我' : (item.fromType === 1 ? '访客' : '系统')),
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
        const currentUserId = Number(this.$store.state.user.id)
        const currentMapped = list.map(item => ({
          sender: item.senderName || (item.fromType === 2 ? '我' : (item.fromType === 1 ? '访客' : '系统')),
          content: item.content,
          time: this.formatTime(item.createTime),
          isSelf: item.fromType === 2,
          isMe: item.fromType === 2 && Number(item.fromUserId) === currentUserId,
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
              sender: item.senderName || (item.fromType === 2 ? '客服' : (item.fromType === 1 ? '访客' : '系统')),
              content: item.content,
              time: this.formatTime(item.createTime),
              isSelf: item.fromType === 2,
              isMe: item.fromType === 2 && Number(item.fromUserId) === currentUserId,
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
          isSelf: true,
          isMe: true
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
            this.visitorDetail = null
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
            this.visitorDetail = null
          }
          // 只关闭WS，保留排队轮询，让客服继续看到排队人数
          if (this.wsClient) {
            this.wsClient.close()
            this.wsClient = null
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
        if (!this.wsClient) {
          this.initWebSocket()
        }
        if (!this.waitingPollTimer) {
          this.startWaitingPoll()
        }
        this.loadSessions()
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
        const currentUserId = Number(this.$store.state.user.id)
        const fromUserId = Number(msg.fromUserId) || 0
        const isCs = fromUserId > 0 && msg.fromUserNickname !== '访客'
        this.addMessage(sessionId, {
          sender: msg.fromUserNickname || '访客',
          content: msg.content,
          time: this.formatTime(new Date()),
          isSelf: isCs,
          isMe: fromUserId === currentUserId
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
          // 非当前会话收到消息：顶部铃铛+1，播放提示音
          this.$store.dispatch('csNotice/incrementUnread')
          this.playNotifySound()
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
      if (msg.type === MessageType.CS_TRANSFER_REQUEST) {
        this.handleTransferRequest(msg)
        return
      }
      if (msg.type === MessageType.CS_TRANSFER_ACCEPT) {
        this.handleTransferAccept(msg)
        return
      }
      if (msg.type === MessageType.CS_TRANSFER_REJECT) {
        this.handleTransferReject(msg)
        return
      }
      console.warn('工作台收到未知消息类型:', msg.type)
    },
    playNotifySound() {
      try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext
        if (!AudioCtx) return
        const ctx = new AudioCtx()
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.type = 'sine'
        osc.frequency.setValueAtTime(880, ctx.currentTime)
        osc.frequency.exponentialRampToValueAtTime(440, ctx.currentTime + 0.15)
        gain.gain.setValueAtTime(0.3, ctx.currentTime)
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.15)
        osc.start(ctx.currentTime)
        osc.stop(ctx.currentTime + 0.15)
      } catch (e) {
        console.warn('播放提示音失败', e)
      }
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
    loadVisitorDetail(visitorId) {
      this.visitorDetail = null
      getVisitorDetail(visitorId).then(res => {
        this.visitorDetail = res.data || null
      }).catch(() => {
        this.visitorDetail = null
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
    openTransferDialog(session) {
      this.transferSessionId = session.id
      this.transferDialogVisible = true
      this.transferLoading = true
      getOnlineStaff().then(res => {
        this.onlineStaffList = res.data || []
      }).catch(() => {
        this.$message.error('获取在线客服列表失败')
      }).finally(() => {
        this.transferLoading = false
      })
    },
    resetTransferDialog() {
      this.transferTargetId = null
      this.transferReason = ''
      this.transferSessionId = null
      this.onlineStaffList = []
    },
    submitTransfer() {
      if (!this.transferTargetId) {
        this.$message.warning('请选择目标客服')
        return
      }
      this.transferLoading = true
      requestTransfer({
        sessionId: this.transferSessionId,
        targetCsUserId: this.transferTargetId,
        reason: this.transferReason
      }).then(() => {
        this.$message.success('转接请求已发送，等待对方确认')
        this.transferDialogVisible = false
      }).catch(err => {
        this.$message.error(err.response?.data?.msg || '转接请求发送失败')
      }).finally(() => {
        this.transferLoading = false
      })
    },
    handleTransferRequest(msg) {
      const transferId = msg.messageId
      const fromCsNickname = msg.fromUserNickname || '客服'
      const visitorNickname = msg.extra?.visitorNickname || '访客'
      const reason = msg.content || ''
      const h = this.$createElement
      const notifyInstance = this.$notify({
        title: '收到转接请求',
        message: h('div', [
          h('p', `${fromCsNickname} 希望将访客 ${visitorNickname} 转接给您`),
          reason ? h('p', `原因：${reason}`) : null,
          h('div', { style: 'margin-top: 10px; text-align: right;' }, [
            h('el-button', {
              props: { size: 'mini' },
              on: {
                click: () => {
                  notifyInstance.close()
                  rejectTransfer({ transferId }).then(() => {
                    this.$message.info('已拒绝转接请求')
                  }).catch(() => {
                    this.$message.error('操作失败')
                  })
                }
              }
            }, '拒绝'),
            h('el-button', {
              props: { size: 'mini', type: 'primary' },
              on: {
                click: () => {
                  notifyInstance.close()
                  acceptTransfer({ transferId }).then(() => {
                    this.$message.success('已接受转接')
                    this.loadSessions()
                  }).catch(err => {
                    this.$message.error(err.response?.data?.msg || '接受转接失败')
                  })
                }
              }
            }, '接受')
          ])
        ]),
        duration: 0,
        position: 'top-right'
      })
    },
    handleTransferAccept(msg) {
      const sessionId = parseInt(msg.sessionId)
      const newSessionId = msg.extra?.newSessionId
      const toCsNickname = msg.extra?.toCsNickname || '客服'
      this.$message.success(`会话已成功转接给 ${toCsNickname}`)
      // 从列表中移除该会话
      this.activeSessions = this.activeSessions.filter(s => s.id !== sessionId)
      delete this.messageMap[sessionId]
      delete this.unreadMap[sessionId]
      if (this.currentSessionId === sessionId) {
        this.currentSessionId = null
        this.currentMessages = []
        this.visitorDetail = null
      }
    },
    handleTransferReject() {
      this.$message.warning('对方拒绝了转接请求')
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
.cs-info-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cs-info-muted {
  color: #c0c4cc;
  font-size: 13px;
}
</style>
