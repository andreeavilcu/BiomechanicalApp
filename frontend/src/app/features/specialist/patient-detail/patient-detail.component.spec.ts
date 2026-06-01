import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PatientDetailComponent } from './patient-detail.component';
import { SpecialistService } from '../../../core/services/specialist.service';
import { UserDTO, Gender } from '../../../core/models/user.model';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

const makePatient = (): UserDTO => ({
  id: 1, email: 'p@test.com', firstName: 'Ana', lastName: 'Pop',
  dateOfBirth: '1990-01-01', gender: Gender.FEMALE, heightCm: 165
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

describe('PatientDetailComponent', () => {
  let component: PatientDetailComponent;
  let fixture: ComponentFixture<PatientDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PatientDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('getInitials returns ? when no patient', () => {
    component.patient = null;
    expect(component.getInitials()).toBe('?');
  });

  it('getInitials returns uppercase initials when patient set', () => {
    component.patient = makePatient();
    expect(component.getInitials()).toBe('AP');
  });

  it('getRiskClass returns correct CSS class for each level', () => {
    expect(component.getRiskClass(RiskLevel.LOW)).toBe('risk-low');
    expect(component.getRiskClass(RiskLevel.MODERATE)).toBe('risk-moderate');
    expect(component.getRiskClass(RiskLevel.HIGH)).toBe('risk-high');
  });

  it('getRiskLabel returns correct label for each level', () => {
    expect(component.getRiskLabel(RiskLevel.LOW)).toBe('Low');
    expect(component.getRiskLabel(RiskLevel.MODERATE)).toBe('Moderate');
    expect(component.getRiskLabel(RiskLevel.HIGH)).toBe('High');
  });

  it('notesChanged is true when clinicalNotes differs from savedNotes', () => {
    component.clinicalNotes = 'new note';
    component.savedNotes = 'old note';
    expect(component.notesChanged).toBe(true);
  });

  it('notesChanged is false when clinicalNotes equals savedNotes', () => {
    component.clinicalNotes = 'same';
    component.savedNotes = 'same';
    expect(component.notesChanged).toBe(false);
  });

  it('getCompletedSessions filters to COMPLETED only', () => {
    component.sessions = [
      makeSession({ status: ProcessingStatus.COMPLETED }),
      makeSession({ status: ProcessingStatus.FAILED }),
    ];
    expect(component.getCompletedSessions().length).toBe(1);
  });

  it('getAverageGps returns null when no completed sessions', () => {
    component.sessions = [];
    expect(component.getAverageGps()).toBeNull();
  });

  it('getAverageGps computes average of completed session scores', () => {
    component.sessions = [
      makeSession({ globalPostureScore: 80, status: ProcessingStatus.COMPLETED }),
      makeSession({ globalPostureScore: 60, status: ProcessingStatus.COMPLETED }),
    ];
    expect(component.getAverageGps()).toBe(70);
  });

  it('getLatestRisk returns null when no completed sessions', () => {
    component.sessions = [];
    expect(component.getLatestRisk()).toBeNull();
  });

  it('getLatestRisk returns riskLevel of first completed session', () => {
    component.sessions = [
      makeSession({ riskLevel: RiskLevel.HIGH, status: ProcessingStatus.COMPLETED }),
    ];
    expect(component.getLatestRisk()).toBe(RiskLevel.HIGH);
  });

  it('formatDate returns formatted string', () => {
    const result = component.formatDate('2025-05-10T10:00:00');
    expect(result).toContain('2025');
  });

  it('computes patient detail summary values when patient and notes exist', () => {
    component.patient = makePatient();
    component.sessions = [makeSession({ status: ProcessingStatus.COMPLETED, globalPostureScore: 75, riskLevel: RiskLevel.MODERATE })];
    component.savedNotes = 'old';
    component.clinicalNotes = 'new';
    component.isLoadingPatient = false;
    component.isLoadingHistory = false;
    component.isLoadingNotes = false;

    expect(component.getInitials()).toBe('AP');
    expect(component.getCompletedSessions().length).toBe(1);
    expect(component.getAverageGps()).toBe(75);
    expect(component.notesChanged).toBe(true);
  });

  it('getRiskClass returns empty string for unknown risk', () => {
    expect(component.getRiskClass('UNKNOWN' as unknown as RiskLevel)).toBe('');
  });

  it('getRiskLabel returns em-dash for unknown risk', () => {
    expect(component.getRiskLabel('UNKNOWN' as unknown as RiskLevel)).toBe('—');
  });
});

describe('PatientDetailComponent with patientId route param', () => {
  let component: PatientDetailComponent;
  let fixture: ComponentFixture<PatientDetailComponent>;
  let mockSpecialistService: {
    getMyPatients: ReturnType<typeof vi.fn>;
    getPatientHistory: ReturnType<typeof vi.fn>;
    getClinicalNotes: ReturnType<typeof vi.fn>;
    saveClinicalNotes: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(async () => {
    mockSpecialistService = {
      getMyPatients: vi.fn().mockReturnValue(of([makePatient()])),
      getPatientHistory: vi.fn().mockReturnValue(of([makeSession()])),
      getClinicalNotes: vi.fn().mockReturnValue(of('initial notes')),
      saveClinicalNotes: vi.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [PatientDetailComponent],
      providers: [
        provideRouter([]),
        { provide: SpecialistService, useValue: mockSpecialistService },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: vi.fn().mockReturnValue('1') } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientDetailComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('ngOnInit loads patient, history and notes', () => {
    expect(mockSpecialistService.getMyPatients).toHaveBeenCalled();
    expect(mockSpecialistService.getPatientHistory).toHaveBeenCalledWith(1);
    expect(mockSpecialistService.getClinicalNotes).toHaveBeenCalledWith(1);
    expect(component.patient).toEqual(makePatient());
    expect(component.clinicalNotes).toBe('initial notes');
  });

  it('sets errorMessage when patient not found in list', () => {
    mockSpecialistService.getMyPatients.mockReturnValue(of([]));
    const f = TestBed.createComponent(PatientDetailComponent);
    f.detectChanges();
    expect(f.componentInstance.errorMessage).toBe('Patient not found or not assigned to you.');
  });

  it('sets isLoadingPatient to false on getMyPatients error', () => {
    mockSpecialistService.getMyPatients.mockReturnValue(throwError(() => new Error('fail')));
    const f = TestBed.createComponent(PatientDetailComponent);
    f.detectChanges();
    expect(f.componentInstance.isLoadingPatient).toBe(false);
  });

  it('sets errorMessage on getPatientHistory error', () => {
    mockSpecialistService.getPatientHistory.mockReturnValue(throwError(() => ({ message: 'History failed' })));
    const f = TestBed.createComponent(PatientDetailComponent);
    f.detectChanges();
    expect(f.componentInstance.errorMessage).toBe('History failed');
  });

  it('sets isLoadingNotes to false on getClinicalNotes error', () => {
    mockSpecialistService.getClinicalNotes.mockReturnValue(throwError(() => new Error('fail')));
    const f = TestBed.createComponent(PatientDetailComponent);
    f.detectChanges();
    expect(f.componentInstance.isLoadingNotes).toBe(false);
  });

  it('saveNotes saves and sets notesSaved', () => {
    component.clinicalNotes = 'updated';
    component.saveNotes();
    expect(mockSpecialistService.saveClinicalNotes).toHaveBeenCalledWith(1, 'updated');
    expect(component.savedNotes).toBe('updated');
    expect(component.notesSaved).toBe(true);
  });

  it('saveNotes sets notesError on failure', () => {
    mockSpecialistService.saveClinicalNotes.mockReturnValue(throwError(() => ({ message: 'Save failed' })));
    component.saveNotes();
    expect(component.notesError).toBe('Save failed');
    expect(component.isSavingNotes).toBe(false);
  });

  it('saveNotes does nothing when already saving', () => {
    component.isSavingNotes = true;
    component.saveNotes();
    expect(mockSpecialistService.saveClinicalNotes).not.toHaveBeenCalled();
  });

  it('viewSession navigates to /scans/:id', () => {
    component.viewSession(42);
    expect(router.navigate).toHaveBeenCalledWith(['/scans', 42]);
  });

  it('goBack navigates to /specialist/patients', () => {
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/specialist/patients']);
  });
});;
