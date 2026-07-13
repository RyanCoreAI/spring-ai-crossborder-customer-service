export type DimensionMetric = {
  name: string;
  count: number;
  rate: number | null;
};

export type OperationsSummary = {
  conversations: number;
  aiResolved: number;
  humanTakeovers: number;
  closedTickets: number;
  pendingActions: number;
  aiResolutionRate: number | null;
  humanTakeoverRate: number | null;
  avgCsat: number | null;
  avgFirstResponseSeconds: number | null;
  costPerResolvedCase: number | null;
  intents: DimensionMetric[];
  channels: DimensionMetric[];
  topFailureCategories: DimensionMetric[];
};

export type DashboardSummary = {
  pendingReturns: number;
  aiResolutionRate: number | null;
  toolSuccessRate: number | null;
};

export type QueueBucket = {
  queueKey: string;
  queueLabel: string;
  count: number;
  description: string;
};

export type SloMetric = {
  key: string;
  label: string;
  target: number | null;
  actual: number | null;
  unit: string;
  status: string;
};

export type AlertSummary = {
  severity: string;
  category: string;
  message: string;
};

export type SreSummary = {
  slos: SloMetric[];
  alerts: AlertSummary[];
};

export type SlaSummary = {
  openTickets: number;
};
