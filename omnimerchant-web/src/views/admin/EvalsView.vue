<template>
  <div>
    <div class="page-head">
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="primary" :loading="running" @click="runEvals"
          >运行确定性评测</a-button
        >
      </a-space>
    </div>

    <a-alert
      v-if="runReport"
      class="run-alert"
      show-icon
      type="success"
      :message="`评测完成：${runReport.passed}/${runReport.total} 通过，通过率 ${runReport.passRate}%`"
    />

    <a-row :gutter="[16, 16]" class="summary">
      <a-col v-for="item in summaryCards" :key="item.label" :xs="12" :xl="6">
        <a-card>
          <a-statistic
            :title="item.label"
            :value="item.value"
            :suffix="item.suffix"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-card class="block">
      <a-tabs>
        <a-tab-pane key="runs" tab="最近运行">
          <a-table
            :columns="runColumns"
            :data-source="runs"
            row-key="id"
            size="small"
            :scroll="{ x: 1500 }"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag
                  :color="
                    record.status === 'PASS' || record.status === 'SUCCESS'
                      ? 'green'
                      : 'red'
                  "
                >
                  {{ statusLabel(record.status) }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'runMode'">
                {{ runModeLabel(record.runMode) }}
              </template>
              <template
                v-else-if="metricColumnKeys.includes(String(column.key))"
              >
                {{ metricPercent(record, column) }}
              </template>
              <template
                v-else-if="decimalColumnKeys.includes(String(column.key))"
              >
                {{ metricValue(record, column) }}
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-button size="small" @click="openRun(record)">详情</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="contract" tab="合同用例">
          <a-table
            :columns="caseColumns"
            :data-source="cases"
            :loading="loading"
            row-key="caseCode"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'default'">{{
                  record.enabled ? "启用" : "停用"
                }}</a-tag>
              </template>
              <template v-else-if="column.key === 'intent'">
                {{ intentLabel(record.intent) }}
              </template>
              <template v-else-if="column.key === 'expectedTools'">
                {{ toolsLabel(record.expectedTools) }}
              </template>
              <template v-else-if="column.key === 'attackType'">
                {{ attackTypeLabel(record.attackType) }}
              </template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="gold" tab="人工 GOLD">
          <div class="tab-toolbar">
            <span>只有人工审核通过的 GOLD 用例会进入评测。</span>
            <a-space>
              <a-button @click="goldDialogs?.openDataset()">新建数据集</a-button>
              <a-button
                type="primary"
                :disabled="!draftGoldDatasets.length"
                @click="goldDialogs?.openCreate()"
                >新建 GOLD 用例</a-button
              >
            </a-space>
          </div>
          <a-alert
            v-if="!draftGoldDatasets.length"
            class="inline-alert"
            type="info"
            show-icon
            message="请先创建一个 GOLD 草稿数据集，再添加人工标注用例。"
          />
          <a-table
            :columns="goldColumns"
            :data-source="goldCases"
            :loading="loading"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <a-tag
                v-if="column.key === 'annotationStatus'"
                :color="
                  record.annotationStatus === 'APPROVED'
                    ? 'green'
                    : record.annotationStatus === 'REJECTED'
                      ? 'red'
                      : 'orange'
                "
                >{{ goldStatusLabel(record.annotationStatus) }}</a-tag
              >
              <span v-else-if="column.key === 'expectedTools'">{{
                toolsLabel(record.expectedTools)
              }}</span>
              <a-space v-else-if="column.key === 'actions'">
                <a-button
                  v-if="record.annotationStatus === 'DRAFT'"
                  size="small"
                  type="primary"
                  @click="goldDialogs?.openReview(record, 'APPROVED')"
                  >通过</a-button
                >
                <a-button
                  v-if="record.annotationStatus === 'DRAFT'"
                  size="small"
                  danger
                  @click="goldDialogs?.openReview(record, 'REJECTED')"
                  >拒绝</a-button
                >
              </a-space>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-drawer v-model:open="drawer" title="评测运行详情" width="72%">
      <a-row :gutter="[12, 12]" class="drawer-summary">
        <a-col v-for="item in toolSummary" :key="item.label" :xs="12" :md="6">
          <a-statistic :title="item.label" :value="item.value" />
        </a-col>
      </a-row>
      <a-table
        :columns="resultColumns"
        :data-source="runDetail.results || []"
        row-key="caseCode"
        size="small"
        :scroll="{ x: 1500 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'PASS' ? 'green' : 'red'">{{
              statusLabel(record.status)
            }}</a-tag>
          </template>
          <template v-else-if="column.key === 'intent'">
            {{ intentLabel(record.intent) }}
          </template>
          <template v-else-if="column.key === 'argumentMatch'">
            <a-tag :color="record.argumentMatch ? 'green' : 'orange'">{{
              record.argumentMatch ? "匹配" : "不匹配"
            }}</a-tag>
          </template>
          <template v-else-if="column.key === 'forbiddenToolViolation'">
            <a-tag :color="record.forbiddenToolViolation ? 'red' : 'green'">{{
              record.forbiddenToolViolation ? "违规" : "正常"
            }}</a-tag>
          </template>
          <template v-else-if="column.key === 'trace'">
            <a-button
              v-if="record.traceId"
              size="small"
              @click="goTrace(record.traceId)"
              >打开</a-button
            >
            <span v-else>-</span>
          </template>
          <template
            v-else-if="
              column.key === 'expectedTools' || column.key === 'actualTools'
            "
          >
            {{ toolsLabel(record[column.dataIndex]) }}
          </template>
          <template v-else-if="column.key === 'failureCategory'">
            {{ failureCategoryLabel(record.failureCategory) }}
          </template>
          <template v-else-if="column.key === 'rerankerMode'">
            {{ rerankerModeLabel(record.rerankerMode) }}
          </template>
        </template>
      </a-table>
    </a-drawer>

    <GoldEvalDialogs ref="goldDialogs" :draft-datasets="draftGoldDatasets" @saved="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ReloadOutlined } from "@ant-design/icons-vue";
