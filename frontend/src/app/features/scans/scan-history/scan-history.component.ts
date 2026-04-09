import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ScanService } from '../../../core/services/scan.service';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

@Component({
  selector: 'app-scan-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './scan-history.component.html',
  styleUrl: './scan-history.component.scss',
})
export class ScanHistoryComponent implements OnInit {
  private scanService = inject(ScanService);
  private router = inject(Router);

  sessions: AnalysisResultDTO[] = [];
  isLoading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadHistory();
  }

  private loadHistory(): void {
    this.scanService.getMyHistory().subscribe({
      next: (data) => {
        this.sessions = data;
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.isLoading = false;
      }
    });
  }

  viewSession(sessionId: number): void {
    this.router.navigate(['/scans', sessionId]);
  }

  newScan(): void {
    this.router.navigate(['/scans/upload']);
  }

  getRiskClass(risk: RiskLevel): string {
    switch (risk) {
      case RiskLevel.LOW: return 'risk-low';
      case RiskLevel.MODERATE: return 'risk-moderate';
      case RiskLevel.HIGH: return 'risk-high';
      default: return '';
    }
  }

  getRiskLabel(risk: RiskLevel): string {
    switch (risk) {
      case RiskLevel.LOW: return 'Low';
      case RiskLevel.MODERATE: return 'Moderate';
      case RiskLevel.HIGH: return 'High';
      default: return '-';
    }
  }

  getStatusLabel(status: ProcessingStatus): string {
    switch (status) {
      case ProcessingStatus.PENDING: return 'Pending';
      case ProcessingStatus.PROCESSING: return 'Processing';
      case ProcessingStatus.COMPLETED: return 'Completed';
      case ProcessingStatus.FAILED: return 'Failed';
      default: return '-';
    }
  }

  getStatusClass(status: ProcessingStatus): string {
    switch (status) {
      case ProcessingStatus.COMPLETED: return 'status-completed';
      case ProcessingStatus.FAILED: return 'status-failed';
      case ProcessingStatus.PROCESSING: return 'status-processing';
      default: return 'status-pending';
    }
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      day: '2-digit', 
      month: 'short', 
      year: 'numeric'
    });
  }
}