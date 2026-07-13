export interface QueueBucket {
  queueKey: string;
  queueLabel: string;
  count: number;
  description?: string;
}

export interface InboxWorkItem {
  sourceType?: string;
  ticketId?: number;
  conversationUuid?: string;
  customerName?: string;
  customerEmail?: string;
  channel?: string;
  channelLabel?: string;
  intent?: string;
  sentiment?: string;
  status?: number;
  statusLabel?: string;
  priority?: number;
  assignedAgentId?: number;
  assignedAgentName?: string;
  messageCount?: number;
  toolCallCount?: number;
  totalCostUsd?: number;
  latestTicketNo?: string;
  latestTicketStatus?: string;
  slaState?: string;
  lastMessageAt?: string;
  startedAt?: string;
}

export interface InboxMessage {
  id?: number;
  messageUuid: string;
  role: string;
  seqNo?: number;
  content?: string;
  contentType?: string;
  originalLang?: string;
  detectionConfidence?: number;
  translatedContent?: string;
  translationProvider?: string;
  translationStatus?: string;
  translationLatencyMs?: number;
  translationFallbackReason?: string;
  toolName?: string;
  createdAt?: string;
}

export interface InboxCustomerContext {
  id?: number;
  displayName?: string;
  email?: string;
  phone?: string;
  countryCode?: string;
  language?: string;
  tier?: string;
  totalOrders?: number;
  totalSpent?: number;
}

export interface InboxOrderContext {
  id?: number;
  orderNumber?: string;
  platform?: string;
  orderStatus?: string;
  paymentStatus?: string;
  fulfillmentStatus?: string;
  currency?: string;
  totalAmount?: number;
  trackingStatus?: string;
  trackingNumber?: string;
}

export interface InboxSlaContext {
  slaState?: string;
  responseDueAt?: string;
  resolveDueAt?: string;
}

export interface InboxActionContext {
  requestNo?: string;
  actionType?: string;
  statusLabel?: string;
  riskReason?: string;
}

export interface InboxToolContext {
  toolName?: string;
  success?: number;
  latencyMs?: number;
}

export interface InboxContext {
  conversation?: InboxWorkItem;
  messages?: InboxMessage[];
  customer?: InboxCustomerContext;
  recentOrders?: InboxOrderContext[];
  sla?: InboxSlaContext;
  actions?: InboxActionContext[];
  toolCalls?: InboxToolContext[];
}

export interface SupportMacro {
  macroCode: string;
  title: string;
  category?: string;
  channel?: string;
  content: string;
  requiresApproval?: number;
  enabled?: number;
}
