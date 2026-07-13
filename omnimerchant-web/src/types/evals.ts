export interface GoldDataset {
  id: number;
  datasetKey: string;
  version: string;
  status?: string;
}

export interface GoldEvalCase {
  id: number;
  datasetVersion?: string;
  caseCode: string;
  intent?: string;
  userMessage?: string;
  expectedTools?: string;
  expectedOutcome?: string;
  attackType?: string;
  annotationStatus?: string;
  annotatedBy?: number;
}

export interface EvalRunSummary {
  id: number;
  runUuid?: string;
  runMode?: string;
  status?: string;
  passRate?: number;
  toolPrecision?: number;
  toolRecall?: number;
  citationCoverage?: number;
  poisoningBlockRate?: number;
  retrievalPrecisionAtK?: number;
  recallAtK?: number;
  mrr?: number;
  ndcgAtK?: number;
  noAnswerAccuracy?: number;
  unsupportedClaimRate?: number;
  p95RetrievalLatencyMs?: number;
}

export interface EvalCaseSummary {
  id?: number;
  caseCode: string;
  intent?: string;
  userMessage?: string;
  expectedTools?: string;
  expectedOutcome?: string;
  attackType?: string;
  enabled?: number;
}

export interface EvalSummary {
  totalCases?: number;
  enabledCases?: number;
  casesByIntent?: Record<string, number>;
}

export interface EvalResult {
  caseCode: string;
  intent?: string;
  status?: string;
  expectedOutcome?: string;
  actualObservation?: string;
  expectedTools?: string;
  actualTools?: string;
  forbiddenTools?: string;
  toolPrecision?: number;
  toolRecall?: number;
  argumentMatch?: boolean;
  forbiddenToolViolation?: boolean;
  traceId?: string;
  failureCategory?: string;
  rerankerMode?: string;
  retrievalRank?: number;
  retrievalLatencyMs?: number;
}

export interface EvalRunReport {
  tenantId?: number;
  total?: number;
  passed?: number;
  failed?: number;
  passRate?: number;
  results?: EvalResult[];
}

export interface EvalRunDetail {
  run?: EvalRunSummary;
  results?: EvalResult[];
}
