<template>
  <div>
    <div class="page-head">
      <a-space
        ><a-button :loading="loading" @click="load">刷新</a-button
        ><a-button type="primary" @click="openCreate"
          >新建用户</a-button
        ></a-space
      >
    </div>
    <a-card>
      <a-table
        :columns="columns"
        :data-source="users"
        :loading="loading"
        row-key="id"
        :pagination="false"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <a-tag
            v-if="column.key === 'status'"
            :color="record.status === 'ACTIVE' ? 'green' : 'default'"
            >{{ userStatusLabel(record.status) }}</a-tag
          >
          <a-tag
            v-else-if="column.key === 'platformAdmin'"
            :color="record.platformAdmin ? 'blue' : 'default'"
            >{{ record.platformAdmin ? "平台管理员" : "租户用户" }}</a-tag
          >
          <span v-else-if="column.key === 'memberships'">{{
            membershipSummary(record.memberships)
          }}</span>
          <span v-else-if="column.key === 'lastLoginAt'">{{
            formatTime(record.lastLoginAt)
          }}</span>
          <a-space v-else-if="column.key === 'actions'">
            <a-button
              v-if="!record.platformAdmin"
              size="small"
              @click="openMembership(record)"
              >租户权限</a-button
            >
            <a-button
              v-if="record.status === 'ACTIVE'"
              size="small"
              danger
              @click="changeStatus(record, 'DISABLED')"
              >停用</a-button
            >
            <a-button
              v-else
              size="small"
              type="primary"
              @click="changeStatus(record, 'ACTIVE')"
              >启用</a-button
            >
          </a-space>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="createOpen"
      title="新建后台用户"
      :confirm-loading="saving"
      @ok="createUser"
    >
      <a-form layout="vertical">
        <a-form-item label="邮箱" required
          ><a-input v-model:value="createForm.email"
        /></a-form-item>
        <a-form-item label="显示名称"
          ><a-input v-model:value="createForm.displayName"
        /></a-form-item>
        <a-form-item
          label="初始密码"
          required
          extra="至少 12 位；后端只保存 BCrypt 哈希。"
          ><a-input-password v-model:value="createForm.password"
        /></a-form-item>
        <a-form-item
          ><a-checkbox v-model:checked="createForm.platformAdmin"
            >平台管理员</a-checkbox
          ></a-form-item
        >
        <template v-if="!createForm.platformAdmin">
          <a-form-item label="租户" required
            ><a-select
              v-model:value="createForm.tenantId"
              :options="tenantOptions"
          /></a-form-item>
          <a-form-item label="角色" required
            ><a-select
              v-model:value="createForm.roleKey"
              :options="roleOptions"
          /></a-form-item>
        </template>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="membershipOpen"
      title="编辑租户权限"
      :confirm-loading="saving"
      @ok="saveMemberships"
    >
      <a-alert
        class="modal-alert"
        type="info"
        show-icon
        message="保存后会递增 token version，并吊销该用户现有 refresh token。"
      />
      <div
        v-for="(item, index) in membershipForm"
        :key="index"
        class="membership-row"
      >
        <a-select
          v-model:value="item.tenantId"
          :options="tenantOptions"
          placeholder="租户"
        />
        <a-select
          v-model:value="item.roleKey"
          :options="roleOptions"
          placeholder="角色"
        />
        <a-button
          danger
          aria-label="删除租户权限"
          @click="membershipForm.splice(index, 1)"
          ><DeleteOutlined
        /></a-button>
      </div>
      <a-button
        type="dashed"
        block
        @click="
          membershipForm.push({ tenantId: undefined, roleKey: undefined })
        "
        >添加租户权限</a-button
      >
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { DeleteOutlined } from "@ant-design/icons-vue";
import { message } from "ant-design-vue";
import api from "@/api";
import { tenantOptionLabel } from "@/utils/display";
import type { AdminUser, SupportRole, TenantSummary, UserMembership } from "@/types/contracts";

const loading = ref(false);
const saving = ref(false);
const users = ref<AdminUser[]>([]);
const createOpen = ref(false);
const membershipOpen = ref(false);
const selectedUser = ref<AdminUser | null>(null);
const membershipForm = ref<Array<Partial<UserMembership>>>([]);
const createForm = reactive({
  email: "",
  displayName: "",
  password: "",
  platformAdmin: false,
  tenantId: undefined as number | undefined,
  roleKey: "SUPPORT_AGENT" as string | undefined,
});
const roleOptions = ref<Array<{ value: string; label: string }>>([]);
const tenantOptions = ref<Array<{ value: number; label: string }>>([]);
const roleLabels: Record<string, string> = {
  TENANT_ADMIN: "租户管理员",
  SUPPORT_SUPERVISOR: "客服主管",
  SUPPORT_AGENT: "客服",
  READ_ONLY_AUDITOR: "只读审计员",
};

