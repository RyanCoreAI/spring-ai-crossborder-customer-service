import axios from 'axios'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { getStoredTenantId } from '@/utils/tenant'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

const TENANT_HEADER = 'X-Tenant-Id'
const TENANT_SCOPED_PATHS = [
  '/chat',
  '/test',
  '/knowledge',
  '/conversations',
  '/billing',
  '/customers',
  '/orders',
  '/products',
  '/escalations',
  '/tool-calls',
  '/dashboard',
  '/integrations',
  '/evals',
  '/observability',
  '/rag',
]

function isTenantScoped(url?: string) {
  if (!url) return false
  return TENANT_SCOPED_PATHS.some((path) => url.startsWith(path) || url.startsWith(`/api${path}`))
}

function resolveTenantId(config: any) {
  const existing = config.headers?.[TENANT_HEADER]
  if (existing) return existing
  const paramsTenant = config.params?.tenantId
  if (paramsTenant) return paramsTenant
  const dataTenant = typeof config.data === 'object' && config.data !== null ? config.data.tenantId : null
  if (dataTenant) return dataTenant
  return getStoredTenantId()
}

function redirectToLogin(text: string) {
  const authStore = useAuthStore()
  authStore.logout()
  message.error(text)
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

function isAdminRoute() {
  return router.currentRoute.value.path.startsWith('/admin') || router.currentRoute.value.path === '/chat'
}

function friendlyHttpMessage(status?: number, rawMessage?: string) {
  if (!status && rawMessage === 'Network Error') {
    return '无法连接后端或被 CORS 拦截，请统一使用 http://127.0.0.1:5188 或 http://localhost:5188'
  }
  if (status === 400) return rawMessage || '请求内容格式不正确'
  if (status === 401) return rawMessage || '登录已过期，请重新登录'
  if (status === 403) return rawMessage || '当前账号无权限访问该租户'
  if (status === 405) return rawMessage || '请求方法错误，请刷新页面后重试'
  if (status && status >= 500) return rawMessage || '服务异常，请稍后重试'
  return rawMessage || '网络错误'
}

api.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  if (isTenantScoped(config.url)) {
    const tenantId = resolveTenantId(config)
    if (tenantId) {
      config.headers[TENANT_HEADER] = String(tenantId)
    }
  }
  return config
})

api.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code && data.code !== '200') {
      if (data.code === '401') {
        redirectToLogin(data.message || '登录已过期，请重新登录')
      } else if (data.code === '403' && isAdminRoute()) {
        redirectToLogin('当前登录权限已失效，请重新登录')
      } else {
        message.error(data.message || '请求失败')
      }
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (err) => {
    const status = err.response?.status
    const text = friendlyHttpMessage(status, err.response?.data?.message || err.message)
    if (status === 401) {
      redirectToLogin(text || '登录已过期，请重新登录')
    } else if (status === 403 && isAdminRoute()) {
      redirectToLogin('当前登录权限已失效，请重新登录')
    } else {
      message.error(text)
    }
    return Promise.reject(err)
  }
)

export default api
