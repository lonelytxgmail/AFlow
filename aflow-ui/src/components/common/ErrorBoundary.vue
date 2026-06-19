<template>
  <slot v-if="!hasError" />
  <div v-else class="error-boundary">
    <el-icon :size="48" class="error-boundary-icon">
      <WarningFilled />
    </el-icon>
    <p class="error-boundary-message">{{ displayMessage }}</p>
    <p v-if="errorDetail" class="error-boundary-detail">{{ errorDetail }}</p>
    <el-button type="primary" @click="handleRetry">重试</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  fallbackMessage?: string
}>(), {
  fallbackMessage: 'Something went wrong'
})

const hasError = ref(false)
const errorDetail = ref<string | null>(null)

const displayMessage = ref(props.fallbackMessage)

onErrorCaptured((err: unknown) => {
  hasError.value = true
  if (err instanceof Error) {
    errorDetail.value = err.message
  } else {
    errorDetail.value = String(err)
  }
  // Prevent the error from propagating further
  return false
})

function handleRetry() {
  hasError.value = false
  errorDetail.value = null
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
  min-height: 200px;
}

.error-boundary-icon {
  color: #f56c6c;
  margin-bottom: 16px;
}

.error-boundary-message {
  font-size: 16px;
  color: #303133;
  margin: 0 0 8px 0;
}

.error-boundary-detail {
  font-size: 13px;
  color: #909399;
  margin: 0 0 20px 0;
  max-width: 400px;
  word-break: break-word;
}
</style>
