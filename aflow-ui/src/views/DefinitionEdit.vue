<template>
  <div class="definition-edit">
    <div class="header">
      <el-page-header @back="$router.back()" :content="isEdit ? '编辑定义 (可拖拽/连线)' : '新建定义 (可拖拽/连线)'" />
      <div class="actions">
        <el-button @click="showVariablePanel = true">
          <el-icon><SetUp /></el-icon> 变量定义
        </el-button>
        <el-button @click="showVersionDrawer = true" v-if="isEdit">
          <el-icon><Clock /></el-icon> 版本历史
        </el-button>
        <el-button @click="formatJson" v-if="activeTab === 'json'">格式化 DSL</el-button>
        <el-button type="primary" @click="save">保存 (Save)</el-button>
      </div>
    </div>

    <div class="form-container">
      <el-form :inline="true" size="small">
        <el-form-item label="定义 ID" required>
          <el-input v-model="form.id" :disabled="isEdit" placeholder="唯一标识符，不填则自动生成" style="width: 250px" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="工作流名称" style="width: 250px" />
        </el-form-item>
      </el-form>
    </div>

    <EditorLayout>
      <template #left>
        <NodePalette />
      </template>

      <template #center>
        <el-tabs v-model="activeTab" class="editor-tabs">
          <el-tab-pane label="可视化编排 (DAG)" name="dag">
            <div class="dag-wrapper" tabindex="0">
              <DagEditor
                v-if="isValidDsl && activeTab === 'dag'"
                v-model="parsedDsl"
                @node-select="handleNodeSelect"
              />
              <div v-else-if="!isValidDsl" class="error-overlay">
                JSON 格式错误，无法渲染 DAG
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="DSL 源码 (JSON)" name="json">
            <div class="dsl-editor" ref="editorContainer"></div>
          </el-tab-pane>
        </el-tabs>
      </template>

      <template #right>
        <NodeConfigPanel
          :node="selectedNodeData"
          @update:node="handleNodeUpdate"
        />
      </template>

      <template #statusbar>
        <StatusBar
          :node-count="parsedDsl.nodes?.length ?? 0"
          :edge-count="parsedDsl.edges?.length ?? 0"
          :save-status="saveStatus"
          :validation-errors="validationErrorCount"
          @click-validation="handleValidationClick"
        />
      </template>
    </EditorLayout>

    <!-- Variable Declaration Panel -->
    <VariableDeclarationPanel
      v-model="showVariablePanel"
      :variables="parsedDsl.variables"
      @update:variables="handleVariablesUpdate"
    />

    <!-- Version History Drawer -->
    <VersionHistoryDrawer
      v-if="isEdit"
      v-model="showVersionDrawer"
      :definition-id="form.id"
      @rollback="handleRollback"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { SetUp, Clock } from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'
import { definitionApi, atomicApi } from '../api'
import DagEditor from '../components/DagEditor.vue'
import NodeConfigPanel from '../components/NodeConfigPanel.vue'
import EditorLayout from '../layouts/EditorLayout.vue'
import NodePalette from '../components/dag/NodePalette.vue'
import StatusBar from '../components/common/StatusBar.vue'
import VariableDeclarationPanel from '../components/common/VariableDeclarationPanel.vue'
import VersionHistoryDrawer from '../components/common/VersionHistoryDrawer.vue'
import type { SaveStatus } from '../components/common/StatusBar.vue'
import type { FlowVariables } from '../types'
import type { ValidationError } from '../api/types'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const form = ref({
  id: '',
  name: '',
  dslContent: '{\n  "nodes": [],\n  "edges": []\n}'
})

const activeTab = ref('dag')

const saveStatus = ref<SaveStatus>('saved')

const parsedDsl = ref<{ nodes: any[]; edges: any[]; variables?: FlowVariables }>({ nodes: [], edges: [] })
const isValidDsl = ref(true)

const showVariablePanel = ref(false)
const showVersionDrawer = ref(false)

const selectedNodeId = ref<string | null>(null)
const selectedNodeData = computed(() => {
  if (!selectedNodeId.value || !parsedDsl.value.nodes) return null
  return parsedDsl.value.nodes.find((n: any) => n.id === selectedNodeId.value)
})

// --- Validation state ---
const validationErrors = ref<ValidationError[]>([])
const validationErrorCount = computed(() => {
  if (!isValidDsl.value) return 1
  return validationErrors.value.length
})

let validationTimer: ReturnType<typeof setTimeout> | null = null

/** Run DSL validation (local pre-check + optional backend validation) */
const runValidation = async () => {
  if (!isValidDsl.value) {
    validationErrors.value = [{ field: 'dslContent', code: 'INVALID_JSON', message: 'DSL 不是合法的 JSON', nodeId: null }]
    return
  }
  try {
    const result = await definitionApi.validate(form.value.dslContent)
    validationErrors.value = result.errors || []
  } catch {
    // If backend is unreachable, do local-only check
    validationErrors.value = []
  }
}

/** Debounced validation triggered on DSL changes */
const scheduleValidation = () => {
  if (validationTimer) clearTimeout(validationTimer)
  validationTimer = setTimeout(() => {
    runValidation()
  }, 1000)
}

/** Handle click on validation errors in status bar — jump to first problematic node */
const handleValidationClick = () => {
  if (validationErrors.value.length > 0) {
    const firstError = validationErrors.value[0]
    if (firstError.nodeId) {
      selectedNodeId.value = firstError.nodeId
      ElMessage.warning(`验证错误: ${firstError.message}`)
    } else {
      ElMessage.warning(`验证错误: ${firstError.message}`)
    }
  }
}

const editorContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let isSyncingDsl = false

