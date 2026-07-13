<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from "ant-design-vue/es/locale/zh_CN";

const theme = {
  token: {
    colorPrimary: "#1677ff",
    colorSuccess: "#067647",
    colorWarning: "#b54708",
    colorError: "#c01b2e",
    colorText: "#182230",
    colorTextSecondary: "#667085",
    colorBorder: "#e4e9f0",
    colorBgLayout: "#f5f7fa",
    borderRadius: 6,
    fontSize: 14,
    controlHeight: 34,
    boxShadow: "none",
  },
  components: {
    Card: { headerHeight: 50, bodyPadding: 18 },
    Table: {
      headerBg: "#f8fafc",
      headerColor: "#667085",
      cellPaddingBlock: 11,
    },
    Menu: {
      darkItemBg: "#0d1728",
      darkSubMenuItemBg: "#0d1728",
      darkItemSelectedBg: "#19345f",
    },
  },
};
</script>
