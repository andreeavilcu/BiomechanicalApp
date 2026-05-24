import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ScanDetailComponent } from './scan-detail.component';
import { ScanService } from '../../../core/services/scan.service';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel, RecommendationSeverity } from '../../../core/models/scan.model';

const makeResult = (overrides: Partial<AnalysisResultDTO> = {}): AnalysisResultDTO => ({
  sessionId: 1, scanDate: '2025-01-01T10:00:00', status: ProcessingStatus.COMPLETED,
  errorMessage: null, processingMethod: 'LIDAR', aiConfidenceScore: 0.9, scalingFactor: 1,
  qAngleLeft: 10, qAngleRight: 10, fhpAngle: 5, fhpDistanceCm: 2, shoulderAsymmetryCm: 1,
  stancePhaseLeft: 60, stancePhaseRight: 60, cadence: 100,
  globalPostureScore: 80, riskLevel: RiskLevel.LOW,
  recommendations: [], globalFeedback: '', medicalDisclaimer: false,
  evolution: null, keypoints: [], targetHeightMeters: null,
  ...overrides
});

describe('ScanDetailComponent', () => {
  let component: ScanDetailComponent;
  let fixture: ComponentFixture<ScanDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScanDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanDetailComponent);
    component = fixture.componentInstance;
    // detectChanges() omitted: ngOnInit would navigate away (no sessionId param)
    // and ngAfterViewInit of the embedded Viewer3dComponent would fail (no WebGL in jsdom)
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('keypoints returns empty array when result is null', () => {
    component.result = null;
    expect(component.keypoints).toEqual([]);
  });

  it('keypoints returns result.keypoints when set', () => {
    const kp = [{ name: 'head', x: 0, y: 0, z: 0 }];
    component.result = makeResult({ keypoints: kp });
    expect(component.keypoints).toEqual(kp);
  });

  it('riskColorClass returns empty string when no result', () => {
    component.result = null;
    expect(component.riskColorClass).toBe('');
  });

  it('riskColorClass returns correct CSS class for each risk level', () => {
    component.result = makeResult({ riskLevel: RiskLevel.LOW });
    expect(component.riskColorClass).toBe('risk-low');
    component.result = makeResult({ riskLevel: RiskLevel.MODERATE });
    expect(component.riskColorClass).toBe('risk-moderate');
    component.result = makeResult({ riskLevel: RiskLevel.HIGH });
    expect(component.riskColorClass).toBe('risk-high');
  });

  it('riskLabel returns empty string when no result', () => {
    component.result = null;
    expect(component.riskLabel).toBe('');
  });

  it('riskLabel returns correct label for each risk level', () => {
    component.result = makeResult({ riskLevel: RiskLevel.LOW });
    expect(component.riskLabel).toBe('Low risk');
    component.result = makeResult({ riskLevel: RiskLevel.MODERATE });
    expect(component.riskLabel).toBe('Moderate risk');
    component.result = makeResult({ riskLevel: RiskLevel.HIGH });
    expect(component.riskLabel).toBe('High risk');
  });

  it('getSeverityClass returns correct CSS class for each severity', () => {
    expect(component.getSeverityClass(RecommendationSeverity.LOW)).toBe('severity-low');
    expect(component.getSeverityClass(RecommendationSeverity.MODERATE)).toBe('severity-moderate');
    expect(component.getSeverityClass(RecommendationSeverity.HIGH)).toBe('severity-high');
  });

  it('trendIcon returns empty string when no result', () => {
    component.result = null;
    expect(component.trendIcon).toBe('');
  });

  it('trendIcon returns correct icon based on trend', () => {
    component.result = makeResult({ evolution: { trend: 'IMPROVEMENT', postureScoreChange: 5, daysSinceLastScan: 7 } });
    expect(component.trendIcon).toBe('↗');
    component.result = makeResult({ evolution: { trend: 'DETERIORATION', postureScoreChange: -3, daysSinceLastScan: 7 } });
    expect(component.trendIcon).toBe('↘');
    component.result = makeResult({ evolution: { trend: 'STABLE', postureScoreChange: 0, daysSinceLastScan: 7 } });
    expect(component.trendIcon).toBe('→');
  });

  it('trendLabel returns empty string when no result', () => {
    component.result = null;
    expect(component.trendLabel).toBe('');
  });

  it('trendLabel returns correct label for each trend', () => {
    component.result = makeResult({ evolution: { trend: 'IMPROVEMENT', postureScoreChange: 5, daysSinceLastScan: 7 } });
    expect(component.trendLabel).toBe('Improvement');
    component.result = makeResult({ evolution: { trend: 'FIRST_SESSION', postureScoreChange: 0, daysSinceLastScan: 0 } });
    expect(component.trendLabel).toBe('First session');
  });

  it('formatDate returns a formatted date string', () => {
    const result = component.formatDate('2025-05-10T10:00:00');
    expect(result).toContain('2025');
  });

  it('goBack navigates to scan history', () => {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/scans/history']);
  });

  it('deleteScan does nothing when result is null', () => {
    component.result = null;
    component.deleteScan();
    expect(component.errorMessage).toBeNull();
  });

  it('deleteScan calls deleteSession and navigates on confirm', () => {
    component.result = makeResult();
    const scanService = TestBed.inject(ScanService);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(scanService, 'deleteSession').mockReturnValue({ subscribe: ({ next }: any) => next() } as any);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.deleteScan();
    expect(scanService.deleteSession).toHaveBeenCalledWith(1);
    expect(router.navigate).toHaveBeenCalledWith(['/scans/history']);
  });

  it('computes risk display values for a completed result', () => {
    component.result = makeResult({ riskLevel: RiskLevel.MODERATE, evolution: { trend: 'IMPROVEMENT', postureScoreChange: 5, daysSinceLastScan: 7 } });
    expect(component.riskColorClass).toBe('risk-moderate');
    expect(component.riskLabel).toBe('Moderate risk');
    expect(component.trendIcon).toBe('↗');
    expect(component.trendLabel).toBe('Improvement');
  });
});
