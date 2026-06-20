<template>
  <div class="message-bubble" :class="role">
    <div class="avatar">
      <el-avatar :size="36" :icon="role === 'user' ? UserFilled : Service" />
    </div>
    <div class="bubble-content">
      <div class="role-label">{{ role === 'user' ? '客户' : 'AI 助手' }}</div>
      <div class="text" v-html="renderedText"></div>
      <div v-if="toolCalls" class="tool-calls">
        <el-tag v-for="(tc, i) in toolCalls" :key="i" size="small" type="info" style="margin:2px">
          {{ tc.name }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { UserFilled, Service } from '@element-plus/icons-vue'
import { renderSafeMarkdown } from '@/utils/markdown'

const props = defineProps<{
  role: string
  text: string
  toolCalls?: { name: string }[]
}>()

const renderedText = computed(() => {
  if (!props.text) return ''
  return renderSafeMarkdown(props.text)
})
</script>

<style scoped>
.message-bubble {
  display: flex;
  gap: 12px;
  padding: 12px 20px;
  max-width: 85%;
}
.message-bubble.user {
  flex-direction: row-reverse;
  align-self: flex-end;
  margin-left: auto;
}
.message-bubble.assistant {
  align-self: flex-start;
}
.bubble-content {
  background: #fff;
  padding: 12px 16px;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  min-width: 0;
}
.user .bubble-content {
  background: #409eff;
  color: #fff;
}
.role-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.user .role-label {
  color: rgba(255,255,255,0.7);
}
.text {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.text :deep(p) { margin: 0 0 8px; }
.text :deep(p:last-child) { margin-bottom: 0; }
.text :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.text :deep(pre) {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
.tool-calls {
  margin-top: 8px;
}
</style>
