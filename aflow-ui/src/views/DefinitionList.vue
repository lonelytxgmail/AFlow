<template>
  <div class="definition-list">
    <div class="header">
      <h2>流程定义管理</h2>
      <el-button type="primary" @click="$router.push('/definitions/new')">
        新建流程 (New Flow)
      </el-button>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && definitions.length === 0" description="暂无流程定义">
      <el-button type="primary" @click="$router.push('/definitions/new')">新建定义</el-button>
    </el-empty>

    <el-table v-else :data="definitions" v-loading="loading" border style="width: 100%">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/definitions/${row.id}`)">编辑</el-button>
          <el-button size="small" type="success" @click="startFlow(row.id)">启动</el-button>
          <el-button size="small" type="primary" plain v-if="row.status !== 'PUBLISHED'" @click="publishDef(row.id)">
            发布
          </el-button>
          <el-button size="small" type="danger" @click="deleteDef(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Start Flow Dialog -->
    <el-dialog v-model="startDialogVisible" title="启动流程" width="500px">
      <el-form label-width="100px">
        <el-form-item label="输入参数">
          <el-input type="textarea" v-model="startInputs" rows="4" placeholder='{"key": "value"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmStart">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { definitionApi, flowApi } from '../api'

const router = useRouter()
const definitions = ref([])
const loading = ref(false)

const startDialogVisible = ref(false)
const currentStartId = ref('')
const startInputs = ref('{}')

const loadData = async () => {
  loading.value = true
  try {
    definitions.value = await definitionApi.list()
  } finally {
    loading.value = false
  }
}

const deleteDef = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该流程定义吗?', '提示', { type: 'warning' })
    await definitionApi.delete(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const publishDef = async (id) => {
  await definitionApi.publish(id)
  ElMessage.success('发布成功')
  loadData()
}

const startFlow = (id) => {
  currentStartId.value = id
  startInputs.value = '{}'
  startDialogVisible.value = true
}

const confirmStart = async () => {
  try {
    const inputs = JSON.parse(startInputs.value)
    const res = await flowApi.start({ flowDefinitionId: currentStartId.value, inputs })
    ElMessage.success('启动成功')
    startDialogVisible.value = false
    router.push(`/instances/${res.flowInstanceId}`)
  } catch (e) {
    ElMessage.error('JSON格式错误或启动失败')
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
