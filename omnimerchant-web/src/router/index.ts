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
        { path: 'inbox', name: 'Inbox', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'inbox' } },
        { path: 'tenants', name: 'Tenants', component: () => import('@/views/admin/TenantView.vue') },
        { path: 'customers', name: 'Customers', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'customers' } },
        { path: 'orders', name: 'Orders', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'orders' } },
        { path: 'products', name: 'Products', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'products' } },
        { path: 'knowledge', name: 'Knowledge', component: () => import('@/views/admin/KnowledgeView.vue') },
        { path: 'conversations', name: 'Conversations', component: () => import('@/views/admin/ConversationView.vue') },
        { path: 'tickets', name: 'Tickets', component: () => import('@/views/admin/ResourceView.vue'), props: { resource: 'tickets' } },
        { path: 'integrations', name: 'Integrations', component: () => import('@/views/admin/IntegrationsView.vue') },
        { path: 'usage', name: 'Usage', component: () => import('@/views/admin/UsageView.vue') },
        { path: 'evals', name: 'Evals', component: () => import('@/views/admin/EvalsView.vue') },
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
