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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑原子能力' : '新建原子能力'" width="680px" destroy-on-close>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：发送告警邮件" />
        </el-form-item>
        <el-form-item label="节点类型" prop="nodeType">
          <el-select v-model="form.nodeType" placeholder="选择节点类型" style="width: 100%">
            <el-option v-for="t in nodeTypes" :key="t.type" :label="`${t.name} (${t.type})`" :value="t.type" />
          </el-select>
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
        <el-form-item label="配置模板">
          <el-input v-model="form.configTemplate" type="textarea" :rows="6"
                    placeholder='{"url": "https://api.example.com", "method": "GET"}' />
        </el-form-item>
        <el-form-item label="输入 Schema">
          <el-input v-model="form.inputSchema" type="textarea" :rows="3"
                    placeholder='{"type": "object", "properties": {"name": {"type": "string"}}}' />
        </el-form-item>
        <el-form-item label="输出 Schema">
          <el-input v-model="form.outputSchema" type="textarea" :rows="3"
                    placeholder='{"type": "object", "properties": {"result": {"type": "string"}}}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invokeDialogVisible" title="测试调用原子能力" width="760px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="原子能力">
          <el-input :model-value="invokeTarget?.name || ''" disabled />
        </el-form-item>
        <el-form-item label="输入变量">
          <el-input v-model="invokeInputsText" type="textarea" :rows="6"
                    placeholder='{"name":"demo","url":"https://example.com"}' />
        </el-form-item>
        <el-form-item label="调用参数">
          <el-input v-model="invokeConfigText" type="textarea" :rows="6"
                    placeholder='{"timeout":3000,"headers":{"X-Debug":"1"}}' />
        </el-form-item>
        <el-form-item label="执行结果">
          <el-input :model-value="invokeResultText" type="textarea" :rows="8" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invokeDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!invokeTarget" @click="invokeComponent">执行测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { atomicApi } from '../api'

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
const invokeInputsText = ref('{\n}')
const invokeConfigText = ref('{\n}')
const invokeResultText = ref('')

const form = ref({
  name: '',
  nodeType: '',
  category: 'general',
  description: '',
  configTemplate: '{}',
  inputSchema: '',
  outputSchema: '',
  status: 'DRAFT'
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  nodeType: [{ required: true, message: '请选择节点类型', trigger: 'change' }]
}

const nodeTypes = ref([])

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
    inputSchema: '',
    outputSchema: '',
    status: 'DRAFT'
  }
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

function openInvokeDialog(row) {
  invokeTarget.value = row
  invokeInputsText.value = '{\n}'
  invokeConfigText.value = row.configTemplate || '{\n}'
  invokeResultText.value = ''
  invokeDialogVisible.value = true
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

async function invokeComponent() {
  if (!invokeTarget.value) return
  try {
    const inputs = JSON.parse(invokeInputsText.value || '{}')
    const config = JSON.parse(invokeConfigText.value || '{}')
    const result = await atomicApi.invokeComponent(invokeTarget.value.id, { inputs, config })
    invokeResultText.value = JSON.stringify(result, null, 2)
    ElMessage.success('调用成功')
  } catch (e) {
    invokeResultText.value = String(e?.message || e)
    ElMessage.error('调用失败或 JSON 非法')
  }
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
</style>
