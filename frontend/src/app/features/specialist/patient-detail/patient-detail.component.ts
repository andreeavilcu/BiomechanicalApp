import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SpecialistService } from '../../../core/services/specialist.service';
import { ScanService } from '../../../core/services/scan.service';
import { UserDTO } from '../../../core/models/user.model';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './patient-detail.component.html',
  styleUrl: './patient-detail.component.scss',
})
export class PatientDetailComponent implements OnInit{
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private specialistService = inject(SpecialistService);
 
  patientId!: number;
  patient: UserDTO | null = null;
  sessions: AnalysisResultDTO[] = [];
 
  isLoadingPatient = true;
  isLoadingHistory = true;
  errorMessage: string | null = null;
 
  showNotesModal = false;
  selectedSessionId: number | null = null;
  clinicalNotes = '';
  isSavingNotes = false;
  notesError: string | null = null;
  notesSuccess = false;
 
  readonly RiskLevel = RiskLevel;
  readonly ProcessingStatus = ProcessingStatus;

  ngOnInit(): void {
    this.patientId = Number(this.route.snapshot.paramMap.get('patientId'));
    if (!this.patientId) {
      this.router.navigate(['/specialist/patients']);
      return;
    }
    this.loadPatientAndHistory();
  }

  private loadPatientAndHistory(): void {
    // Încarcă lista de pacienți și găsește pacientul curent
    this.specialistService.getMyPatients().subscribe({
      next: (patients) => {
        this.patient = patients.find(p => p.id === this.patientId) ?? null;
        this.isLoadingPatient = false;
        if (!this.patient) {
          this.errorMessage = 'Patient not found or not assigned to you.';
        }
      },
      error: () => { this.isLoadingPatient = false; }
    });
 
    // Încarcă istoricul scanărilor
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
  }

  viewSession(sessionId: number): void {
    this.router.navigate(['/scans', sessionId]);
  }
 
  goBack(): void {
    this.router.navigate(['/specialist/patients']);
  }

  openNotesModal(sessionId: number): void {
    this.selectedSessionId = sessionId;
    this.clinicalNotes = '';
    this.notesError = null;
    this.notesSuccess = false;
    this.showNotesModal = true;
  }
 
  closeNotesModal(): void {
    this.showNotesModal = false;
    this.selectedSessionId = null;
  }
 
  saveNotes(): void {
    if (!this.selectedSessionId || !this.clinicalNotes.trim()) return;
    this.isSavingNotes = true;
    this.notesError = null;
 
    this.specialistService.addClinicalNotes(
      this.patientId,
      this.selectedSessionId,
      this.clinicalNotes.trim()
    ).subscribe({
      next: () => {
        this.isSavingNotes = false;
        this.notesSuccess = true;
        setTimeout(() => this.closeNotesModal(), 1500);
      },
      error: (err) => {
        this.isSavingNotes = false;
        this.notesError = err.message ?? 'Could not save notes.';
      }
    });
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
