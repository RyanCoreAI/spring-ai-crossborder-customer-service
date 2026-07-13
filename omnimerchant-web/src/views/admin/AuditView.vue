<template>
  <div>
    <PageToolbar
      ><a-select
        v-model:value="risk"
        allow-clear
        placeholder="全部风险"
        style="width: 150px"
        ><a-select-option value="HIGH">高风险</a-select-option
        ><a-select-option value="MEDIUM">中风险</a-select-option
        ><a-select-option value="LOW">低风险</a-select-option></a-select
      ><a-input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索操作或资源"
        style="width: 220px"
      /><template #actions
        ><a-button :loading="loading" @click="load"
          ><template #icon><ReloadOutlined /></template>刷新</a-button
        ></template
      ></PageToolbar
    ><DataTableShell
      :columns="columns"
      :rows="filtered"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      :scroll="{ x: 1000 }"
      @retry="load"
      @change="change"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'risk'"
          ><a-tag
            :color="
              record.riskLevel === 'HIGH'
                ? 'red'
                : record.riskLevel === 'MEDIUM'
                  ? 'orange'
                  : 'default'
            "
            >{{ riskLabel(record.riskLevel) }}</a-tag
          ></template
        ><template v-else-if="column.key === 'actor'"
          ><strong>{{ record.actorName || "系统" }}</strong
          ><small>{{ record.actorRole || "—" }}</small></template
        ><template v-else-if="column.key === 'resource'"
          ><strong>{{ record.resourceType }}</strong
          ><small>{{ record.resourceId || "—" }}</small></template
        ><template v-else-if="column.key === 'time'">{{
          date(record.createdAt)
        }}</template></template
      ></DataTableShell
    >
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import type { AuditEvent, TablePageChange } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const rows = ref<AuditEvent[]>([]),
  loading = ref(false),
  error = ref(""),
  risk = ref<string>(),
  keyword = ref(""),
  page = ref(1),
  size = ref(20),
  total = ref(0);
const columns = [
  { title: "风险", key: "risk", width: 90 },
  { title: "操作人", key: "actor", width: 150 },
  { title: "操作", dataIndex: "action", width: 190 },
  { title: "资源", key: "resource", width: 170 },
  { title: "摘要", dataIndex: "summary", ellipsis: true },
  { title: "时间", key: "time", width: 180 },
];
const filtered = computed(() =>
  rows.value.filter(
    (x) =>
      (!risk.value || x.riskLevel === risk.value) &&
      (!keyword.value ||
        `${x.action} ${x.resourceType} ${x.summary}`
          .toLowerCase()
          .includes(keyword.value.toLowerCase())),
  ),
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
    const r = await api.get("/audit/events", {
      params: { page: page.value, size: size.value },
    });
    rows.value = r.data?.records || [];
    total.value = r.data?.total || 0;
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
function change(v: TablePageChange) {
  page.value = v.current || 1;
  size.value = v.pageSize || 20;
  void load();
}
function riskLabel(v: string) {
  const labels: Record<string, string> = { HIGH: "高", MEDIUM: "中", LOW: "低" };
  return labels[v] || v || "—";
}
function date(v: string) {
  return v ? new Date(v).toLocaleString("zh-CN") : "—";
}
onMounted(load);
</script>
<style scoped>
small {
  display: block;
  margin-top: 3px;
  color: #98a2b3;
}
</style>
