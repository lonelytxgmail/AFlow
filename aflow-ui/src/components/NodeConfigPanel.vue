<template>
  <div class="node-config-panel" v-if="node">
    <div class="panel-header">
      <h3>节点属性配置</h3>
    </div>
    <el-form label-position="top" size="small">
      <el-form-item label="节点 ID (只读)">
        <el-input :model-value="node.id" disabled />
      </el-form-item>
      <el-form-item label="节点类型 (只读)">
        <el-tag>{{ node.type }}</el-tag>
      </el-form-item>
      <el-form-item label="节点名称">
        <el-input v-model="node.name" @change="emitUpdate" placeholder="请输入节点名称" />
      </el-form-item>
      <el-form-item label="输出变量">
        <el-input v-model="node.output" @change="emitUpdate" placeholder="如：result / agent_result" />
      </el-form-item>
      <el-form-item label="调试断点">
        <el-switch v-model="node.breakpoint" @change="emitUpdate" />
      </el-form-item>
      <el-form-item label="节点配置 (Config JSON)">
        <div class="config-editor" ref="configEditorRef"></div>
      </el-form-item>
      <template v-if="node.type === 'agent'">
        <el-form-item label="用户提示词">
          <el-input v-model="agentConfig.userPrompt" type="textarea" :rows="3" @change="applyAgentConfig" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="agentConfig.systemPrompt" type="textarea" :rows="3" @change="applyAgentConfig" />
        </el-form-item>
        <el-form-item label="工具白名单">
          <el-input v-model="agentConfig.tools" placeholder="逗号分隔，如 atomic_component_1,http_tool" @change="applyAgentConfig" />
        </el-form-item>
        <el-form-item label="最大轮数">
          <el-input-number v-model="agentConfig.maxIterations" :min="1" :max="20" @change="applyAgentConfig" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="agentConfig.model" placeholder="如 gpt-4o-mini" @change="applyAgentConfig" />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-input-number v-model="agentConfig.temperature" :min="0" :max="2" :step="0.1" @change="applyAgentConfig" />
        </el-form-item>
      </template>
      <template v-if="node.type === 'composite'">
        <el-form-item label="原子能力 ID">
          <el-input v-model="compositeConfig.componentId" placeholder="如 component-1" @change="applyCompositeConfig" />
        </el-form-item>
        <el-form-item label="调用参数 JSON">
          <el-input v-model="compositeConfig.paramsText" type="textarea" :rows="4" @change="applyCompositeConfig" />
        </el-form-item>
      </template>
      <el-form-item v-if="node.type === 'agent'" label="Agent 配置提示">
        <div class="config-hint">
          建议配置 `userPrompt`、`systemPrompt`、`tools`、`maxIterations`、`model`、`temperature`。
        </div>
      </el-form-item>
      <el-form-item v-if="node.type === 'composite'" label="原子能力提示">
        <div class="config-hint">
          组合节点应包含 `componentId`，可选 `params` 作为调用参数。
        </div>
      </el-form-item>
    </el-form>
  </div>
  <div class="node-config-panel empty" v-else>
    <el-empty description="请选择一个节点" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, nextTick } from 'vue'
import * as monaco from 'monaco-editor'

interface NodeModel {
  id: string
  type: string
  name: string
  config: Record<string, any>
  output?: string
  breakpoint?: boolean
}

const props = defineProps<{
  node: NodeModel | null
}>()

const emit = defineEmits<{
  'update:node': [value: NodeModel]
}>()

const configEditorRef = ref<HTMLElement | null>(null)
const agentConfig = ref({
  userPrompt: '',
  systemPrompt: '',
  tools: '',
  maxIterations: 5,
  model: '',
  temperature: 0
})
const compositeConfig = ref({
  componentId: '',
  paramsText: '{\n}'
})
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let isSyncing = false

const initEditor = () => {
  if (!configEditorRef.value) return
  
  const initialValue = props.node?.config ? JSON.stringify(props.node.config, null, 2) : '{\n}'
  
  editor = monaco.editor.create(configEditorRef.value, {
    value: initialValue,
    language: 'json',
    theme: 'vs-light',
    automaticLayout: true,
    minimap: { enabled: false },
    lineNumbers: 'off',
    scrollBeyondLastLine: false,
    wordWrap: 'on'
  })

  editor.onDidChangeModelContent(() => {
    if (isSyncing) return
    try {
      const parsed = JSON.parse(editor!.getValue())
      if (props.node) {
        props.node.config = parsed
        emitUpdate()
      }
    } catch {
      // ignore invalid json during typing
    }
  })
}

const emitUpdate = () => {
  if (props.node) {
    emit('update:node', { ...props.node })
  }
}

const syncStructuredForms = () => {
  const config = props.node?.config || {}
  agentConfig.value = {
    userPrompt: config.userPrompt || '',
    systemPrompt: config.systemPrompt || '',
    tools: Array.isArray(config.tools) ? config.tools.join(',') : (config.tools || ''),
    maxIterations: Number(config.maxIterations ?? 5),
    model: config.model || '',
    temperature: Number(config.temperature ?? 0)
  }
  compositeConfig.value = {
    componentId: config.componentId || '',
    paramsText: JSON.stringify(config.params || {}, null, 2)
  }
}

const syncEditorValue = () => {
  if (!editor) return
  isSyncing = true
  const val = props.node?.config ? JSON.stringify(props.node.config, null, 2) : '{\n}'
  if (editor.getValue() !== val) {
    editor.setValue(val)
  }
  isSyncing = false
}

const applyAgentConfig = () => {
  if (!props.node) return
  props.node.config = {
    ...props.node.config,
    userPrompt: agentConfig.value.userPrompt,
    systemPrompt: agentConfig.value.systemPrompt,
    tools: agentConfig.value.tools,
    maxIterations: agentConfig.value.maxIterations,
    model: agentConfig.value.model,
    temperature: agentConfig.value.temperature
  }
  syncEditorValue()
  emitUpdate()
}

const applyCompositeConfig = () => {
  if (!props.node) return
  try {
    const params = JSON.parse(compositeConfig.value.paramsText || '{}')
    props.node.config = {
      ...props.node.config,
      componentId: compositeConfig.value.componentId,
      params
    }
    syncEditorValue()
    emitUpdate()
  } catch {
    // keep user typing state until valid JSON
  }
}

watch(() => props.node?.id, async (newId) => {
  if (newId) {
    if (!editor) {
      await nextTick()
      initEditor()
    } else {
      syncEditorValue()
    }
    syncStructuredForms()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
  }
})
</script>

<style scoped>
.node-config-panel {
  padding: 15px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.node-config-panel.empty {
  justify-content: center;
}
.panel-header {
  margin-bottom: 20px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 10px;
}
.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}
.config-editor {
  height: 300px;
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.config-hint {
  width: 100%;
  padding: 10px 12px;
  border-radius: 4px;
  background: #f4f4f5;
  color: #606266;
  line-height: 1.5;
  font-size: 12px;
}
</style>