import { message } from "ant-design-vue";
import api from "@/api";
import GoldEvalDialogs from "@/components/admin/evals/GoldEvalDialogs.vue";
import type {
  EvalCaseSummary,
  EvalRunDetail,
  EvalRunReport,
  EvalRunSummary,
  EvalSummary,
  GoldDataset,
  GoldEvalCase,
} from "@/types/evals";
import {
  attackTypeLabel,
  failureCategoryLabel,
  intentLabel,
  rerankerModeLabel,
  runModeLabel,
  statusLabel as cnStatusLabel,
  toolsLabel,
} from "@/utils/display";

const router = useRouter();
const loading = ref(false);
const running = ref(false);
const summary = ref<EvalSummary>({});
const cases = ref<EvalCaseSummary[]>([]);
const runs = ref<EvalRunSummary[]>([]);
const runReport = ref<EvalRunReport | null>(null);
const drawer = ref(false);
const runDetail = ref<EvalRunDetail>({});
const goldCases = ref<GoldEvalCase[]>([]);
const goldDatasets = ref<GoldDataset[]>([]);
const goldDialogs = ref<InstanceType<typeof GoldEvalDialogs>>();
const draftGoldDatasets = computed(() =>
  goldDatasets.value.filter((item) => item.status === "DRAFT"),
);
const latestRun = computed<Partial<EvalRunSummary>>(() => runs.value[0] || {});
const detailResults = computed(() => runDetail.value?.results || []);
const toolSummary = computed(() => {
  const rows = detailResults.value;
  return [
    {
      label: "失败用例",
      value: rows.filter((row) => row.status !== "PASS").length,
    },
    {
      label: "禁止工具违规",
      value: rows.filter((row) => row.forbiddenToolViolation).length,
    },
    {
      label: "参数不匹配",
      value: rows.filter((row) => row.argumentMatch === false).length,
    },
    { label: "可回放轨迹", value: rows.filter((row) => row.traceId).length },
  ];
});

