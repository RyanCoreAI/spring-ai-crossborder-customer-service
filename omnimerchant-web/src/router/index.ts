import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/widget',
      name: 'Widget',
      component: () => import('@/views/widget/WidgetView.vue'),
      meta: { public: true },
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/chat/ChatView.vue'),
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      children: [
        { path: '', name: 'Dashboard', component: () => import('@/views/admin/DashboardView.vue') },
        { path: 'channels', name: 'Channels', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'channels' } },
        { path: 'inbox', name: 'Inbox', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'inbox' } },
        { path: 'tenants', name: 'Tenants', component: () => import('@/views/admin/TenantView.vue') },
        { path: 'customers', name: 'Customers', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'customers' } },
        { path: 'orders', name: 'Orders', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'orders' } },
        { path: 'products', name: 'Products', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'products' } },
        { path: 'knowledge', name: 'Knowledge', component: () => import('@/views/admin/KnowledgeView.vue') },
        { path: 'conversations', name: 'Conversations', component: () => import('@/views/admin/ConversationView.vue') },
        { path: 'tickets', name: 'Tickets', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'tickets' } },
        { path: 'sla', name: 'Sla', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'sla' } },
        { path: 'macros', name: 'Macros', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'macros' } },
        { path: 'actions', name: 'Actions', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'actions' } },
        { path: 'qa', name: 'Qa', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'qa' } },
        { path: 'operations', name: 'Operations', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'operations' } },
        { path: 'audit', name: 'Audit', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'audit' } },
        { path: 'sre', name: 'Sre', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'sre' } },
        { path: 'agent-workflow', name: 'AgentWorkflow', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'agent' } },
        { path: 'security', name: 'SecurityReadiness', component: () => import('@/views/admin/CommercialOpsView.vue'), props: { section: 'security' } },
        { path: 'integrations', name: 'Integrations', component: () => import('@/views/admin/IntegrationsView.vue') },
        { path: 'usage', name: 'Usage', component: () => import('@/views/admin/UsageView.vue') },
        { path: 'tool-calls', name: 'ToolCalls', component: () => import('@/views/admin/ToolCallsView.vue') },
        { path: 'evals', name: 'Evals', component: () => import('@/views/admin/EvalsView.vue') },
        { path: 'observability', name: 'Observability', component: () => import('@/views/admin/ObservabilityView.vue') },
        { path: 'traces', name: 'Traces', component: () => import('@/views/admin/TracesView.vue') },
        { path: 'rag-workbench', name: 'RagWorkbench', component: () => import('@/views/admin/RagWorkbenchView.vue') },
        { path: 'rag-safety', name: 'RagSafety', component: () => import('@/views/admin/RagSafetyView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/chat' },
  ],
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.meta.public || authStore.isLoggedIn) {
    next()
  } else {
    next('/login')
  }
})

export default router
