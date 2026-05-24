import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AggregateMetricsComponent } from './aggregate-metrics.component';
import { ResearchService } from '../../../core/services/research.service';
import { AggregateMetricsDTO, PostureTrendDTO } from '../../../core/models/research.model';

const mockMetrics: AggregateMetricsDTO = {
  totalSessions: 100, averageGps: 72.5, averageFhpAngle: 15,
  averageQAngle: 12, stdDevGps: 8, p25Gps: 65, p75Gps: 80
};

const mockTrends: PostureTrendDTO[] = [
  { date: '2025-01-01', averageGps: 70, sessionCount: 5 },
  { date: '2025-01-08', averageGps: 75, sessionCount: 8 },
];

describe('AggregateMetricsComponent', () => {
  let component: AggregateMetricsComponent;
  let fixture: ComponentFixture<AggregateMetricsComponent>;
  let mockResearchService: {
    getAggregateMetrics: ReturnType<typeof vi.fn>;
    getPostureTrends: ReturnType<typeof vi.fn>;
    exportCsv: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockResearchService = {
      getAggregateMetrics: vi.fn().mockReturnValue(of(mockMetrics)),
      getPostureTrends: vi.fn().mockReturnValue(of(mockTrends)),
      exportCsv: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AggregateMetricsComponent],
      providers: [{ provide: ResearchService, useValue: mockResearchService }],
    }).compileComponents();

    fixture = TestBed.createComponent(AggregateMetricsComponent);
    component = fixture.componentInstance;
    // detectChanges() omitted to prevent ngAfterViewInit from running Chart.js canvas init
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loadMetrics populates metrics from service', () => {
    component.loadMetrics();
    expect(component.metrics).toEqual(mockMetrics);
    expect(component.isLoadingMetrics).toBe(false);
  });

  it('loadMetrics sets errorMetrics on failure', () => {
    mockResearchService.getAggregateMetrics.mockReturnValue(throwError(() => new Error('fail')));
    component.loadMetrics();
    expect(component.errorMetrics).toBe('Failed to load aggregate metrics.');
    expect(component.isLoadingMetrics).toBe(false);
  });

  it('loadTrends populates trends from service', () => {
    component.loadTrends();
    expect(component.trends).toEqual(mockTrends);
    expect(component.isLoadingTrends).toBe(false);
  });

  it('loadTrends sets errorTrends on failure', () => {
    mockResearchService.getPostureTrends.mockReturnValue(throwError(() => new Error('fail')));
    component.loadTrends();
    expect(component.errorTrends).toBe('Failed to load posture trends.');
    expect(component.isLoadingTrends).toBe(false);
  });

  it('onPeriodChange updates selectedPeriod and reloads trends', () => {
    component.onPeriodChange(30);
    expect(component.selectedPeriod).toBe(30);
    expect(mockResearchService.getPostureTrends).toHaveBeenCalledWith(30);
  });

  it('getRiskClass returns risk-high when gps <= 30', () => {
    expect(component.getRiskClass(30)).toBe('risk-high');
    expect(component.getRiskClass(0)).toBe('risk-high');
  });

  it('getRiskClass returns risk-moderate when gps <= 60', () => {
    expect(component.getRiskClass(60)).toBe('risk-moderate');
    expect(component.getRiskClass(45)).toBe('risk-moderate');
  });

  it('getRiskClass returns risk-low when gps > 60', () => {
    expect(component.getRiskClass(61)).toBe('risk-low');
    expect(component.getRiskClass(100)).toBe('risk-low');
  });

  it('getRiskLabel returns correct labels', () => {
    expect(component.getRiskLabel(25)).toBe('High Risk');
    expect(component.getRiskLabel(50)).toBe('Moderate Risk');
    expect(component.getRiskLabel(80)).toBe('Low Risk');
  });

  it('formatNum returns "—" for null', () => {
    expect(component.formatNum(null)).toBe('—');
    expect(component.formatNum(undefined)).toBe('—');
  });

  it('formatNum formats number with default 1 decimal', () => {
    expect(component.formatNum(72.567)).toBe('72.6');
  });

  it('formatNum formats number with specified decimals', () => {
    expect(component.formatNum(72.567, 2)).toBe('72.57');
    expect(component.formatNum(72, 0)).toBe('72');
  });

  it('exportCsv calls researchService.exportCsv and sets isExporting', () => {
    const csvContent = 'col1,col2\n1,2';
    mockResearchService.exportCsv.mockReturnValue(of(csvContent));
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    component.exportCsv();
    expect(mockResearchService.exportCsv).toHaveBeenCalled();
    expect(component.isExporting).toBe(false);
  });

  it('renders template sections when metrics and trends present', () => {
    vi.spyOn(component, 'ngAfterViewInit').mockImplementation(() => {});
    component.metrics = mockMetrics as any;
    component.trends = mockTrends as any;
    component.isLoadingMetrics = false;
    component.isLoadingTrends = false;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.kpi-grid')).toBeTruthy();
    expect(el.querySelector('.chart-card')).toBeTruthy();
  });
});
