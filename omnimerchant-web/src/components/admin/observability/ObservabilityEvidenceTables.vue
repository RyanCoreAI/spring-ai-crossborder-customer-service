<template>
  <a-row :gutter="[18, 18]" class="evidence-row">
    <a-col :xs="24" :xl="12"><a-card title="工具调用质量">
      <a-empty v-if="!tools.length" description="暂无工具调用指标" />
      <a-table v-else :columns="toolColumns" :data-source="tools" :pagination="false" row-key="toolName" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'toolName'">{{ toolLabel(record.toolName) }}</template>
          <template v-else-if="column.key === 'successRate'">{{ formatPercent(record.successRate) }}</template>
          <template v-else-if="column.key === 'p95LatencyMs'">{{ formatLatency(record.p95LatencyMs) }}</template>
        </template>
      </a-table>
    </a-card></a-col>
    <a-col :xs="24" :xl="12"><a-card title="评测趋势">
      <a-empty v-if="!trend.length" description="暂无评测趋势" />
      <a-table v-else :columns="trendColumns" :data-source="trend" :pagination="false" row-key="runId" size="small">
        <template #bodyCell="{ column, record }">
          <a-tag v-if="column.key === 'status'" :color="record.status === 'PASS' ? 'green' : 'red'">{{ statusLabel(record.status) }}</a-tag>
          <template v-else-if="metricKeys.includes(String(column.key))">{{ formatPercent(record[column.dataIndex as keyof typeof record]) }}</template>
        </template>
      </a-table>
    </a-card></a-col>
  </a-row>
</template>

<script setup lang="ts">
import { toolLabel } from "@/utils/display";
import type { EvalTrendPoint, ToolMetric } from "@/types/observability";

defineProps<{ tools: ToolMetric[]; trend: EvalTrendPoint[] }>();
const metricKeys = ["passRate", "toolPrecision", "citationCoverage"];
const toolColumns = [{ title: "工具", dataIndex: "toolName", key: "toolName" }, { title: "调用", dataIndex: "calls", width: 80 }, { title: "成功率", dataIndex: "successRate", key: "successRate", width: 100 }, { title: "P95", dataIndex: "p95LatencyMs", key: "p95LatencyMs", width: 100 }, { title: "失败", dataIndex: "failures", width: 80 }];
const trendColumns = [{ title: "运行时间", dataIndex: "startedAt", width: 180 }, { title: "状态", dataIndex: "status", key: "status", width: 90 }, { title: "通过率", dataIndex: "passRate", key: "passRate", width: 90 }, { title: "工具精确率", dataIndex: "toolPrecision", key: "toolPrecision", width: 110 }, { title: "引用覆盖", dataIndex: "citationCoverage", key: "citationCoverage", width: 100 }];
function formatPercent(value?: number) { return value === null || value === undefined ? "—" : `${Number(value).toFixed(1)}%`; }
function formatLatency(value?: number) { return value === null || value === undefined ? "—" : `${Math.round(Number(value))} ms`; }
function statusLabel(value?: string) { return value === "PASS" ? "通过" : value === "FAIL" ? "失败" : value || "—"; }
</script>

<style scoped>.evidence-row { margin-top: 18px; }</style>
