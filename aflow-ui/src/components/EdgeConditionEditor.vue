<template>
  <div class="edge-condition-editor" v-if="visible">
    <div class="editor-header">
      <span class="title">边条件编辑</span>
      <span class="edge-info">{{ edge.from }} → {{ edge.to }}</span>
      <el-button class="close-btn" :icon="Close" circle size="small" @click="close" />
    </div>

    <el-form label-width="80px" class="editor-form">
      <el-form-item label="条件表达式">
        <ExpressionInput
          v-model="localCondition"
          :variables="variables"
          placeholder='如: #status == "approved" 或 #amount > 1000'
        />
      </el-form-item>
      <el-form-item label="快速模板">
        <div class="template-chips">
          <el-tag v-for="t in templates" :key="t.expr" size="small" class="template-tag"
                  @click="applyTemplate(t.expr)">
            {{ t.label }}
          </el-tag>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="emitChange">应用</el-button>
        <el-button size="small" @click="clearCondition">清除条件</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import ExpressionInput from '@/components/common/ExpressionInput.vue'
import type { FlowVariable } from '@/composables/useFlowVariables'

interface EdgeInfo {
  from: string
  to: string
  condition: string
}

const props = withDefaults(defineProps<{
  visible: boolean
  edge: EdgeInfo
  /** Available flow variables for expression autocomplete */
  variables?: FlowVariable[]
}>(), {
  variables: () => [],
})

const emit = defineEmits<{
  'update:condition': [value: string]
  'close': []
}>()

const localCondition = ref('')

const templates = [
  { label: '等于', expr: '#var == "value"' },
  { label: '大于', expr: '#var > 0' },
  { label: '小于', expr: '#var < 0' },
  { label: '包含', expr: '#var.contains("text")' },
  { label: '非空', expr: '#var != null' },
  { label: '布尔真', expr: '#flag == true' }
]

watch(() => props.edge, (e) => {
  localCondition.value = e?.condition || ''
}, { immediate: true })

function emitChange() {
  emit('update:condition', localCondition.value)
}

function clearCondition() {
  localCondition.value = ''
  emit('update:condition', '')
}

function close() {
  emit('close')
}

function applyTemplate(expr: string) {
  localCondition.value = expr
  emitChange()
}
</script>

<style scoped>
.edge-condition-editor {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  min-width: 320px;
}
.editor-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.title {
  font-weight: 600;
  font-size: 14px;
}
.edge-info {
  color: #909399;
  font-size: 12px;
  font-family: monospace;
}
.close-btn {
  margin-left: auto;
}
.template-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.template-tag {
  cursor: pointer;
}
.template-tag:hover {
  opacity: 0.8;
}
</style>
