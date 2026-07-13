export interface PageResult<T> {
  total: number
  records: T[]
}

export interface TablePageChange {
  current?: number
  pageSize?: number
}

export interface TenantSummary {
  id: number
  tenantCode?: string
  tenantName?: string
  name?: string
  storeName?: string
  platform?: string
  externalStoreId?: string
  ownerEmail?: string
  ownerName?: string
  defaultLang?: string
  subscriptionPlan?: string
  monthlyTokenBudget?: number
  qpsLimit?: number
  createdAt?: string
  status?: number | string
}

export interface UserMembership {
  tenantId: number
  roleKey: string
}

export interface AdminUser {
  id: number
  email: string
  displayName?: string
  platformAdmin?: boolean
  status?: string
  memberships?: UserMembership[]
  lastLoginAt?: string
}

export interface SupportRole {
  roleKey: string
  roleLabel?: string
}

export interface ConversationSummary {
  id?: number
  conversationUuid: string
  customerId?: number
  customerName?: string
  customerEmail?: string
  channel?: string
  language?: string
  title?: string
  status?: number
  statusLabel?: string
  intentPrimary?: string
  sentiment?: string
  messageCount?: number
  totalCostUsd?: number
  createdAt?: string
  startedAt?: string
  updatedAt?: string
}

export interface ConversationMessage {
  id?: number
  messageUuid?: string
  role?: string
  content?: string
  contentType?: string
  modelName?: string
  totalTokens?: number
  latencyMs?: number
  createdAt?: string
}

export interface ActionRequest {
  source: string
  id: number
  requestNo?: string
  actionType?: string
  orderNumber?: string
  externalOrderNumber?: string
  customerName?: string
  customerEmail?: string
  amount?: number | string
  currency?: string
  status?: string
  statusLabel?: string
  riskReason?: string
  requestedPayload?: string
  requestedBy?: string
  requestedAt?: string
  decidedBy?: number
  decidedAt?: string
}

export interface TicketSummary {
  id: number
  ticketNo?: string
  conversationUuid?: string
  customerName?: string
  customerEmail?: string
  subject?: string
  status?: string
  statusLabel?: string
  priority?: number
  assignedAgentId?: number
  assignedAgentName?: string
  slaState?: string
  responseDueAt?: string
  resolveDueAt?: string
  closedAt?: string
  summary?: string
  closeReason?: string
  channel?: string
  updatedAt?: string
}

export interface SlaRiskTicket {
  ticketId?: number
  ticketNo?: string
  subject?: string
  priority?: number
  assignedAgentName?: string
  slaState?: string
  responseDueAt?: string
  resolveDueAt?: string
}

export interface SlaSummary {
  openTickets?: number
  dueSoon?: number
  breached?: number
  healthy?: number
  breachRate?: number
  responseBreachRate?: number
  resolveBreachRate?: number
  responseBreached?: number
  resolveBreached?: number
  policies?: Array<Record<string, unknown>>
  risks?: SlaRiskTicket[]
  riskTickets?: SlaRiskTicket[]
}

export interface QaReviewItem {
  id: number
  ticketId?: number
  ticketNo?: string
  conversationUuid?: string
  customerName?: string
  sourceType?: string
  status?: string
  autoScore?: number
  reviewerScore?: number
  resolutionCorrect?: number
  toolUseCorrect?: number
  citationSufficient?: number
  unauthorizedAction?: number
  shouldEscalate?: number
  toneScore?: number
  reviewFlags?: string
  reviewerName?: string
  findings?: string
  actionItems?: string
  createdAt?: string
  queuedAt?: string
}

export interface QaSummary {
  total?: number
  pending?: number
  reviewed?: number
  averageAutoScore?: number
  averageReviewerScore?: number
  unauthorizedActions?: number
  citationFailures?: number
}

export interface AuditEvent {
  id: number
  actorType?: string
  actorId?: number
  actorName?: string
  action?: string
  resourceType?: string
  resourceId?: string
  summary?: string
  riskLevel?: string
  metadataJson?: string
  createdAt?: string
}

