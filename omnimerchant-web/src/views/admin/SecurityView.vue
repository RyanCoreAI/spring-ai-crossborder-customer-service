<template>
  <div>
    <a-tabs class="section-surface"
      ><a-tab-pane key="controls" tab="已执行控制"
        ><DataTableShell
          :columns="controlColumns"
          :rows="data?.securityControls || []"
          :loading="loading"
          :error="error"
          row-key="controlKey"
          @retry="load"
          ><template #bodyCell="{ column, record }"
            ><template v-if="column.key === 'status'"
              ><StatusTag
                :status="record.status"
                :label="label(record.status)" /></template
            ><template v-else-if="column.key === 'risk'"
              ><a-tag :color="record.riskLevel === 'HIGH' ? 'red' : 'orange'">{{
                record.riskLevel
              }}</a-tag></template
            ></template
          ></DataTableShell
        ></a-tab-pane
      ><a-tab-pane key="permissions" tab="权限与审批"
        ><DataTableShell
          :columns="roleColumns"
          :rows="data?.rolePolicies || []"
          :loading="loading"
          :error="error"
          row-key="roleKey"
          @retry="load" /><DataTableShell
          :columns="actionColumns"
          :rows="data?.actionPolicies || []"
          :loading="loading"
          :error="error"
          row-key="actionType"
          @retry="load"
          ><template #bodyCell="{ column, record }"
            ><template v-if="column.key === 'write'"
              ><StatusTag
                :status="record.externalWriteEnabled ? 'ERROR' : 'APPROVED'"
                :label="
                  record.externalWriteEnabled ? '允许外部写' : '仅内部审批'
                " /></template></template></DataTableShell></a-tab-pane
      ><a-tab-pane key="boundaries" tab="明确边界"
        ><a-list :data-source="data?.explicitNonGoals || []" bordered
          ><template #renderItem="{ item }"
            ><a-list-item>{{ item }}</a-list-item></template
          ></a-list
        ></a-tab-pane
      ><template #rightExtra
        ><a-button :loading="loading" @click="load">刷新</a-button></template
      ></a-tabs
    >
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from "vue";
import api from "@/api";
import DataTableShell from "@/components/admin/DataTableShell.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import type { ProductionReadiness } from "@/types/contracts";
import { httpErrorMessage } from "@/utils/httpError";
const data = ref<ProductionReadiness | null>(null),
  loading = ref(false),
  error = ref("");
const controlColumns = [
  { title: "控制", dataIndex: "controlLabel" },
  { title: "证据", dataIndex: "evidence", ellipsis: true },
  { title: "风险", key: "risk", width: 100 },
  { title: "状态", key: "status", width: 130 },
];
const roleColumns = [
  { title: "角色", dataIndex: "roleLabel" },
  { title: "权限", dataIndex: "permissionsJson", ellipsis: true },
  { title: "审批限额", dataIndex: "approvalLimit", width: 130 },
  { title: "状态", dataIndex: "status", width: 120 },
];
const actionColumns = [
  { title: "动作", dataIndex: "actionType" },
  { title: "最低审批角色", dataIndex: "minApproverRole" },
  { title: "身份验证", dataIndex: "requiresIdentityVerification", width: 110 },
  { title: "执行边界", key: "write", width: 140 },
];
async function load() {
  loading.value = true;
  error.value = "";
  try {
    data.value = (await api.get("/security/readiness")).data;
  } catch (cause: unknown) {
    error.value = httpErrorMessage(cause, "加载失败");
  } finally {
    loading.value = false;
  }
}
function label(value: string) {
  const labels: Record<string, string> = {
    IMPLEMENTED: "已执行",
    ENFORCED: "已强制",
    PARTIAL: "部分实现",
    ROADMAP: "路线图",
  };
  return labels[value] || value || "—";
}
onMounted(load);
</script>
<style scoped>
.section-surface {
  padding: 0 14px 14px;
}
</style>
