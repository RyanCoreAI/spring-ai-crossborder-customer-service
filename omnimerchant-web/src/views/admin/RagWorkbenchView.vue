<template>
  <div class="rag-workbench">
    <div class="page-head">
      <a-space class="head-actions">
        <a-button :loading="loadingHealth" @click="loadHealth"
          >刷新健康</a-button
        >
        <a-button @click="openGovernance">RAG 治理</a-button>
      </a-space>
    </div>

    <div class="health-grid">
      <a-card v-for="item in healthCards" :key="item.label" class="health-card">
        <div class="health-label">{{ item.label }}</div>
        <div class="health-value">{{ item.value }}</div>
        <div class="health-note">{{ item.note }}</div>
      </a-card>
    </div>

    <a-card class="query-card">
      <div class="query-form">
        <a-select
          v-model:value="selectedCaseCode"
          allow-clear
          :loading="loadingCases"
          show-search
          option-filter-prop="label"
          placeholder="从后端基准用例选择一个 RAG/政策/商品问题"
          @change="applyEvalCase"
        >
          <a-select-option
            v-for="item in evalCases"
            :key="item.caseCode"
            :value="item.caseCode"
            :label="`${item.caseCode} ${item.userMessage}`"
          >
            {{ item.caseCode }} · {{ intentLabel(item.intent) }} ·
            {{ item.userMessage }}
          </a-select-option>
        </a-select>
        <a-textarea
          v-model:value="question"
          :rows="3"
          placeholder="输入要调试的买家问题，例如：这件外套可以退货吗？"
          :maxlength="500"
          show-count
        />
        <div class="query-controls">
          <a-select
            v-model:value="docType"
            allow-clear
            placeholder="文档类型"
            style="width: 180px"
          >
            <a-select-option value="REFUND_POLICY">退货政策</a-select-option>
            <a-select-option value="SHIPPING_POLICY">物流政策</a-select-option>
            <a-select-option value="FAQ">FAQ</a-select-option>
            <a-select-option value="PRODUCT_GUIDE">商品指南</a-select-option>
          </a-select>
          <a-select
            v-model:value="language"
            allow-clear
            placeholder="语言"
            style="width: 130px"
          >
            <a-select-option value="zh">中文</a-select-option>
            <a-select-option value="en">英文</a-select-option>
          </a-select>
          <a-select v-model:value="retrievalMode" style="width: 190px">
            <a-select-option value="VECTOR_ONLY">仅向量召回</a-select-option>
            <a-select-option value="BM25_ONLY">仅 BM25</a-select-option>
            <a-select-option value="HYBRID">混合召回</a-select-option>
            <a-select-option value="HYBRID_RERANK"
              >混合召回 + 重排</a-select-option
            >
          </a-select>
          <a-button type="primary" :loading="debugLoading" @click="runDebug"
            >运行调试</a-button
          >
          <a-button :loading="evalLoading" @click="runRagEval"
            >运行 RAG 评测</a-button
          >
        </div>
      </div>
    </a-card>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="9">
        <a-card title="查询规划">
          <a-empty v-if="!debugResult" description="运行调试后显示查询规划" />
          <a-descriptions v-else size="small" :column="1" bordered>
            <a-descriptions-item label="原始问题">{{
              debugResult.queryPlan?.originalQuery || "—"
            }}</a-descriptions-item>
            <a-descriptions-item label="改写查询">{{
              debugResult.queryPlan?.rewrittenQuery || "—"
            }}</a-descriptions-item>
            <a-descriptions-item label="意图">{{
              intentLabel(debugResult.queryPlan?.intent)
            }}</a-descriptions-item>
            <a-descriptions-item label="语言">{{
              languageLabel(debugResult.queryPlan?.detectedLanguage)
            }}</a-descriptions-item>
            <a-descriptions-item label="扩展词">
              <a-space wrap>
                <a-tag
                  v-for="term in debugResult.queryPlan?.expansions || []"
                  :key="term"
                  >{{ term }}</a-tag
                >
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card class="evidence-card" title="证据判断">
          <a-empty v-if="!debugResult" description="暂无证据结果" />
          <template v-else>
            <a-result
              :status="evidenceStatus"
              :title="evidenceLabel(debugResult.contextPack?.evidenceLevel)"
              :sub-title="
                refusalReasonLabel(debugResult.contextPack?.refusalReason)
              "
            />
            <a-descriptions size="small" :column="1" bordered>
              <a-descriptions-item label="使用片段">{{
                debugResult.contextPack?.usedChunks ?? 0
              }}</a-descriptions-item>
              <a-descriptions-item label="预算字符">{{
                debugResult.contextPack?.budgetChars ?? 0
              }}</a-descriptions-item>
              <a-descriptions-item label="耗时"
                >{{ debugResult.latencyMs ?? 0 }} ms</a-descriptions-item
              >
              <a-descriptions-item label="活动索引">{{
                debugResult.activeIndexVersion || "未启用版本锁定"
              }}</a-descriptions-item>
              <a-descriptions-item label="检索模式">{{
                retrievalModeLabel(debugResult.retrievalMode)
              }}</a-descriptions-item>
              <a-descriptions-item label="重排状态">{{
                rerankerModeLabel(debugResult.rerankerMode)
              }}</a-descriptions-item>
            </a-descriptions>
          </template>
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="15">
        <a-card title="检索候选">
          <a-tabs v-model:activeKey="activeTab">
            <a-tab-pane key="vector" tab="向量候选">
              <candidate-table :rows="debugResult?.vectorCandidates || []" />
            </a-tab-pane>
            <a-tab-pane key="bm25" tab="BM25 候选">
              <candidate-table :rows="debugResult?.bm25Candidates || []" />
            </a-tab-pane>
            <a-tab-pane key="fusion" tab="融合排序">
              <candidate-table :rows="debugResult?.fusedCandidates || []" />
            </a-tab-pane>
            <a-tab-pane key="context" tab="最终上下文">
              <candidate-table
                :rows="debugResult?.expandedContext || []"
                :show-neighbor="true"
              />
            </a-tab-pane>
          </a-tabs>
        </a-card>

        <a-card class="context-card" title="上下文打包">
          <a-empty
            v-if="!debugResult?.contextPack?.context"
            description="暂无上下文片段"
          />
          <pre v-else>{{ debugResult.contextPack.context }}</pre>
        </a-card>
      </a-col>
    </a-row>

    <RagGovernancePanel
      v-model="governanceOpen"
      :question="question"
      :candidate="debugResult?.expandedContext?.[0]"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import api from "@/api";