export interface AgentNode {
  nodeKey: string
  nodeLabel?: string
  responsibility?: string
  toolAllowlist?: string
  status?: string
}

export interface AgentPolicy {
  policyKey: string
  description?: string
  enforcement?: string
}

export interface AgentWorkflow {
  workflowName?: string
  currentMode?: string
  workflowKey?: string
  workflowLabel?: string
  nodes?: AgentNode[]
  policies?: AgentPolicy[]
}

export interface AgentPlan {
  specialistKey?: string
  specialistLabel?: string
  toolAllowlist?: string[]
  riskLevel?: string
  requiresIdentityVerification?: boolean
  requiresApproval?: boolean
  recommendHumanHandoff?: boolean
  routingEvidence?: string
}

export interface ChannelAccount {
  id: number
  channelType?: string
  channelLabel?: string
  accountName?: string
  connectionMode?: string
  connectionStatus?: string
  adapterStatus?: string
  authMode?: string
  webhookStatus?: string
  supportsInbound?: boolean
  supportsOutbound?: boolean
  credentialStatus?: string
  lastWebhookAt?: string
  lastSyncAt?: string
  lastError?: string
}

export interface ChannelSummary {
  channelType?: string
  channelLabel?: string
  connectionStatus?: string
  conversations?: number
  inboundMessages?: number
  outboundMessages?: number
  deliverySuccessRate?: number
}

export interface ShopifyJob {
  id: number
  resource?: string
  status?: string
  attempts?: number
  importedCount?: number
  cursor?: string
  lastError?: string
  nextRunAt?: string
  updatedAt?: string
}

export interface ShopifyWebhook {
  id: number
  webhookId?: string
  topic?: string
  status?: string
  attempts?: number
  processAttempts?: number
  signatureValid?: boolean
  lastError?: string
  createdAt?: string
}

export interface ShopifyBulkOperation {
  id: number
  resource?: string
  operationGid?: string
  status?: string
  objectCount?: number
  resultUrl?: string
  resultReady?: boolean
  errorCode?: string
  lastError?: string
  updatedAt?: string
}

export interface ShopifyPrivacyRequest {
  id: number
  requestUuid?: string
  topic?: string
  shopDomain?: string
  customerExternalId?: string
  status?: string
  affectedRows?: number
  affectedRecords?: number
  completedAt?: string
  lastError?: string
  createdAt?: string
}

export interface DomesticPlatform {
  platform: string
  platformLabel?: string
  mode?: string
  authStatus?: string
  connectionStatus?: string
  lastSyncAt?: string
  lastWebhookAt?: string
  lastError?: string
  pendingActionRequests?: number
  evidence?: string
}

export interface MultilingualSummary {
  conversations?: number
  multilingualConversations?: number
  multilingualRate?: number
  translatedMessages?: number
  translationFallbackRate?: number
  languages?: Array<{ name: string; count: number; rate?: number }>
}

export interface MultilingualEvent {
  id: number
  conversationUuid?: string
  messageUuid?: string
  traceId?: string
  direction?: string
  sourceLanguage?: string
  targetLanguage?: string
  detectionConfidence?: number
  sourceText?: string
  translatedText?: string
  provider?: string
  model?: string
  status?: string
  latencyMs?: number
  fallbackReason?: string
  createdAt?: string
}

export interface MultilingualDebugResult {
  originalText?: string
  detectedLanguage?: string
  confidence?: number
  needsTranslation?: boolean
  agentInput?: string
  targetLanguage?: string
  provider?: string
  model?: string
  status?: string
  latencyMs?: number
  fallback?: boolean
  fallbackReason?: string
}

export interface SloMetric {
  key: string
  label?: string
  target?: number
  actual?: number
  unit?: string
  status?: string
}

export interface AlertEvent {
  id: number
  severity?: string
  category?: string
  message?: string
  status?: string
  observedAt?: string
  acknowledgedAt?: string
  closedAt?: string
}