const columns = [
  { title: "用户", dataIndex: "email", width: 220 },
  { title: "名称", dataIndex: "displayName", width: 150 },
  {
    title: "类型",
    dataIndex: "platformAdmin",
    key: "platformAdmin",
    width: 120,
  },
  { title: "状态", dataIndex: "status", key: "status", width: 90 },
  {
    title: "租户权限",
    dataIndex: "memberships",
    key: "memberships",
    ellipsis: true,
  },
  {
    title: "最近登录",
    dataIndex: "lastLoginAt",
    key: "lastLoginAt",
    width: 170,
  },
  { title: "操作", key: "actions", width: 190 },
];

async function load() {
  loading.value = true;
  try {
    const [userRes, roleRes, tenantRes] = await Promise.all([
      api.get("/admin/users"),
      api.get("/admin/roles"),
      api.get("/tenants", { params: { page: 1, size: 100 } }),
    ]);
    users.value = userRes.data || [];
    roleOptions.value = (roleRes.data || [])
      .filter((item: SupportRole) => item.roleKey !== "PLATFORM_ADMIN")
      .map((item: SupportRole) => ({
        value: item.roleKey,
        label: roleLabels[item.roleKey] || item.roleKey,
      }));
    tenantOptions.value = (tenantRes.data?.records || []).map((item: TenantSummary) => ({
      value: item.id,
      label: tenantOptionLabel(item),
    }));
  } finally {
    loading.value = false;
  }
}
function openCreate() {
  Object.assign(createForm, {
    email: "",
    displayName: "",
    password: "",
    platformAdmin: false,
    tenantId: tenantOptions.value[0]?.value,
    roleKey: roleOptions.value[0]?.value || "SUPPORT_AGENT",
  });
  createOpen.value = true;
}
async function createUser() {
  if (
    !createForm.email.trim() ||
    createForm.password.length < 12 ||
    (!createForm.platformAdmin && (!createForm.tenantId || !createForm.roleKey))
  ) {
    message.warning("请填写有效邮箱、至少 12 位密码和租户角色");
    return;
  }
  saving.value = true;
  try {
    await api.post("/admin/users", {
      email: createForm.email,
      displayName: createForm.displayName,
      password: createForm.password,
      platformAdmin: createForm.platformAdmin,
      memberships: createForm.platformAdmin
        ? []
        : [{ tenantId: createForm.tenantId, roleKey: createForm.roleKey }],
    });
    createOpen.value = false;
    message.success("用户已创建");
    await load();
  } finally {
    saving.value = false;
  }
}
function openMembership(user: AdminUser) {
  selectedUser.value = user;
  membershipForm.value = (user.memberships || []).map((item: UserMembership) => ({
    tenantId: item.tenantId,
    roleKey: item.roleKey,
  }));
  membershipOpen.value = true;
}
async function saveMemberships() {
  if (
    !selectedUser.value ||
    membershipForm.value.some((item) => !item.tenantId || !item.roleKey)
  ) {
    message.warning("每条权限都必须选择租户和角色");
    return;
  }
  saving.value = true;
  try {
    await api.put(`/admin/users/${selectedUser.value.id}/memberships`, {
      memberships: membershipForm.value,
    });
    membershipOpen.value = false;
    message.success("租户权限已更新，旧刷新令牌已吊销");
    await load();
  } finally {
    saving.value = false;
  }
}
async function changeStatus(user: AdminUser, status: string) {
  await api.put(`/admin/users/${user.id}/status`, { status });
  message.success(status === "ACTIVE" ? "用户已启用" : "用户已停用");
  await load();
}
function membershipSummary(items: UserMembership[]) {
  return items?.length
    ? items
        .map(
          (item) =>
            `#${item.tenantId} ${roleLabels[item.roleKey] || item.roleKey}`,
        )
        .join("；")
    : "—";
}
function userStatusLabel(status: string) {
  return status === "ACTIVE"
    ? "启用"
    : status === "LOCKED"
      ? "已锁定"
      : "已停用";
}
function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString("zh-CN") : "—";
}
onMounted(load);
</script>

<style scoped>
.modal-alert {
  margin-bottom: 16px;
}
.membership-row {
  display: grid;
  grid-template-columns: 1fr 1fr 40px;
  gap: 8px;
  margin-bottom: 10px;
}
@media (max-width: 560px) {
  .membership-row {
    grid-template-columns: 1fr 1fr;
  }
  .membership-row button {
    grid-column: 2;
    justify-self: end;
  }
}
</style>
