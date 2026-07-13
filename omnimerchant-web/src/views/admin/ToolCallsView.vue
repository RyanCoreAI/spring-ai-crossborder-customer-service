<template>
  <div>
    <div class="page-head">
      <a-button :loading="loading" @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-card class="toolbar-card" :bordered="false">
      <div class="toolbar">
        <a-select
          v-model:value="toolName"
          allow-clear
          placeholder="筛选工具"
          style="width: 220px"
          @change="load"
        >
          <a-select-option
            v-for="tool in toolOptions"
            :key="tool"
            :value="tool"
          >
            {{ toolLabel(tool) }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="success"
          allow-clear
          placeholder="调用结果"
          style="width: 140px"
          @change="load"
        >
          <a-select-option :value="1">成功</a-select-option>
          <a-select-option :value="0">失败</a-select-option>
        </a-select>
        <a-button type="primary" @click="load">查询</a-button>
      </div>
    </a-card>

    <a-row :gutter="[14, 14]" class="metric-row">
      <a-col :xs="12" :md="6">
        <a-card><a-statistic title="工具调用" :value="total" /></a-card>
      </a-col>
      <a-col :xs="12" :md="6">
        <a-card
          ><a-statistic title="当前页失败" :value="failedOnPage"
        /></a-card>
      </a-col>
      <a-col :xs="12" :md="6">
        <a-card
          ><a-statistic title="当前页 P95 耗时" :value="p95Latency" suffix="ms"
        /></a-card>
      </a-col>
      <a-col :xs="12" :md="6">
        <a-card
          ><a-statistic title="可回放 Trace" :value="traceCount"
        /></a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :custom-row="auditRow"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1200 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'toolName'">
            {{ toolLabel(record.toolName) }}
          </template>
          <template v-else-if="column.key === 'success'">
            <a-tag :color="record.success ? 'green' : 'red'">{{
              record.success ? "成功" : "失败"
            }}</a-tag>
          </template>
          <template v-else-if="column.key === 'triggeredByModel'">
            <a-tag :color="record.triggeredByModel ? 'blue' : 'default'">
              {{ record.triggeredByModel ? "模型触发" : "系统触发" }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'latencyMs'">
            {{ formatLatency(record.latencyMs) }}
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ displayBusinessValue(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'trace'">
            <a-button
              v-if="record.traceId"
              size="small"
              @click.stop="goTrace(record.traceId)"
              >回放</a-button
            >
            <span v-else>—</span>
          </template>
        </template>
      </a-table>
      <a-pagination
        v-model:current="page"
        class="pager"
        :page-size="size"
        :total="total"
        :show-size-changer="false"
        @change="load"
      />
    </a-card>

    <a-drawer v-model:open="drawer" title="工具调用详情" width="680px">
      <a-descriptions v-if="selected" :column="1" bordered>
        <a-descriptions-item label="工具">{{
          toolLabel(selected.toolName)
        }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="selected.success ? 'green' : 'red'">{{
            selected.success ? "成功" : "失败"
          }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="Trace">
          <a-space>
            <span>{{ selected.traceId || "—" }}</span>
            <a-button
              v-if="selected.traceId"
              size="small"
              @click="goTrace(selected.traceId)"
              >打开轨迹</a-button
            >
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item label="会话">{{
          selected.conversationUuid || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="工具调用 ID">{{
          selected.toolCallId || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="耗时">{{
          formatLatency(selected.latencyMs)
        }}</a-descriptions-item>
        <a-descriptions-item label="触发来源">
          {{
            selected.triggeredByModel
              ? "模型请求工具，应用执行工具"
              : "系统流程触发"
          }}
        </a-descriptions-item>
        <a-descriptions-item label="错误码">{{
          selected.errorCode || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="错误信息">{{
          selected.errorMessage || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{
          displayBusinessValue(selected.createdAt)
        }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import { displayBusinessValue, toolLabel } from "@/utils/display";
import type { ToolCallRecord } from "@/types/contracts";

const router = useRouter();
const toolName = ref<string | undefined>();
const success = ref<number | undefined>();
const rows = ref<ToolCallRecord[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const drawer = ref(false);
const selected = ref<ToolCallRecord | null>(null);

const toolOptions = [
  "queryOrder",
  "trackLogistics",
  "searchProductCatalog",
  "refundPolicyRAG",
  "answerPolicy",
  "createReturnRequest",
  "requestRefundOrReplacement",
  "requestAddressChange",
  "escalateToHuman",
  "translate",
];

const columns = [
  { title: "工具", dataIndex: "toolName", key: "toolName", width: 180 },
  { title: "结果", dataIndex: "success", key: "success", width: 90 },
  { title: "Trace", dataIndex: "traceId", ellipsis: true, width: 190 },
  { title: "会话", dataIndex: "conversationUuid", ellipsis: true, width: 190 },
  { title: "耗时", dataIndex: "latencyMs", key: "latencyMs", width: 100 },
  {
    title: "触发来源",
    dataIndex: "triggeredByModel",
    key: "triggeredByModel",
    width: 110,
  },
  { title: "错误码", dataIndex: "errorCode", width: 120 },
  { title: "错误信息", dataIndex: "errorMessage", ellipsis: true },
  { title: "时间", dataIndex: "createdAt", key: "createdAt", width: 180 },
  { title: "轨迹", key: "trace", width: 90 },
];

const failedOnPage = computed(
  () => rows.value.filter((row) => !row.success).length,
);
const traceCount = computed(
  () => rows.value.filter((row) => row.traceId).length,
);
const p95Latency = computed(() => {
  const values = rows.value
    .map((row) => Number(row.latencyMs))
    .filter((value) => Number.isFinite(value))
    .sort((a, b) => a - b);
  if (!values.length) return 0;
  const index = Math.max(0, Math.ceil(values.length * 0.95) - 1);
  return values[index];
});

function formatLatency(value: number | null | undefined) {
  return value === null || value === undefined ? "—" : `${value}ms`;
}

function auditRow(record: ToolCallRecord) {
  return {
    style: { cursor: "pointer" },
    onClick: () => {
      selected.value = record;
      drawer.value = true;
    },
  };
}

function goTrace(traceId: string) {
  router.push({ path: "/admin/traces", query: { traceId } });
}

async function load() {
  loading.value = true;
  try {
    const params: { page: number; size: number; toolName?: string; success?: number } = { page: page.value, size: size.value };
    if (toolName.value) params.toolName = toolName.value;
    if (success.value !== null && success.value !== undefined)
      params.success = success.value;
    const res = await api.get("/tool-calls", { params });
    rows.value = res.data?.records || [];
    total.value = res.data?.total || 0;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar-card,
.metric-row {
  margin-bottom: 14px;
}
</style>
