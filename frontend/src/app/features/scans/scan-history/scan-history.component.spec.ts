import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { ScanHistoryComponent } from './scan-history.component';
import { ScanService } from '../../../core/services/scan.service';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

const makeSession = (overrides: Partial<AnalysisResultDTO> = {}): AnalysisResultDTO => ({
  sessionId: 1, scanDate: '2025-01-01T10:00:00', status: ProcessingStatus.COMPLETED,
  errorMessage: null, processingMethod: 'LIDAR', aiConfidenceScore: 0.9, scalingFactor: 1,
  qAngleLeft: 10, qAngleRight: 10, fhpAngle: 5, fhpDistanceCm: 2, shoulderAsymmetryCm: 1,
  stancePhaseLeft: 60, stancePhaseRight: 60, cadence: 100,
  globalPostureScore: 80, riskLevel: RiskLevel.LOW,
  recommendations: [], globalFeedback: '', medicalDisclaimer: false,
  evolution: null, keypoints: [], targetHeightMeters: null,
  ...overrides
});

describe('ScanHistoryComponent', () => {
  let component: ScanHistoryComponent;
  let fixture: ComponentFixture<ScanHistoryComponent>;
  let mockScanService: {
    getMyHistory: ReturnType<typeof vi.fn>;
    getCohortBenchmark: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(async () => {
    mockScanService = {
      getMyHistory: vi.fn().mockReturnValue(of([])),
      // Required by embedded EvolutionChartComponent's ngAfterViewInit
      getCohortBenchmark: vi.fn().mockReturnValue(of(null)),
    };

    await TestBed.configureTestingModule({
      imports: [ScanHistoryComponent],
      providers: [
        provideRouter([]),
        { provide: ScanService, useValue: mockScanService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanHistoryComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    // detectChanges called per-test to control timing
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads history on init and sets isLoading to false', () => {
    mockScanService.getMyHistory.mockReturnValue(of([makeSession()]));
    fixture.detectChanges();
    expect(component.sessions.length).toBe(1);
    expect(component.isLoading).toBe(false);
  });

  it('completedSessionsCount returns count of COMPLETED sessions', () => {
    component.sessions = [
      makeSession({ status: ProcessingStatus.COMPLETED }),
      makeSession({ status: ProcessingStatus.FAILED }),
      makeSession({ status: ProcessingStatus.COMPLETED }),
    ];
    expect(component.completedSessionsCount).toBe(2);
  });

  it('completedSessionsCount is 0 with empty sessions', () => {
    component.sessions = [];
    expect(component.completedSessionsCount).toBe(0);
  });

  it('viewSession navigates to /scans/:id', () => {
    component.viewSession(5);
    expect(router.navigate).toHaveBeenCalledWith(['/scans', 5]);
  });

  it('newScan navigates to /scans/upload', () => {
    component.newScan();
    expect(router.navigate).toHaveBeenCalledWith(['/scans/upload']);
  });

  it('getRiskClass returns correct CSS classes', () => {
    expect(component.getRiskClass(RiskLevel.LOW)).toBe('risk-low');
    expect(component.getRiskClass(RiskLevel.MODERATE)).toBe('risk-moderate');
    expect(component.getRiskClass(RiskLevel.HIGH)).toBe('risk-high');
  });

  it('getRiskLabel returns correct labels', () => {
    expect(component.getRiskLabel(RiskLevel.LOW)).toBe('Low');
    expect(component.getRiskLabel(RiskLevel.MODERATE)).toBe('Moderate');
    expect(component.getRiskLabel(RiskLevel.HIGH)).toBe('High');
  });

  it('getStatusLabel returns correct labels', () => {
    expect(component.getStatusLabel(ProcessingStatus.PENDING)).toBe('Pending');
    expect(component.getStatusLabel(ProcessingStatus.PROCESSING)).toBe('Processing');
    expect(component.getStatusLabel(ProcessingStatus.COMPLETED)).toBe('Completed');
    expect(component.getStatusLabel(ProcessingStatus.FAILED)).toBe('Failed');
  });

  it('getStatusClass returns correct CSS classes', () => {
    expect(component.getStatusClass(ProcessingStatus.COMPLETED)).toBe('status-completed');
    expect(component.getStatusClass(ProcessingStatus.FAILED)).toBe('status-failed');
    expect(component.getStatusClass(ProcessingStatus.PROCESSING)).toBe('status-processing');
    expect(component.getStatusClass(ProcessingStatus.PENDING)).toBe('status-pending');
  });

  it('formatDate returns a formatted date string containing the year', () => {
    expect(component.formatDate('2025-05-10T10:00:00')).toContain('2025');
  });

  it('sets errorMessage and isLoading=false when getMyHistory fails', () => {
    mockScanService.getMyHistory.mockReturnValue(throwError(() => ({ message: 'Fetch failed' })));
    fixture.detectChanges();
    expect(component.errorMessage).toBe('Fetch failed');
    expect(component.isLoading).toBe(false);
  });

  it('renders loading spinner when isLoading is true', () => {
    component.isLoading = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.loading-state')).toBeTruthy();
  });

  it('renders error-state when errorMessage is set', () => {
    component.isLoading = false;
    component.errorMessage = 'Fetch failed';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.error-state')).toBeTruthy();
  });

  it('renders empty-state when sessions is empty', () => {
    mockScanService.getMyHistory.mockReturnValue(of([]));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state')).toBeTruthy();
  });

  it('renders history-table with rows when sessions are loaded', () => {
    mockScanService.getMyHistory.mockReturnValue(of([makeSession(), makeSession({ sessionId: 2 })]));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.history-table')).toBeTruthy();
    expect(el.querySelectorAll('.table-row').length).toBe(2);
  });

  it('renders evolution-chart when completedSessionsCount >= 1', () => {
    mockScanService.getMyHistory.mockReturnValue(of([makeSession()]));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-evolution-chart')).toBeTruthy();
  });
});
