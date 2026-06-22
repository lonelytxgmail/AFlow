<template>
  <div class="atomic-components">
    <div class="header">
      <h2>原子能力管理</h2>
      <div class="header-actions">
        <el-input v-model="searchKeyword" placeholder="搜索原子能力..." clearable
                  style="width: 240px; margin-right: 12px;" @input="fetchComponents" />
        <el-select v-model="filterCategory" placeholder="分类筛选" clearable style="width: 140px; margin-right: 12px;"
                   @change="fetchComponents">
          <el-option label="全部" value="" />
          <el-option label="HTTP" value="http" />
          <el-option label="数据转换" value="transform" />
          <el-option label="脚本" value="script" />
          <el-option label="通知" value="notification" />
          <el-option label="Agent" value="agent" />
          <el-option label="其他" value="general" />
        </el-select>
        <el-button type="primary" @click="openCreateDialog">新建原子能力</el-button>
      </div>
    </div>

    <el-table :data="components" v-loading="loading" border style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="nodeType" label="节点类型" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.nodeType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="openInvokeDialog(row)">测试</el-button>
          <el-button size="small" type="success" v-if="row.status === 'DRAFT'" @click="publishComponent(row)">
            发布
          </el-button>
          <el-button size="small" type="warning" v-if="row.status === 'PUBLISHED'" @click="archiveComponent(row)">
            归档
          </el-button>
          <el-button size="small" type="danger" @click="deleteComponent(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑原子能力' : '新建原子能力'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：发送告警邮件" />
        </el-form-item>
        <el-form-item label="节点类型" prop="nodeType">
          <el-select v-model="form.nodeType" placeholder="选择节点类型" style="width: 100%" @change="onNodeTypeChange">
            <el-option v-for="t in nodeTypes" :key="t.type" :label="`${t.name} (${t.type})`" :value="t.type" />
          </el-select>
          <div v-if="selectedNodeType?.description" class="node-type-hint">
            {{ selectedNodeType.description }}
          </div>
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="选择分类" style="width: 100%">
            <el-option label="HTTP" value="http" />
            <el-option label="数据转换" value="transform" />
            <el-option label="脚本" value="script" />
            <el-option label="通知" value="notification" />
            <el-option label="Agent" value="agent" />
            <el-option label="其他" value="general" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述该原子能力的功能" />
        </el-form-item>

        <!-- Schema-driven config form -->
        <div v-if="currentConfigSchema" class="config-section">
          <div class="config-section-label">配置模板</div>
          <div class="schema-form-wrapper">
            <SchemaForm
              :schema="currentConfigSchema"
              :model-value="configObject"
              @update:model-value="onConfigUpdate"
            />
          </div>
        </div>

        <!-- Fallback: raw JSON for types without schema -->
        <el-form-item v-else label="配置模板">
          <el-input v-model="form.configTemplate" type="textarea" :rows="6"
                    placeholder='{"url": "https://api.example.com", "method": "GET"}' />
        </el-form-item>

      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invokeDialogVisible" title="调试测试" width="800px" destroy-on-close>
      <div class="invoke-dialog">
        <!-- 组件信息 -->
        <div class="invoke-header">
          <el-tag type="success" size="small">{{ invokeTarget?.nodeType }}</el-tag>
          <span class="invoke-name">{{ invokeTarget?.name }}</span>
          <span v-if="invokeTarget?.description" class="invoke-desc">{{ invokeTarget?.description }}</span>
        </div>

        <!-- 变量输入区 -->
        <div class="invoke-section">
          <div class="section-title">
            <span>输入变量</span>
            <el-button type="primary" text size="small" @click="addInvokeVariable">+ 添加变量</el-button>
          </div>
          <div v-if="invokeVariables.length === 0" class="empty-vars">
            该原子能力不需要输入变量（configTemplate 中无 ${#xxx} 占位符）
          </div>
          <div v-for="(v, idx) in invokeVariables" :key="idx" class="var-row">
            <el-input
              v-model="v.name"
              placeholder="变量名"
              class="var-name"
              :disabled="v.fromTemplate"
            />
            <span class="var-eq">=</span>
            <el-input
              v-model="v.value"
              placeholder="变量值"
              class="var-value"
            />
            <el-button
              v-if="!v.fromTemplate"
              type="danger" text circle size="small"
              @click="invokeVariables.splice(idx, 1)"
            >✕</el-button>
            <el-tooltip v-if="v.context" :content="v.context" placement="top">
              <span class="var-hint">?</span>
            </el-tooltip>
          </div>
        </div>

        <!-- 执行按钮 -->
        <div class="invoke-actions">
          <el-button type="primary" :loading="invokeLoading" @click="executeInvoke">执行测试</el-button>
        </div>

        <!-- 执行结果 -->
        <div v-if="invokeResult" class="invoke-section">
          <div class="section-title">
            <span>执行结果</span>
            <el-tag :type="invokeResult.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ invokeResult.status }}
            </el-tag>
            <span v-if="invokeResult.debug?.duration" class="duration-tag">
              {{ invokeResult.debug.duration }}ms
            </span>
          </div>

          <!-- 调试信息：实际请求 -->
          <div v-if="invokeResult.debug?.resolvedConfig" class="debug-block">
            <div class="debug-title">实际请求配置</div>
            <pre class="debug-json">{{ JSON.stringify(invokeResult.debug.resolvedConfig, null, 2) }}</pre>
          </div>

          <!-- 输出 -->
          <div v-if="invokeResult.output" class="debug-block">
            <div class="debug-title">输出数据</div>
            <pre class="debug-json">{{ JSON.stringify(invokeResult.output, null, 2) }}</pre>
          </div>

          <!-- 错误信息 -->
          <div v-if="invokeResult.error" class="debug-block error">
            <div class="debug-title">错误</div>
            <pre class="debug-json error-text">{{ invokeResult.error }}</pre>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="invokeDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { atomicApi } from '../api'
import SchemaForm from '@/components/SchemaForm.vue'

const components = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref('')
const formRef = ref(null)
const invokeDialogVisible = ref(false)
const invokeTarget = ref(null)
const invokeVariables = ref([])
const invokeResult = ref(null)
const invokeLoading = ref(false)

const form = ref({
  name: '',
  nodeType: '',
  category: 'general',
  description: '',
  configTemplate: '{}',
  status: 'DRAFT'
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  nodeType: [{ required: true, message: '请选择节点类型', trigger: 'change' }]
}

const nodeTypes = ref([])

// --- Schema-driven config ---

/** 当前选中节点类型的元数据 */
const selectedNodeType = computed(() => {
  if (!form.value.nodeType) return null
  return nodeTypes.value.find(t => t.type === form.value.nodeType) || null
})

/** 当前节点类型的 configSchema (parsed) */
const currentConfigSchema = computed(() => {
  const nt = selectedNodeType.value
  if (!nt || !nt.configSchema) return null
  if (typeof nt.configSchema === 'object') return nt.configSchema
  try {
    const parsed = JSON.parse(nt.configSchema)
    if (parsed && typeof parsed === 'object' && parsed.properties) return parsed
    return null
  } catch {
    return null
  }
})

/** configTemplate 解析为对象给 SchemaForm 使用 */
const configObject = computed(() => {
  try {
    let val = form.value.configTemplate
    if (typeof val === 'object') return val
    if (!val) return {}
    // 第一次 parse
    let parsed = JSON.parse(val)
    // 处理双重序列化：如果 parse 结果仍是字符串，再 parse 一次
    if (typeof parsed === 'string') {
      parsed = JSON.parse(parsed)
    }
    return (parsed && typeof parsed === 'object') ? parsed : {}
  } catch {
    return {}
  }
})

/** SchemaForm 更新配置对象 */
function onConfigUpdate(newConfig) {
  form.value.configTemplate = JSON.stringify(newConfig, null, 2)
}

/** 切换节点类型时，根据 schema defaults 预填配置 */
function onNodeTypeChange(newType) {
  const nt = nodeTypes.value.find(t => t.type === newType)
  if (!nt) return

  // 自动填充分类
  if (newType === 'http') form.value.category = 'http'
  else if (newType === 'script') form.value.category = 'script'
  else if (newType === 'agent') form.value.category = 'agent'
  else if (newType === 'transform') form.value.category = 'transform'

  // 根据 schema 生成默认配置
  let schema = null
  try {
    schema = typeof nt.configSchema === 'object' ? nt.configSchema : JSON.parse(nt.configSchema || '{}')
  } catch { schema = null }

  if (schema?.properties) {
    const defaults = {}
    for (const [key, def] of Object.entries(schema.properties)) {
      if (def.default !== undefined) {
        defaults[key] = def.default
      } else if (def.type === 'object') {
        defaults[key] = {}
      } else if (def.type === 'string') {
        defaults[key] = ''
      }
    }
    form.value.configTemplate = JSON.stringify(defaults, null, 2)
  } else {
    form.value.configTemplate = '{}'
  }
}

const statusType = (s) => ({ DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }[s] || 'info')
const statusLabel = (s) => ({ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[s] || s)

onMounted(async () => {
  await fetchComponents()
  await fetchNodeTypes()
})

async function fetchComponents() {
  loading.value = true
  try {
    const params = {}
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterCategory.value) params.category = filterCategory.value
    components.value = await atomicApi.listComponents(params)
  } finally {
    loading.value = false
  }
}

async function fetchNodeTypes() {
  try {
    nodeTypes.value = await atomicApi.registry()
  } catch {
    nodeTypes.value = []
  }
}

function openCreateDialog() {
  isEdit.value = false
  editId.value = ''
  form.value = {
    name: '',
    nodeType: '',
    category: 'general',
    description: '',
    configTemplate: '{}',
    status: 'DRAFT'
  }
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editId.value = row.id

  // 处理 configTemplate 双重序列化
  let ct = row.configTemplate || '{}'
  if (typeof ct === 'string') {
    try {
      const parsed = JSON.parse(ct)
      // 如果 parse 后还是字符串，说明是双重序列化
      if (typeof parsed === 'string') {
        ct = parsed
      } else if (typeof parsed === 'object') {
        ct = JSON.stringify(parsed, null, 2)
      }
    } catch { /* keep original */ }
  } else if (typeof ct === 'object') {
    ct = JSON.stringify(ct, null, 2)
  }

  form.value = {
    name: row.name || '',
    nodeType: row.nodeType || '',
    category: row.category || 'general',
    description: row.description || '',
    configTemplate: ct,
    status: row.status || 'DRAFT'
  }
  dialogVisible.value = true
}

async function openInvokeDialog(row) {
  invokeTarget.value = row
  invokeResult.value = null
  invokeLoading.value = false

  // 从后端获取该组件需要的变量列表
  try {
    const vars = await atomicApi.getVariables(row.id)
    invokeVariables.value = (Array.isArray(vars) ? vars : []).map(v => ({
      name: v.name,
      value: '',
      context: v.context || '',
      fromTemplate: true
    }))
  } catch {
    invokeVariables.value = []
  }
  invokeDialogVisible.value = true
}

function addInvokeVariable() {
  invokeVariables.value.push({ name: '', value: '', context: '', fromTemplate: false })
}

async function executeInvoke() {
  if (!invokeTarget.value) return
  invokeLoading.value = true
  invokeResult.value = null

  try {
    const variables = {}
    for (const v of invokeVariables.value) {
      if (v.name.trim()) {
        let val = v.value
        try { val = JSON.parse(v.value) } catch { /* keep string */ }
        variables[v.name.trim()] = val
      }
    }
    const result = await atomicApi.invokeComponent(invokeTarget.value.id, variables)
    invokeResult.value = result
    ElMessage.success('执行完成')
  } catch (e) {
    invokeResult.value = { status: 'FAILED', error: String(e?.message || e), output: null, debug: null }
  } finally {
    invokeLoading.value = false
  }
}

async function submitForm() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (isEdit.value) {
    await atomicApi.updateComponent(editId.value, form.value)
    ElMessage.success('更新成功')
  } else {
    await atomicApi.createComponent(form.value)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchComponents()
}

async function publishComponent(row) {
  await atomicApi.updateComponent(row.id, { ...row, status: 'PUBLISHED' })
  ElMessage.success('已发布')
  fetchComponents()
}

async function archiveComponent(row) {
  await atomicApi.updateComponent(row.id, { ...row, status: 'ARCHIVED' })
  ElMessage.success('已归档')
  fetchComponents()
}

async function deleteComponent(row) {
  await ElMessageBox.confirm(`确定删除原子能力「${row.name}」？`, '确认删除', { type: 'warning' })
  await atomicApi.deleteComponent(row.id)
  ElMessage.success('已删除')
  fetchComponents()
}
</script>

<style scoped>
.atomic-components {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header h2 {
  margin: 0;
}
.header-actions {
  display: flex;
  align-items: center;
}
.node-type-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.schema-form-wrapper {
  width: 100%;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
}
.config-section {
  margin-bottom: 18px;
}
.config-section-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
  padding-left: 0;
}

/* Invoke dialog styles */
.invoke-dialog {
  max-height: 65vh;
  overflow-y: auto;
}
.invoke-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.invoke-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.invoke-desc {
  font-size: 12px;
  color: #909399;
}
.invoke-section {
  margin-bottom: 16px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.duration-tag {
  font-size: 12px;
  color: #67C23A;
  font-weight: normal;
}
.empty-vars {
  padding: 12px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  border: 1px dashed #e4e7ed;
  border-radius: 6px;
}
.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.var-name {
  flex: 2;
}
.var-eq {
  color: #909399;
  font-weight: bold;
}
.var-value {
  flex: 3;
}
.var-hint {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #e4e7ed;
  color: #606266;
  font-size: 11px;
  cursor: help;
}
.invoke-actions {
  margin: 16px 0;
}
.debug-block {
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.debug-block.error {
  border-color: #fde2e2;
}
.debug-title {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
}
.debug-block.error .debug-title {
  background: #fef0f0;
  color: #f56c6c;
}
.debug-json {
  padding: 10px 12px;
  margin: 0;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
.error-text {
  color: #f56c6c;
}
</style>
