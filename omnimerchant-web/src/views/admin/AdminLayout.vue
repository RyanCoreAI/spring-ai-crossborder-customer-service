<template>
  <a-layout class="admin-shell">
    <a-layout-sider
      v-if="!isMobile"
      class="sidebar"
      :width="232"
      :collapsed-width="72"
      :collapsed="collapsed"
    >
      <button class="brand" type="button" @click="router.push('/admin')">
        <span class="brand-mark">OM</span>
        <span v-if="!collapsed" class="brand-copy">
          <strong>OmniMerchant</strong>
          <small>可信客服运营平台</small>
        </span>
      </button>

      <a-menu
        class="sidebar-menu"
        mode="inline"
        theme="dark"
        :inline-collapsed="collapsed"
        :selected-keys="[activeMenuKey]"
        :open-keys="collapsed ? [] : openKeys"
        @open-change="onOpenChange"
        @click="onMenuClick"
      >
        <template v-for="group in visibleNavigation" :key="group.key">
          <a-menu-item v-if="group.path" :key="group.path">
            <template #icon><component :is="group.icon" /></template>
            {{ group.label }}
          </a-menu-item>
          <a-sub-menu v-else :key="group.key">
            <template #icon><component :is="group.icon" /></template>
            <template #title>{{ group.label }}</template>
            <a-menu-item
              v-for="item in visibleChildren(group.children)"
              :key="item.path"
              >{{ item.label }}</a-menu-item
            >
          </a-sub-menu>
        </template>
      </a-menu>

      <div class="sidebar-footer">
        <a-tooltip :title="collapsed ? '知识库对话测试' : ''" placement="right">
          <a-button
            type="text"
            block
            class="side-action"
            @click="router.push('/chat')"
          >
            <template #icon><MessageOutlined /></template>
            <span v-if="!collapsed">知识库对话</span>
          </a-button>
        </a-tooltip>
        <a-button
          type="text"
          block
          class="side-action"
          @click="collapsed = !collapsed"
        >
          <template #icon
            ><MenuFoldOutlined v-if="!collapsed" /><MenuUnfoldOutlined v-else
          /></template>
          <span v-if="!collapsed">收起导航</span>
        </a-button>
      </div>
    </a-layout-sider>

    <a-drawer
      v-model:open="mobileMenuOpen"
      placement="left"
      :width="280"
      :closable="false"
      class="mobile-nav-drawer"
    >
      <div class="mobile-brand">
        <span class="brand-mark">OM</span>
        <div><strong>OmniMerchant</strong><small>可信客服运营平台</small></div>
      </div>
      <a-menu
        mode="inline"
        :selected-keys="[activeMenuKey]"
        :open-keys="openKeys"
        @click="onMobileMenuClick"
      >
        <template v-for="group in visibleNavigation" :key="group.key">
          <a-menu-item v-if="group.path" :key="group.path">
            <template #icon><component :is="group.icon" /></template
            >{{ group.label }}
          </a-menu-item>
          <a-sub-menu v-else :key="group.key">
            <template #icon><component :is="group.icon" /></template>
            <template #title>{{ group.label }}</template>
            <a-menu-item
              v-for="item in visibleChildren(group.children)"
              :key="item.path"
              >{{ item.label }}</a-menu-item
            >
          </a-sub-menu>
        </template>
      </a-menu>
    </a-drawer>

    <a-layout class="workspace">
      <header class="topbar">
        <div class="topbar-start">
          <a-button
            v-if="isMobile"
            type="text"
            class="icon-button"
            aria-label="打开导航"
            @click="mobileMenuOpen = true"
          >
            <template #icon><MenuOutlined /></template>
          </a-button>
          <div>
            <h1>{{ currentPage.label }}</h1>
            <p>{{ currentPage.description }}</p>
          </div>
        </div>
        <div class="topbar-actions">
          <a-tag v-if="runtime?.demoDataEnabled" color="blue"
            >演示数据 · {{ runtime.seedVersion }}</a-tag
          >
          <a-select
            v-if="tenantOptions.length"
            v-model:value="selectedTenantId"
            class="tenant-select"
            :options="tenantOptions"
            :loading="tenantLoading"
            @change="changeTenant"
          />
          <a-tag v-else-if="tenantLoading" color="processing">加载租户</a-tag>
          <a-dropdown placement="bottomRight">
            <a-button type="text" class="user-trigger">
              <span class="user-avatar">{{ userInitial }}</span>
              <span v-if="!isCompact" class="user-copy"
                ><strong>{{ authStore.email || "当前用户" }}</strong
                ><small>{{ roleLabel }}</small></span
              >
              <DownOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="chat" @click="router.push('/chat')"
                  >知识库对话测试</a-menu-item
                >
                <a-menu-divider />
                <a-menu-item key="logout" danger @click="handleLogout"
                  >退出登录</a-menu-item
                >
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>

      <a-layout-content class="content-area">
        <div class="content-inner">
          <router-view
            v-if="tenantReady"
            :key="`${route.fullPath}:${tenantRenderKey}`"
          />
          <div v-else class="workspace-loading">
            <a-spin /><span>正在加载工作空间</span>
          </div>
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  DownOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
} from "@ant-design/icons-vue";
import api from "@/api";
import { useAuthStore } from "@/stores/auth";
import { getStoredTenantId, setStoredTenantId } from "@/utils/tenant";
import { tenantOptionLabel } from "@/utils/display";
import {
  adminNavigation,
  canViewAdminPage,
  compatibilityAdminPages,
  type AdminNavItem,
} from "@/config/adminNavigation";
import type { RuntimeSummary, TenantSummary } from "@/types/contracts";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const collapsed = ref(false);
const mobileMenuOpen = ref(false);
const viewportWidth = ref(window.innerWidth);
const tenantLoading = ref(false);
const selectedTenantId = ref<number | string | undefined>(
  getStoredTenantId() || undefined,
);
const tenantOptions = ref<Array<{ value: number | string; label: string }>>([]);
const openKeys = ref<string[]>([]);
const tenantRenderKey = ref(0);
const tenantReady = ref(false);
const runtime = ref<RuntimeSummary | null>(null);

