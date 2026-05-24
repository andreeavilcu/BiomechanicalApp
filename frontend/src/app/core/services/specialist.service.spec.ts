import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SpecialistService } from './specialist.service';
import { UserDTO, UserRole, Gender } from '../models/user.model';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../models/scan.model';

const mockPatient: UserDTO = {
  id: 10,
  email: 'patient@example.com',
  firstName: 'Jane',
  lastName: 'Patient',
  dateOfBirth: '1995-03-15',
  gender: Gender.FEMALE,
  heightCm: 162,
  role: UserRole.PATIENT,
  isActive: true,
};

const mockSession: AnalysisResultDTO = {
  sessionId: 99,
  scanDate: '2024-02-01T09:00:00',
  status: ProcessingStatus.COMPLETED,
  errorMessage: null,
  processingMethod: 'LIDAR',
  aiConfidenceScore: 0.9,
  scalingFactor: 1.0,
  qAngleLeft: 13,
  qAngleRight: 14,
  fhpAngle: 10,
  fhpDistanceCm: 2.5,
  shoulderAsymmetryCm: 0.5,
  stancePhaseLeft: 0.6,
  stancePhaseRight: 0.61,
  cadence: 115,
  globalPostureScore: 80,
  riskLevel: RiskLevel.LOW,
  recommendations: [],
  globalFeedback: 'Excellent posture',
  medicalDisclaimer: false,
  evolution: null,
  keypoints: [],
  targetHeightMeters: 1.62,
};

describe('SpecialistService', () => {
  let service: SpecialistService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SpecialistService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getMyPatients', () => {
    it('should GET /api/patients/my-patients', () => {
      service.getMyPatients().subscribe(patients => expect(patients).toEqual([mockPatient]));
      const req = httpMock.expectOne('/api/patients/my-patients');
      expect(req.request.method).toBe('GET');
      req.flush([mockPatient]);
    });
  });

  describe('getPatientHistory', () => {
    it('should GET /api/patients/:patientId/history', () => {
      service.getPatientHistory(10).subscribe(h => expect(h).toEqual([mockSession]));
      const req = httpMock.expectOne('/api/patients/10/history');
      expect(req.request.method).toBe('GET');
      req.flush([mockSession]);
    });
  });

  describe('getPatientReport', () => {
    it('should GET /api/patients/:patientId/reports/:sessionId', () => {
      service.getPatientReport(10, 99).subscribe(r => expect(r).toEqual(mockSession));
      const req = httpMock.expectOne('/api/patients/10/reports/99');
      expect(req.request.method).toBe('GET');
      req.flush(mockSession);
    });
  });

  describe('getClinicalNotes', () => {
    it('should GET /api/patients/:patientId/notes with text response type', () => {
      const notes = 'Patient shows improvement in posture alignment.';
      service.getClinicalNotes(10).subscribe(n => expect(n).toBe(notes));
      const req = httpMock.expectOne('/api/patients/10/notes');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('text');
      req.flush(notes);
    });
  });

  describe('saveClinicalNotes', () => {
    it('should PUT /api/patients/:patientId/notes with notes body and json Content-Type', () => {
      service.saveClinicalNotes(10, 'Updated notes').subscribe();
      const req = httpMock.expectOne('/api/patients/10/notes');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toBe('Updated notes');
      expect(req.request.headers.get('Content-Type')).toBe('application/json');
      req.flush(null);
    });
  });

  describe('assignPatient', () => {
    it('should POST /api/patients/assign with patientEmail query param', () => {
      service.assignPatient('patient@example.com').subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/patients/assign');
      expect(req.request.method).toBe('POST');
      expect(req.request.params.get('patientEmail')).toBe('patient@example.com');
      expect(req.request.params.has('referralReason')).toBe(false);
      req.flush(null);
    });

    it('should include referralReason param when provided', () => {
      service.assignPatient('patient@example.com', 'Chronic back pain').subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/patients/assign');
      expect(req.request.params.get('patientEmail')).toBe('patient@example.com');
      expect(req.request.params.get('referralReason')).toBe('Chronic back pain');
      req.flush(null);
    });
  });
});
