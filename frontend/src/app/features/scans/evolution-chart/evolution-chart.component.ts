import {
  Component, ElementRef, Input, OnChanges, OnDestroy, AfterViewInit,
  SimpleChanges, ViewChild, inject, ChangeDetectorRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { ScanService } from '../../../core/services/scan.service';
import {
  AnalysisResultDTO,
  ProcessingStatus
} from '../../../core/models/scan.model';
import {
  BenchmarkMetric,
  CohortBenchmarkDTO,
  MetricStats,
  METRIC_CONFIGS
} from '../../../core/models/cohort-benchmark.model';

Chart.register(...registerables);

@Component({
  selector: 'app-evolution-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './evolution-chart.component.html',
  styleUrl: './evolution-chart.component.scss',
})
export class EvolutionChartComponent implements AfterViewInit, OnChanges, OnDestroy {

  @Input() sessions: AnalysisResultDTO[] = [];

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  private scanService = inject(ScanService);
  private cdr = inject(ChangeDetectorRef);

  private chart: Chart | null = null;
  benchmark: CohortBenchmarkDTO | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  selectedMetric: BenchmarkMetric = 'gps';
  showTrendline = true;

  readonly metrics: BenchmarkMetric[] = ['gps', 'fhpAngle', 'qAngle', 'shoulderAsymmetry'];
  readonly METRIC_CONFIGS = METRIC_CONFIGS;

  ngAfterViewInit(): void {
    this.loadBenchmark();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['sessions'] && this.canvasRef) {
      this.cdr.detectChanges();
      this.rebuildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private loadBenchmark(): void {
    this.isLoading = true;
    this.scanService.getCohortBenchmark().subscribe({
      next: (data) => {
        this.benchmark = data;
        this.isLoading = false;
        this.cdr.detectChanges();
        this.rebuildChart();
      },
      error: (err) => {
        this.errorMessage = err.message ?? 'Could not load benchmark data.';
        this.isLoading = false;
        this.cdr.detectChanges();
        this.rebuildChart();
      }
    });
  }

  selectMetric(metric: BenchmarkMetric): void {
    if (this.selectedMetric === metric) return;
    this.selectedMetric = metric;
    this.rebuildChart();
  }

  toggleTrendline(): void {
    this.showTrendline = !this.showTrendline;
    this.rebuildChart();
  }

  get currentConfig() {
    return METRIC_CONFIGS[this.selectedMetric];
  }

  get currentBenchmark(): MetricStats | null {
    if (!this.benchmark) return null;
    return this.benchmark[this.selectedMetric];
  }

  get hasEnoughSessions(): boolean {
    return this.completedSessions.length >= 2;
  }

  get completedSessions(): AnalysisResultDTO[] {
    return [...this.sessions]
      .filter(s => s.status === ProcessingStatus.COMPLETED)
      .sort((a, b) => new Date(a.scanDate).getTime() - new Date(b.scanDate).getTime());
  }

  private getMetricValue(s: AnalysisResultDTO): number | null {
    switch (this.selectedMetric) {
      case 'gps':
        return s.globalPostureScore ?? null;
      case 'fhpAngle':
        return s.fhpAngle ?? null;
      case 'qAngle':
        if (s.qAngleLeft != null && s.qAngleRight != null) {
          return (s.qAngleLeft + s.qAngleRight) / 2;
        }
        return null;
      case 'shoulderAsymmetry':
        return s.shoulderAsymmetryCm ?? null;
    }
  }

  private linearRegression(points: number[]): number[] {
    const n = points.length;
    if (n < 2) return points.slice();
    const xs = points.map((_, i) => i);
    const xMean = xs.reduce((a, b) => a + b, 0) / n;
    const yMean = points.reduce((a, b) => a + b, 0) / n;
    let num = 0, den = 0;
    for (let i = 0; i < n; i++) {
      num += (xs[i] - xMean) * (points[i] - yMean);
      den += (xs[i] - xMean) ** 2;
    }
    const slope = den === 0 ? 0 : num / den;
    const intercept = yMean - slope * xMean;
    return xs.map(x => slope * x + intercept);
  }

  private rebuildChart(): void {
    if (!this.canvasRef) return;

    if (this.chart) {
      this.chart.destroy();
      this.chart = null;
    }

    const ctx = this.canvasRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const sessions = this.completedSessions;
    const labels = sessions.map(s => this.formatDate(s.scanDate));
    const values: (number | null)[] = sessions.map(s => this.getMetricValue(s));

    const validValues = values.filter((v): v is number => v != null);
    const trendValues = validValues.length >= 2 ? this.linearRegression(validValues) : [];
    const trendline: (number | null)[] = [];
    let trendIdx = 0;
    for (const v of values) {
      if (v == null) {
        trendline.push(null);
      } else {
        trendline.push(trendValues[trendIdx] ?? null);
        trendIdx++;
      }
    }

    const cfg = this.currentConfig;
    const bm = this.currentBenchmark;

    const datasets: ChartConfiguration<'line'>['data']['datasets'] = [];

    if (bm && bm.p25 != null && bm.p75 != null) {
      datasets.push({
        label: 'Cohort P75',
        data: labels.map(() => bm.p75!),
        borderColor: 'rgba(56, 189, 248, 0.3)',
        backgroundColor: 'rgba(56, 189, 248, 0.08)',
        borderWidth: 1,
        borderDash: [3, 3],
        pointRadius: 0,
        tension: 0,
        fill: '+1',
      });
      datasets.push({
        label: 'Cohort P25',
        data: labels.map(() => bm.p25!),
        borderColor: 'rgba(56, 189, 248, 0.3)',
        backgroundColor: 'rgba(56, 189, 248, 0.0)',
        borderWidth: 1,
        borderDash: [3, 3],
        pointRadius: 0,
        tension: 0,
        fill: false,
      });
    }

    if (bm && bm.avg != null) {
      datasets.push({
        label: 'Cohort Average',
        data: labels.map(() => bm.avg!),
        borderColor: 'rgba(148, 163, 184, 0.55)',
        borderWidth: 1.5,
        borderDash: [6, 4],
        pointRadius: 0,
        tension: 0,
        fill: false,
      });
    }


    if (cfg.showRiskThresholds) {
      datasets.push({
        label: 'Moderate Risk (20%)',
        data: labels.map(() => 20),
        borderColor: 'rgba(234, 179, 8, 0.45)',
        borderWidth: 1.2,
        borderDash: [2, 4],
        pointRadius: 0,
        tension: 0,
        fill: false,
      });
      datasets.push({
        label: 'High Risk (50%)',
        data: labels.map(() => 50),
        borderColor: 'rgba(239, 68, 68, 0.5)',
        borderWidth: 1.2,
        borderDash: [2, 4],
        pointRadius: 0,
        tension: 0,
        fill: false,
      });
    }

    datasets.push({
      label: `Your ${cfg.shortLabel}`,
      data: values,
      borderColor: '#22c55e',
      backgroundColor: 'rgba(34, 197, 94, 0.12)',
      borderWidth: 2.5,
      pointRadius: 4,
      pointHoverRadius: 6,
      pointBackgroundColor: '#22c55e',
      pointBorderColor: '#0f172a',
      pointBorderWidth: 2,
      tension: 0.25,
      fill: false,
      spanGaps: true,
    });

    if (this.showTrendline && trendline.some(v => v != null)) {
      datasets.push({
        label: 'Trend',
        data: trendline,
        borderColor: 'rgba(34, 197, 94, 0.5)',
        borderWidth: 1.5,
        borderDash: [8, 4],
        pointRadius: 0,
        tension: 0,
        fill: false,
        spanGaps: true,
      });
    }

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: { labels, datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index',
          intersect: false,
        },
        plugins: {
          legend: {
            position: 'bottom',
            align: 'start',
            labels: {
              boxWidth: 12,
              boxHeight: 12,
              padding: 14,
              color: '#94a3b8',
              font: { size: 11 },
              filter: (item) => {
                return item.text !== 'Cohort P75';
              },
            },
          },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.95)',
            borderColor: 'rgba(148, 163, 184, 0.15)',
            borderWidth: 1,
            titleColor: '#e2e8f0',
            bodyColor: '#cbd5e1',
            padding: 10,
            displayColors: true,
            callbacks: {
              label: (ctx) => {
                const v = ctx.parsed.y;
                if (v == null) return `${ctx.dataset.label}: —`;
                return `${ctx.dataset.label}: ${v.toFixed(1)}${cfg.unit}`;
              }
            }
          },
        },
        scales: {
          y: {
            beginAtZero: cfg.suggestedMin === 0,
            suggestedMin: cfg.suggestedMin,
            suggestedMax: cfg.suggestedMax,
            grid: { color: 'rgba(148, 163, 184, 0.06)' },
            ticks: {
              color: '#64748b',
              font: { size: 10 },
              callback: (value) => `${value}${cfg.unit}`,
            },
            title: {
              display: true,
              text: `${cfg.label} (${cfg.unit})`,
              color: '#94a3b8',
              font: { size: 11, weight: 600 },
            },
          },
          x: {
            grid: { display: false },
            ticks: {
              color: '#64748b',
              font: { size: 10 },
              maxRotation: 0,
              autoSkip: true,
              maxTicksLimit: 8,
            },
          },
        },
      },
    };

    this.chart = new Chart(ctx, config);
  }

  private formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short'
    });
  }

  formatBenchmarkValue(v: number | null | undefined): string {
    if (v == null) return '—';
    return v.toFixed(1) + this.currentConfig.unit;
  }

  get patientPositionInfo(): { label: string; cssClass: string } | null {
    const bm = this.currentBenchmark;
    if (!bm || bm.p25 == null || bm.p75 == null) return null;

    const sessions = this.completedSessions;
    if (sessions.length === 0) return null;

    const latest = this.getMetricValue(sessions[sessions.length - 1]);
    if (latest == null) return null;

    const lowerIsBetter = this.currentConfig.lowerIsBetter;

    if (latest < bm.p25) {
      return lowerIsBetter
        ? { label: 'Better than 75% of users', cssClass: 'pos-good' }
        : { label: 'Below 25th percentile', cssClass: 'pos-bad' };
    }
    if (latest > bm.p75) {
      return lowerIsBetter
        ? { label: 'Above 75th percentile', cssClass: 'pos-bad' }
        : { label: 'Better than 75% of users', cssClass: 'pos-good' };
    }
    return { label: 'Within typical range (P25–P75)', cssClass: 'pos-neutral' };
  }
}