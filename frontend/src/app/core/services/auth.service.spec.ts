import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { UserRole, Gender } from '../models/user.model';

function makeJwt(exp: number): string {
  const header = btoa('{"alg":"HS256"}').replace(/=/g, '');
  const payload = btoa(JSON.stringify({ sub: '1', exp })).replace(/=/g, '');
  return `${header}.${payload}.sig`;
}

const VALID_TOKEN = makeJwt(Math.floor(Date.now() / 1000) + 3600);
const EXPIRED_TOKEN = makeJwt(1);

const mockAuthResponse: AuthResponse = {
  accessToken: VALID_TOKEN,
  refreshToken: 'refresh-token',
  role: UserRole.PATIENT,
  userId: 1,
  email: 'test@example.com',
  firstName: 'Test',
  lastName: 'User',
  heightCm: 175,
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    it('should POST to /api/auth/login with credentials', () => {
      const req: LoginRequest = { email: 'test@example.com', password: 'password' };
      service.login(req).subscribe(res => expect(res).toEqual(mockAuthResponse));
      const http = httpMock.expectOne('/api/auth/login');
      expect(http.request.method).toBe('POST');
      expect(http.request.body).toEqual(req);
      http.flush(mockAuthResponse);
    });

    it('should store tokens in localStorage after login', () => {
      service.login({ email: 'test@example.com', password: 'password' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
      expect(localStorage.getItem('accessToken')).toBe(VALID_TOKEN);
      expect(localStorage.getItem('refreshToken')).toBe('refresh-token');
    });

    it('should update currentUser$ after login', () => {
      service.login({ email: 'test@example.com', password: 'password' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
      service.currentUser$.subscribe(user => expect(user).toEqual(mockAuthResponse));
    });
  });

  describe('register', () => {
    const registerReq: RegisterRequest = {
      email: 'new@example.com',
      password: 'password',
      firstName: 'New',
      lastName: 'User',
      dateOfBirth: '1990-01-01',
      gender: Gender.MALE,
      heightCm: 180,
    };

    it('should POST to /api/auth/register', () => {
      service.register(registerReq).subscribe(res => expect(res).toEqual(mockAuthResponse));
      const http = httpMock.expectOne('/api/auth/register');
      expect(http.request.method).toBe('POST');
      expect(http.request.body).toEqual(registerReq);
      http.flush(mockAuthResponse);
    });

    it('should store tokens in localStorage after register', () => {
      service.register(registerReq).subscribe();
      httpMock.expectOne('/api/auth/register').flush(mockAuthResponse);
      expect(localStorage.getItem('accessToken')).toBe(VALID_TOKEN);
      expect(localStorage.getItem('refreshToken')).toBe('refresh-token');
    });
  });

  describe('refreshToken', () => {
    it('should POST to /api/auth/refresh with Bearer Authorization header', () => {
      localStorage.setItem('refreshToken', 'stored-refresh');
      service.refreshToken().subscribe();
      const http = httpMock.expectOne('/api/auth/refresh');
      expect(http.request.method).toBe('POST');
      expect(http.request.headers.get('Authorization')).toBe('Bearer stored-refresh');
      http.flush(mockAuthResponse);
    });
  });

  describe('logout', () => {
    it('should remove all auth keys from localStorage', () => {
      localStorage.setItem('accessToken', VALID_TOKEN);
      localStorage.setItem('refreshToken', 'refresh');
      localStorage.setItem('currentUser', JSON.stringify(mockAuthResponse));
      vi.spyOn(router, 'navigate').mockResolvedValue(true);
      service.logout();
      expect(localStorage.getItem('accessToken')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
      expect(localStorage.getItem('currentUser')).toBeNull();
    });

    it('should navigate to /home', () => {
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      service.logout();
      expect(navigateSpy).toHaveBeenCalledWith(['/home']);
    });

    it('should set currentUser$ to null', () => {
      vi.spyOn(router, 'navigate').mockResolvedValue(true);
      service.logout();
      expect(service.getCurrentUser()).toBeNull();
    });
  });

  describe('getAccessToken', () => {
    it('should return the stored access token', () => {
      localStorage.setItem('accessToken', VALID_TOKEN);
      expect(service.getAccessToken()).toBe(VALID_TOKEN);
    });

    it('should return null when no token is stored', () => {
      expect(service.getAccessToken()).toBeNull();
    });
  });

  describe('getRefreshToken', () => {
    it('should return the stored refresh token', () => {
      localStorage.setItem('refreshToken', 'my-refresh');
      expect(service.getRefreshToken()).toBe('my-refresh');
    });

    it('should return null when no refresh token is stored', () => {
      expect(service.getRefreshToken()).toBeNull();
    });
  });

  describe('isLoggedIn', () => {
    it('should return false when no token is present', () => {
      expect(service.isLoggedIn()).toBe(false);
    });

    it('should return true with a valid non-expired token', () => {
      localStorage.setItem('accessToken', VALID_TOKEN);
      expect(service.isLoggedIn()).toBe(true);
    });

    it('should return false with an expired token', () => {
      localStorage.setItem('accessToken', EXPIRED_TOKEN);
      expect(service.isLoggedIn()).toBe(false);
    });

    it('should return false with a malformed token', () => {
      localStorage.setItem('accessToken', 'not-a-valid-jwt');
      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('getCurrentUser', () => {
    it('should return null when no user is logged in', () => {
      expect(service.getCurrentUser()).toBeNull();
    });

    it('should return the current user after login', () => {
      service.login({ email: 'test@example.com', password: 'pass' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
      expect(service.getCurrentUser()).toEqual(mockAuthResponse);
    });
  });

  describe('getUserRole', () => {
    it('should return null when not logged in', () => {
      expect(service.getUserRole()).toBeNull();
    });

    it('should return the role of the current user', () => {
      service.login({ email: 'test@example.com', password: 'pass' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
      expect(service.getUserRole()).toBe(UserRole.PATIENT);
    });
  });

  describe('hasRole', () => {
    beforeEach(() => {
      service.login({ email: 'test@example.com', password: 'pass' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
    });

    it('should return true when user has the specified role', () => {
      expect(service.hasRole(UserRole.PATIENT)).toBe(true);
    });

    it('should return false when user does not have the specified role', () => {
      expect(service.hasRole(UserRole.ADMIN)).toBe(false);
    });
  });

  describe('hasAnyRole', () => {
    it('should return false when not logged in', () => {
      expect(service.hasAnyRole(UserRole.PATIENT)).toBe(false);
    });

    it('should return true when user has one of the specified roles', () => {
      service.login({ email: 'test@example.com', password: 'pass' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
      expect(service.hasAnyRole(UserRole.ADMIN, UserRole.PATIENT)).toBe(true);
    });

    it('should return false when user has none of the specified roles', () => {
      service.login({ email: 'test@example.com', password: 'pass' }).subscribe();
      httpMock.expectOne('/api/auth/login').flush(mockAuthResponse);
      expect(service.hasAnyRole(UserRole.ADMIN, UserRole.SPECIALIST)).toBe(false);
    });
  });
});

describe('AuthService - localStorage initialization', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  it('should restore user from localStorage on service creation', () => {
    localStorage.setItem('currentUser', JSON.stringify(mockAuthResponse));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const svc = TestBed.inject(AuthService);
    expect(svc.getCurrentUser()).toEqual(mockAuthResponse);
  });

  it('should return null when stored user is invalid JSON', () => {
    localStorage.setItem('currentUser', 'not-valid-json');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const svc = TestBed.inject(AuthService);
    expect(svc.getCurrentUser()).toBeNull();
  });
});
