<template>
  <div>
    <PageToolbar>
      <a-input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索标题、分类或内容"
        style="width: 280px"
      />
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><ReloadOutlined /></template>刷新
        </a-button>
      </template>
    </PageToolbar>

    <DataTableShell
      class="macros-table"
      :columns="columns"
      :rows="filteredRows"
      :loading="loading"
      :error="error"
      :pagination="false"
      :scroll="{ x: 960 }"
      empty-text="当前租户尚未配置宏回复"
      @retry="load"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <span class="macro-code">{{ record.macroCode }}</span>
        </template>
        <template v-else-if="column.key === 'approval'">
          <a-tag :color="record.requiresApproval ? 'orange' : 'green'">
            {{ record.requiresApproval ? "需要审批" : "可直接发送" }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'content'">
          <span class="macro-content">{{ record.content || "—" }}</span>
        </template>
      </template>
    </DataTableShell>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import api from "@/api";
import PageToolbar from "@/components/admin/PageToolbar.vue";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import type { SupportMacro } from "@/types/inbox";
import { httpErrorMessage } from "@/utils/httpError";

const loading = ref(false);
const error = ref("");
const keyword = ref("");
const rows = ref<SupportMacro[]>([]);
const columns = [
  { title: "编码", key: "code", dataIndex: "macroCode", width: 150 },
  { title: "标题", dataIndex: "title", width: 180 },
  { title: "分类", dataIndex: "category", width: 120 },
  { title: "渠道", dataIndex: "channel", width: 100 },
  { title: "审批", key: "approval", width: 120 },
  { title: "内容", key: "content" },
];

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  if (!query) return rows.value;
  return rows.value.filter((row) =>
    [row.macroCode, row.title, row.category, row.content]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(query)),
  );
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const response = await api.get("/macros");
    rows.value = response.data || [];
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "宏回复加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.macros-table {
  margin-top: 14px;
}
.macro-code {
  color: #175cd3;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}
.macro-content {
  display: block;
  max-width: 560px;
  overflow: hidden;
  color: #475467;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
