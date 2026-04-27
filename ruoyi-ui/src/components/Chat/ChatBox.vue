<template>
  <div class="chat-container">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <h3>会话列表</h3>
        <el-button size="mini" icon="el-icon-plus" @click="showNewChatDialog">新建</el-button>
      </div>

      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: currentSessionId === session.sessionId }"
          @click="selectSession(session)"
        >
          <div class="session-avatar">
            <img :src="session.avatar || defaultAvatar" alt="" />
            <span v-if="session.unreadCount > 0" class="unread-badge">
              {{ session.unreadCount }}
            </span>
          </div>
          <div class="session-info">
            <div class="session-name">{{ session.sessionName }}</div>
            <div class="session-last-message">{{ session.lastMessage }}</div>
          </div>
          <div class="session-time">{{ formatTime(session.lastMessageTime) }}</div>
        </div>
      </div>
    </div>

    <div class="chat-main">
      <div v-if="currentSessionId" class="chat-header">
        <div class="chat-title">
          <h3>{{ currentSession.sessionName }}</h3>
          <span :class="['connection-status', wsConnected ? 'connected' : 'disconnected']">
            {{ wsConnected ? '已连接' : '未连接' }}
          </span>
        </div>
        <div class="chat-actions">
          <el-button size="mini" icon="el-icon-more" @click="showSessionOptions"></el-button>
        </div>
      </div>

      <div ref="messagesContainer" class="chat-messages" @scroll="handleScroll">
        <div v-if="loadingHistory" class="loading-tip">
          <i class="el-icon-loading"></i> 加载历史消息中...
        </div>

        <div v-for="(message, index) in messages" :key="message.messageId || ('msg-' + index)"
             class="message-wrapper"
             :class="message.fromUserId === currentUserId ? 'sent' : 'received'">

          <div class="message-avatar">
            <img :src="defaultAvatar" alt="" />
          </div>

          <div class="message-bubble">
            <div class="message-header">
              <span class="sender">{{ message.fromUserNickname || '用户' + message.fromUserId }}</span>
              <span class="timestamp">{{ formatTimestamp(message.createTime) }}</span>
            </div>
            <div class="message-content">
              <div v-if="getMessageContentType(message) === 'TEXT'" class="text-content">
                {{ message.content }}
              </div>
              <img v-else-if="getMessageContentType(message) === 'IMAGE'"
                   :src="message.content"
                   class="message-image"
                   @click="previewImage(message.content)" />
              <div v-else-if="getMessageContentType(message) === 'EMOJI'" class="emoji-content">
                {{ message.content }}
              </div>
              <a v-else-if="getMessageContentType(message) === 'FILE'"
                 :href="message.content"
                 download
                 class="file-link">
                📎 {{ message.extraData || '文件' }}
              </a>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <div class="input-toolbar">
          <el-button size="mini" icon="el-icon-picture" @click="selectImage"></el-button>
          <el-button size="mini" icon="el-icon-files" @click="selectFile"></el-button>
          <el-button size="mini" icon="el-icon-s-grid" @click="showEmojiPicker"></el-button>
        </div>
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="输入消息..."
          @keyup.enter.native="handleEnter"
        ></el-input>
        <div class="input-actions">
          <el-button size="small" @click="clearInput">清空</el-button>
          <el-button size="small" type="primary" @click="sendMessage" :disabled="!inputMessage.trim()">
            发送
          </el-button>
        </div>
      </div>
    </div>

    <el-dialog title="新建会话" :visible.sync="newChatDialogVisible" width="500px">
      <el-tabs v-model="newChatType">
        <el-tab-pane label="私聊" name="private">
          <el-select v-model="selectedUserId" placeholder="选择联系人" style="width: 100%">
            <el-option
              v-for="user in onlineUsers"
              :key="user.userId"
              :label="user.userName"
              :value="user.userId"
            >
              <span>{{ user.userName }}</span>
              <span style="float: right; color: #67c23a">● 在线</span>
            </el-option>
          </el-select>
        </el-tab-pane>
        <el-tab-pane label="群聊" name="group">
          <el-input v-model="newGroupName" placeholder="请输入群名称"></el-input>
          <el-checkbox-group v-model="selectedMembers" style="margin-top: 10px">
            <el-checkbox
              v-for="user in onlineUsers"
              :key="user.userId"
              :label="user.userId"
            >
              {{ user.userName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-tab-pane>
      </el-tabs>
      <div slot="footer">
        <el-button @click="newChatDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createNewSession">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import ChatWebSocket from '@/utils/chatWebSocket';
import { WS_URL, MessageType, ContentType } from '@/utils/chatConstants';
import {
  getUserSessions,
  createPrivateSession,
  createGroupSession,
  getSessionMessages,
  markMessageAsRead
} from '@/api/chat';
import { getToken } from '@/utils/auth';
import request from '@/utils/request';

export default {
  name: 'ChatBox',
  data() {
    return {
      chatClient: null,
      wsConnected: false,
      currentUserId: null,
      sessions: [],
      currentSessionId: null,
      currentSession: null,
      messages: [],
      inputMessage: '',
      newChatDialogVisible: false,
      newChatType: 'private',
      selectedUserId: null,
      newGroupName: '',
      selectedMembers: [],
      onlineUsers: [],
      defaultAvatar: require('@/assets/images/profile.jpg'),

      currentPage: 1,
      pageSize: 50,
      hasMore: true,
      loadingHistory: false,
      scrollTimer: null
    };
  },
  created() {
    const userInfo = this.$store.state.user || JSON.parse(localStorage.getItem('userInfo') || '{}');
    this.currentUserId = userInfo.userId || userInfo.id || userInfo.user_id;
    console.log('当前用户ID:', this.currentUserId);
    console.log('用户信息:', userInfo);
  },
  mounted() {
    this.initChat();
    this.loadSessions();
    this.loadOnlineUsers();
  },
  beforeDestroy() {
    if (this.chatClient) {
      this.chatClient.close();
    }
  },
  methods: {
    initChat() {
      const token = getToken();
      const wsUrl = WS_URL;

      this.chatClient = new ChatWebSocket(wsUrl, {
        maxReconnectAttempts: 5,
        reconnectInterval: 3000,
        heartbeatInterval: 20000,

        onOpen: () => {
          console.log('聊天连接已建立');
          this.wsConnected = true;

          const authMessage = {
            type: 'AUTH',
            fromUserId: this.currentUserId
          };
          console.log('准备发送认证消息:', authMessage);

          setTimeout(() => {
            console.log('开始发送认证消息...');
            const result = this.chatClient.send(authMessage);
            console.log('认证消息发送结果:', result);
          }, 200);

          this.$message.success('聊天服务已连接');
        },

        onMessage: (message) => {
          console.log('收到WebSocket消息:', message);

          if (message.type === 'AUTH_SUCCESS' || message.type === 'AUTH') {
            console.log('认证成功');
            // 认证成功后，重置重连计数器
            if (this.chatClient) {
              this.chatClient.reconnectAttempts = 0;
            }
            return;
          }
          if (message.type === 'AUTH_FAILED') {
            console.error('认证失败:', message.content);
            this.$message.error('认证失败：' + message.content);
            return;
          }

          this.handleIncomingMessage(message);
        },

        onClose: () => {
          console.log('聊天连接已断开');
          this.wsConnected = false;
          this.$message.warning('聊天服务已断开');
        },

        onError: () => {
          console.error('聊天连接错误');
          this.wsConnected = false;
          this.$message.error('聊天服务连接失败');
        }
      });
    },

    async loadSessions() {
      try {
        const response = await getUserSessions();
        this.sessions = response.data || [];
      } catch (error) {
        console.error('加载会话列表失败:', error);
      }
    },

    async loadOnlineUsers() {
      try {
        const response = await request({
          url: '/system/user/list',
          method: 'get'
        });
        this.onlineUsers = response.rows || [];
      } catch (error) {
        console.error('加载用户列表失败:', error);
      }
    },

    selectSession(session) {
      this.currentSessionId = session.sessionId;
      this.currentSession = session;
      this.loadMessages(session.sessionId);
      this.markMessagesAsRead(session.sessionId);
    },

    async loadMessages(sessionId) {
      this.messages = [];
      this.currentPage = 1;
      this.hasMore = true;

      await this.loadMoreMessages();
    },

    async loadMoreMessages() {
      if (!this.hasMore || this.loadingHistory) {
        return;
      }

      this.loadingHistory = true;
      console.log('开始加载第', this.currentPage, '页消息，当前消息总数:', this.messages.length);

      try {
        const response = await getSessionMessages(this.currentSessionId, {
          pageNum: this.currentPage,
          pageSize: this.pageSize
        });

        let newMessages = response.rows || response.data || [];
        console.log('第', this.currentPage, '页返回消息数:', newMessages.length, '消息内容:', newMessages.map(m => m.content));

        if (newMessages.length > 0) {
          // 去重
          const existingIds = new Set(this.messages.map(m => m.messageId));
          const uniqueNewMessages = newMessages.filter(m => !existingIds.has(m.messageId));

          if (uniqueNewMessages.length > 0) {
            // 后端返回的是降序（最新在前），需要反转为升序（旧在前）
            uniqueNewMessages.reverse();
            console.log('反转后的消息:', uniqueNewMessages.map(m => m.content));

            if (this.currentPage === 1) {
              // 第 1 页：最新的消息，直接赋值（已经是升序：旧→新）
              this.messages = uniqueNewMessages;
              console.log('第 1 页加载完成，消息数:', this.messages.length);
            } else {
              // 第 2 页及以后：更早的消息，拼接到前面
              this.messages = [...uniqueNewMessages, ...this.messages];
              console.log('第', this.currentPage, '页加载完成，新增:', uniqueNewMessages.length, '总消息数:', this.messages.length);
            }
          } else {
            console.log('所有消息已存在，跳过拼接');
          }

          if (newMessages.length < this.pageSize) {
            this.hasMore = false;
            console.log('没有更多历史消息了');
          }

          this.currentPage++;
        } else {
          this.hasMore = false;
          console.log('返回空数组，没有更多消息');
        }

        this.$nextTick(() => {
          if (this.currentPage === 2) {
            this.scrollToBottom();
          }
        });
      } catch (error) {
        console.error('加载消息失败:', error);
      } finally {
        this.loadingHistory = false;
      }
    },

    handleScroll() {
      const container = this.$refs.messagesContainer;
      if (!container) return;

      // 当滚动到顶部附近时，加载更多
      if (container.scrollTop < 50 && this.hasMore && !this.loadingHistory) {
        this.loadMoreMessages();
      }
    },

    async sendMessage() {
      if (!this.inputMessage.trim() || !this.currentSessionId) {
        return;
      }

      const messageContent = this.inputMessage.trim();

      try {
        const response = await request({
          url: '/chat/message/send',
          method: 'post',
          data: {
            sessionId: this.currentSessionId,
            messageType: 1,
            content: messageContent
          }
        });

        if (response.code === 200) {
          const message = response.data;
          console.log('HTTP返回的消息:', message);
          this.messages.push(message);
          this.inputMessage = '';
          this.$nextTick(() => {
            this.scrollToBottom();
          });

          if (this.chatClient && this.wsConnected) {
            this.chatClient.send({
              type: this.currentSession.sessionType === 'GROUP' ? MessageType.GROUP_CHAT : MessageType.PRIVATE_CHAT,
              messageId: message.messageId,
              sessionId: this.currentSessionId,
              fromUserId: this.currentUserId,
              contentType: ContentType.TEXT,
              content: messageContent,
              timestamp: new Date().toISOString()
            });
          }
        }
      } catch (error) {
        console.error('发送消息失败:', error);
        this.$message.error('发送消息失败');
      }
    },

    getMessageContentType(message) {
      // 优先使用 contentType（字符串），兼容 messageType（数字）
      if (message.contentType) {
        return message.contentType;
      }
      const typeMap = {
        1: 'TEXT',
        2: 'IMAGE',
        3: 'EMOJI',
        4: 'FILE',
        99: 'SYSTEM'
      };
      return typeMap[message.messageType] || 'TEXT';
    },

    handleIncomingMessage(message) {
      console.log('=== 收到 WebSocket 推送 ===', message);
      console.log('当前会话 ID:', this.currentSessionId);
      console.log('消息会话 ID:', message.sessionId);

      switch (message.type) {
        case MessageType.PRIVATE_CHAT:
        case MessageType.GROUP_CHAT:
        case 'PRIVATE_CHAT':
        case 'GROUP_CHAT':
          if (String(message.sessionId) === String(this.currentSessionId)) {
            if (Number(message.fromUserId) === Number(this.currentUserId)) {
              console.log('收到自己发送的消息回显，跳过渲染');
              this.markMessagesAsRead(message.messageId);
              return;
            }

            console.log('收到对方消息，准备渲染:', message.content);
            console.log('push 前 messages 长度:', this.messages.length);
            console.log('消息完整数据:', JSON.stringify(message));

            // 创建新消息对象，确保 messageId 存在
            const newMessage = Object.assign({}, message);
            if (!newMessage.messageId) {
              newMessage.messageId = 'ws_' + Date.now() + '_' + Math.random();
            }

            // 如果消息中没有昵称，尝试从会话列表中获取
            if (!newMessage.fromUserNickname && this.currentSession) {
              if (this.currentSession.sessionType === 'PRIVATE') {
                // 私聊：对方就是会话的另一方
                newMessage.fromUserNickname = this.currentSession.sessionName;
              } else {
                // 群聊：需要查找成员昵称
                newMessage.fromUserNickname = '用户' + newMessage.fromUserId;
              }
            }

            // 使用展开运算符创建新数组，触发 Vue 响应式更新
            this.messages = [...this.messages, newMessage];

            console.log('更新后 messages 长度:', this.messages.length);
            console.log('最后一条消息:', this.messages[this.messages.length - 1]);

            this.$nextTick(() => {
              console.log('$nextTick 后 messages 长度:', this.messages.length);
              this.scrollToBottom();
            });

            this.markMessagesAsRead(message.messageId);
          } else {
            console.log('消息属于其他会话，更新未读数');
            this.updateSessionUnreadCount(message.sessionId);
          }
          break;
        case MessageType.SYSTEM_NOTICE:
          this.$message.info(message.content);
          break;
        case MessageType.USER_ONLINE:
        case MessageType.USER_OFFLINE:
          this.loadOnlineUsers();
          break;
        case MessageType.ERROR:
          this.$message.error(message.content);
          break;
      }
    },

    async markMessagesAsRead(messageId) {
      try {
        await markMessageAsRead(messageId);
      } catch (error) {
        console.error('标记已读失败:', error);
      }
    },

    scrollToBottom() {
      const container = this.$refs.messagesContainer;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    },

    formatTimestamp(timestamp) {
      if (!timestamp) return '';
      const date = new Date(timestamp);
      if (isNaN(date.getTime())) return '';

      const now = new Date();
      const diff = now - date;

      if (diff < 60000) {
        return '刚刚';
      } else if (diff < 3600000) {
        return Math.floor(diff / 60000) + '分钟前';
      } else if (diff < 86400000) {
        return Math.floor(diff / 3600000) + '小时前';
      } else {
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
      }
    },

    formatTime(timestamp) {
      if (!timestamp) return '';
      const date = new Date(timestamp);
      if (isNaN(date.getTime())) return '';
      return date.toLocaleDateString();
    },

    showNewChatDialog() {
      this.newChatDialogVisible = true;
    },

    async createNewSession() {
      try {
        if (this.newChatType === 'private') {
          if (!this.selectedUserId) {
            this.$message.warning('请选择联系人');
            return;
          }
          await createPrivateSession({ userId2: this.selectedUserId });
        } else {
          if (!this.newGroupName || this.selectedMembers.length === 0) {
            this.$message.warning('请填写群名称并选择成员');
            return;
          }
          await createGroupSession({
            sessionName: this.newGroupName,
            memberIds: this.selectedMembers
          });
        }
        this.$message.success('创建成功');
        this.newChatDialogVisible = false;
        this.loadSessions();
      } catch (error) {
        console.error('创建会话失败:', error);
      }
    },

    selectImage() {
      this.$message.info('图片上传功能待实现');
    },

    selectFile() {
      this.$message.info('文件上传功能待实现');
    },

    showEmojiPicker() {
      this.$message.info('表情选择功能待实现');
    },

    previewImage(url) {
      window.open(url, '_blank');
    },

    handleEnter(event) {
      if (!event.shiftKey) {
        event.preventDefault();
        this.sendMessage();
      }
    },

    clearInput() {
      this.inputMessage = '';
    },

    showSessionOptions() {
      this.$message.info('会话选项功能待实现');
    },

    updateSessionUnreadCount(sessionId) {
      const session = this.sessions.find(function(s) { return s.sessionId === sessionId; });
      if (session) {
        session.unreadCount = (session.unreadCount || 0) + 1;
      }
    }
  }
};
</script>

<style scoped>
.loading-tip {
  text-align: center;
  padding: 10px;
  color: #909399;
  font-size: 12px;
}

.chat-container {
  display: flex;
  height: calc(100vh - 120px);
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
  background-color: #fff;
}

.chat-sidebar {
  width: 280px;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: background-color 0.3s;
  border-bottom: 1px solid #f0f0f0;
}

.session-item:hover {
  background-color: #e8e8e8;
}

.session-item.active {
  background-color: #d9d9d9;
}

.session-avatar {
  position: relative;
  margin-right: 12px;
}

.session-avatar img {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.unread-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background-color: #ff4d4f;
  color: white;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 12px;
  min-width: 18px;
  text-align: center;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-name {
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-last-message {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-title h3 {
  margin: 0;
  font-size: 16px;
}

.connection-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.connection-status.connected {
  background-color: #d4edda;
  color: #155724;
}

.connection-status.disconnected {
  background-color: #f8d7da;
  color: #721c24;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f5f5f5;
}

.message-wrapper {
  margin-bottom: 20px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.message-wrapper.sent {
  flex-direction: row-reverse;
}

.message-wrapper.received {
  flex-direction: row;
}

.message-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}

.message-avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.message-bubble {
  max-width: 70%;
  display: flex;
  flex-direction: column;
}

.message-wrapper.sent .message-bubble {
  align-items: flex-end;
}

.message-wrapper.received .message-bubble {
  align-items: flex-start;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
}

.message-wrapper.received .message-header {
  justify-content: flex-start;
}

.message-wrapper.sent .message-header {
  justify-content: flex-end;
}

.sender {
  color: #666;
  font-weight: 500;
  font-size: 13px;
}

.timestamp {
  color: #999;
  font-size: 11px;
}

.message-content {
  background-color: white;
  padding: 10px 14px;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  word-wrap: break-word;
  line-height: 1.5;
}

.message-wrapper.sent .message-content {
  background-color: #1890ff;
  color: white;
  border-bottom-right-radius: 4px;
}

.message-wrapper.received .message-content {
  background-color: white;
  color: #333;
  border-bottom-left-radius: 4px;
}

.text-content {
  word-wrap: break-word;
}

.message-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s;
}

.message-image:hover {
  transform: scale(1.02);
}

.emoji-content {
  font-size: 28px;
  line-height: 1.2;
}

.file-link {
  color: #1890ff;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.file-link:hover {
  text-decoration: underline;
}

.chat-input {
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background-color: white;
}

.input-toolbar {
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
}

.input-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
