import { Component, OnInit, inject, AfterViewInit, ElementRef, ViewChild, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ResearchService } from '../../../core/services/research.service';
import { AggregateMetricsDTO, PostureTrendDTO } from '../../../core/models/research.model';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables); 

type Period = 30 | 60 | 90 | 180; 

@Component({
  selector: 'app-aggregate-metrics',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './aggregate-metrics.component.html',
  styleUrl: './aggregate-metrics.component.scss',
})
export class AggregateMetricsComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('trendCanvas') trendCanvas!: ElementRef<HTMLCanvasElement>;

  private researchService = inject(ResearchService);
  private cdr = inject(ChangeDetectorRef);

  metrics: AggregateMetricsDTO | null = null;
  trends: PostureTrendDTO[] = [];
 
  isLoadingMetrics = true;
  isLoadingTrends = true;
  isExporting = false;
 
  errorMetrics: string | null = null;
  errorTrends: string | null = null;
 
  selectedPeriod: Period = 90;
  readonly periods: Period[] = [30, 60, 90, 180];
 
  private chart: Chart | null = null;
  private viewReady = false;
  private trendsReady = false;

  ngOnInit(): void {
    this.loadMetrics();
    this.loadTrends();
  }
 
  ngAfterViewInit(): void {
    this.viewReady = true;
    if (this.trendsReady) {
      this.buildChart();
    }
  }
 
  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  loadMetrics(): void {
    this.isLoadingMetrics = true;
    this.errorMetrics = null;
    this.researchService.getAggregateMetrics().subscribe({
      next: (data) => {
        this.metrics = data;
        this.isLoadingMetrics = false;
      },
      error: () => {
        this.errorMetrics = 'Failed to load aggregate metrics.';
        this.isLoadingMetrics = false;
      }
    });
  }

  loadTrends(): void {
  this.isLoadingTrends = true;
  this.errorTrends = null;
  this.chart?.destroy();
  this.researchService.getPostureTrends(this.selectedPeriod).subscribe({
    next: (data) => {
      this.trends = data;
      this.isLoadingTrends = false;
      this.trendsReady = true;
      this.cdr.detectChanges();
      if (this.viewReady) {
        this.buildChart();
      }
    },
    error: () => {
      this.errorTrends = 'Failed to load posture trends.';
      this.isLoadingTrends = false;
    }
  });
}

  onPeriodChange(period: Period): void {
    this.selectedPeriod = period;
    this.loadTrends();
  }

  private buildChart(): void {
    if (!this.trendCanvas?.nativeElement || this.trends.length === 0) return;
 
    this.chart?.destroy();
 
    const ctx = this.trendCanvas.nativeElement.getContext('2d');
    if (!ctx) return;
 
    const labels = this.trends.map(t => {
      const d = new Date(t.date);
      return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short' });
    });
    const gpsData = this.trends.map(t => Number(t.averageGps));
 
    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, 'rgba(56, 189, 248, 0.3)');
    gradient.addColorStop(1, 'rgba(56, 189, 248, 0.0)');
 
    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Average GPS (%)',
          data: gpsData,
          borderColor: '#38bdf8',
          backgroundColor: gradient,
          borderWidth: 2,
          pointBackgroundColor: '#38bdf8',
          pointBorderColor: '#0f172a',
          pointBorderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 6,
          fill: true,
          tension: 0.4,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { intersect: false, mode: 'index' },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.95)',
            borderColor: 'rgba(56, 189, 248, 0.2)',
            borderWidth: 1,
            titleColor: '#94a3b8',
            bodyColor: '#f1f5f9',
            padding: 10,
            callbacks: {
              label: (item) => ` GPS: ${Number(item.parsed.y).toFixed(1)}%`
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(148, 163, 184, 0.06)' },
            ticks: { color: '#64748b', font: { size: 11 }, maxTicksLimit: 8 },
            border: { display: false }
          },
          y: {
            min: 0,
            max: 100,
            grid: { color: 'rgba(148, 163, 184, 0.06)' },
            ticks: {
              color: '#64748b',
              font: { size: 11 },
              callback: (v) => `${v}%`
            },
            border: { display: false }
          }
        }
      }
    });
  }

   exportCsv(): void {
    this.isExporting = true;
    this.researchService.exportCsv().subscribe({
      next: (csv) => {
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'biomechanica_anonimizat.csv';
        a.click();
        URL.revokeObjectURL(url);
        this.isExporting = false;
      },
      error: () => { this.isExporting = false; }
    });
  }
 

  getRiskClass(gps: number): string {
    if (gps <= 30) return 'risk-high';
    if (gps <= 60) return 'risk-moderate';
    return 'risk-low';
  }
 
  getRiskLabel(gps: number): string {
    if (gps <= 30) return 'High Risk';
    if (gps <= 60) return 'Moderate Risk';
    return 'Low Risk';
  }
 
  formatNum(val: number | null | undefined, decimals = 1): string {
    if (val == null) return '—';
    return Number(val).toFixed(decimals);
  }
 }
