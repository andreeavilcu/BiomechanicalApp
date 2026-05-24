import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { UserManagementComponent } from './user-management.component';
import { AdminService } from '../../../core/services/admin.service';
import { UserDTO, UserRole, Gender } from '../../../core/models/user.model';
import { SystemStatsDTO } from '../../../core/models/admin.model';

const makeUser = (id: number, role = UserRole.PATIENT, isActive = true): UserDTO => ({
  id, email: `user${id}@test.com`, firstName: `First${id}`, lastName: `Last${id}`,
  dateOfBirth: '1990-01-01', gender: Gender.MALE, heightCm: 170, role, isActive
});

const mockStats: SystemStatsDTO = {
  totalUsers: 10, totalPatients: 5, totalSpecialists: 2,
  totalResearchers: 2, totalAdmins: 1, activeUsers: 8
};

describe('UserManagementComponent', () => {
  let component: UserManagementComponent;
  let fixture: ComponentFixture<UserManagementComponent>;
  let mockAdminService: {
    getSystemStats: ReturnType<typeof vi.fn>;
    getAllUsers: ReturnType<typeof vi.fn>;
    updateUserRole: ReturnType<typeof vi.fn>;
    toggleUserStatus: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockAdminService = {
      getSystemStats: vi.fn().mockReturnValue(of(mockStats)),
      getAllUsers: vi.fn().mockReturnValue(of([makeUser(1), makeUser(2, UserRole.SPECIALIST)])),
      updateUserRole: vi.fn(),
      toggleUserStatus: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [UserManagementComponent],
      providers: [{ provide: AdminService, useValue: mockAdminService }],
    }).compileComponents();

    fixture = TestBed.createComponent(UserManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads stats and users on init', () => {
    expect(component.stats).toEqual(mockStats);
    expect(component.allUsers.length).toBe(2);
    expect(component.filteredUsers.length).toBe(2);
  });

  it('inactiveUsers computes totalUsers - activeUsers', () => {
    expect(component.inactiveUsers).toBe(2);
  });

  it('inactiveUsers returns 0 when stats is null', () => {
    component.stats = null;
    expect(component.inactiveUsers).toBe(0);
  });

  it('applyFilters filters by role', () => {
    component.selectedRoleFilter = UserRole.SPECIALIST;
    component.applyFilters();
    expect(component.filteredUsers.every(u => u.role === UserRole.SPECIALIST)).toBe(true);
  });

  it('applyFilters shows all users when filter is ALL', () => {
    component.selectedRoleFilter = 'ALL';
    component.applyFilters();
    expect(component.filteredUsers.length).toBe(2);
  });

  it('applyFilters filters by search query on firstName', () => {
    component.searchQuery = 'first1';
    component.applyFilters();
    expect(component.filteredUsers.length).toBe(1);
    expect(component.filteredUsers[0].firstName).toBe('First1');
  });

  it('applyFilters filters by email', () => {
    component.searchQuery = 'user2@';
    component.applyFilters();
    expect(component.filteredUsers.length).toBe(1);
  });

  it('getRoleLabel returns correct labels', () => {
    expect(component.getRoleLabel(UserRole.PATIENT)).toBe('Patient');
    expect(component.getRoleLabel(UserRole.SPECIALIST)).toBe('Specialist');
    expect(component.getRoleLabel(UserRole.RESEARCHER)).toBe('Researcher');
    expect(component.getRoleLabel(UserRole.ADMIN)).toBe('Administrator');
  });

  it('getRoleClass returns role-patient for PATIENT', () => {
    expect(component.getRoleClass(UserRole.PATIENT)).toBe('role-patient');
  });

  it('getUserInitials returns uppercase initials', () => {
    const user = makeUser(1);
    expect(component.getUserInitials(user)).toBe('FL');
  });

  it('isActionLoading returns false when no action', () => {
    expect(component.isActionLoading(999, 'role')).toBe(false);
  });

  it('isActionSuccess returns false when no action', () => {
    expect(component.isActionSuccess(999, 'status')).toBe(false);
  });

  it('changeRole skips when same role', () => {
    const user = makeUser(1, UserRole.PATIENT);
    component.changeRole(user, UserRole.PATIENT);
    expect(mockAdminService.updateUserRole).not.toHaveBeenCalled();
  });

  it('changeRole calls adminService.updateUserRole and updates list on success', () => {
    const updated = makeUser(1, UserRole.SPECIALIST);
    mockAdminService.updateUserRole.mockReturnValue(of(updated));
    const user = makeUser(1, UserRole.PATIENT);
    component.allUsers = [user];
    component.filteredUsers = [user];
    component.changeRole(user, UserRole.SPECIALIST);
    expect(mockAdminService.updateUserRole).toHaveBeenCalledWith(1, UserRole.SPECIALIST);
    expect(component.allUsers[0].role).toBe(UserRole.SPECIALIST);
  });

  it('toggleStatus calls adminService.toggleUserStatus', () => {
    const updated = { ...makeUser(1), isActive: false };
    mockAdminService.toggleUserStatus.mockReturnValue(of(updated));
    const user = makeUser(1, UserRole.PATIENT, true);
    component.allUsers = [user];
    component.filteredUsers = [user];
    component.toggleStatus(user);
    expect(mockAdminService.toggleUserStatus).toHaveBeenCalledWith(1, false);
    expect(component.allUsers[0].isActive).toBe(false);
  });

  it('sets errorMessage when getAllUsers fails', async () => {
    mockAdminService.getAllUsers.mockReturnValue(throwError(() => ({ message: 'Load failed' })));
    const f2 = TestBed.createComponent(UserManagementComponent);
    f2.detectChanges();
    await f2.whenStable();
    expect(f2.componentInstance.errorMessage).toBe('Load failed');
  });
});