import RagCandidateTable from "@/components/admin/rag/RagCandidateTable.vue";
import RagGovernancePanel from "@/components/admin/rag/RagGovernancePanel.vue";
import type { RagDebugResult, RagEvalCase, RagHealth } from "@/types/rag";

const loadingHealth = ref(false);
const debugLoading = ref(false);
const evalLoading = ref(false);
const loadingCases = ref(false);
const governanceOpen = ref(false);
const selectedCaseCode = ref<string | undefined>();
const evalCases = ref<RagEvalCase[]>([]);
const health = ref<RagHealth | null>(null);
const debugResult = ref<RagDebugResult | null>(null);
const question = ref("");
const docType = ref<string | undefined>();
const language = ref<string | undefined>();
const retrievalMode = ref("HYBRID_RERANK");
const activeTab = ref("vector");
const healthCards = computed(() => [
  {
    label: "已发布知识",
    value: valueOrDash(health.value?.approvedDocs),
    note: "可参与检索的知识文档",
  },
  {
    label: "待审核",
    value: valueOrDash(health.value?.pendingReviews),
    note: "需人工处理的安全审核",
  },
  {
    label: "向量检索",
    value: vectorStatusLabel(health.value?.vectorStatus),
    note: `${valueOrDash(health.value?.vectorChunkCount)} 个已索引片段`,
  },
  {
    label: "低证据运行",
    value: valueOrDash(health.value?.lowEvidenceRuns),
    note: "eval 中存在无依据或弱依据",
  },
]);