export interface SloSnapshot {
  id: number
  sloKey?: string
  targetValue?: number
  actualValue?: number
  status?: string
  windowStartedAt?: string
  evaluatedAt?: string
}

export interface SreSummary {
  slos?: SloMetric[]
  alerts?: Array<{ severity?: string; category?: string; message?: string }>
  webhookBacklog?: number
  deadShopifyJobs?: number
  llmFailureRate?: number
  rateLimitFailures?: number
  failedTraces?: number
  failedTools?: number
  latestEvalPassRate?: number
  generatedAt?: string
}

export interface ReadinessControl {
  controlKey: string
  controlLabel?: string
  status?: string
  evidence?: string
  nextStep?: string
  riskLevel?: string
}

export interface ProductionReadiness {
  securityControls?: ReadinessControl[]
  rolePolicies?: Array<Record<string, unknown>>
  actionPolicies?: Array<Record<string, unknown>>
  retentionPolicies?: Array<Record<string, unknown>>
  shopifyCapabilities?: Array<Record<string, unknown>>
  runbooks?: Array<Record<string, unknown>>
  recentAgentGuards?: Array<Record<string, unknown>>
  explicitNonGoals?: string[]
  generatedAt?: string
}

export interface ToolCallRecord {
  id: number
  toolCallId?: string
  traceId?: string
  conversationUuid?: string
  toolName?: string
  inputSummary?: string
  outputSummary?: string
  latencyMs?: number
  success?: number
  triggeredByModel?: number
  errorCode?: string
  errorMessage?: string
  failureReason?: string
  createdAt?: string
}

export interface TraceSummary {
  traceId: string
  conversationUuid?: string
  intent?: string
  model?: string
  modelName?: string
  status?: string
  failureCategory?: string
  totalLatencyMs?: number
  toolCallCount?: number
  totalCostUsd?: number
  createdAt?: string
}

export interface TraceStep {
  id?: number
  stepNo?: number
  stepIndex?: number
  stepType?: string
  stepName?: string
  name?: string
  status?: string
  inputSummary?: string
  outputSummary?: string
  latencyMs?: number
  failureCategory?: string
  createdAt?: string
}

export interface TraceDetail {
  run?: TraceSummary
  steps?: TraceStep[]
  toolCalls?: ToolCallRecord[]
}

export interface WidgetSession {
  tenantId?: number
  tenantCode?: string
  conversationUuid: string
  customerSessionToken: string
  expiresAt: string
  welcomeMessage?: string
  storeName?: string
}

export interface KnowledgeDoc {
  id?: number
  tenantId?: number
  docUuid: string
  docType?: string
  title?: string
  summary?: string
  language?: string
  rawContent?: string
  priority?: number
  chunkCount?: number
  vectorSynced?: number
  retrievalCount?: number
  status?: number
  createdAt?: string
}

export interface RagSafetyReview {
  id?: number
  docUuid: string
  sourceTrustLevel?: string
  riskLevel?: string
  status?: string
  indexAllowed?: boolean
  indexVersion?: string
  riskRules?: string
  redactedExcerpt?: string
}

export interface ChatTenant extends TenantSummary {
  code?: string
}

export interface ChatMessage {
  role: string
  text: string
  toolCalls?: Array<{ name: string; result?: string }>
}

export interface IntegrationResult extends Record<string, unknown> {
  status?: string
  message?: string
  installUrl?: string
  customers?: number
  orders?: number
  products?: number
}

export interface UsageSummary extends Record<string, unknown> {
  totalTokens?: number
  totalCostUsd?: number
  quotaTokens?: number
  quotaRemaining?: number
}

export interface DashboardCommerceSummary {
  aiResolutionRate?: number
  escalationRate?: number
  toolSuccessRate?: number
  openTickets?: number
}

export interface RuntimeSummary {
  mode: string
  demoDataEnabled: boolean
  seedVersion: string
}

export type ResourceRecord = Record<string, unknown> & { id?: number | string }