const visibleNavigation = computed(() =>
  adminNavigation
    .map((group) => ({
      ...group,
      children: visibleChildren(group.children),
    }))
    .filter((group) => group.path || group.children.length > 0),
);
const allPages = computed(() => [
  ...visibleNavigation.value.flatMap((group) =>
    group.path
      ? [
          {
            path: group.path,
            label: group.label,
            description: group.description || "",
          },
        ]
      : visibleChildren(group.children),
  ),
  ...visibleChildren(compatibilityAdminPages),
]);
const activeMenuKey = computed(() => {
  const exact = allPages.value.find((item) => route.path === item.path);
  if (exact) return exact.path;
  return (
    allPages.value.find(
      (item) =>
        item.path !== "/admin" && route.path.startsWith(`${item.path}/`),
    )?.path || "/admin"
  );
});
const currentPage = computed(
  () =>
    allPages.value.find((item) => item.path === activeMenuKey.value) || {
      path: route.path,
      label: "OmniMerchant",
      description: "",
    },
);
const isMobile = computed(() => viewportWidth.value < 900);
const isCompact = computed(() => viewportWidth.value < 1120);
const userInitial = computed(() =>
  (authStore.email || "O").trim().charAt(0).toUpperCase(),
);
const roleLabel = computed(() => {
  if (authStore.platformAdmin) return "平台管理员";
  const labels: Record<string, string> = {
    TENANT_ADMIN: "租户管理员",
    SUPPORT_SUPERVISOR: "客服主管",
    SUPPORT_AGENT: "客服",
    READ_ONLY_AUDITOR: "只读审计员",
  };
  return (
    authStore.roles.map((role) => labels[role] || role).join("、") || "租户成员"
  );
});

function onMenuClick({ key }: { key: string }) {
  router.push(key);
}
function visibleChildren(items?: AdminNavItem[]) {
  return (items || []).filter((item) =>
    canViewAdminPage(item, authStore.roles, authStore.platformAdmin),
  );
}
function onMobileMenuClick({ key }: { key: string }) {
  mobileMenuOpen.value = false;
  router.push(key);
}
function onOpenChange(keys: string[]) {
  openKeys.value = keys.slice(-2);
}
function updateViewport() {
  viewportWidth.value = window.innerWidth;
}
function changeTenant(value: number | string) {
  const changed = String(getStoredTenantId() ?? "") !== String(value);
  setStoredTenantId(value);
  selectedTenantId.value = value;
  if (changed) tenantRenderKey.value += 1;
}

async function loadTenants() {
  tenantLoading.value = true;
  try {
    if (authStore.platformAdmin) {
      const response = await api.get("/tenants", {
        params: { page: 1, size: 100 },
      });
      tenantOptions.value = (response.data?.records || []).map(
        (tenant: TenantSummary) => ({
          value: tenant.id,
          label: tenantOptionLabel(tenant),
        }),
      );
    } else {
      tenantOptions.value = authStore.tenantIds.map((id) => ({
        value: id,
        label: `租户 #${id}`,
      }));
    }
    const current = selectedTenantId.value;
    const fallback =
      tenantOptions.value.find((option) => option.value === current)?.value ||
      tenantOptions.value[0]?.value;
    if (fallback !== undefined) {
      changeTenant(fallback);
      const response = await api.get("/system/runtime");
      runtime.value = response.data || null;
    }
  } finally {
    tenantLoading.value = false;
    tenantReady.value = true;
  }
}

