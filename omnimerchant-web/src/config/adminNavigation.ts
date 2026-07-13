import { markRaw, type Component } from "vue";
import {
  AppstoreOutlined,
  ExperimentOutlined,
  MessageOutlined,
  SettingOutlined,
  ShopOutlined,
} from "@ant-design/icons-vue";

export type AdminNavItem = {
  path: string;
  label: string;
  description: string;
  platformAdminOnly?: boolean;
};

export type AdminNavGroup = {
  key: string;
  label: string;
  icon: Component;
  path?: string;
  description?: string;
  children?: AdminNavItem[];
};

export const adminNavigation: AdminNavGroup[] = [
  {
    key: "workspace",
    label: "工作台",
    icon: markRaw(AppstoreOutlined),
    children: [
      { path: "/admin", label: "数据概览", description: "查看最重要的客服、业务和系统信号。" },
      { path: "/admin/inbox", label: "统一收件箱", description: "处理分配、接管、回复与待审批会话。" },
      { path: "/admin/tickets", label: "工单", description: "跟进人工工单、负责人和解决状态。" },
      { path: "/admin/actions", label: "动作审批", description: "审核退货、退款、补发和地址变更请求。" },
      { path: "/admin/conversations", label: "会话记录", description: "查看租户内历史对话与消息。" },
    ],
  },
  {
    key: "commerce",
    label: "客户与交易",
    icon: markRaw(ShopOutlined),
    children: [
      { path: "/admin/customers", label: "客户", description: "查看客户资料、订单与服务历史。" },
      { path: "/admin/orders", label: "订单", description: "查看订单、履约、物流和关联会话。" },
      { path: "/admin/products", label: "商品", description: "查看商品、库存与向量索引状态。" },
    ],
  },
  {
    key: "ai-quality",
    label: "AI 与知识",
    icon: markRaw(ExperimentOutlined),
    children: [
      { path: "/admin/observability", label: "可信控制台", description: "聚合质量、成本、延迟和失败归因。" },
      { path: "/admin/traces", label: "轨迹回放", description: "回放路由、检索、工具与最终响应。" },
      { path: "/admin/evals", label: "智能体评测", description: "管理基准用例、评测运行和失败证据。" },
      { path: "/admin/agent-workflow", label: "智能体工作流", description: "检查 supervisor 路由和 specialist 权限边界。" },
      { path: "/admin/knowledge", label: "知识库", description: "管理政策文档、审核状态和索引任务。" },
      { path: "/admin/rag-workbench", label: "RAG 工作台", description: "解释查询改写、召回、重排和证据充分度。" },
      { path: "/admin/rag-safety", label: "知识安全", description: "审核投毒风险、隔离文档和引用健康。" },
      { path: "/admin/tool-calls", label: "工具调用", description: "检查工具参数摘要、耗时与失败。" },
    ],
  },
  {
    key: "integrations",
    label: "渠道与集成",
    icon: markRaw(MessageOutlined),
    children: [
      { path: "/admin/channels", label: "渠道状态", description: "区分真实连接、Fixture 与等待凭据。" },
      { path: "/admin/integrations", label: "平台集成", description: "管理 Shopify、国内平台同步和 Webhook。" },
      { path: "/admin/multilingual", label: "多语言证据", description: "检查语言检测、翻译、耗时和降级原因。" },
    ],
  },
  {
    key: "operations",
    label: "运营与管理",
    icon: markRaw(SettingOutlined),
    children: [
      { path: "/admin/operations", label: "运营指标", description: "按真实会话和工单计算解决率、接管率与成本。" },
      { path: "/admin/sla", label: "SLA", description: "查看超时风险、目标和处理窗口。" },
      { path: "/admin/qa", label: "客服质检", description: "复核关闭会话和工单的质量任务。" },
      { path: "/admin/sre", label: "生产健康", description: "查看 SLO 快照、告警生命周期和灰度边界。" },
      { path: "/admin/audit", label: "审计日志", description: "追踪审批、配置、知识和工具操作。" },
      { path: "/admin/security", label: "安全边界", description: "核对 RBAC、数据保留与明确非目标。" },
      { path: "/admin/usage", label: "用量计费", description: "查看租户真实 Token 用量与预算。" },
      { path: "/admin/tenants", label: "租户管理", description: "管理店铺租户和平台配置。" },
      {
        path: "/admin/users",
        label: "用户与权限",
        description: "管理后台用户、租户 membership 和角色。",
        platformAdminOnly: true,
      },
    ],
  },
];

export const compatibilityAdminPages: AdminNavItem[] = [
  {
    path: "/admin/macros",
    label: "宏回复",
    description: "维护客服回复模板；常用宏可直接从统一收件箱插入。",
  },
];
