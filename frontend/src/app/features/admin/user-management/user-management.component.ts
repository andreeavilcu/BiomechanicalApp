import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { UserDTO, UserRole } from '../../../core/models/user.model';
import { SystemStatsDTO, ActionState, UserAction } from '../../../core/models/admin.model';
 

@Component({
 selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent implements OnInit {
  private adminService = inject(AdminService);
 
  // ── State ────────────────────────────────────────────────────────────
  stats: SystemStatsDTO | null = null;
  allUsers: UserDTO[] = [];
  filteredUsers: UserDTO[] = [];
 
  isLoadingStats = true;
  isLoadingUsers = true;
  errorMessage: string | null = null;
 
  selectedRoleFilter: UserRole | 'ALL' = 'ALL';
  searchQuery = '';
 
  activeActions = new Map<number, UserAction>();
 
  readonly UserRole = UserRole;
  readonly roleOptions: Array<{ value: UserRole | 'ALL'; label: string }> = [
    { value: 'ALL', label: 'All roles' },
    { value: UserRole.PATIENT, label: 'Patients' },
    { value: UserRole.SPECIALIST, label: 'Specialists' },
    { value: UserRole.RESEARCHER, label: 'Researchers' },
    { value: UserRole.ADMIN, label: 'Administrators' },
  ];
  readonly allRoles = Object.values(UserRole);
 
  ngOnInit(): void {
    this.loadStats();
    this.loadUsers();
  }
 
  private loadStats(): void {
    this.adminService.getSystemStats().subscribe({
      next: (data) => {
        this.stats = data;
        this.isLoadingStats = false;
      },
      error: () => { this.isLoadingStats = false; }
    });
  }
 
  private loadUsers(): void {
    this.isLoadingUsers = true;
    this.adminService.getAllUsers().subscribe({
      next: (data) => {
        this.allUsers = data;
        this.applyFilters();
        this.isLoadingUsers = false;
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.isLoadingUsers = false;
      }
    });
  }
 
  applyFilters(): void {
    let result = [...this.allUsers];
 
    if (this.selectedRoleFilter !== 'ALL') {
      result = result.filter(u => u.role === this.selectedRoleFilter);
    }
 
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(u =>
        u.firstName.toLowerCase().includes(q) ||
        u.lastName.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q)
      );
    }
 
    this.filteredUsers = result;
  }
 
  onFilterChange(): void {
    this.applyFilters();
  }
 
  onSearchChange(): void {
    this.applyFilters();
  }
 
  changeRole(user: UserDTO, newRole: UserRole): void {
    if (user.role === newRole) return;
    if (!user.id) return;
 
    this.activeActions.set(user.id, { userId: user.id, type: 'role', state: 'loading' });
 
    this.adminService.updateUserRole(user.id, newRole).subscribe({
      next: (updated) => {
        this.updateUserInList(updated);
        this.activeActions.set(user.id, { userId: user.id, type: 'role', state: 'success' });
        setTimeout(() => this.activeActions.delete(user.id), 2000);
      },
      error: (err) => {
        this.activeActions.set(user.id, { userId: user.id, type: 'role', state: 'error' });
        setTimeout(() => this.activeActions.delete(user.id), 3000);
        console.error('Role update failed:', err.message);
      }
    });
  }
 
  toggleStatus(user: UserDTO): void {
    if (!user.id) return;
    const newStatus = !user.isActive;
 
    this.activeActions.set(user.id, { userId: user.id, type: 'status', state: 'loading' });
 
    this.adminService.toggleUserStatus(user.id, newStatus).subscribe({
      next: (updated) => {
        this.updateUserInList(updated);
        this.activeActions.set(user.id, { userId: user.id, type: 'status', state: 'success' });
        setTimeout(() => this.activeActions.delete(user.id), 2000);
      },
      error: (err) => {
        this.activeActions.set(user.id, { userId: user.id, type: 'status', state: 'error' });
        setTimeout(() => this.activeActions.delete(user.id), 3000);
        console.error('Status toggle failed:', err.message);
      }
    });
  }
 
  private updateUserInList(updated: UserDTO): void {
    const updateIn = (list: UserDTO[]) => {
      const idx = list.findIndex(u => u.id === updated.id);
      if (idx !== -1) list[idx] = updated;
    };
    updateIn(this.allUsers);
    updateIn(this.filteredUsers);
  }
 
  getActionFor(userId: number): UserAction | undefined {
    return this.activeActions.get(userId);
  }
 
  isActionLoading(userId: number, type: 'role' | 'status'): boolean {
    const a = this.activeActions.get(userId);
    return a?.type === type && a?.state === 'loading';
  }
 
  isActionSuccess(userId: number, type: 'role' | 'status'): boolean {
    const a = this.activeActions.get(userId);
    return a?.type === type && a?.state === 'success';
  }
 
  getRoleLabel(role?: UserRole): string {
    const labels: Record<UserRole, string> = {
      [UserRole.PATIENT]: 'Patient',
      [UserRole.SPECIALIST]: 'Specialist',
      [UserRole.RESEARCHER]: 'Researcher',
      [UserRole.ADMIN]: 'Administrator',
    };
    return role ? (labels[role] ?? role) : '—';
  }
 
  getRoleClass(role?: UserRole): string {
    return 'role-' + (role?.toLowerCase() ?? 'unknown');
  }
 
  getUserInitials(user: UserDTO): string {
    return ((user.firstName?.[0] ?? '') + (user.lastName?.[0] ?? '')).toUpperCase();
  }
 
  get inactiveUsers(): number {
    return this.stats ? this.stats.totalUsers - this.stats.activeUsers : 0;
  }
}