const initEditor = () => {
  if (editor || !editorContainer.value) return
  editor = monaco.editor.create(editorContainer.value, {
    value: form.value.dslContent,
    language: 'json',
    theme: 'vs-light',
    automaticLayout: true,
    minimap: { enabled: false }
  })

  editor.onDidChangeModelContent(() => {
    if (isSyncingDsl) return
    form.value.dslContent = editor!.getValue()
    try {
      const parsed = JSON.parse(form.value.dslContent)
      if (parsed && typeof parsed === 'object') {
        parsedDsl.value = parsed
      }
      isValidDsl.value = true
    } catch (e) {
      isValidDsl.value = false
    }
  })
}

const updateMonacoFromDsl = () => {
  if (isSyncingDsl) return
  isSyncingDsl = true
  const jsonStr = JSON.stringify(parsedDsl.value, null, 2)
  form.value.dslContent = jsonStr
  if (editor && editor.getValue() !== jsonStr) {
    editor.setValue(jsonStr)
  }
  isSyncingDsl = false
}

// Watch DAG changes to update Monaco JSON
watch(parsedDsl, () => {
  updateMonacoFromDsl()
  if (saveStatus.value === 'saved') {
    saveStatus.value = 'unsaved'
  }
  scheduleValidation()
}, { deep: true })

// Initialize or switch tab logic
watch(activeTab, async (tab) => {
  if (tab === 'json') {
    await nextTick()
    if (!editor && editorContainer.value) {
      initEditor()
    }
    updateMonacoFromDsl()
  }
})

const formatJson = () => {
  if (editor) {
    editor.getAction('editor.action.formatDocument')?.run()
  }
}

const loadData = async () => {
  if (isEdit.value) {
    const res = await definitionApi.get(route.params.id as string)
    form.value.id = res.definition.id
    form.value.name = res.definition.name
    form.value.dslContent = res.dslContent || '{\n  "nodes": [],\n  "edges": []\n}'
    try {
      parsedDsl.value = JSON.parse(form.value.dslContent)
      isValidDsl.value = true
    } catch (e) {
      isValidDsl.value = false
    }
    if (editor) {
      isSyncingDsl = true
      editor.setValue(form.value.dslContent)
      isSyncingDsl = false
    }
  }
}

const save = async () => {
  try {
    JSON.parse(form.value.dslContent)
  } catch (e) {
    ElMessage.error('DSL不是合法的JSON')
    return
  }

  if (!form.value.name) {
    ElMessage.error('名称不能为空')
    return
  }

  saveStatus.value = 'saving'
  try {
    if (isEdit.value) {
      await definitionApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await definitionApi.create(form.value)
      ElMessage.success('创建成功')
      router.push('/definitions')
    }
    saveStatus.value = 'saved'
  } catch (e) {
    saveStatus.value = 'unsaved'
    ElMessage.error('保存失败')
  }
}

const handleNodeSelect = (nodeId: string) => {
  selectedNodeId.value = nodeId
}

const handleNodeUpdate = (updatedNode: any) => {
  const index = parsedDsl.value.nodes.findIndex((n: any) => n.id === updatedNode.id)
  if (index !== -1) {
    parsedDsl.value.nodes[index] = updatedNode
  }
}

const handleVariablesUpdate = (variables: FlowVariables) => {
  parsedDsl.value.variables = Object.keys(variables).length > 0 ? variables : undefined
}

const handleRollback = async (snapshotJson: string) => {
  try {
    parsedDsl.value = JSON.parse(snapshotJson)
    isValidDsl.value = true
    form.value.dslContent = snapshotJson
    if (editor) {
      isSyncingDsl = true
      editor.setValue(snapshotJson)
      isSyncingDsl = false
    }
    saveStatus.value = 'unsaved'
    ElMessage.success('已回滚到历史版本，请点击保存确认')
  } catch (e) {
    ElMessage.error('回滚失败：版本数据格式错误')
  }
}

const onDeleteKey = () => {
  if (selectedNodeId.value) {
    const confirm = window.confirm('确定删除该节点吗？')
    if (confirm) {
      parsedDsl.value.nodes = parsedDsl.value.nodes.filter((n: any) => n.id !== selectedNodeId.value)
      parsedDsl.value.edges = parsedDsl.value.edges.filter((ed: any) => ed.from !== selectedNodeId.value && ed.to !== selectedNodeId.value)
      selectedNodeId.value = null
    }
  }
}

// 全局键盘快捷键：Ctrl+S 保存, Delete 删除选中节点
const onGlobalKeydown = (e: KeyboardEvent) => {
  // Ctrl+S / Cmd+S: Save
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    save()
    return
  }

  // Delete: Remove selected node (only when DAG tab is active and focus is not in an input)
  if (e.key === 'Delete' && activeTab.value === 'dag') {
    const target = e.target as HTMLElement
    const isInputFocused = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable
    if (!isInputFocused) {
      onDeleteKey()
    }
  }
}

onMounted(() => {
  loadData()
  document.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onGlobalKeydown)
  if (editor) {
    editor.dispose()
  }
  if (validationTimer) {
    clearTimeout(validationTimer)
  }
})
</script>

<style scoped>
.definition-edit {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.form-container {
  background: white;
  padding: 8px 16px 0 16px;
  border-bottom: 1px solid #e4e7ed;
}

.editor-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.editor-tabs :deep(.el-tabs__content) {
  flex: 1;
  padding: 0;
}

.editor-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.dag-wrapper {
  height: 100%;
  position: relative;
  outline: none;
}

.dsl-editor {
  height: 100%;
  width: 100%;
}

.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 0, 0, 0.05);
  color: #f56c6c;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

</style>
