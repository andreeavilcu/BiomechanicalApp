import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ScanService } from './scan.service';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../models/scan.model';
import { CohortBenchmarkDTO } from '../models/cohort-benchmark.model';

const mockSession: AnalysisResultDTO = {
  sessionId: 42,
  scanDate: '2024-01-15T10:00:00',
  status: ProcessingStatus.COMPLETED,
  errorMessage: null,
  processingMethod: 'LIDAR',
  aiConfidenceScore: 0.95,
  scalingFactor: 1.0,
  qAngleLeft: 14.5,
  qAngleRight: 15.2,
  fhpAngle: 12.3,
  fhpDistanceCm: 3.2,
  shoulderAsymmetryCm: 0.8,
  globalPostureScore: 72,
  riskLevel: RiskLevel.LOW,
  recommendations: [],
  globalFeedback: 'Good posture',
  medicalDisclaimer: false,
  evolution: null,
  keypoints: [],
  targetHeightMeters: 1.75,
};

const mockBenchmark: CohortBenchmarkDTO = {
  gps: { p25: 60, p50: 72, p75: 85, avg: 71 },
  fhpAngle: { p25: 8, p50: 12, p75: 18, avg: 12.5 },
  qAngle: { p25: 12, p50: 15, p75: 18, avg: 15 },
  shoulderAsymmetry: { p25: 0.3, p50: 0.8, p75: 1.5, avg: 0.9 },
  totalSessions: 500,
};

describe('ScanService', () => {
  let service: ScanService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ScanService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('uploadScan', () => {
    it('should POST to /api/scans/upload with FormData and reportProgress enabled', () => {
      const file = new File(['data'], 'scan.ply', { type: 'application/octet-stream' });
      service.uploadScan(file, 1, 175).subscribe();

      const req = httpMock.expectOne('/api/scans/upload');
      expect(req.request.method).toBe('POST');
      expect(req.request.reportProgress).toBe(true);

      const body = req.request.body as FormData;
      expect(body.get('userId')).toBe('1');
      expect(body.get('heightCm')).toBe('175');
      expect(body.get('scanType')).toBe('LIDAR');
      expect(body.get('file')).toBe(file);

      req.flush(mockSession);
    });

    it('should use the provided scanType when supplied', () => {
      const file = new File(['data'], 'scan.ply');
      service.uploadScan(file, 2, 180, 'RGB').subscribe();

      const req = httpMock.expectOne('/api/scans/upload');
      expect((req.request.body as FormData).get('scanType')).toBe('RGB');
      req.flush(mockSession);
    });
  });

  describe('getMyHistory', () => {
    it('should GET /api/scans/my-history', () => {
      service.getMyHistory().subscribe(history => expect(history).toEqual([mockSession]));
      const req = httpMock.expectOne('/api/scans/my-history');
      expect(req.request.method).toBe('GET');
      req.flush([mockSession]);
    });
  });

  describe('getSession', () => {
    it('should GET /api/scans/:sessionId', () => {
      service.getSession(42).subscribe(session => expect(session).toEqual(mockSession));
      const req = httpMock.expectOne('/api/scans/42');
      expect(req.request.method).toBe('GET');
      req.flush(mockSession);
    });
  });

  describe('getUserHistory', () => {
    it('should GET /api/scans/user/:userId/history', () => {
      service.getUserHistory(7).subscribe(history => expect(history).toEqual([mockSession]));
      const req = httpMock.expectOne('/api/scans/user/7/history');
      expect(req.request.method).toBe('GET');
      req.flush([mockSession]);
    });
  });

  describe('deleteSession', () => {
    it('should DELETE /api/scans/:sessionId', () => {
      service.deleteSession(42).subscribe();
      const req = httpMock.expectOne('/api/scans/42');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('getPointCloud', () => {
    it('should GET /api/scans/:sessionId/point-cloud with arraybuffer response type', () => {
      service.getPointCloud(42).subscribe();
      const req = httpMock.expectOne('/api/scans/42/point-cloud');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('arraybuffer');
      req.flush(new ArrayBuffer(8));
    });
  });

  describe('getCohortBenchmark', () => {
    it('should GET /api/scans/cohort-benchmark', () => {
      service.getCohortBenchmark().subscribe(b => expect(b).toEqual(mockBenchmark));
      const req = httpMock.expectOne('/api/scans/cohort-benchmark');
      expect(req.request.method).toBe('GET');
      req.flush(mockBenchmark);
    });
  });
});
