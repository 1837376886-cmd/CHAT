<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" icon="el-icon-plus" size="mini" @click="handleAdd">新增客服</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="staffList">
      <el-table-column label="用户ID" prop="userId" width="80" />
      <el-table-column label="用户名" prop="userName" />
      <el-table-column label="别名" prop="nickName" />
      <el-table-column label="默认回复语" prop="autoReply" show-overflow-tooltip />
      <el-table-column label="是否客服" width="100">
        <template slot-scope="scope">
          <el-tag :type="scope.row.isCustomerService === 1 ? 'success' : 'info'">
            {{ scope.row.isCustomerService === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button
            size="mini"
            :type="scope.row.isCustomerService === 1 ? 'danger' : 'primary'"
            @click="toggleStaff(scope.row)"
          >
            {{ scope.row.isCustomerService === 1 ? '取消' : '设为客服' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增客服弹窗 -->
    <el-dialog title="选择用户" :visible.sync="dialogVisible" width="600px">
      <el-table :data="userList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column label="用户名" prop="userName" />
        <el-table-column label="昵称" prop="nickName" />
      </el-table>
      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAdd">确定</el-button>
      </div>
    </el-dialog>

    <!-- 编辑客服配置弹窗 -->
    <el-dialog title="编辑客服配置" :visible.sync="editVisible" width="500px">
      <el-form ref="editForm" :model="editForm" label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.userName" disabled />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="editForm.nickName" placeholder="访客看到的客服名称" />
        </el-form-item>
        <el-form-item label="默认回复语">
          <el-input
            v-model="editForm.autoReply"
            type="textarea"
            :rows="3"
            placeholder="访客接入时自动发送的欢迎语，为空则使用系统默认"
          />
        </el-form-item>
        <el-form-item label="最大接待数">
          <el-input-number v-model="editForm.maxSessions" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmEdit">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { setCsStaff, getCsConfig, saveCsConfig } from '@/api/cs'
import { listUser } from '@/api/system/user'

export default {
  name: 'CsStaff',
  data() {
    return {
      loading: false,
      staffList: [],
      userList: [],
      dialogVisible: false,
      editVisible: false,
      selectedUsers: [],
      editForm: {
        userId: null,
        userName: '',
        nickName: '',
        autoReply: '',
        maxSessions: 5
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listUser({ pageSize: 999 }).then(res => {
        const allUsers = res.rows || []
        this.staffList = allUsers.filter(u => u.isCustomerService === 1)
        // 逐个加载客服配置补充默认回复语
        this.staffList.forEach(staff => {
          getCsConfig(staff.userId).then(cfgRes => {
            const data = cfgRes.data || {}
            this.$set(staff, 'autoReply', data.autoReply || '')
          }).catch(() => {
            this.$set(staff, 'autoReply', '')
          })
        })
        this.loading = false
      })
    },
    handleAdd() {
      listUser({ pageSize: 999 }).then(res => {
        this.userList = (res.rows || []).filter(u => u.isCustomerService !== 1)
        this.dialogVisible = true
      })
    },
    handleSelectionChange(val) {
      this.selectedUsers = val
    },
    confirmAdd() {
      const promises = this.selectedUsers.map(u =>
        setCsStaff({ userId: u.userId, isCustomerService: 1 })
      )
      Promise.all(promises).then(() => {
        this.$message.success('操作成功')
        this.dialogVisible = false
        this.getList()
      })
    },
    toggleStaff(row) {
      const isCs = row.isCustomerService === 1 ? 0 : 1
      setCsStaff({ userId: row.userId, isCustomerService: isCs }).then(() => {
        this.$message.success('操作成功')
        this.getList()
      })
    },
    handleEdit(row) {
      this.editForm = {
        userId: row.userId,
        userName: row.userName,
        nickName: row.nickName || '',
        autoReply: row.autoReply || '',
        maxSessions: 5
      }
      getCsConfig(row.userId).then(res => {
        const data = res.data || {}
        this.editForm.nickName = data.nickName || row.nickName || ''
        this.editForm.autoReply = data.autoReply || ''
        this.editForm.maxSessions = data.maxSessions || 5
      }).catch(() => {
        this.editForm.nickName = row.nickName || ''
      })
      this.editVisible = true
    },
    confirmEdit() {
      saveCsConfig({
        userId: this.editForm.userId,
        nickName: this.editForm.nickName,
        autoReply: this.editForm.autoReply,
        maxSessions: this.editForm.maxSessions
      }).then(() => {
        this.$message.success('保存成功')
        this.editVisible = false
        this.getList()
      })
    }
  }
}
</script>
