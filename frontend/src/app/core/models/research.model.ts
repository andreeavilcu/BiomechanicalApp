export interface AggregateMetricsDTO {
  totalSessions: number;
  averageGps: number;
  averageFhpAngle: number;
  averageQAngle: number;
  stdDevGps: number;
  p25Gps: number;
  p75Gps: number;
}

export interface PostureTrendDTO {
  date: string;
  averageGps: number;
  sessionCount: number;
}