<template>
  <div class="instance-list">
    <div class="header">
      <h2>运行实例管理</h2>
      <el-select v-model="statusFilter" placeholder="按状态过滤" clearable @change="loadData">
        <el-option label="RUNNING" value="RUNNING" />
        <el-option label="SUSPENDED" value="SUSPENDED" />
        <el-option label="COMPLETED" value="COMPLETED" />
        <el-option label="FAILED" value="FAILED" />
        <el-option label="CANCELLED" value="CANCELLED" />
      </el-select>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && instances.length === 0" description="暂无运行实例" />

    <el-table v-else :data="instances" v-loading="loading" border style="width: 100%">
      <el-table-column prop="flowInstanceId" label="实例 ID" width="280" />
      <el-table-column prop="flowDefinitionId" label="定义 ID" width="280" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeId" label="当前节点" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/instances/${row.flowInstanceId}`)">详情</el-button>
          <el-button size="small" type="danger" plain v-if="['RUNNING', 'SUSPENDED'].includes(row.status)" @click="cancelInstance(row.flowInstanceId)">
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flowApi } from '../api'

const instances = ref([])
const loading = ref(false)
const statusFilter = ref('')

const loadData = async () => {
  loading.value = true
  try {
    instances.value = await flowApi.list(statusFilter.value)
  } finally {
    loading.value = false
  }
}

const getStatusType = (status) => {
  switch (status) {
    case 'RUNNING': return 'primary'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'SUSPENDED': return 'warning'
    default: return 'info'
  }
}

const cancelInstance = async (id) => {
  try {
    await ElMessageBox.confirm('确定要取消该运行实例吗?', '提示', { type: 'warning' })
    await flowApi.cancel(id)
    ElMessage.success('已取消')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>
