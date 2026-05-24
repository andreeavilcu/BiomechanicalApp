import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { UserRole } from '../models/user.model';

const makeRoute = (roles?: UserRole[]): ActivatedRouteSnapshot => {
  const snap = {} as ActivatedRouteSnapshot;
  (snap as unknown as { data?: { roles?: UserRole[] } }).data = roles ? { roles } : {};
  return snap;
};

const makeState = (url = '/dashboard'): RouterStateSnapshot =>
  ({ url } as RouterStateSnapshot);

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (route, state) =>
    TestBed.runInInjectionContext(() => authGuard(route, state));

  let mockAuthService: { isLoggedIn: ReturnType<typeof vi.fn>; hasAnyRole: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(() => {
    mockAuthService = {
      isLoggedIn: vi.fn().mockReturnValue(true),
      hasAnyRole: vi.fn().mockReturnValue(true),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuthService },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  it('allows access when logged in and no role restriction', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    const result = executeGuard(makeRoute(), makeState('/dashboard'));
    expect(result).toBe(true);
  });

  it('redirects to /auth/login with returnUrl when not logged in', () => {
    mockAuthService.isLoggedIn.mockReturnValue(false);
    const result = executeGuard(makeRoute(), makeState('/scans/history'));
    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(
      ['/auth/login'],
      { queryParams: { returnUrl: '/scans/history' } }
    );
  });

  it('allows access when logged in and user has required role', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    mockAuthService.hasAnyRole.mockReturnValue(true);
    const result = executeGuard(makeRoute([UserRole.ADMIN]), makeState('/admin'));
    expect(result).toBe(true);
    expect(mockAuthService.hasAnyRole).toHaveBeenCalledWith(UserRole.ADMIN);
  });

  it('redirects to /dashboard when logged in but lacks required role', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    mockAuthService.hasAnyRole.mockReturnValue(false);
    const result = executeGuard(makeRoute([UserRole.ADMIN]), makeState('/admin'));
    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('allows access when route has no role data', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    const result = executeGuard(makeRoute(undefined), makeState('/dashboard'));
    expect(result).toBe(true);
    expect(mockAuthService.hasAnyRole).not.toHaveBeenCalled();
  });

  it('allows access when route has empty roles array', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    const result = executeGuard(makeRoute([]), makeState('/dashboard'));
    expect(result).toBe(true);
  });
});
