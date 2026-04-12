import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { ScanService } from '../../core/services/scan.service';
import { ProfileService } from '../../core/services/profile.service';
import { UserDTO, Gender } from '../../core/models/user.model';
import { AnalysisResultDTO, ProcessingStatus } from '../../core/models/scan.model';

type ActiveTab = 'info' | 'security';
type SaveState = 'idle' | 'saving' | 'success' | 'error';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private profileService = inject(ProfileService);
  private scanService = inject(ScanService);

  activeTab: ActiveTab = 'info';
  isLoading = true;
  profile: UserDTO | null = null;
  sessions: AnalysisResultDTO[] = [];
 
  profileSaveState: SaveState = 'idle';
  passwordSaveState: SaveState = 'idle';
  profileErrorMsg: string | null = null;
  passwordErrorMsg: string | null = null;
 
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;

   readonly genderOptions = Object.values(Gender);

   profileForm: FormGroup = this.fb.group({
    firstName:   ['', [Validators.required, Validators.maxLength(100)]],
    lastName:    ['', [Validators.required, Validators.maxLength(100)]],
    dateOfBirth: ['', [Validators.required]],
    gender:      ['', [Validators.required]],
    heightCm:    ['', [Validators.required, Validators.min(50), Validators.max(250)]],
  });

  passwordForm: FormGroup = this.fb.group({
    currentPassword: ['', [Validators.required, Validators.minLength(8)]],
    newPassword:     ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: this.passwordMatchValidator });

  ngOnInit(): void {
    this.loadProfile();
    this.loadSessions();
  }

   private loadProfile(): void {
    this.profileService.getProfile().subscribe({
      next: (data) => {
        this.profile = data;
        this.profileForm.patchValue({
          firstName:   data.firstName,
          lastName:    data.lastName,
          dateOfBirth: data.dateOfBirth,
          gender:      data.gender,
          heightCm:    data.heightCm,
        });
        this.isLoading = false;
      },
      error: (err) => {
        this.profileErrorMsg = err.message;
        this.isLoading = false;
      }
    });
  }

  private loadSessions(): void {
    this.scanService.getMyHistory().subscribe({
      next: (data) => {
        this.sessions = data.filter(s => s.status === ProcessingStatus.COMPLETED);
      },
      error: () => { }
    });
  }

  get currentUser() {
    return this.authService.getCurrentUser();
  }
 
  get userInitials(): string {
    const u = this.currentUser;
    if (!u) return '';
    return (u.firstName[0] + u.lastName[0]).toUpperCase();
  }
 
  get roleLabel(): string {
    const role = this.currentUser?.role;
    const labels: Record<string, string> = {
      PATIENT: 'Patient',
      SPECIALIST: 'Specialist',
      RESEARCHER: 'Researcher',
      ADMIN: 'Administrator',
    };
    return role ? (labels[role] ?? role) : '';
  }
 
  get roleClass(): string {
    return 'role-' + (this.currentUser?.role?.toLowerCase() ?? '');
  }

  get totalScans(): number {
    return this.sessions.length;
  }
 
  get averageGPS(): number | null {
    if (this.sessions.length === 0) return null;
    const sum = this.sessions.reduce((acc, s) => acc + (s.globalPostureScore ?? 0), 0);
    return sum / this.sessions.length;
  }
 
  get firstScanDate(): string | null {
    if (this.sessions.length === 0) return null;
    const sorted = [...this.sessions].sort(
      (a, b) => new Date(a.scanDate).getTime() - new Date(b.scanDate).getTime()
    );
    return new Date(sorted[0].scanDate).toLocaleDateString('en-US', {
      day: '2-digit', month: 'long', year: 'numeric'
    });
  }

  setTab(tab: ActiveTab): void {
    this.activeTab = tab;
    this.profileErrorMsg = null;
    this.passwordErrorMsg = null;
    this.profileSaveState = 'idle';
    this.passwordSaveState = 'idle';
  }

   onSaveProfile(): void {
  if (this.profileForm.invalid) {
    this.profileForm.markAllAsTouched();
    return;
  }

  this.profileSaveState = 'saving';
  this.profileErrorMsg = null;

  const payload = {
    ...this.profileForm.value,
    email: this.currentUser?.email
  };

  this.profileService.updateProfile(payload).subscribe({
    next: (updated) => {
      this.profile = updated;
      this.profileSaveState = 'success';
      setTimeout(() => (this.profileSaveState = 'idle'), 3000);
    },
    error: (err) => {
      this.profileSaveState = 'error';
      this.profileErrorMsg = err.message ?? 'Eroare la salvare.';
    }
  });
}
 
  onChangePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
 
    const { currentPassword, newPassword } = this.passwordForm.value;
 
    this.passwordSaveState = 'saving';
    this.passwordErrorMsg = null;
 
    this.profileService.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.passwordSaveState = 'success';
        this.passwordForm.reset();
        setTimeout(() => (this.passwordSaveState = 'idle'), 3000);
      },
      error: (err) => {
        this.passwordSaveState = 'error';
        this.passwordErrorMsg = err.message ?? 'Failed to change password.';
      }
    });
  }
 
  getGenderLabel(gender: Gender): string {
    const labels: Record<Gender, string> = {
      [Gender.MALE]: 'Male',
      [Gender.FEMALE]: 'Female',
      [Gender.OTHER]: 'Other',
    };
    return labels[gender];
  }
 
  private passwordMatchValidator(group: AbstractControl) {
    const np = group.get('newPassword')?.value;
    const cp = group.get('confirmPassword')?.value;
    return np === cp ? null : { passwordMismatch: true };
  }
 
  get pf() { return this.profileForm.controls; }
  get pwf() { return this.passwordForm.controls; }
}
