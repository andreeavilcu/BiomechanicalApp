import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ResearchService } from './research.service';
import { AggregateMetricsDTO, PostureTrendDTO } from '../models/research.model';

const mockMetrics: AggregateMetricsDTO = {
  totalSessions: 1000,
  averageGps: 72.5,
  averageFhpAngle: 13.2,
  averageQAngle: 15.1,
  stdDevGps: 8.4,
  p25Gps: 65,
  p75Gps: 82,
};

const mockTrends: PostureTrendDTO[] = [
  { date: '2024-01-01', averageGps: 70, sessionCount: 20 },
  { date: '2024-01-08', averageGps: 73, sessionCount: 25 },
];

describe('ResearchService', () => {
  let service: ResearchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ResearchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAggregateMetrics', () => {
    it('should GET /api/research/metrics/aggregate without params when no dates provided', () => {
      service.getAggregateMetrics().subscribe(m => expect(m).toEqual(mockMetrics));
      const req = httpMock.expectOne('/api/research/metrics/aggregate');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.has('from')).toBe(false);
      expect(req.request.params.has('to')).toBe(false);
      req.flush(mockMetrics);
    });

    it('should include from param when provided', () => {
      service.getAggregateMetrics('2024-01-01').subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/research/metrics/aggregate');
      expect(req.request.params.get('from')).toBe('2024-01-01');
      expect(req.request.params.has('to')).toBe(false);
      req.flush(mockMetrics);
    });

    it('should include both from and to params when both are provided', () => {
      service.getAggregateMetrics('2024-01-01', '2024-03-31').subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/research/metrics/aggregate');
      expect(req.request.params.get('from')).toBe('2024-01-01');
      expect(req.request.params.get('to')).toBe('2024-03-31');
      req.flush(mockMetrics);
    });
  });

  describe('getPostureTrends', () => {
    it('should GET /api/research/posture-trends with default lastDays=90', () => {
      service.getPostureTrends().subscribe(t => expect(t).toEqual(mockTrends));
      const req = httpMock.expectOne(r => r.url === '/api/research/posture-trends');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('lastDays')).toBe('90');
      req.flush(mockTrends);
    });

    it('should GET /api/research/posture-trends with custom lastDays', () => {
      service.getPostureTrends(30).subscribe(t => expect(t).toEqual(mockTrends));
      const req = httpMock.expectOne(r => r.url === '/api/research/posture-trends');
      expect(req.request.params.get('lastDays')).toBe('30');
      req.flush(mockTrends);
    });
  });

  describe('exportCsv', () => {
    it('should GET /api/research/export/csv with text response type', () => {
      const csv = 'date,averageGps\n2024-01-01,70';
      service.exportCsv().subscribe(data => expect(data).toBe(csv));
      const req = httpMock.expectOne('/api/research/export/csv');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('text');
      req.flush(csv);
    });
  });
});
