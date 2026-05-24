import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProfileService } from './profile.service';
import { UserDTO, ChangePasswordRequest, Gender, UserRole } from '../models/user.model';

const mockUser: UserDTO = {
  id: 1,
  email: 'patient@example.com',
  firstName: 'Ana',
  lastName: 'Popescu',
  dateOfBirth: '1990-06-15',
  gender: Gender.FEMALE,
  heightCm: 165,
  role: UserRole.PATIENT,
  isActive: true,
};

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getProfile', () => {
    it('should GET /api/patients/profile', () => {
      service.getProfile().subscribe(user => expect(user).toEqual(mockUser));
      const req = httpMock.expectOne('/api/patients/profile');
      expect(req.request.method).toBe('GET');
      req.flush(mockUser);
    });
  });

  describe('updateProfile', () => {
    it('should PUT /api/patients/update_profile with the partial profile data', () => {
      const update: Partial<UserDTO> = { firstName: 'Updated', heightCm: 170 };
      service.updateProfile(update).subscribe(user => expect(user).toEqual(mockUser));
      const req = httpMock.expectOne('/api/patients/update_profile');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(update);
      req.flush(mockUser);
    });
  });

  describe('changePassword', () => {
    it('should PUT /api/patients/update_password with the password request body', () => {
      const passwordReq: ChangePasswordRequest = {
        currentPassword: 'oldPass123',
        newPassword: 'newPass456',
      };
      service.changePassword(passwordReq).subscribe();
      const req = httpMock.expectOne('/api/patients/update_password');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(passwordReq);
      req.flush(null);
    });
  });
});
