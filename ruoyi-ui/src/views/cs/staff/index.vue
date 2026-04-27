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
      <el-table-column label="昵称" prop="nickName" />
      <el-table-column label="是否客服" width="100">
        <template slot-scope="scope">
          <el-tag :type="scope.row.isCustomerService === 1 ? 'success' : 'info'">
            {{ scope.row.isCustomerService === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template slot-scope="scope">
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
  </div>
</template>

<script>
import { setCsStaff } from '@/api/cs'
import { listUser } from '@/api/system/user'

export default {
  name: 'CsStaff',
  data() {
    return {
      loading: false,
      staffList: [],
      userList: [],
      dialogVisible: false,
      selectedUsers: []
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listUser({ pageSize: 999 }).then(res => {
        this.staffList = (res.rows || []).filter(u => u.isCustomerService === 1)
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
    }
  }
}
</script>
