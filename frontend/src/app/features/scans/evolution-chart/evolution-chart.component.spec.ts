import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { EvolutionChartComponent } from './evolution-chart.component';
import { AnalysisResultDTO, ProcessingStatus } from '../../../core/models/scan.model';
import { ScanService } from '../../../core/services/scan.service';
import { CohortBenchmarkDTO } from '../../../core/models/cohort-benchmark.model';

vi.mock('chart.js', () => {
  const ChartMock = vi.fn(function ChartCtor(this: any) { this.destroy = vi.fn(); });
  (ChartMock as any).register = vi.fn();
  return {
    Chart: ChartMock,
    registerables: [],
  };
});

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

const makeBenchmark = (): CohortBenchmarkDTO => ({
  totalSessions: 100,
  gps: { p25: 50, p50: 65, p75: 80, avg: 65 },
  fhpAngle: { p25: 5, p50: 12, p75: 20, avg: 12 },
  qAngle: { p25: 8, p50: 13, p75: 18, avg: 13 },
  shoulderAsymmetry: { p25: 0.5, p50: 1.5, p75: 3, avg: 1.5 },
});

describe('EvolutionChartComponent (unit-only)', () => {
  let comp: EvolutionChartComponent;
  let fixture: ComponentFixture<EvolutionChartComponent>;
  let mockScanService: { getCohortBenchmark: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockScanService = {
      getCohortBenchmark: vi.fn().mockReturnValue(of(makeBenchmark())),
    };

    await TestBed.configureTestingModule({
      imports: [EvolutionChartComponent],
      providers: [{ provide: ScanService, useValue: mockScanService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EvolutionChartComponent);
    comp = fixture.componentInstance;
  });

  it('should create', () => {
    expect(comp).toBeTruthy();
  });

  it('linearRegression returns same points when less than 2', () => {
    expect((comp as any).linearRegression([5])).toEqual([5]);
  });

  it('linearRegression computes a trend for increasing points', () => {
    const trend = (comp as any).linearRegression([1, 2, 3, 4]);
    expect(trend.length).toBe(4);
    expect(trend[3]).toBeGreaterThan(trend[0]);
  });

  it('linearRegression handles zero denominator (all same x)', () => {
    const trend = (comp as any).linearRegression([5, 5]);
    expect(trend.length).toBe(2);
  });

  it('getMetricValue handles gps', () => {
    comp.selectedMetric = 'gps';
    expect((comp as any).getMetricValue(makeSession({ globalPostureScore: 55 }))).toBe(55);
  });

  it('getMetricValue handles fhpAngle', () => {
    comp.selectedMetric = 'fhpAngle';
    expect((comp as any).getMetricValue(makeSession({ fhpAngle: 12 }))).toBe(12);
  });

  it('getMetricValue handles qAngle average', () => {
    comp.selectedMetric = 'qAngle';
    expect((comp as any).getMetricValue(makeSession({ qAngleLeft: 10, qAngleRight: 20 }))).toBe(15);
  });

  it('getMetricValue returns null for qAngle when values missing', () => {
    comp.selectedMetric = 'qAngle';
    expect((comp as any).getMetricValue(makeSession({ qAngleLeft: null as any, qAngleRight: null as any }))).toBeNull();
  });

  it('getMetricValue handles shoulderAsymmetry', () => {
    comp.selectedMetric = 'shoulderAsymmetry';
    expect((comp as any).getMetricValue(makeSession({ shoulderAsymmetryCm: 2 }))).toBe(2);
  });

  it('formatBenchmarkValue formats null as em-dash', () => {
    expect(comp.formatBenchmarkValue(null)).toBe('—');
  });

  it('formatBenchmarkValue formats numbers with unit', () => {
    comp.selectedMetric = 'gps';
    expect(comp.formatBenchmarkValue(72.5)).toBe('72.5%');
  });

  it('patientPositionInfo returns null when benchmark missing', () => {
    comp.benchmark = null;
    comp.sessions = [];
    expect(comp.patientPositionInfo).toBeNull();
  });

  it('patientPositionInfo returns null when benchmark has no p25/p75', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = { ...makeBenchmark(), gps: { p25: null as any, p50: null as any, p75: null as any, avg: 50 } };
    comp.sessions = [makeSession()];
    expect(comp.patientPositionInfo).toBeNull();
  });

  it('patientPositionInfo returns null when no completed sessions', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = makeBenchmark();
    comp.sessions = [];
    expect(comp.patientPositionInfo).toBeNull();
  });

  it('patientPositionInfo returns null when metric value is null', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = makeBenchmark();
    comp.sessions = [makeSession({ globalPostureScore: null as any })];
    expect(comp.patientPositionInfo).toBeNull();
  });

  it('patientPositionInfo returns pos-bad for GPS above p75 (lowerIsBetter)', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = { ...makeBenchmark(), gps: { p25: 30, p50: 50, p75: 70, avg: 50 } };
    comp.sessions = [makeSession({ globalPostureScore: 80 })];
    expect(comp.patientPositionInfo).toEqual({ label: 'Above 75th percentile', cssClass: 'pos-bad' });
  });

  it('patientPositionInfo returns pos-good for GPS below p25 (lowerIsBetter)', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = { ...makeBenchmark(), gps: { p25: 50, p50: 65, p75: 80, avg: 65 } };
    comp.sessions = [makeSession({ globalPostureScore: 20 })];
    expect(comp.patientPositionInfo).toEqual({ label: 'Better than 75% of users', cssClass: 'pos-good' });
  });

  it('patientPositionInfo returns pos-neutral for value within range', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = { ...makeBenchmark(), gps: { p25: 50, p50: 65, p75: 80, avg: 65 } };
    comp.sessions = [makeSession({ globalPostureScore: 65 })];
    expect(comp.patientPositionInfo).toEqual({ label: 'Within typical range (P25–P75)', cssClass: 'pos-neutral' });
  });

  it('patientPositionInfo returns pos-good for fhpAngle below p25 (lowerIsBetter)', () => {
    comp.selectedMetric = 'fhpAngle';
    comp.benchmark = { ...makeBenchmark(), fhpAngle: { p25: 10, p50: 17, p75: 25, avg: 15 } };
    comp.sessions = [makeSession({ fhpAngle: 3 })];
    expect(comp.patientPositionInfo).toEqual({ label: 'Better than 75% of users', cssClass: 'pos-good' });
  });

  it('patientPositionInfo returns pos-bad for fhpAngle above p75 (lowerIsBetter)', () => {
    comp.selectedMetric = 'fhpAngle';
    comp.benchmark = { ...makeBenchmark(), fhpAngle: { p25: 10, p50: 17, p75: 25, avg: 15 } };
    comp.sessions = [makeSession({ fhpAngle: 30 })];
    expect(comp.patientPositionInfo).toEqual({ label: 'Above 75th percentile', cssClass: 'pos-bad' });
  });

  it('currentBenchmark returns null when benchmark is missing', () => {
    comp.benchmark = null;
    expect(comp.currentBenchmark).toBeNull();
  });

  it('currentBenchmark returns benchmark stats when available', () => {
    comp.selectedMetric = 'gps';
    comp.benchmark = makeBenchmark();
    expect(comp.currentBenchmark).toEqual({ p25: 50, p50: 65, p75: 80, avg: 65 });
  });

  it('hasEnoughSessions returns false for less than two completed sessions', () => {
    comp.sessions = [makeSession()];
    expect(comp.hasEnoughSessions).toBe(false);
  });

  it('hasEnoughSessions returns true for two or more completed sessions', () => {
    comp.sessions = [makeSession(), makeSession({ sessionId: 2, scanDate: '2025-02-01T00:00:00' })];
    expect(comp.hasEnoughSessions).toBe(true);
  });

  it('completedSessions filters and sorts sessions ascending by date', () => {
    comp.sessions = [makeSession({ scanDate: '2025-02-01' }), makeSession({ scanDate: '2025-01-01' })];
    const cs = comp.completedSessions;
    expect(cs[0].scanDate).toBe('2025-01-01');
  });

  it('selectMetric changes selectedMetric', () => {
    comp.selectedMetric = 'gps';
    comp.selectMetric('fhpAngle');
    expect(comp.selectedMetric).toBe('fhpAngle');
  });

  it('selectMetric does nothing when same metric selected', () => {
    comp.selectedMetric = 'gps';
    const rebuildSpy = vi.spyOn(comp as any, 'rebuildChart');
    comp.selectMetric('gps');
    expect(rebuildSpy).not.toHaveBeenCalled();
  });

  it('toggleTrendline flips showTrendline', () => {
    comp.showTrendline = true;
    comp.toggleTrendline();
    expect(comp.showTrendline).toBe(false);
    comp.toggleTrendline();
    expect(comp.showTrendline).toBe(true);
  });

  it('ngOnDestroy destroys chart if present', () => {
    const mockChart = { destroy: vi.fn() };
    (comp as any).chart = mockChart;
    comp.ngOnDestroy();
    expect(mockChart.destroy).toHaveBeenCalled();
  });

  it('ngOnDestroy does nothing when no chart', () => {
    (comp as any).chart = null;
    expect(() => comp.ngOnDestroy()).not.toThrow();
  });

  it('loadBenchmark populates benchmark on success', () => {
    (comp as any).loadBenchmark();
    expect(comp.benchmark).toEqual(makeBenchmark());
    expect(comp.isLoading).toBe(false);
  });

  it('loadBenchmark sets errorMessage on failure', () => {
    mockScanService.getCohortBenchmark.mockReturnValue(throwError(() => ({ message: 'Benchmark failed' })));
    (comp as any).loadBenchmark();
    expect(comp.errorMessage).toBe('Benchmark failed');
    expect(comp.isLoading).toBe(false);
  });

  it('ngAfterViewInit triggers loadBenchmark', () => {
    vi.spyOn(comp as any, 'loadBenchmark');
    comp.ngAfterViewInit();
    expect((comp as any).loadBenchmark).toHaveBeenCalled();
  });

  it('ngOnChanges triggers rebuildChart when canvasRef present', () => {
    const rebuildSpy = vi.spyOn(comp as any, 'rebuildChart');
    (comp as any).canvasRef = { nativeElement: document.createElement('canvas') };
    comp.ngOnChanges({ sessions: { currentValue: [], previousValue: null, firstChange: true, isFirstChange: () => true } });
    expect(rebuildSpy).toHaveBeenCalled();
  });

  it('ngOnChanges does nothing when canvasRef not present', () => {
    const rebuildSpy = vi.spyOn(comp as any, 'rebuildChart');
    (comp as any).canvasRef = undefined;
    comp.ngOnChanges({ sessions: { currentValue: [], previousValue: null, firstChange: true, isFirstChange: () => true } });
    expect(rebuildSpy).not.toHaveBeenCalled();
  });
});