const evidenceStatus = computed(() => {
  const level = debugResult.value?.contextPack?.evidenceLevel;
  if (level === "SUFFICIENT") return "success";
  if (level === "PARTIAL") return "warning";
  return "error";
});

function valueOrDash(value: unknown) {
  return value === null || value === undefined ? "—" : value;
}

function vectorStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    READY: "可用",
    NO_EMBEDDING_MODEL: "未配置模型",
    EMPTY_INDEX: "索引为空",
  };
  return labels[status || ""] || "未知";
}

function formatScore(value: unknown) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return "—";
  return parsed.toFixed(parsed >= 10 ? 1 : 4);
}

function evidenceLabel(level?: string) {
  const labels: Record<string, string> = {
    NONE: "没有证据",
    WEAK: "证据较弱",
    PARTIAL: "证据有限",
    SUFFICIENT: "证据充分",
  };
  return labels[level || ""] || level || "暂无判断";
}

function intentLabel(intent?: string) {
  const labels: Record<string, string> = {
    RETURN_REFUND: "退货退款",
    LOGISTICS: "物流问题",
    PRODUCT_ADVICE: "商品咨询",
    POLICY_QA: "政策问答",
  };
  return labels[intent || ""] || intent || "—";
}

function languageLabel(value?: string) {
  const labels: Record<string, string> = { zh: "中文", en: "英文" };
  return labels[value || ""] || value || "—";
}

function retrievalModeLabel(value?: string) {
  const labels: Record<string, string> = {
    VECTOR_ONLY: "仅向量召回",
    BM25_ONLY: "仅 BM25",
    HYBRID: "混合召回",
    HYBRID_RERANK: "混合召回 + 重排",
  };
  return labels[value || ""] || value || "—";
}

function rerankerModeLabel(value?: string) {
  const labels: Record<string, string> = {
    "cross-encoder": "Cross-Encoder 已执行",
    "fallback-rrf": "Cross-Encoder 不可用，已回退 RRF",
    "skipped-empty": "无候选，未执行重排",
    "skipped-not-required": "候选较少，无需重排",
    "disabled-by-mode": "当前模式未启用重排",
  };
  return labels[value || ""] || value || "—";
}

async function loadHealth() {
  loadingHealth.value = true;
  try {
    const res = await api.get("/rag/health");
    health.value = res.data || null;
  } finally {
    loadingHealth.value = false;
  }
}

async function loadEvalCases(autoDebug = false) {
  loadingCases.value = true;
  try {
    const res = await api.get("/evals", { params: { page: 1, size: 100 } });
    const records = (res.data?.cases?.records || []) as RagEvalCase[];
    evalCases.value = records.filter(isRagCase);
    if (
      autoDebug &&
      evalCases.value.length > 0 &&
      !selectedCaseCode.value &&
      !question.value
    ) {
      selectedCaseCode.value = preferredDemoCase(evalCases.value).caseCode;
      applyEvalCase(selectedCaseCode.value);
      await runDebug();
    }
  } finally {
    loadingCases.value = false;
  }
}

function isRagCase(item: RagEvalCase) {
  const text = `${item.intent || ""} ${item.expectedTools || ""} ${item.attackType || ""} ${item.caseCode || ""}`;
  return /POLICY_QA|RETURN_REFUND|PRODUCT_ADVICE|refundPolicyRAG|RAG_POISONING|POLICY|RETURN|PRODUCT/.test(
    text,
  );
}

function preferredDemoCase(items: RagEvalCase[]) {
  return (
    items.find(
      (item) =>
        isBusinessDemoCase(item) &&
        hasChinese(item.userMessage) &&
        (item.intent === "RETURN_REFUND" || item.intent === "POLICY_QA"),
    ) ||
    items.find(
      (item) => isBusinessDemoCase(item) && hasChinese(item.userMessage),
    ) ||
    items.find(
      (item) =>
        isBusinessDemoCase(item) &&
        (item.intent === "RETURN_REFUND" || item.intent === "POLICY_QA"),
    ) ||
    items.find(
      (item) => isBusinessDemoCase(item) && item.intent === "PRODUCT_ADVICE",
    ) ||
    items[0]
  );
}

