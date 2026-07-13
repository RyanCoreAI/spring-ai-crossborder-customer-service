export interface RagCandidate {
  chunkUuid?: string;
  docUuid?: string;
  sourceTitle?: string;
  sectionPath?: string;
  rrfScore?: number;
  rerankScore?: number;
  supportScore?: number;
  fusedRank?: number;
  neighbor?: boolean;
  snippet?: string;
}

export interface RagContextPack {
  context?: string;
  evidenceLevel?: string;
  refusalReason?: string;
  usedChunks?: number;
  budgetChars?: number;
}

export interface RagQueryPlan {
  originalQuery?: string;
  rewrittenQuery?: string;
  expansions?: string[];
  detectedLanguage?: string;
  intent?: string;
  liveRewrite?: boolean;
}

export interface RagDebugResult {
  question?: string;
  queryPlan?: RagQueryPlan;
  retrievalMode?: string;
  rerankerMode?: string;
  retrievalLatencyMs?: number;
  vectorCandidates?: RagCandidate[];
  bm25Candidates?: RagCandidate[];
  fusedCandidates?: RagCandidate[];
  expandedContext?: RagCandidate[];
  contextPack?: RagContextPack;
  latencyMs?: number;
  activeIndexVersion?: string;
}

export interface RagHealth {
  approvedDocs?: number;
  pendingReviews?: number;
  vectorStatus?: string;
  vectorChunkCount?: number;
  lowEvidenceRuns?: number;
}

export interface RagEvalCase {
  caseCode: string;
  intent?: string;
  userMessage?: string;
  expectedTools?: string;
  expectedOutcome?: string;
  attackType?: string;
}

export interface RagDataset {
  id: number;
  datasetKey?: string;
  datasetKind?: string;
  version?: string;
  status?: string;
  caseCount?: number;
}

export interface RagIndexRelease {
  id: number;
  indexVersion: string;
  status?: string;
  embeddingModel?: string;
  rerankerMode?: string;
  queryPlannerVersion?: string;
}

export interface RagExperiment {
  id: number;
  retrievalMode?: string;
  datasetVersion?: string;
  indexVersion?: string;
  caseCount?: number;
  contextRecall?: number;
  mrr?: number;
  ndcgAtK?: number;
  p95RetrievalLatencyMs?: number;
  status?: string;
}

export interface RagFeedback {
  id: number;
  feedbackType?: string;
  questionHash?: string;
  commentRedacted?: string;
  status?: string;
  createdAt?: string;
}
