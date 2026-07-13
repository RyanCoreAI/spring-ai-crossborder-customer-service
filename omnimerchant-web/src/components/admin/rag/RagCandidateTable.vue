<template>
  <a-table
    :columns="columns"
    :data-source="rows"
    :row-key="rowKey"
    size="small"
    :pagination="{ pageSize: 5, hideOnSinglePage: true }"
  >
    <template #bodyCell="{ column, record }">
      <a-tag v-if="column.key === 'neighbor' && record.neighbor" color="blue">是</a-tag>
      <span v-else-if="column.key === 'neighbor'">否</span>
      <template v-else-if="scoreKeys.includes(String(column.key))">
        {{ formatScore(record[String(column.key) as keyof RagCandidate]) }}
      </template>
      <template v-else>{{ displayBusinessValue(record[column.dataIndex as keyof RagCandidate], String(column.dataIndex)) }}</template>
    </template>
    <template #emptyText><a-empty description="暂无候选" /></template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { displayBusinessValue } from "@/utils/display";
import type { RagCandidate } from "@/types/rag";

const props = withDefaults(defineProps<{ rows: RagCandidate[]; showNeighbor?: boolean }>(), {
  showNeighbor: false,
});
const scoreKeys = ["rrfScore", "rerankScore", "supportScore"];
const columns = computed(() => [
  { title: "片段 ID", dataIndex: "chunkUuid", key: "chunkUuid", ellipsis: true, width: 170 },
  { title: "文档", dataIndex: "docUuid", key: "docUuid", ellipsis: true, width: 150 },
  { title: "来源", dataIndex: "sourceTitle", key: "sourceTitle", ellipsis: true, width: 160 },
  { title: "章节", dataIndex: "sectionPath", key: "sectionPath", ellipsis: true, width: 160 },
  { title: "RRF", dataIndex: "rrfScore", key: "rrfScore", width: 90 },
  { title: "重排", dataIndex: "rerankScore", key: "rerankScore", width: 90 },
  { title: "支持分", dataIndex: "supportScore", key: "supportScore", width: 90 },
  ...(props.showNeighbor ? [{ title: "邻居", dataIndex: "neighbor", key: "neighbor", width: 80 }] : []),
  { title: "片段", dataIndex: "snippet", key: "snippet", ellipsis: true },
]);

function rowKey(row: RagCandidate) {
  return `${row.chunkUuid}-${row.fusedRank}-${row.neighbor}`;
}

function formatScore(value: unknown) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed.toFixed(parsed >= 10 ? 1 : 4) : "—";
}
</script>