const summaryCards = computed(() => [
  {
    label: "最新通过率",
    value: displayValue(latestRun.value.passRate),
    suffix: hasValue(latestRun.value.passRate) ? "%" : undefined,
  },
  {
    label: "工具精确率",
    value: displayValue(latestRun.value.toolPrecision),
    suffix: hasValue(latestRun.value.toolPrecision) ? "%" : undefined,
  },
  {
    label: "引用覆盖率",
    value: displayValue(latestRun.value.citationCoverage),
    suffix: hasValue(latestRun.value.citationCoverage) ? "%" : undefined,
  },
  {
    label: "投毒拦截率",
    value: displayValue(latestRun.value.poisoningBlockRate),
    suffix: hasValue(latestRun.value.poisoningBlockRate) ? "%" : undefined,
  },
]);

const metricColumnKeys = [
  "passRate",
  "toolPrecision",
  "toolRecall",
  "citationCoverage",
  "retrievalPrecisionAtK",
  "recallAtK",
  "unsupportedClaimRate",
  "poisoningBlockRate",
  "noAnswerAccuracy",
];
const decimalColumnKeys = ["mrr", "ndcgAtK"];

const runColumns = [
  { title: "运行编号", dataIndex: "runUuid", ellipsis: true },
  { title: "模式", dataIndex: "runMode", key: "runMode", width: 130 },
  { title: "状态", dataIndex: "status", key: "status", width: 110 },
  { title: "通过率", dataIndex: "passRate", key: "passRate", width: 90 },
  {
    title: "工具精确率",
    dataIndex: "toolPrecision",
    key: "toolPrecision",
    width: 110,
  },
  {
    title: "工具召回率",
    dataIndex: "toolRecall",
    key: "toolRecall",
    width: 110,
  },
  {
    title: "引用覆盖率",
    dataIndex: "citationCoverage",
    key: "citationCoverage",
    width: 110,
  },
  {
    title: "检索@K",
    dataIndex: "retrievalPrecisionAtK",
    key: "retrievalPrecisionAtK",
    width: 100,
  },
  { title: "召回@K", dataIndex: "recallAtK", key: "recallAtK", width: 100 },
  { title: "MRR", dataIndex: "mrr", key: "mrr", width: 90 },
  { title: "nDCG@K", dataIndex: "ndcgAtK", key: "ndcgAtK", width: 100 },
  {
    title: "无答案准确",
    dataIndex: "noAnswerAccuracy",
    key: "noAnswerAccuracy",
    width: 120,
  },
  { title: "P95检索", dataIndex: "p95RetrievalLatencyMs", width: 100 },
  {
    title: "无依据率",
    dataIndex: "unsupportedClaimRate",
    key: "unsupportedClaimRate",
    width: 100,
  },
  {
    title: "投毒拦截",
    dataIndex: "poisoningBlockRate",
    key: "poisoningBlockRate",
    width: 110,
  },
  { title: "开始时间", dataIndex: "startedAt", width: 180 },
  { title: "操作", key: "actions", width: 90 },
];

const caseColumns = [
  { title: "用例编号", dataIndex: "caseCode", width: 150 },
  { title: "意图", dataIndex: "intent", key: "intent", width: 160 },
  { title: "用户消息", dataIndex: "userMessage", ellipsis: true },
  {
    title: "期望工具",
    dataIndex: "expectedTools",
    key: "expectedTools",
    ellipsis: true,
  },
  { title: "攻击类型", dataIndex: "attackType", key: "attackType", width: 150 },
  { title: "启用", dataIndex: "enabled", key: "enabled", width: 90 },
];