function isBusinessDemoCase(item: RagEvalCase) {
  const marker =
    `${item.attackType || ""} ${item.caseCode || ""}`.toUpperCase();
  return !/(INJECT|POISON|CROSS|NOANSWER|PROMPT|TENANT|IDENTITY)/.test(marker);
}

function hasChinese(value?: string) {
  return /[\u4e00-\u9fa5]/.test(value || "");
}

function refusalReasonLabel(reason?: string) {
  if (!reason) return "证据达到明确回答标准";
  if (reason.includes("RAG_PARTIAL_EVIDENCE"))
    return "依据有限，回答必须说明不确定性。";
  if (reason.includes("RAG_NO_CITATION"))
    return "缺少可引用证据，不能给出确定结论。";
  if (reason.includes("UNSUPPORTED_CLAIM"))
    return "回答中的关键结论缺少证据支持。";
  return reason;
}

function applyEvalCase(caseCode?: string) {
  const selected = evalCases.value.find((item) => item.caseCode === caseCode);
  if (!selected) return;
  question.value = selected.userMessage || "";
  if (selected.intent === "POLICY_QA" || selected.intent === "RETURN_REFUND") {
    docType.value =
      selected.userMessage?.toLowerCase().includes("shipping") ||
      selected.userMessage?.includes("配送")
        ? "SHIPPING_POLICY"
        : "REFUND_POLICY";
  } else if (selected.intent === "PRODUCT_ADVICE") {
    docType.value = "PRODUCT_GUIDE";
  }
  language.value = /[\u4e00-\u9fa5]/.test(question.value) ? "zh" : undefined;
}

async function runDebug() {
  if (!question.value.trim()) {
    message.warning("请输入要调试的问题");
    return;
  }
  debugLoading.value = true;
  try {
    const res = await api.post("/rag/query/debug", {
      question: question.value.trim(),
      docType: docType.value,
      language: language.value,
      topK: 8,
      retrievalMode: retrievalMode.value,
    });
    debugResult.value = res.data || null;
    activeTab.value = "fusion";
  } finally {
    debugLoading.value = false;
  }
}

async function runRagEval() {
  evalLoading.value = true;
  try {
    await api.post("/rag/evals/run", {
      mode: "DETERMINISTIC",
      failOnThreshold: false,
      datasetKind: "CONTRACT",
      datasetVersion: "contract-v1",
      retrievalMode: retrievalMode.value,
    });
    message.success("RAG 评测已完成");
    await loadHealth();
  } finally {
    evalLoading.value = false;
  }
}

function openGovernance() {
  governanceOpen.value = true;
}

onMounted(async () => {
  await loadHealth();
  await loadEvalCases(true);
});
</script>

<style scoped>
.rag-workbench {
  max-width: 1280px;
}

.head-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.health-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
}

.health-card :deep(.ant-card-body) {
  padding: 16px;
}

.health-label {
  color: #4b5870;
  font-size: 13px;
}

.health-value {
  color: #07111f;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.2;
  margin-top: 8px;
}

.health-note {
  color: #667085;
  font-size: 12px;
  margin-top: 8px;
}

.query-card {
  margin-bottom: 16px;
}

.query-form {
  display: grid;
  gap: 12px;
}

.query-controls {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.evidence-card,
.context-card {
  margin-top: 16px;
}

.context-card pre {
  background: #f8fafc;
  border: 1px solid #e6ebf2;
  border-radius: 8px;
  color: #172033;
  font-size: 12px;
  line-height: 1.7;
  margin: 0;
  max-height: 360px;
  overflow: auto;
  padding: 14px;
  white-space: pre-wrap;
}

@media (max-width: 980px) {
  .health-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .health-grid {
    grid-template-columns: 1fr;
  }

  .query-controls :deep(.ant-select),
  .query-controls :deep(.ant-btn) {
    width: 100% !important;
  }
}
</style>
