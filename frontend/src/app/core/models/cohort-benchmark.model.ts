export interface MetricStats {
  p25: number | null;
  p50: number | null;
  p75: number | null;
  avg: number | null;
}

export interface CohortBenchmarkDTO {
  gps: MetricStats;
  fhpAngle: MetricStats;
  qAngle: MetricStats;
  shoulderAsymmetry: MetricStats;
  totalSessions: number;
}

export type BenchmarkMetric = 'gps' | 'fhpAngle' | 'qAngle' | 'shoulderAsymmetry';

export interface MetricConfig {
  key: BenchmarkMetric;
  label: string;
  shortLabel: string;
  unit: string;
  showRiskThresholds: boolean;
  suggestedMin?: number;
  suggestedMax?: number;
  lowerIsBetter: boolean;
}

export const METRIC_CONFIGS: Record<BenchmarkMetric, MetricConfig> = {
  gps: {
    key: 'gps',
    label: 'Global Posture Score',
    shortLabel: 'GPS',
    unit: '%',
    showRiskThresholds: true,
    suggestedMin: 0,
    suggestedMax: 100,
    lowerIsBetter: true,
  },
  fhpAngle: {
    key: 'fhpAngle',
    label: 'Forward Head Posture',
    shortLabel: 'FHP',
    unit: '°',
    showRiskThresholds: false,
    suggestedMin: 0,
    suggestedMax: 40,
    lowerIsBetter: true,
  },
  qAngle: {
    key: 'qAngle',
    label: 'Q Angle (avg L/R)',
    shortLabel: 'Q Angle',
    unit: '°',
    showRiskThresholds: false,
    suggestedMin: 0,
    suggestedMax: 25,
    lowerIsBetter: true,
  },
  shoulderAsymmetry: {
    key: 'shoulderAsymmetry',
    label: 'Shoulder Asymmetry',
    shortLabel: 'Shoulders',
    unit: 'cm',
    showRiskThresholds: false,
    suggestedMin: 0,
    suggestedMax: 5,
    lowerIsBetter: true,
  },
};