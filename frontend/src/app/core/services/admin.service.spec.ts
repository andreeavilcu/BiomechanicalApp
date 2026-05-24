import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminService } from './admin.service';
import { UserDTO, UserRole, Gender } from '../models/user.model';
import { SystemStatsDTO } from '../models/admin.model';

const mockUser: UserDTO = {
  id: 5,
  email: 'john@example.com',
  firstName: 'John',
  lastName: 'Doe',
  dateOfBirth: '1985-05-20',
  gender: Gender.MALE,
  heightCm: 180,
  role: UserRole.PATIENT,
  isActive: true,
};

const mockStats: SystemStatsDTO = {
  totalUsers: 100,
  totalPatients: 70,
  totalSpecialists: 15,
  totalResearchers: 10,
  totalAdmins: 5,
  activeUsers: 80,
};

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getSystemStats', () => {
    it('should GET /api/admin/stats', () => {
      service.getSystemStats().subscribe(stats => expect(stats).toEqual(mockStats));
      const req = httpMock.expectOne('/api/admin/stats');
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);
    });
  });

  describe('getAllUsers', () => {
    it('should GET /api/admin/users without role param when none provided', () => {
      service.getAllUsers().subscribe(users => expect(users).toEqual([mockUser]));
      const req = httpMock.expectOne('/api/admin/users');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.has('role')).toBe(false);
      req.flush([mockUser]);
    });

    it('should include role param in query when role is provided', () => {
      service.getAllUsers(UserRole.PATIENT).subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/admin/users');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('role')).toBe(UserRole.PATIENT);
      req.flush([mockUser]);
    });
  });

  describe('updateUserRole', () => {
    it('should PUT /api/admin/users/:userId/role with newRole query param and null body', () => {
      service.updateUserRole(5, UserRole.SPECIALIST).subscribe(u => expect(u).toEqual(mockUser));
      const req = httpMock.expectOne(r => r.url === '/api/admin/users/5/role');
      expect(req.request.method).toBe('PUT');
      expect(req.request.params.get('newRole')).toBe(UserRole.SPECIALIST);
      expect(req.request.body).toBeNull();
      req.flush(mockUser);
    });
  });

  describe('toggleUserStatus', () => {
    it('should PUT /api/admin/users/:userId/status with active=true', () => {
      service.toggleUserStatus(5, true).subscribe(u => expect(u).toEqual(mockUser));
      const req = httpMock.expectOne(r => r.url === '/api/admin/users/5/status');
      expect(req.request.method).toBe('PUT');
      expect(req.request.params.get('active')).toBe('true');
      expect(req.request.body).toBeNull();
      req.flush(mockUser);
    });

    it('should PUT /api/admin/users/:userId/status with active=false', () => {
      service.toggleUserStatus(5, false).subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/admin/users/5/status');
      expect(req.request.params.get('active')).toBe('false');
      req.flush(mockUser);
    });
  });
});
