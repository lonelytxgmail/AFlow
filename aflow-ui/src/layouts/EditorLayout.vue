<template>
  <div class="editor-layout">
    <div class="editor-left" :style="{ width: leftWidth + 'px' }">
      <slot name="left" />
      <div class="resize-handle resize-left" @mousedown="startResize('left', $event)"></div>
    </div>
    <div class="editor-center">
      <slot name="center" />
    </div>
    <div class="editor-right" :style="{ width: rightWidth + 'px' }">
      <div class="resize-handle resize-right" @mousedown="startResize('right', $event)"></div>
      <slot name="right" />
    </div>
    <div class="editor-statusbar" v-if="$slots.statusbar">
      <slot name="statusbar" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'

const leftWidth = ref(240)
const rightWidth = ref(320)

let resizing: 'left' | 'right' | null = null
let startX = 0
let startWidth = 0

function startResize(side: 'left' | 'right', e: MouseEvent) {
  resizing = side
  startX = e.clientX
  startWidth = side === 'left' ? leftWidth.value : rightWidth.value
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onMouseMove(e: MouseEvent) {
  if (!resizing) return
  const delta = e.clientX - startX
  if (resizing === 'left') {
    leftWidth.value = Math.max(180, Math.min(400, startWidth + delta))
  } else {
    rightWidth.value = Math.max(250, Math.min(500, startWidth - delta))
  }
}

function stopResize() {
  resizing = null
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.editor-layout {
  display: flex;
  height: calc(100vh - 60px);
  position: relative;
  overflow: hidden;
}

.editor-left {
  position: relative;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  flex-shrink: 0;
  background: #fafafa;
}

.editor-center {
  flex: 1;
  min-width: 0;
  position: relative;
  overflow: hidden;
}

.editor-right {
  position: relative;
  border-left: 1px solid #e4e7ed;
  overflow-y: auto;
  flex-shrink: 0;
  background: #fff;
}

.editor-statusbar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 28px;
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 12px;
  color: #909399;
  z-index: 10;
}

.resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 4px;
  cursor: col-resize;
  z-index: 5;
}

.resize-handle:hover {
  background: #409EFF40;
}

.resize-left {
  right: 0;
}

.resize-right {
  left: 0;
}
</style>
