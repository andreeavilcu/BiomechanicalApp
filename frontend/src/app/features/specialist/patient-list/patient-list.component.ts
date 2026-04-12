import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SpecialistService } from '../../../core/services/specialist.service';
import { UserDTO, Gender } from '../../../core/models/user.model';

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './patient-list.component.html',
  styleUrl: './patient-list.component.scss',
})
export class PatientListComponent implements OnInit{ 
  private specialistService = inject(SpecialistService);
  private router = inject(Router);
 
  allPatients: UserDTO[] = [];
  filteredPatients: UserDTO[] = [];
  isLoading = true;
  errorMessage: string | null = null;
  searchQuery = '';

  showAssignModal = false;
  assignEmail = '';
  assignReason = '';
  isAssigning = false;
  assignError: string | null = null;
  assignSuccess = false;
 
  readonly Gender = Gender;
 
  ngOnInit(): void {
    this.loadPatients();
  }

  private loadPatients(): void {
    this.isLoading = true;
    this.specialistService.getMyPatients().subscribe({
      next: (data) => {
        this.allPatients = data;
        this.applyFilter();
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.isLoading = false;
      }
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.toLowerCase().trim();
    if (!q) {
      this.filteredPatients = [...this.allPatients];
      return;
    }
    this.filteredPatients = this.allPatients.filter(p =>
      p.firstName.toLowerCase().includes(q) ||
      p.lastName.toLowerCase().includes(q) ||
      p.email.toLowerCase().includes(q)
    );
  }

  viewPatient(patientId: number): void {
    this.router.navigate(['/specialist/patients', patientId]);
  }
 
  getInitials(patient: UserDTO): string {
    return ((patient.firstName?.[0] ?? '') + (patient.lastName?.[0] ?? '')).toUpperCase();
  }
 
  getGenderLabel(gender: Gender): string {
    switch (gender) {
      case Gender.MALE: return 'Male';
      case Gender.FEMALE: return 'Female';
      default: return 'Other';
    }
  }
 
  openAssignModal(): void {
    this.showAssignModal = true;
    this.assignEmail = '';
    this.assignReason = '';
    this.assignError = null;
    this.assignSuccess = false;
  }
 
  closeAssignModal(): void {
    this.showAssignModal = false;
  }

  submitAssign(): void {
    if (!this.assignEmail.trim()) return;
    this.isAssigning = true;
    this.assignError = null;
 
    this.specialistService.assignPatient(this.assignEmail.trim(), this.assignReason.trim() || undefined).subscribe({
      next: () => {
        this.isAssigning = false;
        this.assignSuccess = true;
        setTimeout(() => {
          this.closeAssignModal();
          this.loadPatients();
        }, 1500);
      },
      error: (err) => {
        this.isAssigning = false;
        this.assignError = err.message ?? 'Could not assign patient. Check the email and try again.';
      }
    });
  }

  
}
