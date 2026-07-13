<template>
  <a-drawer v-model:open="model" title="RAG 质量治理" width="min(860px, 94vw)" @after-open-change="onOpen">
    <a-tabs>
      <a-tab-pane key="datasets" tab="数据集">
        <a-alert type="info" show-icon message="CONTRACT 是自动生成的合同测试；只有逐条人工审核的数据才能发布为 GOLD。" />
        <a-table class="drawer-table" :columns="datasetColumns" :data-source="datasets" :loading="loading" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'kind'" :color="record.datasetKind === 'GOLD' ? 'gold' : 'blue'">{{ datasetKindLabel(record.datasetKind) }}</a-tag>
            <a-tag v-else-if="column.key === 'status'" :color="record.status === 'PUBLISHED' ? 'green' : 'default'">{{ releaseStatusLabel(record.status) }}</a-tag>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="indexes" tab="索引版本">
        <div class="drawer-actions">
          <a-button type="primary" @click="indexModalOpen = true">新建索引版本</a-button>
          <a-button danger :loading="actionLoading" @click="rollbackIndex">回滚活动索引</a-button>
        </div>
        <a-table class="drawer-table" :columns="indexColumns" :data-source="indexReleases" :loading="loading" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <a-tag v-if="column.key === 'status'" :color="record.status === 'ACTIVE' ? 'green' : record.status === 'ROLLED_BACK' ? 'red' : 'default'">{{ releaseStatusLabel(record.status) }}</a-tag>
            <a-button v-else-if="column.key === 'actions' && record.status !== 'ACTIVE'" type="link" size="small" :loading="actionLoading" @click="activateIndex(record.indexVersion)">激活</a-button>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="experiments" tab="检索实验">
        <a-table class="drawer-table" :columns="experimentColumns" :data-source="experiments" :loading="loading" row-key="id" size="small" />
      </a-tab-pane>
      <a-tab-pane key="feedback" tab="质量反馈">
        <div class="drawer-actions"><a-button type="primary" :disabled="!question.trim()" @click="feedbackModalOpen = true">反馈当前问题</a-button></div>
        <a-table class="drawer-table" :columns="feedbackColumns" :data-source="feedback" :loading="loading" row-key="id" size="small" />
      </a-tab-pane>
    </a-tabs>
  </a-drawer>

  <a-modal v-model:open="indexModalOpen" title="新建索引版本" ok-text="创建" cancel-text="取消" :confirm-loading="actionLoading" @ok="createIndexRelease">
    <a-form layout="vertical">
      <a-form-item label="版本号"><a-input v-model:value="indexForm.indexVersion" placeholder="例如 index-2026-07-10" /></a-form-item>
      <a-form-item label="Embedding 模型"><a-input v-model:value="indexForm.embeddingModel" /></a-form-item>
      <a-form-item label="重排模式"><a-input v-model:value="indexForm.rerankerMode" /></a-form-item>
      <a-form-item label="查询规划版本"><a-input v-model:value="indexForm.queryPlannerVersion" /></a-form-item>
      <a-form-item label="发布说明"><a-textarea v-model:value="indexForm.releaseNote" :rows="2" /></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="feedbackModalOpen" title="提交 RAG 质量反馈" ok-text="提交" cancel-text="取消" :confirm-loading="actionLoading" @ok="submitFeedback">
    <a-form layout="vertical">
      <a-form-item label="问题"><a-textarea :value="question" :rows="2" disabled /></a-form-item>
      <a-form-item label="问题类型"><a-select v-model:value="feedbackForm.feedbackType">
        <a-select-option value="CITATION_ERROR">引用错误</a-select-option><a-select-option value="RETRIEVAL_ERROR">召回错误</a-select-option>
        <a-select-option value="POLICY_STALE">政策过期</a-select-option><a-select-option value="ANSWER_ERROR">答案错误</a-select-option>
      </a-select></a-form-item>
      <a-form-item label="说明"><a-textarea v-model:value="feedbackForm.comment" :rows="3" :maxlength="1000" show-count /></a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { message } from "ant-design-vue";
import api from "@/api";
import type { RagCandidate, RagDataset, RagExperiment, RagFeedback, RagIndexRelease } from "@/types/rag";