const resultColumns = [
  { title: "用例编号", dataIndex: "caseCode", width: 150 },
  { title: "意图", dataIndex: "intent", key: "intent", width: 140 },
  { title: "状态", dataIndex: "status", key: "status", width: 90 },
  { title: "工具精确率", dataIndex: "toolPrecision", width: 100 },
  { title: "工具召回率", dataIndex: "toolRecall", width: 100 },
  {
    title: "参数",
    dataIndex: "argumentMatch",
    key: "argumentMatch",
    width: 90,
  },
  {
    title: "禁止工具",
    dataIndex: "forbiddenToolViolation",
    key: "forbiddenToolViolation",
    width: 100,
  },
  {
    title: "期望工具",
    dataIndex: "expectedTools",
    key: "expectedTools",
    width: 180,
    ellipsis: true,
  },
  {
    title: "实际工具",
    dataIndex: "actualTools",
    key: "actualTools",
    width: 180,
    ellipsis: true,
  },
  {
    title: "失败归因",
    dataIndex: "failureCategory",
    key: "failureCategory",
    width: 130,
  },
  {
    title: "重排模式",
    dataIndex: "rerankerMode",
    key: "rerankerMode",
    width: 120,
  },
  { title: "检索排名", dataIndex: "retrievalRank", width: 90 },
  { title: "检索耗时", dataIndex: "retrievalLatencyMs", width: 90 },
  { title: "观测摘要", dataIndex: "actualObservation", ellipsis: true },
  { title: "轨迹", key: "trace", width: 90 },
];

const goldColumns = [
  { title: "用例编号", dataIndex: "caseCode", width: 160 },
  { title: "版本", dataIndex: "datasetVersion", width: 110 },
  { title: "意图", dataIndex: "intent", width: 130 },
  { title: "用户问题", dataIndex: "userMessage", ellipsis: true },
  {
    title: "期望工具",
    dataIndex: "expectedTools",
    key: "expectedTools",
    width: 190,
    ellipsis: true,
  },
  {
    title: "审核",
    dataIndex: "annotationStatus",
    key: "annotationStatus",
    width: 100,
  },
  { title: "审核人", dataIndex: "annotatedBy", width: 90 },
  { title: "操作", key: "actions", width: 130 },
];

function statusLabel(status: string) {
  return cnStatusLabel(status);
}

function metricValue(record: Record<string, unknown>, column: { dataIndex: string }) {
  return displayValue(record[column.dataIndex]);
}

function metricPercent(record: Record<string, unknown>, column: { dataIndex: string }) {
  const value = metricValue(record, column);
  return value === "—" ? value : `${value}%`;
}

function hasValue(value: unknown) {
  return value !== null && value !== undefined && value !== "";
}

function displayValue(value: unknown) {
  return hasValue(value) ? value : "—";
}

async function load() {
  loading.value = true;
  try {
    const [evals, runList, goldList, datasetList] = await Promise.all([
      api.get("/evals", { params: { page: 1, size: 100 } }),
      api.get("/evals/runs", { params: { page: 1, size: 20 } }),
      api.get("/evals/gold/cases", { params: { page: 1, size: 100 } }),
      api.get("/rag/datasets", { params: { kind: "GOLD" } }),
    ]);
    summary.value = evals.data || {};
    cases.value = evals.data?.cases?.records || [];
    runs.value = runList.data?.records || [];
    goldCases.value = goldList.data?.records || [];
    goldDatasets.value = datasetList.data || [];
  } finally {
    loading.value = false;
  }
}

async function runEvals() {
  running.value = true;
  try {
    const res = await api.post("/evals/run", {
      mode: "DETERMINISTIC",
      failOnThreshold: false,
    });
    runReport.value = res.data;
    await load();
  } finally {
    running.value = false;
  }
}

async function openRun(row: EvalRunSummary) {
  const res = await api.get(`/evals/runs/${row.id}`);
  runDetail.value = res.data || {};
  drawer.value = true;
}

function goTrace(traceId: string) {
  router.push({ path: "/admin/traces", query: { traceId } });
}

function goldStatusLabel(status: string) {
  return status === "APPROVED"
    ? "已通过"
    : status === "REJECTED"
      ? "已拒绝"
      : "待审核";
}

onMounted(load);
</script>

<style scoped>
.run-alert,
.summary,
.block {
  margin-bottom: 16px;
}

.drawer-summary {
  margin-bottom: 16px;
}

.tab-toolbar {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
}
.inline-alert {
  margin-bottom: 12px;
}
</style>
