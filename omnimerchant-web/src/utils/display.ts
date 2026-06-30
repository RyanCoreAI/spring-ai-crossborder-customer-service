const intentMap: Record<string, string> = {
  ORDER_STATUS: '订单查询',
  LOGISTICS: '物流查询',
  RETURN_REFUND: '退货退款',
  PRODUCT_ADVICE: '商品推荐',
  POLICY_QA: '政策问答',
  COMPLAINT: '投诉升级',
  ADDRESS_CHANGE: '修改地址',
  CANCEL_ORDER: '取消订单',
  HUMAN_REQUEST: '人工客服',
  UNKNOWN: '未知意图',
  UNCLEAR: '待识别',
}

const toolMap: Record<string, string> = {
  queryOrder: '查询订单',
  trackLogistics: '查询物流',
  searchProductCatalog: '检索商品',
  refundPolicyRAG: '检索退货政策',
  answerPolicy: '回答政策',
  createReturnRequest: '创建退货申请',
  requestRefundOrReplacement: '申请退款/补发',
  requestAddressChange: '申请改地址',
  escalateToHuman: '升级人工',
  translate: '翻译',
}

const attackMap: Record<string, string> = {
  NONE: '无',
  PROMPT_INJECTION: '提示注入',
  RAG_POISONING: 'RAG 投毒',
  CROSS_TENANT: '跨租户诱导',
  SENSITIVE_DATA: '敏感数据',
  UNSAFE_ACTION: '危险操作',
}

const failureMap: Record<string, string> = {
  AUTH: '认证失败',
  TENANT: '租户校验失败',
  RATE_LIMIT: '限流拒绝',
  LLM_TIMEOUT: '模型超时',
  CIRCUIT_OPEN: '熔断打开',
  MODEL_UNAVAILABLE: '模型不可用',
  TOOL_EXCEPTION: '工具异常',
  RAG_NO_RESULT: '无检索结果',
  RAG_NO_CITATION: '缺少引用',
  SAFETY_BLOCK: '安全拦截',
  SHOPIFY_API: 'Shopify API 异常',
  WEBHOOK_INVALID: 'Webhook 验签失败',
  EVAL_ASSERTION: '评测断言失败',
  UNKNOWN: '未知失败',
}

const statusMap: Record<string, string> = {
  PASS: '通过',
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败',
  RUNNING: '运行中',
  PENDING: '待处理',
  PROCESSING: '处理中',
  RECEIVED: '已接收',
  DEAD: '死信',
  APPROVED: '已审核',
  INDEXED: '已索引',
  QUARANTINED: '已隔离',
  REJECTED: '已拒绝',
  DRAFT: '草稿',
  paid: '已支付',
  unpaid: '未支付',
  processing: '处理中',
  shipped: '已发货',
  delivered: '已送达',
  refunded: '已退款',
  returned: '已退货',
  cancelled: '已取消',
  fulfilled: '已履约',
  unfulfilled: '未履约',
  partial: '部分履约',
  partially_fulfilled: '部分履约',
  in_transit: '运输中',
  out_for_delivery: '派送中',
  label_created: '面单已创建',
  exception: '物流异常',
  in_stock: '有货',
  low_stock: '低库存',
  out_of_stock: '缺货',
}

const runModeMap: Record<string, string> = {
  DETERMINISTIC: '确定性评测',
  LIVE_AGENT: '真实模型评测',
}

const rerankerModeMap: Record<string, string> = {
  'lexical-fallback': '词法兜底',
  fallback: '兜底重排',
  'cross-encoder': '交叉编码器',
}

const stepTypeMap: Record<string, string> = {
  start: '开始',
  intent: '意图识别',
  retrieval: '检索',
  tool: '工具调用',
  model: '模型回复',
  final: '最终回答',
  error: '异常',
}

const docTypeMap: Record<string, string> = {
  REFUND_POLICY: '退货政策',
  SHIPPING_POLICY: '物流政策',
  FAQ: '常见问题',
  PRODUCT_GUIDE: '商品指南',
  PRIVACY_POLICY: '隐私政策',
  TERMS_OF_SERVICE: '服务条款',
}

export function tenantDisplayName(tenant: any) {
  const raw = tenant?.storeName || tenant?.tenantCode || tenant?.id || '—'
  return String(raw)
}

export function tenantOptionLabel(tenant: any) {
  const name = tenantDisplayName(tenant)
  return tenant?.tenantCode ? `${name}（${tenant.tenantCode}）` : name
}

export function intentLabel(value?: string) {
  return intentMap[value || ''] || value || '—'
}

export function toolLabel(value?: string) {
  return toolMap[value || ''] || value || '—'
}

export function toolsLabel(value: any) {
  if (!value) return '—'
  const list = Array.isArray(value) ? value : parseJsonArray(value)
  return list.length ? list.map((item) => toolLabel(String(item))).join('、') : '无'
}

export function attackTypeLabel(value?: string) {
  return attackMap[value || ''] || value || '—'
}

export function failureCategoryLabel(value?: string) {
  return failureMap[value || ''] || value || '无'
}

export function statusLabel(value?: string) {
  return statusMap[value || ''] || value || '—'
}

export function runModeLabel(value?: string) {
  return runModeMap[value || ''] || value || '—'
}

export function rerankerModeLabel(value?: string) {
  return rerankerModeMap[value || ''] || value || '—'
}

export function stepTypeLabel(value?: string) {
  return stepTypeMap[value || ''] || value || '—'
}

export function docTypeLabel(value?: string) {
  return docTypeMap[value || ''] || value || '—'
}

export function displayBusinessValue(value: any, prop?: string) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'number') return String(value)
  const text = String(value)
  if (prop === 'intentPrimary' || prop === 'intent') return intentLabel(text)
  if (prop === 'orderStatus' || prop === 'fulfillmentStatus' || prop === 'trackingStatus' || prop === 'stockStatus' || prop === 'status') {
    return statusLabel(text)
  }
  if (prop === 'customerTier') return text === 'VIP' ? 'VIP 客户' : text === 'REGULAR' ? '普通客户' : text
  if (prop === 'languagePref' || prop === 'language') return text === 'zh' ? '中文' : text === 'en' ? '英文' : text
  if (prop === 'title' || prop === 'sourceTitle') return text
  if (prop === 'productType' || prop === 'categoryL1') return categoryLabel(text)
  if (prop === 'escalationReason') return intentLabel(text) || text
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)) return new Date(text).toLocaleString('zh-CN')
  return statusLabel(text)
}

function categoryLabel(value: string) {
  const map: Record<string, string> = {
    Backpack: '背包',
    Jacket: '外套',
    'T-Shirt': 'T 恤',
    Organizer: '收纳',
    Hoodie: '连帽衫',
    Sling: '斜挎包',
    Pants: '长裤',
    Socks: '袜子',
    Bottle: '水瓶',
    Scarf: '围巾',
    Bags: '箱包',
    Apparel: '服装',
    Accessories: '配件',
    Travel: '旅行',
    Outerwear: '外套',
    Tops: '上装',
    Bottoms: '下装',
  }
  return map[value] || value
}

function parseJsonArray(value: string) {
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return value.split(',').map((item) => item.trim()).filter(Boolean)
  }
}
