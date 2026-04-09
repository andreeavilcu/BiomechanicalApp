import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ScanService } from '../../../core/services/scan.service';
import { AnalysisResultDTO, RiskLevel, RecommendationSeverity } from '../../../core/models/scan.model';
import { Viewer3dComponent } from '../viewer-3d/viewer-3d.component';


@Component({
  selector: 'app-scan-detail',
  standalone: true,
  imports: [CommonModule, Viewer3dComponent],
  templateUrl: './scan-detail.component.html',
  styleUrl: './scan-detail.component.scss',
})
export class ScanDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private scanService = inject(ScanService);

  result: AnalysisResultDTO | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    const sessionId = Number(this.route.snapshot.paramMap.get('sessionId'));
    if (!sessionId) {
      this.router.navigate(['/scans/history']);
      return;
    }

    this.scanService.getSession(sessionId).subscribe({
      next: (data) => {
        this.result = data;
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

  deleteScan(): void {
    if (!this.result) return;
    if (confirm('Are you sure you want to delete this session?')) {
      this.scanService.deleteSession(this.result.sessionId).subscribe({
        next: () => this.router.navigate(['/scans/history']),
        error: (err) => this.errorMessage = err.message
      });
    }
  }
}