const props = defineProps<{ question: string; candidate?: RagCandidate }>();
const model = defineModel<boolean>({ default: false });
const loading = ref(false), actionLoading = ref(false), indexModalOpen = ref(false), feedbackModalOpen = ref(false);
const datasets = ref<RagDataset[]>([]), indexReleases = ref<RagIndexRelease[]>([]), experiments = ref<RagExperiment[]>([]), feedback = ref<RagFeedback[]>([]);
const indexForm = reactive({ indexVersion: "", embeddingModel: "text-embedding-3-small", rerankerMode: "bge-reranker-v2", queryPlannerVersion: "deterministic-v1", releaseNote: "" });
const feedbackForm = reactive({ feedbackType: "RETRIEVAL_ERROR", comment: "" });
const datasetColumns = [{ title: "数据集", dataIndex: "datasetKey", ellipsis: true }, { title: "类型", dataIndex: "datasetKind", key: "kind", width: 110 }, { title: "版本", dataIndex: "version", width: 130 }, { title: "状态", dataIndex: "status", key: "status", width: 100 }, { title: "用例数", dataIndex: "caseCount", width: 90 }];
const indexColumns = [{ title: "索引版本", dataIndex: "indexVersion", width: 150 }, { title: "状态", dataIndex: "status", key: "status", width: 110 }, { title: "Embedding", dataIndex: "embeddingModel", ellipsis: true }, { title: "重排", dataIndex: "rerankerMode", ellipsis: true }, { title: "规划版本", dataIndex: "queryPlannerVersion", ellipsis: true }, { title: "操作", key: "actions", width: 80 }];
const experimentColumns = [{ title: "检索模式", dataIndex: "retrievalMode", width: 150 }, { title: "数据集", dataIndex: "datasetVersion", width: 120 }, { title: "索引", dataIndex: "indexVersion", width: 120 }, { title: "用例", dataIndex: "caseCount", width: 70 }, { title: "召回率", dataIndex: "contextRecall", width: 90 }, { title: "MRR", dataIndex: "mrr", width: 80 }, { title: "nDCG", dataIndex: "ndcgAtK", width: 80 }, { title: "P95", dataIndex: "p95RetrievalLatencyMs", width: 90 }, { title: "状态", dataIndex: "status", width: 90 }];
const feedbackColumns = [{ title: "类型", dataIndex: "feedbackType", width: 140 }, { title: "问题哈希", dataIndex: "questionHash", ellipsis: true }, { title: "脱敏说明", dataIndex: "commentRedacted", ellipsis: true }, { title: "状态", dataIndex: "status", width: 90 }, { title: "提交时间", dataIndex: "createdAt", width: 170 }];

async function onOpen(open: boolean) { if (open) await load(); }
async function load() {
  loading.value = true;
  try {
    const [dataset, indexes, runs, feedbackRows] = await Promise.all([api.get("/rag/datasets"), api.get("/rag/index/releases"), api.get("/rag/experiments"), api.get("/rag/feedback")]);
    datasets.value = dataset.data || []; indexReleases.value = indexes.data || []; experiments.value = runs.data || []; feedback.value = feedbackRows.data || [];
  } finally { loading.value = false; }
}
async function createIndexRelease() {
  if (!indexForm.indexVersion.trim()) return void message.warning("请输入索引版本号");
  await withAction(async () => { await api.post("/rag/index/releases", { ...indexForm }); indexModalOpen.value = false; message.success("索引版本已创建；完成索引后才能激活"); await load(); });
}
async function activateIndex(version: string) { await withAction(async () => { await api.post(`/rag/index/releases/${encodeURIComponent(version)}/activate`); message.success("活动索引已切换"); await load(); }); }
async function rollbackIndex() { await withAction(async () => { await api.post("/rag/index/releases/rollback"); message.success("已回滚到上一索引版本"); await load(); }); }
async function submitFeedback() {
  await withAction(async () => { await api.post("/rag/feedback", { question: props.question, feedbackType: feedbackForm.feedbackType, docUuid: props.candidate?.docUuid, chunkUuid: props.candidate?.chunkUuid, comment: feedbackForm.comment }); feedbackModalOpen.value = false; feedbackForm.comment = ""; message.success("反馈已进入人工处理队列"); await load(); });
}
async function withAction(action: () => Promise<void>) { actionLoading.value = true; try { await action(); } finally { actionLoading.value = false; } }
function datasetKindLabel(value?: string) { return value === "GOLD" ? "人工金标" : value === "CONTRACT" ? "合同测试" : value || "—"; }
function releaseStatusLabel(value?: string) { return ({ DRAFT: "草稿", PUBLISHED: "已发布", ACTIVE: "活动", SUPERSEDED: "已替代", ROLLED_BACK: "已回滚" } as Record<string, string>)[value || ""] || value || "—"; }
</script>

<style scoped>
.drawer-actions { display: flex; gap: 8px; margin-bottom: 12px; }
.drawer-table { margin-top: 12px; }
</style>
