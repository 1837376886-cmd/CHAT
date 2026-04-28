<template>
  <div class="app-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span>我的客服历史</span>
      </div>
      <el-table v-loading="loading" :data="historyList">
        <el-table-column label="会话ID" prop="id" width="80" />
        <el-table-column label="访客" prop="visitorNickname" />
        <el-table-column label="开始时间" prop="startTime" />
        <el-table-column label="结束时间" prop="endTime" />
        <el-table-column label="状态" width="100">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template slot-scope="scope">
            <el-button size="mini" @click="viewDetail(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-dialog title="消息记录" :visible.sync="detailVisible" width="600px">
      <div class="chat-history">
        <div
          v-for="(msg, index) in detailMessages"
          :key="index"
          class="chat-msg"
          :class="[msg.fromType === 2 ? 'self' : (msg.fromType === 3 ? 'system' : 'other')]"
        >
          <template v-if="msg.fromType === 3">
            <div class="system-bubble">{{ msg.content }}</div>
          </template>
          <template v-else>
            <div class="msg-bubble">
              <div class="msg-sender">{{ msg.senderName || (msg.fromType === 2 ? '客服' : '访客') }}</div>
              <div class="msg-content" :class="{ 'emoji-only': isPureEmoji(msg.content) }">{{ msg.content }}</div>
              <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
            </div>
          </template>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getCsMyHistory, getCsSessionMessages } from '@/api/cs'
import { isPureEmoji } from '@/utils/emoji'

export default {
  name: 'CsMyHistory',
  data() {
    return {
      loading: false,
      historyList: [],
      total: 0,
      queryParams: {
        pageNum: 1,
        pageSize: 10
      },
      detailVisible: false,
      detailMessages: []
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      getCsMyHistory(this.queryParams).then(res => {
        const data = res.data || {}
        this.historyList = data.rows || []
        this.total = data.total || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    viewDetail(row) {
      getCsSessionMessages(row.id).then(res => {
        this.detailMessages = res.data || []
        this.detailVisible = true
      })
    },
    isPureEmoji(content) {
      return isPureEmoji(content)
    },
    formatTime(timeStr) {
      if (!timeStr) return ''
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
.chat-history {
  max-height: 500px;
  overflow-y: auto;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}
.chat-msg {
  margin-bottom: 16px;
  display: flex;
}
.chat-msg.self {
  justify-content: flex-end;
}
.chat-msg.other {
  justify-content: flex-start;
}
.chat-msg.system {
  justify-content: center;
}
.system-bubble {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 12px;
  background: #dcdfe6;
  color: #606266;
  font-size: 12px;
}
.msg-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.chat-msg.self .msg-bubble {
  background: #409eff;
  color: #fff;
}
.msg-sender {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
  font-weight: 600;
}
.chat-msg.self .msg-sender {
  color: rgba(255,255,255,0.85);
}
.msg-content {
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}
.msg-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 6px;
  text-align: right;
}
.chat-msg.self .msg-time {
  color: rgba(255,255,255,0.7);
}
.msg-content.emoji-only {
  font-size: 28px;
  line-height: 1.2;
}
</style>
