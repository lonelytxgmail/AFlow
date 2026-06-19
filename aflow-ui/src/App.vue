<template>
  <div class="layout">
    <el-menu mode="horizontal" router :default-active="$route.path" class="header-menu">
      <el-menu-item class="logo-item" index="/">
        <span class="logo-text">AFlow</span>
      </el-menu-item>
      <el-menu-item index="/definitions">流程定义</el-menu-item>
      <el-menu-item index="/instances">运行实例</el-menu-item>
      <el-menu-item index="/atomic-components">原子能力</el-menu-item>
      <el-menu-item index="/approvals">
        审批
        <el-badge v-if="pendingCount > 0" :value="pendingCount" :max="99" class="approval-badge" />
      </el-menu-item>
      <el-menu-item index="/monitoring">监控</el-menu-item>
    </el-menu>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { approvalApi } from '@/api'

// --- Pending approval count badge ---

const pendingCount = ref(0)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function fetchPendingCount() {
  try {
    const list = await approvalApi.list('PENDING')
    pendingCount.value = list.length
  } catch {
    // Silently ignore — badge is non-critical
  }
}

onMounted(() => {
  fetchPendingCount()
  // Poll every 30 seconds
  pollTimer = setInterval(fetchPendingCount, 30_000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.header-menu {
  padding: 0 20px;
  border-bottom: 1px solid #dcdfe6;
}
.logo-item {
  font-size: 20px;
  font-weight: bold;
  color: #409EFF !important;
}
.main-content {
  flex: 1;
  padding: 20px;
  background-color: #f5f7fa;
}
.approval-badge {
  margin-left: 6px;
  vertical-align: middle;
}
</style>