async function handleLogout() {
  await authStore.logout();
  router.push("/login");
}

onMounted(() => {
  window.addEventListener("resize", updateViewport);
  const group = visibleNavigation.value.find((item) =>
    item.children?.some((child) => route.path.startsWith(child.path)),
  );
  if (group) openKeys.value = [group.key];
  void loadTenants();
});
onBeforeUnmount(() => window.removeEventListener("resize", updateViewport));
</script>

<style scoped>
.admin-shell {
  min-height: 100vh;
  background: var(--omni-page);
}
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  background: #0d1728;
  border-right: 1px solid #16243a;
}
.brand {
  width: 100%;
  height: 76px;
  display: flex;
  align-items: center;
  gap: 11px;
  border: 0;
  background: transparent;
  padding: 0 18px;
  text-align: left;
  cursor: pointer;
}
.brand-mark {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  color: #fff;
  background: #1677ff;
  font-size: 13px;
  font-weight: 800;
}
.brand-copy,
.mobile-brand div,
.user-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.brand-copy strong,
.mobile-brand strong {
  color: #fff;
  font-size: 15px;
  line-height: 20px;
}
.brand-copy small,
.mobile-brand small {
  color: #8492a6;
  font-size: 11px;
  line-height: 18px;
}
.sidebar-menu {
  height: calc(100vh - 154px);
  overflow-y: auto;
  border: 0;
  background: #0d1728;
  padding: 6px 10px;
}
.sidebar-menu :deep(.ant-menu-item),
.sidebar-menu :deep(.ant-menu-submenu-title) {
  height: 38px;
  line-height: 38px;
  border-radius: 6px;
  margin: 2px 0;
}
.sidebar-menu :deep(.ant-menu-sub) {
  background: #0d1728 !important;
}
.sidebar-menu :deep(.ant-menu-item-selected) {
  background: #17365f !important;
}
.sidebar-footer {
  height: 78px;
  padding: 8px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}
.side-action {
  height: 31px;
  color: #aab6c8;
  justify-content: flex-start;
}
.side-action:hover {
  color: #fff !important;
  background: rgba(255, 255, 255, 0.06) !important;
}
.workspace {
  min-width: 0;
  background: var(--omni-page);
}
.topbar {
  height: 76px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: #fff;
  border-bottom: 1px solid var(--omni-border);
  position: sticky;
  top: 0;
  z-index: 20;
}
.topbar-start,
.topbar-actions,
.user-trigger {
  display: flex;
  align-items: center;
}
.topbar-start {
  gap: 12px;
  min-width: 0;
}
.topbar-start h1 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
}
.topbar-start p {
  margin: 2px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 640px;
}
.topbar-actions {
  gap: 10px;
}
.tenant-select {
  width: 210px;
}
.user-trigger {
  height: 44px;
  gap: 9px;
  padding: 4px 7px;
}
.user-avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eaf2ff;
  color: #1769ff;
  font-size: 12px;
  font-weight: 800;
}
.user-copy {
  max-width: 170px;
  align-items: flex-start;
}
.user-copy strong {
  max-width: 100%;
  color: #344054;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-copy small {
  color: #98a2b3;
  font-size: 11px;
}
.content-area {
  min-width: 0;
  padding: 24px 28px 40px;
}
.content-inner {
  width: min(1540px, 100%);
  margin: 0 auto;
}
.mobile-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: -8px 0 18px;
  padding: 0 8px 16px;
  border-bottom: 1px solid var(--omni-border);
}
.mobile-brand strong {
  color: #111827;
}
.icon-button {
  width: 36px;
  height: 36px;
}
.workspace-loading {
  min-height: 280px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #667085;
  font-size: 12px;
}
@media (max-width: 900px) {
  .topbar {
    min-height: 108px;
    height: auto;
    align-content: center;
    flex-wrap: wrap;
    padding: 9px 12px;
  }
  .topbar-start {
    width: 100%;
    min-width: 0;
  }
  .topbar-start > div:last-child {
    min-width: 0;
  }
  .topbar-start h1 {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .topbar-start p {
    display: none;
  }
  .tenant-select {
    width: 132px;
  }
  .topbar-actions {
    width: 100%;
    justify-content: flex-end;
    gap: 4px;
  }
  .content-area {
    padding: 18px 14px 32px;
  }
}
@media (max-width: 560px) {
  .topbar-start h1 {
    font-size: 16px;
  }
  .tenant-select {
    width: 120px;
  }
  .topbar-actions {
    gap: 2px;
  }
  .user-trigger {
    padding: 3px;
  }
}
</style>
