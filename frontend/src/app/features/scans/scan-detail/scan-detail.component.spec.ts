import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ScanDetailComponent } from './scan-detail.component';
import { ScanService } from '../../../core/services/scan.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ToastService } from '../../../core/services/toast.service';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel, RecommendationSeverity } from '../../../core/models/scan.model';

const makeResult = (overrides: Partial<AnalysisResultDTO> = {}): AnalysisResultDTO => ({
  sessionId: 1, scanDate: '2025-01-01T10:00:00', status: ProcessingStatus.COMPLETED,
  errorMessage: null, processingMethod: 'LIDAR', aiConfidenceScore: 0.9, scalingFactor: 1,
  qAngleLeft: 10, qAngleRight: 10, fhpAngle: 5, fhpDistanceCm: 2, shoulderAsymmetryCm: 1,
  globalPostureScore: 80, riskLevel: RiskLevel.LOW,
  recommendations: [], globalFeedback: '', medicalDisclaimer: false,
  evolution: null, keypoints: [], targetHeightMeters: null,
  ...overrides
});

describe('ScanDetailComponent', () => {
  let component: ScanDetailComponent;
  let fixture: ComponentFixture<ScanDetailComponent>;
  let confirmService: ConfirmService;
  let toastService: ToastService;

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
    confirmService = TestBed.inject(ConfirmService);
    toastService = TestBed.inject(ToastService);
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

  it('deleteScan does nothing when result is null', async () => {
    component.result = null;
    await component.deleteScan();
    expect(component.errorMessage).toBeNull();
  });

  it('deleteScan calls deleteSession and shows success toast on confirm', async () => {
    component.result = makeResult();
    const scanService = TestBed.inject(ScanService);
    const router = TestBed.inject(Router);
    vi.spyOn(confirmService, 'open').mockResolvedValue(true);
    vi.spyOn(scanService, 'deleteSession').mockReturnValue(of(undefined));
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(toastService, 'success');

    await component.deleteScan();

    expect(scanService.deleteSession).toHaveBeenCalledWith(1);
    expect(toastService.success).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/scans/history']);
  });

  it('deleteScan does not call deleteSession when cancelled', async () => {
    component.result = makeResult();
    const scanService = TestBed.inject(ScanService);
    vi.spyOn(confirmService, 'open').mockResolvedValue(false);
    vi.spyOn(scanService, 'deleteSession');

    await component.deleteScan();

    expect(scanService.deleteSession).not.toHaveBeenCalled();
  });

  it('deleteScan shows error toast on delete failure', async () => {
    component.result = makeResult();
    const scanService = TestBed.inject(ScanService);
    vi.spyOn(confirmService, 'open').mockResolvedValue(true);
    vi.spyOn(scanService, 'deleteSession').mockReturnValue(throwError(() => ({ message: 'Delete failed' })));
    vi.spyOn(toastService, 'error');

    await component.deleteScan();

    expect(toastService.error).toHaveBeenCalledWith('Delete failed');
  });

  it('computes risk display values for a completed result', () => {
    component.result = makeResult({ riskLevel: RiskLevel.MODERATE, evolution: { trend: 'IMPROVEMENT', postureScoreChange: 5, daysSinceLastScan: 7 } });
    expect(component.riskColorClass).toBe('risk-moderate');
    expect(component.riskLabel).toBe('Moderate risk');
    expect(component.trendIcon).toBe('↗');
    expect(component.trendLabel).toBe('Improvement');
  });

  it('riskColorClass returns empty string for unknown risk level', () => {
    component.result = makeResult({ riskLevel: 'UNKNOWN' as unknown as RiskLevel });
    expect(component.riskColorClass).toBe('');
  });

  it('riskLabel returns empty string for unknown risk level', () => {
    component.result = makeResult({ riskLevel: 'UNKNOWN' as unknown as RiskLevel });
    expect(component.riskLabel).toBe('');
  });

  it('getSeverityClass returns empty string for unknown severity', () => {
    expect(component.getSeverityClass('UNKNOWN' as unknown as RecommendationSeverity)).toBe('');
  });

  it('trendIcon returns bullet for unknown trend', () => {
    component.result = makeResult({ evolution: { trend: 'UNKNOWN' as 'STABLE', postureScoreChange: 0, daysSinceLastScan: 0 } });
    expect(component.trendIcon).toBe('●');
  });

  it('trendLabel returns DETERIORATION and STABLE labels', () => {
    component.result = makeResult({ evolution: { trend: 'DETERIORATION', postureScoreChange: -3, daysSinceLastScan: 7 } });
    expect(component.trendLabel).toBe('Deterioration');
    component.result = makeResult({ evolution: { trend: 'STABLE', postureScoreChange: 0, daysSinceLastScan: 7 } });
    expect(component.trendLabel).toBe('Stable');
  });

  it('trendLabel returns empty string for unknown trend', () => {
    component.result = makeResult({ evolution: { trend: 'UNKNOWN' as 'STABLE', postureScoreChange: 0, daysSinceLastScan: 0 } });
    expect(component.trendLabel).toBe('');
  });
});

describe('ScanDetailComponent with sessionId route param', () => {
  let component: ScanDetailComponent;
  let fixture: ComponentFixture<ScanDetailComponent>;
  let mockScanService: { getSession: ReturnType<typeof vi.fn>; deleteSession: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockScanService = {
      getSession: vi.fn().mockReturnValue(of(makeResult())),
      deleteSession: vi.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [ScanDetailComponent],
      providers: [
        provideRouter([]),
        { provide: ScanService, useValue: mockScanService },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: vi.fn().mockReturnValue('1') } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanDetailComponent);
    component = fixture.componentInstance;
  });

  it('ngOnInit loads session when sessionId present', () => {
    component.ngOnInit();
    expect(mockScanService.getSession).toHaveBeenCalledWith(1);
    expect(component.result).toBeTruthy();
    expect(component.isLoading).toBe(false);
  });

  it('ngOnInit sets errorMessage when getSession fails', () => {
    mockScanService.getSession.mockReturnValue(throwError(() => ({ message: 'Not found' })));
    component.ngOnInit();
    expect(component.errorMessage).toBe('Not found');
    expect(component.isLoading).toBe(false);
  });
});
