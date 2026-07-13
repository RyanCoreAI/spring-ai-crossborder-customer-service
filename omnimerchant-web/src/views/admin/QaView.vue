<template>
  <div>
    <MetricStrip :items="metrics" /><PageToolbar class="section-row"
      ><a-select
        v-model:value="status"
        allow-clear
        placeholder="全部状态"
        style="width: 160px"
        @change="load"
        ><a-select-option value="PENDING">待复核</a-select-option
        ><a-select-option value="REVIEWED">已复核</a-select-option></a-select
      ><template #actions
        ><a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        ></template
      ></PageToolbar
    ><DataTableShell
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      :scroll="{ x: 980 }"
      @retry="load"
      @change="change"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'source'"
          ><a-button type="link" class="link" @click="open(record)">{{
            record.ticketNo || record.conversationUuid
          }}</a-button
          ><small>{{ record.sourceType }}</small></template
        ><template v-else-if="column.key === 'score'"
          >{{ record.autoScore ?? "—" }} /
          {{ record.reviewerScore ?? "—" }}</template
        ><template v-else-if="column.key === 'status'"
          ><StatusTag
            :status="record.status"
            :label="
              record.status === 'REVIEWED' ? '已复核' : '待复核'
            " /></template
        ><template v-else-if="column.key === 'reviewer'">{{
          record.reviewerName || "—"
        }}</template
        ><template v-else-if="column.key === 'created'">{{
          date(record.createdAt)
        }}</template></template
      ></DataTableShell
    ><DetailDrawer :open="drawer" title="质检复核" @close="drawer = false"
      ><a-descriptions v-if="current" :column="1" bordered size="small"
        ><a-descriptions-item label="自动评分">{{
          current.autoScore ?? "—"
        }}</a-descriptions-item
        ><a-descriptions-item label="问题标记">{{
          current.reviewFlags || "—"
        }}</a-descriptions-item
        ><a-descriptions-item label="评审发现">{{
          current.findings || "—"
        }}</a-descriptions-item
        ><a-descriptions-item label="改进项">{{
          current.actionItems || "—"
        }}</a-descriptions-item></a-descriptions
      ><a-form
        v-if="current?.status !== 'REVIEWED'"
        layout="vertical"
        class="review-form"
        ><a-form-item label="人工评分"
          ><a-input-number
            v-model:value="review.score"
            :min="0"
            :max="100"
            style="width: 100%" /></a-form-item
        ><a-form-item label="复核结论"
          ><a-textarea v-model:value="review.findings" :rows="3" /></a-form-item
        ><a-form-item label="改进项"
          ><a-textarea
            v-model:value="review.actionItems"
            :rows="2" /></a-form-item
        ><a-button type="primary" block :loading="saving" @click="submit"
          >提交复核</a-button
        ></a-form
      ></DetailDrawer
    >
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import MetricStrip from "@/components/admin/MetricStrip.vue";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import DetailDrawer from "@/components/admin/DetailDrawer.vue";
import type { QaReviewItem, QaSummary, TablePageChange } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const rows = ref<QaReviewItem[]>([]),
  summary = ref<QaSummary | null>(null),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  status = ref<string>(),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  drawer = ref(false),
  current = ref<QaReviewItem | null>(null);
const review = reactive({ score: 85, findings: "", actionItems: "" });
const metrics = computed(() => [
  {
    key: "total",
    label: "质检任务",
    value: summary.value?.total,
    note: "当前租户",
  },
  {
    key: "pending",
    label: "待人工复核",
    value: summary.value?.pending,
    note: "需主管确认",
  },
  {
    key: "auto",
    label: "平均自动评分",
    value: summary.value?.averageAutoScore,
    note: "关闭会话快照",
  },
  {
    key: "human",
    label: "平均人工评分",
    value: summary.value?.averageReviewerScore,
    note: "已复核任务",
  },
]);
const columns = [
  { title: "来源", key: "source", width: 200 },
  { title: "评分 自动/人工", key: "score", width: 135 },
  { title: "问题标记", dataIndex: "reviewFlags", ellipsis: true },
  { title: "状态", key: "status", width: 100 },
  { title: "复核人", key: "reviewer", width: 110 },
  { title: "创建时间", key: "created", width: 170 },
];
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
      api.get("/qa/queue", {
        params: { status: status.value, page: page.value, size: size.value },
      }),
      api.get("/qa/summary"),
    ]);
    rows.value = a.data?.records || [];
    total.value = a.data?.total || 0;
    summary.value = b.data;
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
function change(p: TablePageChange) {
  page.value = p.current || 1;
  size.value = p.pageSize || 20;
  void load();
}
function open(v: QaReviewItem) {
  current.value = v;
  review.score = v.reviewerScore || v.autoScore || 85;
  review.findings = v.findings || "";
  review.actionItems = v.actionItems || "";
  drawer.value = true;
}
async function submit() {
  saving.value = true;
  try {
    await api.post(`/qa/${current.value.id}/review`, review);
    message.success("复核已提交");
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
function date(v: string) {
  return v ? new Date(v).toLocaleString("zh-CN") : "—";
}
onMounted(load);
</script>
<style scoped>
.link {
  height: auto;
  padding: 0;
  font-weight: 650;
}
.link + small {
  display: block;
  color: #98a2b3;
}
.review-form {
  margin-top: 18px;
}
</style>
