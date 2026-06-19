<template>
  <el-dialog
    v-model="visible"
    title="变量定义 (Variables)"
    width="720px"
    destroy-on-close
    @close="handleClose"
  >
    <!-- Input Variables -->
    <div class="variable-section">
      <div class="section-header">
        <h4>输入变量 (Input Variables)</h4>
        <el-button size="small" type="primary" @click="addInputVariable">
          <el-icon><Plus /></el-icon> 添加
        </el-button>
      </div>
      <el-table :data="localInputs" border size="small" class="variable-table">
        <el-table-column label="变量名" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="variableName" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.type" placeholder="类型" size="small">
              <el-option label="string" value="string" />
              <el-option label="number" value="number" />
              <el-option label="boolean" value="boolean" />
              <el-option label="object" value="object" />
              <el-option label="array" value="array" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.description" placeholder="变量描述" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="必填" width="60" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.required" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeInputVariable($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="localInputs.length === 0" description="暂无输入变量" :image-size="40" />
    </div>

    <!-- Output Variables -->
    <div class="variable-section">
      <div class="section-header">
        <h4>输出变量 (Output Variables)</h4>
        <el-button size="small" type="primary" @click="addOutputVariable">
          <el-icon><Plus /></el-icon> 添加
        </el-button>
      </div>
      <el-table :data="localOutputs" border size="small" class="variable-table">
        <el-table-column label="变量名" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="variableName" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.type" placeholder="类型" size="small">
              <el-option label="string" value="string" />
              <el-option label="number" value="number" />
              <el-option label="boolean" value="boolean" />
              <el-option label="object" value="object" />
              <el-option label="array" value="array" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.description" placeholder="变量描述" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeOutputVariable($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="localOutputs.length === 0" description="暂无输出变量" :image-size="40" />
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { FlowVariables, VariableSchema } from '@/types'

interface VariableRow {
  name: string
  type: string
  description: string
  required?: boolean
}

const props = defineProps<{
  modelValue: boolean
  variables?: FlowVariables
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:variables': [value: FlowVariables]
}>()

const visible = ref(props.modelValue)
const localInputs = ref<VariableRow[]>([])
const localOutputs = ref<VariableRow[]>([])

// Sync dialog visibility
watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    loadFromVariables()
  }
})

watch(visible, (val) => {
  if (!val) {
    emit('update:modelValue', false)
  }
})

/**
 * Load existing variables into local editable rows
 */
function loadFromVariables() {
  localInputs.value = []
  localOutputs.value = []

  if (props.variables?.input) {
    for (const [name, schema] of Object.entries(props.variables.input)) {
      localInputs.value.push({
        name,
        type: schema.type || 'string',
        description: schema.description || '',
        required: schema.required ?? false,
      })
    }
  }

  if (props.variables?.output) {
    for (const [name, schema] of Object.entries(props.variables.output)) {
      localOutputs.value.push({
        name,
        type: schema.type || 'string',
        description: schema.description || '',
      })
    }
  }
}

function addInputVariable() {
  localInputs.value.push({ name: '', type: 'string', description: '', required: false })
}

function removeInputVariable(index: number) {
  localInputs.value.splice(index, 1)
}

function addOutputVariable() {
  localOutputs.value.push({ name: '', type: 'string', description: '' })
}

function removeOutputVariable(index: number) {
  localOutputs.value.splice(index, 1)
}

function handleClose() {
  visible.value = false
  emit('update:modelValue', false)
}

function handleConfirm() {
  const input: Record<string, VariableSchema> = {}
  const output: Record<string, VariableSchema> = {}

  for (const row of localInputs.value) {
    if (row.name.trim()) {
      input[row.name.trim()] = {
        type: row.type,
        description: row.description || undefined,
        required: row.required || undefined,
      } as VariableSchema
    }
  }

  for (const row of localOutputs.value) {
    if (row.name.trim()) {
      output[row.name.trim()] = {
        type: row.type,
        description: row.description || undefined,
      } as VariableSchema
    }
  }

  const variables: FlowVariables = {}
  if (Object.keys(input).length > 0) {
    variables.input = input
  }
  if (Object.keys(output).length > 0) {
    variables.output = output
  }

  emit('update:variables', variables)
  handleClose()
}
</script>

<style scoped>
.variable-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.section-header h4 {
  margin: 0;
  font-size: 14px;
  color: #303133;
}

.variable-table {
  width: 100%;
}

.el-empty {
  padding: 10px 0;
}
</style>
