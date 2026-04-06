export enum ProcessingStatus {
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}
 
export enum RiskLevel {
  LOW = 'LOW',
  MODERATE = 'MODERATE',
  HIGH = 'HIGH'
}
 
export enum MetricType {
  FHP = 'FHP',
  Q_ANGLE = 'Q_ANGLE',
  SHOULDER_ASYMMETRY = 'SHOULDER_ASYMMETRY',
  GAIT_ASYMMETRY = 'GAIT_ASYMMETRY',
  GLOBAL = 'GLOBAL'
}
 
export enum RecommendationSeverity {
  LOW = 'LOW',
  MODERATE = 'MODERATE',
  HIGH = 'HIGH'
}

export interface RecommendationDTO {
  id: number;
  metricType: MetricType;
  severity: RecommendationSeverity;
  title: string;
  biomechanicalCause: string;
  exercise: string | null;
  ergonomicTip: string | null;
  isBlocked: boolean;
  disclaimerRequired: boolean;
  detectedValue: string;
  normalRange: string;
}

export interface EvolutionDTO {
  postureScoreChange: number;
  trend: 'IMPROVEMENT' | 'DETERIORATION' | 'STABLE' | 'FIRST_SESSION';
  daysSinceLastScan: number;
}

export interface AnalysisResultDTO {
  sessionId: number;
  scanDate: string;
  status: ProcessingStatus;
  errorMessage: string | null;
 
  processingMethod: string;
  aiConfidenceScore: number;
  scalingFactor: number;
 
  qAngleLeft: number;
  qAngleRight: number;
  fhpAngle: number;
  fhpDistanceCm: number;
  shoulderAsymmetryCm: number;
 
  stancePhaseLeft: number;
  stancePhaseRight: number;
  cadence: number;
 
  globalPostureScore: number;
  riskLevel: RiskLevel;
 
  recommendations: RecommendationDTO[];
  globalFeedback: string;
  medicalDisclaimer: boolean;
 
  evolution: EvolutionDTO | null;
}
 
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: FieldError[];
}
 
export interface FieldError {
  field: string;
  message: string;
  rejectedValue: any;
}
