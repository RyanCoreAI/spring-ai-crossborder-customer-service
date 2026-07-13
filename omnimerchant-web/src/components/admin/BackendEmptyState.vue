<template>
  <div class="backend-state">
    <a-skeleton v-if="loading" active :paragraph="{ rows: 4 }" />
    <a-result
      v-else-if="error"
      status="warning"
      title="数据加载失败"
      :sub-title="error"
    >
      <template #extra
        ><a-button @click="$emit('retry')">重试</a-button></template
      >
    </a-result>
    <a-empty v-else :description="description" />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ loading?: boolean; error?: string; description?: string }>(),
  { description: "暂无数据" },
);
defineEmits<{ retry: [] }>();
</script>

<style scoped>
.backend-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #fff;
  border: 1px solid var(--omni-border);
  border-radius: 6px;
}
.backend-state :deep(.ant-skeleton) {
  width: 100%;
}
</style>
