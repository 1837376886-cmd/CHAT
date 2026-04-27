<template>
  <div class="app-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span>我的客服历史</span>
      </div>
      <el-table v-loading="loading" :data="historyList">
        <el-table-column label="会话ID" prop="id" width="80" />
        <el-table-column label="客服" prop="csUserId" />
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
    </el-card>

    <el-dialog title="消息记录" :visible.sync="detailVisible" width="600px">
      <div class="msg-list">
        <div v-for="(msg, index) in detailMessages" :key="index" class="msg-item">
          <span class="msg-sender">{{ msg.fromType === 1 ? '我' : '客服' }}</span>
          <span class="msg-content">{{ msg.content }}</span>
          <span class="msg-time">{{ msg.createTime }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMyCsHistory } from '@/api/cs'
import request from '@/utils/request'

export default {
  name: 'CsMyHistory',
  data() {
    return {
      loading: false,
      historyList: [],
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
      getMyCsHistory().then(res => {
        this.historyList = res.data || []
        this.loading = false
      })
    },
    viewDetail(row) {
      request({
        url: `/cs/my/history/${row.id}/messages`,
        method: 'get'
      }).then(res => {
        this.detailMessages = res.data || []
        this.detailVisible = true
      })
    }
  }
}
</script>

<style scoped>
.msg-list {
  max-height: 400px;
  overflow-y: auto;
}
.msg-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.msg-sender {
  font-weight: bold;
  margin-right: 8px;
}
.msg-time {
  float: right;
  color: #999;
  font-size: 12px;
}
</style>