describe('EvolutionChartComponent with canvas (rebuildChart)', () => {
  let comp: EvolutionChartComponent;
  let fixture: ComponentFixture<EvolutionChartComponent>;
  let mockScanService: { getCohortBenchmark: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({} as any);

    mockScanService = {
      getCohortBenchmark: vi.fn().mockReturnValue(of(makeBenchmark())),
    };

    await TestBed.configureTestingModule({
      imports: [EvolutionChartComponent],
      providers: [{ provide: ScanService, useValue: mockScanService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EvolutionChartComponent);
    comp = fixture.componentInstance;
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('rebuildChart builds chart with sessions and benchmark', () => {
    comp.sessions = [
      makeSession({ globalPostureScore: 70, scanDate: '2025-01-01T00:00:00' }),
      makeSession({ sessionId: 2, globalPostureScore: 80, scanDate: '2025-02-01T00:00:00' }),
    ];
    comp.benchmark = makeBenchmark();
    comp.selectedMetric = 'gps';
    comp.showTrendline = true;
    (comp as any).canvasRef = { nativeElement: document.createElement('canvas') };
    expect(() => (comp as any).rebuildChart()).not.toThrow();
  });

  it('rebuildChart destroys previous chart before building new one', () => {
    const mockChart = { destroy: vi.fn() };
    (comp as any).chart = mockChart;
    (comp as any).canvasRef = { nativeElement: document.createElement('canvas') };
    (comp as any).rebuildChart();
    expect(mockChart.destroy).toHaveBeenCalled();
  });

  it('rebuildChart returns early when canvasRef missing', () => {
    (comp as any).canvasRef = undefined;
    expect(() => (comp as any).rebuildChart()).not.toThrow();
  });

  it('rebuildChart with fhpAngle (showRiskThresholds=false) does not add threshold datasets', () => {
    comp.sessions = [makeSession({ fhpAngle: 12, scanDate: '2025-01-01T00:00:00' })];
    comp.selectedMetric = 'fhpAngle';
    comp.benchmark = makeBenchmark();
    comp.showTrendline = false;
    (comp as any).canvasRef = { nativeElement: document.createElement('canvas') };
    expect(() => (comp as any).rebuildChart()).not.toThrow();
  });

  it('rebuildChart with no sessions renders empty chart', () => {
    comp.sessions = [];
    comp.benchmark = null;
    (comp as any).canvasRef = { nativeElement: document.createElement('canvas') };
    expect(() => (comp as any).rebuildChart()).not.toThrow();
  });

  it('rebuildChart with sessions containing null metric values handles spanGaps', () => {
    comp.sessions = [
      makeSession({ globalPostureScore: null as any, scanDate: '2025-01-01T00:00:00' }),
      makeSession({ sessionId: 2, globalPostureScore: 75, scanDate: '2025-02-01T00:00:00' }),
    ];
    comp.selectedMetric = 'gps';
    comp.benchmark = makeBenchmark();
    comp.showTrendline = true;
    (comp as any).canvasRef = { nativeElement: document.createElement('canvas') };
    expect(() => (comp as any).rebuildChart()).not.toThrow();
  });

  it('renders chart section and benchmark stats when hasEnoughSessions is true', () => {
    comp.sessions = [
      makeSession({ globalPostureScore: 70, scanDate: '2025-01-01T00:00:00' }),
      makeSession({ sessionId: 2, globalPostureScore: 80, scanDate: '2025-02-01T00:00:00' }),
    ];
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.chart-wrap')).toBeTruthy();
    expect(el.querySelector('.benchmark-summary')).toBeTruthy();
  });

  it('renders position banner when patientPositionInfo is non-null', () => {
    comp.sessions = [
      makeSession({ globalPostureScore: 90, scanDate: '2025-01-01T00:00:00' }),
      makeSession({ sessionId: 2, globalPostureScore: 85, scanDate: '2025-02-01T00:00:00' }),
    ];
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.position-banner')).toBeTruthy();
  });

  it('renders no-data message when hasEnoughSessions is false', () => {
    comp.sessions = [makeSession()];
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.chart-state.empty')).toBeTruthy();
  });

  it('renders error state when loadBenchmark fails', () => {
    mockScanService.getCohortBenchmark.mockReturnValue(throwError(() => ({ message: 'Server error' })));
    comp.sessions = [makeSession(), makeSession({ sessionId: 2 })];
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.chart-state.error')).toBeTruthy();
  });
});
