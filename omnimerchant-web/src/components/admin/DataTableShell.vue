<template>
  <div class="table-shell">
    <BackendEmptyState
      v-if="loading || error || !rows.length"
      :loading="loading"
      :error="error"
      :description="emptyText"
      @retry="$emit('retry')"
    />
    <a-table
      v-else
      :columns="columns"
      :data-source="rows"
      :row-key="rowKey"
      :pagination="pagination"
      :scroll="scroll"
      size="middle"
      @change="$emit('change', $event)"
    >
      <template v-for="(_, name) in $slots" #[name]="slotData"
        ><slot :name="name" v-bind="slotData || {}"
      /></template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import type { TablePaginationConfig, TableProps } from "ant-design-vue";
import BackendEmptyState from "./BackendEmptyState.vue";
withDefaults(
  defineProps<{
    columns: object[];
    rows: object[];
    loading?: boolean;
    error?: string;
    emptyText?: string;
    rowKey?: TableProps<object>["rowKey"];
    pagination?: TableProps<object>["pagination"];
    scroll?: TableProps<object>["scroll"];
  }>(),
  { emptyText: "暂无符合条件的记录", rowKey: "id" },
);
defineEmits<{ retry: []; change: [value: TablePaginationConfig] }>();
</script>

<style scoped>
.table-shell {
  margin-top: 12px;
  border: 1px solid var(--omni-border);
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.table-shell :deep(.ant-table-wrapper) {
  margin: -1px;
}
</style>
