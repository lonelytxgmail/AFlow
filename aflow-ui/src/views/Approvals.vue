<template>
  <div class="approvals-page">
    <div class="header">
      <h2>审批管理 / Approvals</h2>
      <div class="header-actions">
        <el-radio-group v-model="statusFilter" size="small" @change="fetchApprovals">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PENDING">待审批</el-radio-button>
          <el-radio-button value="APPROVED">已通过</el-radio-button>
          <el-radio-button value="REJECTED">已拒绝</el-radio-button>
          <el-radio-button value="TIMEOUT">已超时</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" :loading="loading" circle size="small" @click="fetchApprovals" />
      </div>
    </div>

    <!-- 审批列表 -->
    <el-table
      v-loading="loading"
      :data="approvals"
      stripe
      style="width: 100%"
      empty-text="暂无审批请求"
    >
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="flowId" label="流程实例" width="180" show-overflow-tooltip />
      <el-table-column prop="nodeId" label="节点" width="120" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="截止时间" width="180">
        <template #default="{ row }">
          <span v-if="row.deadline" :class="{ 'deadline-warn': isNearDeadline(row.deadline) }">
            {{ formatDateTime(row.deadline) }}
          </span>
          <span v-else class="text-muted">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button type="success" size="small" @click="handleApprove(row)">
              批准
            </el-button>
            <el-button type="danger" size="small" @click="handleReject(row)">
              拒绝
            </el-button>
          </template>
          <span v-else class="text-muted">--</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 拒绝原因对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝审批" width="400px">
      <el-form>
        <el-form-item label="拒绝原因">
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            placeholder="请输入拒绝原因（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoading" @click="confirmReject">
          确认拒绝
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi } from '@/api'
import type { ApprovalRequest } from '@/api/types'

// --- State ---

const loading = ref(false)
const actionLoading = ref(false)
const statusFilter = ref('')
const approvals = ref<ApprovalRequest[]>([])

const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const currentRejectId = ref('')

// --- Fetch ---

async function fetchApprovals() {
  loading.value = true
  try {
    const data = await approvalApi.list(statusFilter.value || undefined)
    approvals.value = data
  } catch {
    // Error shown by global interceptor
  } finally {
    loading.value = false
  }
}

// --- Actions ---

async function handleApprove(row: ApprovalRequest) {
  try {
    await ElMessageBox.confirm(
      `确认批准「${row.title}」？`,
      '批准审批',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'success' }
    )
    actionLoading.value = true
    await approvalApi.approve(row.id)
    ElMessage.success('审批已通过')
    await fetchApprovals()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  } finally {
    actionLoading.value = false
  }
}

function handleReject(row: ApprovalRequest) {
  currentRejectId.value = row.id
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  actionLoading.value = true
  try {
    await approvalApi.reject(currentRejectId.value, rejectReason.value || undefined)
    ElMessage.success('审批已拒绝')
    rejectDialogVisible.value = false
    await fetchApprovals()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    actionLoading.value = false
  }
}

// --- Helpers ---

function statusTagType(status: string) {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    case 'TIMEOUT': return 'info'
    default: return 'info'
  }
}

function statusLabel(status: string) {
  switch (status) {
    case 'PENDING': return '待审批'
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已拒绝'
    case 'TIMEOUT': return '已超时'
    default: return status
  }
}

function formatDateTime(dateStr: string | undefined): string {
  if (!dateStr) return '--'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function isNearDeadline(deadlineStr: string): boolean {
  const deadline = new Date(deadlineStr)
  const now = new Date()
  const diffMs = deadline.getTime() - now.getTime()
  // Less than 1 hour remaining
  return diffMs > 0 && diffMs < 3600_000
}

// --- Lifecycle ---

onMounted(() => {
  fetchApprovals()
})
</script>

<style scoped>
.approvals-page {
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.text-muted {
  color: #909399;
  font-size: 13px;
}

.deadline-warn {
  color: #e6a23c;
  font-weight: 500;
}
</style>
