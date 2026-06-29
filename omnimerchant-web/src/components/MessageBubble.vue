<template>
  <div class="message-bubble" :class="role">
    <a-avatar class="avatar" :size="36">
      <template #icon>
        <UserOutlined v-if="role === 'user'" />
        <CustomerServiceOutlined v-else />
      </template>
    </a-avatar>
    <div class="bubble-content">
      <div class="role-label">{{ role === 'user' ? '客户' : '智能客服' }}</div>
      <div class="text" v-html="renderedText"></div>
      <div v-if="toolCalls?.length" class="tool-calls">
        <a-tag v-for="(tc, i) in toolCalls" :key="i" color="blue">
          {{ tc.name }}
        </a-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CustomerServiceOutlined, UserOutlined } from '@ant-design/icons-vue'
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
  max-width: min(760px, 88%);
  padding: 10px 20px;
}

.message-bubble.user {
  flex-direction: row-reverse;
  align-self: flex-end;
  margin-left: auto;
}

.message-bubble.assistant {
  align-self: flex-start;
}

.avatar {
  flex: 0 0 auto;
  background: #1677ff;
}

.assistant .avatar {
  background: #0f766e;
}

.bubble-content {
  min-width: 0;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.user .bubble-content {
  color: #fff;
  background: #1677ff;
  border-color: #1677ff;
}

.role-label {
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.user .role-label {
  color: rgba(255, 255, 255, 0.75);
}

.text {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.text :deep(p) {
  margin: 0 0 8px;
}

.text :deep(p:last-child) {
  margin-bottom: 0;
}

.text :deep(code) {
  padding: 2px 6px;
  font-size: 13px;
  background: rgba(15, 23, 42, 0.08);
  border-radius: 4px;
}

.text :deep(pre) {
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  background: #f8fafc;
  border-radius: 8px;
}

.tool-calls {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
</style>
