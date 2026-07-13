<template>
  <div>
    <MetricStrip :items="metrics" />
    <PageToolbar class="section-row">
      <a-select
        v-model:value="direction"
        allow-clear
        placeholder="全部方向"
        style="width: 150px"
      >
        <a-select-option value="IN">入站翻译</a-select-option>
        <a-select-option value="OUT">出站翻译</a-select-option>
      </a-select>
      <template #actions>
        <a-button @click="debugOpen = true"
          ><template #icon><ExperimentOutlined /></template>调试检测</a-button
        >
        <a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        >
      </template>
    </PageToolbar>

    <DataTableShell
      :columns="columns"
      :rows="filteredEvents"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      :scroll="{ x: 1120 }"
      empty-text="暂无真实翻译执行记录"
      @retry="load"
      @change="changePage"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'direction'">
          <a-tag :color="record.direction === 'IN' ? 'blue' : 'purple'">{{
            record.direction === "IN" ? "入站" : "出站"
          }}</a-tag>
        </template>
        <template v-else-if="column.key === 'language'">
          <strong>{{ languageLabel(record.sourceLanguage) }}</strong
          ><span class="language-arrow">→</span
          >{{ languageLabel(record.targetLanguage) }}
          <small>置信度 {{ confidence(record.detectionConfidence) }}</small>
        </template>
        <template v-else-if="column.key === 'text'">
          <strong class="text-preview">{{
            evidenceText(record.sourceText, "原文未保留")
          }}</strong>
          <small class="text-preview">{{
            evidenceText(record.translatedText, "未生成译文")
          }}</small>
        </template>
        <template v-else-if="column.key === 'provider'">
          <span>{{ record.provider || "—" }}</span
          ><small>{{ record.model || "—" }}</small>
        </template>
        <template v-else-if="column.key === 'status'"
          ><StatusTag
            :status="record.status"
            :label="statusLabel(record.status)"
        /></template>
        <template v-else-if="column.key === 'latency'"
          >{{ record.latencyMs ?? "—" }} ms</template
        >
        <template v-else-if="column.key === 'created'">{{
          formatDate(record.createdAt)
        }}</template>
      </template>
    </DataTableShell>

    <DetailDrawer
      :open="debugOpen"
      title="语言检测与翻译调试"
      width="560"
      @close="debugOpen = false"
    >
      <a-alert
        type="info"
        show-icon
        message="调试只返回后端实际检测与翻译结果，不生成模拟客服回复。"
      />
      <a-form layout="vertical" class="debug-form">
        <a-form-item label="原文"
          ><a-textarea
            v-model:value="debugForm.text"
            :rows="5"
            placeholder="输入中文、英文、西班牙语或日语文本"
        /></a-form-item>
        <a-form-item label="目标语言"
          ><a-select v-model:value="debugForm.targetLanguage"
            ><a-select-option
              v-for="item in languages"
              :key="item.value"
              :value="item.value"
              >{{ item.label }}</a-select-option
            ></a-select
          ></a-form-item
        >
        <a-button
          type="primary"
          block
          :disabled="!debugForm.text.trim()"
          :loading="debugging"
          @click="runDebug"
          >执行真实检测与翻译</a-button
        >
      </a-form>
      <a-descriptions
        v-if="debugResult"
        class="debug-result"
        :column="1"
        bordered
        size="small"
      >
        <a-descriptions-item label="检测语言"
          >{{ languageLabel(debugResult.detectedLanguage) }} ·
          {{ confidence(debugResult.confidence) }}</a-descriptions-item
        >
        <a-descriptions-item label="Agent 输入">{{
          debugResult.agentInput || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="提供方"
          >{{ debugResult.provider || "—" }} /
          {{ debugResult.model || "—" }}</a-descriptions-item
        >
        <a-descriptions-item label="耗时"
          >{{ debugResult.latencyMs ?? "—" }} ms</a-descriptions-item
        >
        <a-descriptions-item label="状态"
          ><StatusTag
            :status="debugResult.fallback ? 'DEGRADED' : debugResult.status"
            :label="
              debugResult.fallback ? '已降级' : statusLabel(debugResult.status)
            "
          />
          {{ debugResult.fallbackReason || "" }}</a-descriptions-item
        >
      </a-descriptions>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ExperimentOutlined, ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import DetailDrawer from "@/components/admin/DetailDrawer.vue";
import type {
  MultilingualDebugResult,
  MultilingualEvent,
  MultilingualSummary,
  TablePageChange,
} from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";

const summary = ref<MultilingualSummary | null>(null),
  events = ref<MultilingualEvent[]>([]),
  loading = ref(false),
  debugging = ref(false);
const error = ref(""),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  direction = ref<string>(),
  debugOpen = ref(false),
  debugResult = ref<MultilingualDebugResult | null>(null);
const debugForm = reactive({ text: "", targetLanguage: "en" });
const languages = [
  { value: "zh", label: "中文" },
  { value: "en", label: "英语" },
  { value: "es", label: "西班牙语" },
  { value: "ja", label: "日语" },
];
const columns = [
  { title: "方向", key: "direction", width: 90 },
  { title: "语言", key: "language", width: 170 },
  { title: "原文 / 译文", key: "text" },
  { title: "提供方", key: "provider", width: 170 },
  { title: "状态", key: "status", width: 105 },
  { title: "耗时", key: "latency", width: 90 },
  { title: "时间", key: "created", width: 165 },
];
const metrics = computed(() => [
  {
    key: "rate",
    label: "多语言会话占比",
    value: summary.value?.multilingualRate,
    suffix: "%",
    note: `${summary.value?.multilingualConversations ?? "—"} 个非英语会话`,
  },
  {
    key: "messages",
    label: "成功翻译事件",
    value: summary.value?.translatedMessages,
    note: "来自 translation_event",
  },
  {
    key: "fallback",
    label: "翻译降级率",
    value: summary.value?.translationFallbackRate,
    suffix: "%",
    note: "不用 0 代替未知",
  },
]);
const filteredEvents = computed(() =>
  direction.value
    ? events.value.filter((item) => item.direction === direction.value)
    : events.value,
);
const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: true,
}));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [a, b] = await Promise.all([
      api.get("/multilingual/summary"),
      api.get("/multilingual/events", {
        params: { page: page.value, size: size.value },
      }),
    ]);
    summary.value = a.data;
    events.value = b.data?.records || [];
    total.value = b.data?.total || 0;
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
async function runDebug() {
  debugging.value = true;
  try {
    debugResult.value = (await api.post("/multilingual/debug", debugForm)).data;
  } finally {
    debugging.value = false;
  }
}
function changePage(value: TablePageChange) {
  page.value = value.current || 1;
  size.value = value.pageSize || 20;
  void load();
}
function confidence(value: number | null | undefined) {
  return value === null || value === undefined
    ? "—"
    : `${(Number(value) * 100).toFixed(1)}%`;
}
function evidenceText(value: string | null | undefined, empty: string) {
  if (!value) return empty;
  return /^\?{4,}$/.test(value.trim()) ? "历史记录编码不可用" : value;
}
function formatDate(value: string) {
  return value ? new Date(value).toLocaleString("zh-CN") : "—";
}
function statusLabel(value: string) {
  const labels: Record<string, string> = {
    SUCCESS: "成功",
    FALLBACK: "已降级",
    SKIPPED: "无需翻译",
    ERROR: "失败",
  };
  return labels[value] || value || "未知";
}
function languageLabel(value: string) {
  const labels: Record<string, string> = {
    zh: "中文",
    en: "英语",
    es: "西班牙语",
    ja: "日语",
    fr: "法语",
    de: "德语",
    ar: "阿拉伯语",
    unknown: "未知",
  };
  return labels[value] || value || "—";
}
onMounted(load);
</script>

<style scoped>
small {
  display: block;
  margin-top: 3px;
  color: #98a2b3;
  font-size: 11px;
}
.language-arrow {
  margin: 0 7px;
  color: #98a2b3;
}
.text-preview {
  display: block;
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.debug-form,
.debug-result {
  margin-top: 16px;
}
</style>
