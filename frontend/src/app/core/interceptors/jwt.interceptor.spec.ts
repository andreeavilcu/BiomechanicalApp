import { TestBed } from '@angular/core/testing';
import {
  HttpInterceptorFn, HttpRequest, HttpHandlerFn,
  HttpErrorResponse, HttpResponse
} from '@angular/common/http';
import { of, throwError, firstValueFrom } from 'rxjs';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';

const executeInterceptor: HttpInterceptorFn = (req, next) =>
  TestBed.runInInjectionContext(() => jwtInterceptor(req, next));

describe('jwtInterceptor', () => {
  let mockAuthService: {
    getAccessToken: ReturnType<typeof vi.fn>;
    refreshToken: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockAuthService = {
      getAccessToken: vi.fn().mockReturnValue(null),
      refreshToken: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: mockAuthService }],
    });
  });

  it('should be created', () => {
    expect(executeInterceptor).toBeTruthy();
  });

  it('passes auth URLs through without adding Authorization header', async () => {
    mockAuthService.getAccessToken.mockReturnValue('token123');
    const req = new HttpRequest('POST', '/api/auth/login', {});
    let capturedReq: HttpRequest<unknown> | null = null;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r as HttpRequest<unknown>;
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(executeInterceptor(req, next));
    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });

  it('adds Authorization Bearer header when token is present', async () => {
    mockAuthService.getAccessToken.mockReturnValue('mytoken');
    const req = new HttpRequest('GET', '/api/scans/history');
    let capturedReq: HttpRequest<unknown> | null = null;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r as HttpRequest<unknown>;
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(executeInterceptor(req, next));
    expect(capturedReq!.headers.get('Authorization')).toBe('Bearer mytoken');
  });

  it('does not add Authorization header when no token', async () => {
    mockAuthService.getAccessToken.mockReturnValue(null);
    const req = new HttpRequest('GET', '/api/scans/history');
    let capturedReq: HttpRequest<unknown> | null = null;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r as HttpRequest<unknown>;
      return of(new HttpResponse({ status: 200 }));
    };

    await firstValueFrom(executeInterceptor(req, next));
    expect(capturedReq!.headers.has('Authorization')).toBe(false);
  });

  it('calls refreshToken on 401 and retries with new token', async () => {
    mockAuthService.getAccessToken.mockReturnValue('old-token');
    mockAuthService.refreshToken.mockReturnValue(of({ accessToken: 'new-token', refreshToken: 'rt' }));

    const req = new HttpRequest('GET', '/api/scans/history');
    let callCount = 0;
    const next: HttpHandlerFn = () => {
      callCount++;
      if (callCount === 1) return throwError(() => new HttpErrorResponse({ status: 401 }));
      return of(new HttpResponse({ status: 200, body: 'retried' }));
    };

    const result = await firstValueFrom(executeInterceptor(req, next)) as HttpResponse<string>;
    expect(result.body).toBe('retried');
    expect(mockAuthService.refreshToken).toHaveBeenCalled();
  });

  it('calls logout when refresh token fails on 401', async () => {
    mockAuthService.getAccessToken.mockReturnValue('old-token');
    mockAuthService.refreshToken.mockReturnValue(throwError(() => new Error('refresh failed')));

    const req = new HttpRequest('GET', '/api/scans/history');
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 401 }));

    await expect(firstValueFrom(executeInterceptor(req, next))).rejects.toBeDefined();
    expect(mockAuthService.logout).toHaveBeenCalled();
  });

  it('passes non-401 errors through without refreshing', async () => {
    mockAuthService.getAccessToken.mockReturnValue('token');
    const req = new HttpRequest('GET', '/api/scans/history');
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 500 }));

    await expect(firstValueFrom(executeInterceptor(req, next))).rejects.toMatchObject({ status: 500 });
    expect(mockAuthService.refreshToken).not.toHaveBeenCalled();
  });
});
