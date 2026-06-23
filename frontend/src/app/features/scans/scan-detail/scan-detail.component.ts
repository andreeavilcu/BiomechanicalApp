import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ScanService } from '../../../core/services/scan.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { AnalysisResultDTO, RiskLevel, RecommendationSeverity } from '../../../core/models/scan.model';
import { Viewer3dComponent } from '../viewer-3d/viewer-3d.component';
import { BreadcrumbComponent, Crumb } from '../../../shared/components/breadcrumb/breadcrumb.component';


@Component({
  selector: 'app-scan-detail',
  standalone: true,
  imports: [CommonModule, Viewer3dComponent, BreadcrumbComponent],
  templateUrl: './scan-detail.component.html',
  styleUrl: './scan-detail.component.scss',
})
export class ScanDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private scanService = inject(ScanService);
  private toastSvc = inject(ToastService);
  private confirmSvc = inject(ConfirmService);

  result: AnalysisResultDTO | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  scanNumber: number | null = null;

  breadcrumbs: Crumb[] = [
    { label: 'Scan History', route: '/scans/history' },
    { label: 'Session' },
  ];

  ngOnInit(): void {
    const sessionId = Number(this.route.snapshot.paramMap.get('sessionId'));
    if (!sessionId) {
      this.router.navigate(['/scans/history']);
      return;
    }

    const n = this.route.snapshot.queryParamMap.get('n');
    this.scanNumber = n ? Number(n) : null;

    this.scanService.getSession(sessionId).subscribe({
      next: (data) => {
        this.result = data;
        const label = this.scanNumber ? `Session #${this.scanNumber}` : `Session #${data.sessionId}`;
        this.breadcrumbs = [
          { label: 'Scan History', route: '/scans/history' },
          { label: label },
        ];
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.isLoading = false;
      }
    });
  }

  get keypoints() {
    return this.result?.keypoints ?? [];
  }

  get riskColorClass(): string {
    if (!this.result) return '';
    switch (this.result.riskLevel) {
      case RiskLevel.LOW: return 'risk-low';
      case RiskLevel.MODERATE: return 'risk-moderate';
      case RiskLevel.HIGH: return 'risk-high';
      default: return '';
    }
  }

  get riskLabel(): string {
    if (!this.result) return '';
    switch (this.result.riskLevel) {
      case RiskLevel.LOW: return 'Low risk';
      case RiskLevel.MODERATE: return 'Moderate risk';
      case RiskLevel.HIGH: return 'High risk';
      default: return '';
    }
  }

  getSeverityClass(severity: RecommendationSeverity): string {
    switch (severity) {
      case RecommendationSeverity.LOW: return 'severity-low';
      case RecommendationSeverity.MODERATE: return 'severity-moderate';
      case RecommendationSeverity.HIGH: return 'severity-high';
      default: return '';
    }
  }

  get trendIcon(): string {
    if (!this.result?.evolution) return '';
    switch (this.result.evolution.trend) {
      case 'IMPROVEMENT': return '↗';
      case 'DETERIORATION': return '↘';
      case 'STABLE': return '→';
      default: return '●';
    }
  }

  get trendLabel(): string {
    if (!this.result?.evolution) return '';
    switch (this.result.evolution.trend) {
      case 'IMPROVEMENT': return 'Improvement';
      case 'DETERIORATION': return 'Deterioration';
      case 'STABLE': return 'Stable';
      case 'FIRST_SESSION': return 'First session';
      default: return '';
    }
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'long', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  goBack(): void {
    this.router.navigate(['/scans/history']);
  }

  async deleteScan(): Promise<void> {
    if (!this.result) return;

    const confirmed = await this.confirmSvc.open({
      message: 'Delete this session?',
      detail: 'This action cannot be undone. All data for this session will be permanently removed.',
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
    });

    if (!confirmed) return;

    this.scanService.deleteSession(this.result.sessionId).subscribe({
      next: () => {
        this.toastSvc.success('Session deleted successfully.');
        this.router.navigate(['/scans/history']);
      },
      error: (err) => {
        this.toastSvc.error(err.message ?? 'Failed to delete session.');
      }
    });
  }
}
