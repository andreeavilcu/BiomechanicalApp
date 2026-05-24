import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { DashboardHomeComponent } from './dashboard-home.component';
import { AuthService } from '../../../core/services/auth.service';
import { ScanService } from '../../../core/services/scan.service';
import { AuthResponse } from '../../../core/models/auth.model';
import { UserRole } from '../../../core/models/user.model';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

const makeUser = (role: UserRole): AuthResponse => ({
  accessToken: 'tok', refreshToken: 'rt', role,
  userId: 1, email: 'a@b.com', firstName: 'Ana', lastName: 'Pop'
});

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

describe('DashboardHomeComponent', () => {
  let component: DashboardHomeComponent;
  let fixture: ComponentFixture<DashboardHomeComponent>;
  let mockAuthService: { getCurrentUser: ReturnType<typeof vi.fn>; hasRole: ReturnType<typeof vi.fn> };
  let mockScanService: { getMyHistory: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    mockAuthService = {
      getCurrentUser: vi.fn().mockReturnValue(makeUser(UserRole.PATIENT)),
      hasRole: vi.fn().mockImplementation((role: UserRole) => role === UserRole.PATIENT),
    };
    mockScanService = { getMyHistory: vi.fn().mockReturnValue(of([])) };

    await TestBed.configureTestingModule({
      imports: [DashboardHomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
        { provide: ScanService, useValue: mockScanService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardHomeComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads sessions for patient on init', () => {
    expect(mockScanService.getMyHistory).toHaveBeenCalled();
  });

  it('latestSession returns first session', () => {
    component.sessions = [makeSession({ sessionId: 10 }), makeSession({ sessionId: 20 })];
    expect(component.latestSession?.sessionId).toBe(10);
  });

  it('latestSession returns null when sessions empty', () => {
    component.sessions = [];
    expect(component.latestSession).toBeNull();
  });

  it('gpsScore returns globalPostureScore from latest session', () => {
    component.sessions = [makeSession({ globalPostureScore: 75 })];
    expect(component.gpsScore).toBe(75);
  });

  it('riskClass returns correct CSS classes for each risk level', () => {
    component.sessions = [makeSession({ riskLevel: RiskLevel.LOW })];
    expect(component.riskClass).toBe('risk-low');
    component.sessions = [makeSession({ riskLevel: RiskLevel.MODERATE })];
    expect(component.riskClass).toBe('risk-moderate');
    component.sessions = [makeSession({ riskLevel: RiskLevel.HIGH })];
    expect(component.riskClass).toBe('risk-high');
  });

  it('riskLabel returns correct labels', () => {
    component.sessions = [makeSession({ riskLevel: RiskLevel.LOW })];
    expect(component.riskLabel).toBe('Low risk');
    component.sessions = [makeSession({ riskLevel: RiskLevel.HIGH })];
    expect(component.riskLabel).toBe('High risk');
  });

  it('trendIcon returns correct icon based on trend', () => {
    component.sessions = [makeSession({ evolution: { trend: 'IMPROVEMENT', postureScoreChange: 5, daysSinceLastScan: 7 } })];
    expect(component.trendIcon).toBe('↗');
    component.sessions = [makeSession({ evolution: { trend: 'DETERIORATION', postureScoreChange: -3, daysSinceLastScan: 7 } })];
    expect(component.trendIcon).toBe('↘');
    component.sessions = [makeSession({ evolution: { trend: 'STABLE', postureScoreChange: 0, daysSinceLastScan: 7 } })];
    expect(component.trendIcon).toBe('→');
  });

  it('gpsCircleDashoffset is 0 when score is 100', () => {
    component.sessions = [makeSession({ globalPostureScore: 100 })];
    expect(component.gpsCircleDashoffset).toBe(0);
  });

  it('gpsCircleDashoffset is full circumference when score is 0', () => {
    component.sessions = [];
    expect(component.gpsCircleDashoffset).toBe(282.7);
  });

  it('getRiskClass returns correct class for each level', () => {
    expect(component.getRiskClass(RiskLevel.LOW)).toBe('risk-low');
    expect(component.getRiskClass(RiskLevel.MODERATE)).toBe('risk-moderate');
    expect(component.getRiskClass(RiskLevel.HIGH)).toBe('risk-high');
  });

  it('getRiskLabel returns correct label for each level', () => {
    expect(component.getRiskLabel(RiskLevel.LOW)).toBe('Low');
    expect(component.getRiskLabel(RiskLevel.MODERATE)).toBe('Moderate');
    expect(component.getRiskLabel(RiskLevel.HIGH)).toBe('High');
  });

  it('formatDate formats date string', () => {
    const result = component.formatDate('2025-05-10T00:00:00');
    expect(result).toContain('2025');
  });

  it('viewSession navigates to /scans/:id', () => {
    component.viewSession(42);
    expect(router.navigate).toHaveBeenCalledWith(['/scans', 42]);
  });

  it('sets errorMessage when getMyHistory fails', () => {
    mockScanService.getMyHistory.mockReturnValue(throwError(() => ({ message: 'Network error' })));
    const f2 = TestBed.createComponent(DashboardHomeComponent);
    f2.detectChanges();
    expect(f2.componentInstance.errorMessage).toBe('Network error');
  });

  it('reports total sessions when multiple sessions exist', () => {
    const localFixture = TestBed.createComponent(DashboardHomeComponent);
    const localComponent = localFixture.componentInstance;
    localComponent.currentUser = makeUser(UserRole.PATIENT);
    localComponent.sessions = [makeSession({ sessionId: 1 }), makeSession({ sessionId: 2 })];
    localComponent.isLoading = false;

    expect(localComponent.totalSessions).toBe(2);
    expect(localComponent.latestSession?.sessionId).toBe(1);
  });

  it('does not load sessions for non-patient users', () => {
    mockAuthService.hasRole.mockReturnValue(false);
    mockScanService.getMyHistory.mockClear();
    const localFixture = TestBed.createComponent(DashboardHomeComponent);
    const localComponent = localFixture.componentInstance;
    localComponent.ngOnInit();

    expect(mockScanService.getMyHistory).not.toHaveBeenCalled();
    expect(localComponent.isLoading).toBe(false);
  });
});
