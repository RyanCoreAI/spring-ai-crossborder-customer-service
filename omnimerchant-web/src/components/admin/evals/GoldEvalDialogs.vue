<template>
  <a-modal v-model:open="createOpen" title="新建 GOLD 用例" :confirm-loading="saving" @ok="createCase">
    <a-form layout="vertical">
      <a-form-item label="草稿数据集" required><a-select v-model:value="caseForm.datasetVersion" placeholder="选择后端已创建的数据集">
        <a-select-option v-for="dataset in draftDatasets" :key="dataset.id" :value="dataset.version">{{ dataset.datasetKey }} / {{ dataset.version }}</a-select-option>
      </a-select></a-form-item>
      <a-row :gutter="12"><a-col :span="12"><a-form-item label="用例编号"><a-input v-model:value="caseForm.caseCode" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="意图"><a-input v-model:value="caseForm.intent" placeholder="POLICY_QA" /></a-form-item></a-col></a-row>
      <a-form-item label="用户问题"><a-textarea v-model:value="caseForm.userMessage" :rows="3" /></a-form-item>
      <a-form-item label="期望工具"><a-select v-model:value="caseForm.expectedTools" mode="tags" placeholder="输入真实工具名" /></a-form-item>
      <a-form-item label="期望结果"><a-textarea v-model:value="caseForm.expectedOutcome" :rows="3" /></a-form-item>
      <a-form-item label="攻击类型（可选）"><a-input v-model:value="caseForm.attackType" /></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="datasetOpen" title="新建 GOLD 数据集" :confirm-loading="saving" @ok="createDataset">
    <a-form layout="vertical">
      <a-form-item label="数据集标识" required><a-input v-model:value="datasetForm.datasetKey" placeholder="例如 support-gold" /></a-form-item>
      <a-form-item label="版本" required><a-input v-model:value="datasetForm.version" placeholder="例如 gold-v1" /></a-form-item>
      <a-form-item label="语言分布"><a-input v-model:value="datasetForm.languageDistribution" placeholder="例如 zh:50,en:30,es:10,ja:10" /></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="reviewOpen" :title="reviewDecision === 'APPROVED' ? '通过 GOLD 用例' : '拒绝 GOLD 用例'" :confirm-loading="saving" @ok="reviewCase">
    <a-form layout="vertical"><a-form-item label="审核备注"><a-textarea v-model:value="reviewNote" :rows="4" /></a-form-item></a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { message } from "ant-design-vue";
import api from "@/api";
import type { GoldDataset, GoldEvalCase } from "@/types/evals";

const props = defineProps<{ draftDatasets: GoldDataset[] }>();
const emit = defineEmits<{ saved: [] }>();
const createOpen = ref(false), datasetOpen = ref(false), reviewOpen = ref(false), saving = ref(false);
const reviewRow = ref<GoldEvalCase | null>(null), reviewDecision = ref("APPROVED"), reviewNote = ref("");
const caseForm = reactive({ datasetVersion: "", caseCode: "", intent: "", userMessage: "", expectedTools: [] as string[], expectedOutcome: "", attackType: "" });
const datasetForm = reactive({ datasetKey: "support-gold", version: "gold-v1", languageDistribution: "zh:50,en:30,es:10,ja:10" });

function openCreate() { caseForm.datasetVersion = props.draftDatasets[0]?.version || ""; createOpen.value = true; }
function openDataset() { datasetOpen.value = true; }
function openReview(row: GoldEvalCase, decision: string) { reviewRow.value = row; reviewDecision.value = decision; reviewNote.value = ""; reviewOpen.value = true; }

async function createCase() {
  if (!caseForm.datasetVersion || !caseForm.caseCode.trim() || !caseForm.userMessage.trim()) return void message.warning("请选择数据集并填写用例编号和用户问题");
  await save(async () => { await api.post("/evals/gold/cases", { ...caseForm, attackType: caseForm.attackType || null }); createOpen.value = false; Object.assign(caseForm, { datasetVersion: props.draftDatasets[0]?.version || "", caseCode: "", intent: "", userMessage: "", expectedTools: [], expectedOutcome: "", attackType: "" }); message.success("GOLD 草稿已创建，审核前不会进入评测"); });
}

async function createDataset() {
  if (!datasetForm.datasetKey.trim() || !datasetForm.version.trim()) return void message.warning("请填写数据集标识和版本");
  await save(async () => { await api.post("/rag/datasets", { ...datasetForm, datasetKind: "GOLD" }); datasetOpen.value = false; message.success("GOLD 草稿数据集已创建"); });
}

async function reviewCase() {
  if (!reviewRow.value) return;
  await save(async () => { await api.post(`/evals/gold/cases/${reviewRow.value?.id}/review`, { decision: reviewDecision.value, note: reviewNote.value }); reviewOpen.value = false; message.success(reviewDecision.value === "APPROVED" ? "用例已通过审核并启用" : "用例已拒绝"); });
}

async function save(action: () => Promise<void>) { saving.value = true; try { await action(); emit("saved"); } finally { saving.value = false; } }

defineExpose({ openCreate, openDataset, openReview });
</script>
