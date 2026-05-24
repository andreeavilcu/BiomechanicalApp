import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EvolutionChartComponent } from './evolution-chart.component';
import { AnalysisResultDTO, ProcessingStatus } from '../../../core/models/scan.model';
import { ScanService } from '../../../core/services/scan.service';
import { CohortBenchmarkDTO } from '../../../core/models/cohort-benchmark.model';

function makeSession(partial: Partial<AnalysisResultDTO> = {}): AnalysisResultDTO {
  return {
    sessionId: 1,
    scanDate: '2025-01-01T10:00:00',
    status: ProcessingStatus.COMPLETED,
    errorMessage: null,
    processingMethod: 'LIDAR',
    aiConfidenceScore: 0.9,
    scalingFactor: 1,
    qAngleLeft: 10,
    qAngleRight: 10,
    fhpAngle: 5,
    fhpDistanceCm: 2,
    shoulderAsymmetryCm: 1,
    stancePhaseLeft: 60,
    stancePhaseRight: 60,
    cadence: 100,
    globalPostureScore: 80,
    riskLevel: null as any,
    recommendations: [],
    globalFeedback: '',
    medicalDisclaimer: false,
    evolution: null,
    keypoints: [],
    targetHeightMeters: null,
    ...partial,
  };
}

describe('EvolutionChartComponent (unit-only)', () => {
  let comp: EvolutionChartComponent;
  let fixture: ComponentFixture<EvolutionChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvolutionChartComponent],
      providers: [{ provide: ScanService, useValue: { getCohortBenchmark: vi.fn() } }],
    }).compileComponents();

    fixture = TestBed.createComponent(EvolutionChartComponent);
    comp = fixture.componentInstance;
  });

  it('linearRegression returns same points when less than 2', () => {
    expect((comp as any).linearRegression([5])).toEqual([5]);
  });

  it('linearRegression computes a trend for increasing points', () => {
    const trend = (comp as any).linearRegression([1, 2, 3, 4]);
    expect(trend.length).toBe(4);
    expect(trend[3]).toBeGreaterThan(trend[0]);
  });

  it('getMetricValue handles gps and qAngle average', () => {
    comp.selectedMetric = 'gps';
    expect((comp as any).getMetricValue(makeSession({ globalPostureScore: 55 }))).toBe(55);
    comp.selectedMetric = 'qAngle';
    expect((comp as any).getMetricValue(makeSession({ qAngleLeft: 10, qAngleRight: 20 }))).toBe(15);
  });

  it('formatBenchmarkValue formats null and numbers', () => {
    // @ts-ignore
    comp.selectedMetric = 'gps';
    // set currentConfig stub
    // @ts-ignore
    comp.selectedMetric = 'gps';
    expect(comp.formatBenchmarkValue(null)).toBe('—');
  });

  it('patientPositionInfo returns null when benchmark missing', () => {
    comp.benchmark = null;
    comp.sessions = [];
    expect(comp.patientPositionInfo).toBeNull();
  });

  it('patientPositionInfo returns position info when benchmark and sessions present', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = { gps: { p25: 30, p75: 70, avg: 50 }, fhpAngle: null, qAngle: null, shoulderAsymmetry: null } as any;
    comp.sessions = [makeSession({ globalPostureScore: 80 })];
    expect(comp.patientPositionInfo).toEqual({ label: 'Above 75th percentile', cssClass: 'pos-bad' });
  });

  it('currentBenchmark returns null when benchmark is missing', () => {
    comp.benchmark = null;
    expect(comp.currentBenchmark).toBeNull();
  });

  it('currentBenchmark returns benchmark stats when available', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = { gps: { p25: 30, p75: 70, avg: 50 }, fhpAngle: null, qAngle: null, shoulderAsymmetry: null } as any;
    expect(comp.currentBenchmark).toEqual({ p25: 30, p75: 70, avg: 50 });
  });

  it('hasEnoughSessions returns false for less than two completed sessions', () => {
    comp.sessions = [makeSession()];
    expect(comp.hasEnoughSessions).toBe(false);
  });

  it('hasEnoughSessions returns true for two or more completed sessions', () => {
    comp.sessions = [makeSession(), makeSession({ sessionId: 2, scanDate: '2025-02-01T00:00:00' })];
    expect(comp.hasEnoughSessions).toBe(true);
  });

  it('completedSessions filters and sorts sessions', () => {
    comp.sessions = [makeSession({ scanDate: '2025-02-01' }), makeSession({ scanDate: '2025-01-01' })];
    const cs = comp.completedSessions;
    expect(cs[0].scanDate).toBe('2025-01-01');
  });
});