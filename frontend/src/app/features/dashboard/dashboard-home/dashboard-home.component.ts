import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ScanService } from '../../../core/services/scan.service';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';
import { UserRole } from '../../../core/models/user.model';
import { AuthResponse } from '../../../core/models/auth.model';

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard-home.component.html',
  styleUrl: './dashboard-home.component.scss',
})
export class DashboardHomeComponent { 
  private authService = inject(AuthService);
  private scanService = inject(ScanService);
  private router = inject(Router);
 
  currentUser: AuthResponse | null = null;
  sessions: AnalysisResultDTO[] = [];
  isLoading = true;
  errorMessage: string | null = null;

  readonly UserRole = UserRole;
  readonly RiskLevel = RiskLevel;
  readonly ProcessingStatus = ProcessingStatus;

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser();

    if (this.isPatient) {
      this.loadSessions();
    } else {
      this.isLoading = false;
    }
  }

  private loadSessions(): void {
    this.scanService.getMyHistory().subscribe({
      next: (data) => {
        this.sessions = data
          .filter(s => s.status === ProcessingStatus.COMPLETED)
          .sort((a, b) => new Date(b.scanDate).getTime() - new Date(a.scanDate).getTime())
          .slice(0, 3);
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.isLoading = false;
      }
    });
  }

  get isPatient(): boolean {
    return this.authService.hasRole(UserRole.PATIENT);
  }
 
  get isSpecialist(): boolean {
    return this.authService.hasRole(UserRole.SPECIALIST);
  }
 
  get isResearcher(): boolean {
    return this.authService.hasRole(UserRole.RESEARCHER);
  }
 
  get isAdmin(): boolean {
    return this.authService.hasRole(UserRole.ADMIN);
  }
 
  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  }

  get todayDate(): string {
    return new Date().toLocaleDateString('en-US', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }
 
  get latestSession(): AnalysisResultDTO | null {
    return this.sessions.length > 0 ? this.sessions[0] : null;
  }
 
  get totalSessions(): number {
    return this.sessions.length;
  }
 
  get daysSinceLastScan(): number | null {
    if (!this.latestSession) return null;
    const diff = Date.now() - new Date(this.latestSession.scanDate).getTime();
    return Math.floor(diff / (1000 * 60 * 60 * 24));
  }

  get gpsScore(): number | null {
    return this.latestSession?.globalPostureScore ?? null;
  }
 
  get riskLevel(): RiskLevel | null {
    return this.latestSession?.riskLevel ?? null;
  }
 
  get riskClass(): string {
    switch (this.riskLevel) {
      case RiskLevel.LOW: return 'risk-low';
      case RiskLevel.MODERATE: return 'risk-moderate';
      case RiskLevel.HIGH: return 'risk-high';
      default: return '';
    }
  }
 
  get riskLabel(): string {
    switch (this.riskLevel) {
      case RiskLevel.LOW: return 'Low risk';
      case RiskLevel.MODERATE: return 'Moderate risk';
      case RiskLevel.HIGH: return 'High risk';
      default: return '—';
    }
  }
 
  get trendIcon(): string {
    const trend = this.latestSession?.evolution?.trend;
    switch (trend) {
      case 'IMPROVEMENT': return '↗';
      case 'DETERIORATION': return '↘';
      case 'STABLE': return '→';
      default: return '●';
    }
  }
 
  get trendLabel(): string {
    const trend = this.latestSession?.evolution?.trend;
    switch (trend) {
      case 'IMPROVEMENT': return 'Improvement';
      case 'DETERIORATION': return 'Deterioration';
      case 'STABLE': return 'Stable';
      case 'FIRST_SESSION': return 'First session';
      default: return '—';
    }
  }
 
  get trendClass(): string {
    const trend = this.latestSession?.evolution?.trend;
    switch (trend) {
      case 'IMPROVEMENT': return 'trend-up';
      case 'DETERIORATION': return 'trend-down';
      default: return 'trend-stable';
    }
  }
 
  get scoreChange(): number | null {
    return this.latestSession?.evolution?.postureScoreChange ?? null;
  }
 
  get gpsCircleDashoffset(): number {
    const score = this.gpsScore ?? 0;
    const circumference = 282.7;
    return circumference - (score / 100) * circumference;
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
      default: return '—';
    }
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }
 
  viewSession(sessionId: number): void {
    this.router.navigate(['/scans', sessionId]);
  }
}
