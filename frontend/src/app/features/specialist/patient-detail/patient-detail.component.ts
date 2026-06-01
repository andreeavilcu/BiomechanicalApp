import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SpecialistService } from '../../../core/services/specialist.service';
import { ToastService } from '../../../core/services/toast.service';
import { BreadcrumbComponent, Crumb } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { UserDTO } from '../../../core/models/user.model';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, BreadcrumbComponent],
  templateUrl: './patient-detail.component.html',
  styleUrl: './patient-detail.component.scss',
})
export class PatientDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private specialistService = inject(SpecialistService);
  private toastSvc = inject(ToastService);

  patientId!: number;
  patient: UserDTO | null = null;

  breadcrumbs: Crumb[] = [
    { label: 'My Patients', route: '/specialist/patients' },
    { label: 'Patient' },
  ];
  sessions: AnalysisResultDTO[] = [];

  isLoadingPatient = true;
  isLoadingHistory = true;
  errorMessage: string | null = null;

  clinicalNotes = '';
  savedNotes = '';         
  isLoadingNotes = true;
  isSavingNotes = false;
  notesSaved = false;
  notesError: string | null = null;

  readonly RiskLevel = RiskLevel;
  readonly ProcessingStatus = ProcessingStatus;

  ngOnInit(): void {
    this.patientId = Number(this.route.snapshot.paramMap.get('patientId'));
    if (!this.patientId) {
      this.router.navigate(['/specialist/patients']);
      return;
    }
    this.loadAll();
  }

  private loadAll(): void {
    this.specialistService.getMyPatients().subscribe({
      next: (patients) => {
        this.patient = patients.find(p => p.id === this.patientId) ?? null;
        this.isLoadingPatient = false;
        if (this.patient) {
          this.breadcrumbs = [
            { label: 'My Patients', route: '/specialist/patients' },
            { label: `${this.patient.firstName} ${this.patient.lastName}` },
          ];
        } else {
          this.errorMessage = 'Patient not found or not assigned to you.';
        }
      },
      error: () => { this.isLoadingPatient = false; }
    });

    this.specialistService.getPatientHistory(this.patientId).subscribe({
      next: (data) => {
        this.sessions = data.sort(
          (a, b) => new Date(b.scanDate).getTime() - new Date(a.scanDate).getTime()
        );
        this.isLoadingHistory = false;
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.isLoadingHistory = false;
      }
    });

    this.specialistService.getClinicalNotes(this.patientId).subscribe({
      next: (notes) => {
        this.clinicalNotes = notes;
        this.savedNotes = notes;
        this.isLoadingNotes = false;
      },
      error: () => {
        this.isLoadingNotes = false;
      }
    });
  }

  saveNotes(): void {
    if (this.isSavingNotes) return;
    this.isSavingNotes = true;
    this.notesError = null;
    this.notesSaved = false;

    this.specialistService.saveClinicalNotes(this.patientId, this.clinicalNotes).subscribe({
      next: () => {
        this.savedNotes = this.clinicalNotes;
        this.isSavingNotes = false;
        this.notesSaved = true;
        this.toastSvc.success('Clinical notes saved.');
        setTimeout(() => this.notesSaved = false, 3000);
      },
      error: (err) => {
        this.isSavingNotes = false;
        this.notesError = err.message ?? 'Could not save notes.';
        this.toastSvc.error(err.message ?? 'Could not save notes.');
      }
    });
  }

  get notesChanged(): boolean {
    return this.clinicalNotes !== this.savedNotes;
  }

  viewSession(sessionId: number): void {
    this.router.navigate(['/scans', sessionId]);
  }

  goBack(): void {
    this.router.navigate(['/specialist/patients']);
  }

  getInitials(): string {
    if (!this.patient) return '?';
    return ((this.patient.firstName?.[0] ?? '') + (this.patient.lastName?.[0] ?? '')).toUpperCase();
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

  getCompletedSessions(): AnalysisResultDTO[] {
    return this.sessions.filter(s => s.status === ProcessingStatus.COMPLETED);
  }

  getAverageGps(): number | null {
    const completed = this.getCompletedSessions();
    if (!completed.length) return null;
    const sum = completed.reduce((acc, s) => acc + (Number(s.globalPostureScore) || 0), 0);
    return Math.round(sum / completed.length);
  }

  getLatestRisk(): RiskLevel | null {
    const completed = this.getCompletedSessions();
    return completed.length ? completed[0].riskLevel : null;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }
}