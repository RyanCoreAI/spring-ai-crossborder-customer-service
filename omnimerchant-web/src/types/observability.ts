export interface ToolMetric {
  toolName: string;
  calls?: number;
  successRate?: number;
  p95LatencyMs?: number;
  failures?: number;
}

export interface EvalTrendPoint {
  runId: number;
  startedAt?: string;
  status?: string;
  passRate?: number;
  toolPrecision?: number;
  citationCoverage?: number;
}

export interface ObservabilitySummary {
  evalRuns?: number
  latestEvalPassRate?: number
  toolCalls?: number
  toolSuccessRate?: number
  aiResolved?: number
  costPerResolvedConversation?: number
  estimatedCost?: number
  traces?: number
  failedTraces?: number
  failedToolCalls?: number
  safetyBlocks?: number
  shopifyWebhookBacklog?: number
  fallbackRate?: number
  topFailedTool?: string
}

export interface RagObservabilityMetric {
  evalRuns?: number
  citationCoverage?: number
  unsupportedClaimRate?: number
  recallAtK?: number
  mrr?: number
  ndcgAtK?: number
  p95RetrievalLatencyMs?: number
  poisoningBlockRate?: number
  noAnswerAccuracy?: number
}

export interface FailureBucket {
  category?: string
  count?: number
  rate?: number
}
