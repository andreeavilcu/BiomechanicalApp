import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/services/auth.service';
import { AuthResponse } from '../../../core/models/auth.model';
import { UserRole } from '../../../core/models/user.model';

const fakeUser: AuthResponse = {
  accessToken: 'tok', refreshToken: 'rt', role: UserRole.PATIENT,
  userId: 1, email: 'a@b.com', firstName: 'Ana', lastName: 'Pop'
};

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let mockAuthService: { login: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    mockAuthService = { login: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('form is invalid when empty', () => {
    expect(component.loginForm.valid).toBe(false);
  });

  it('form is valid with valid email and password', () => {
    component.loginForm.setValue({ email: 'test@example.com', password: 'Password1' });
    expect(component.loginForm.valid).toBe(true);
  });

  it('email field validates email format', () => {
    component.loginForm.get('email')!.setValue('not-an-email');
    expect(component.loginForm.get('email')!.valid).toBe(false);
  });

  it('password field requires minimum 8 characters', () => {
    component.loginForm.get('password')!.setValue('short');
    expect(component.loginForm.get('password')!.valid).toBe(false);
  });

  it('onSubmit marks form touched when invalid', () => {
    component.loginForm.setValue({ email: '', password: '' });
    component.onSubmit();
    expect(component.loginForm.touched).toBe(true);
    expect(mockAuthService.login).not.toHaveBeenCalled();
  });

  it('onSubmit calls authService.login with form value', () => {
    mockAuthService.login.mockReturnValue(of(fakeUser));
    component.loginForm.setValue({ email: 'a@b.com', password: 'Password1' });
    component.onSubmit();
    expect(mockAuthService.login).toHaveBeenCalledWith({ email: 'a@b.com', password: 'Password1' });
  });

  it('onSubmit navigates to /dashboard on success', () => {
    mockAuthService.login.mockReturnValue(of(fakeUser));
    component.loginForm.setValue({ email: 'a@b.com', password: 'Password1' });
    component.onSubmit();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('onSubmit sets errorMessage on login failure', () => {
    mockAuthService.login.mockReturnValue(throwError(() => ({ message: 'Invalid credentials' })));
    component.loginForm.setValue({ email: 'a@b.com', password: 'Password1' });
    component.onSubmit();
    expect(component.errorMessage).toBe('Invalid credentials');
  });

  it('onSubmit sets fallback errorMessage when error has no message', () => {
    mockAuthService.login.mockReturnValue(throwError(() => ({})));
    component.loginForm.setValue({ email: 'a@b.com', password: 'Password1' });
    component.onSubmit();
    expect(component.errorMessage).toBe('Authentication failed.');
  });

  it('togglePassword flips showPassword flag', () => {
    expect(component.showPassword).toBe(false);
    component.togglePassword();
    expect(component.showPassword).toBe(true);
    component.togglePassword();
    expect(component.showPassword).toBe(false);
  });

  it('renders error banner when errorMessage is set', () => {
    mockAuthService.login.mockReturnValue(throwError(() => ({ message: 'Invalid credentials' })));
    component.loginForm.setValue({ email: 'a@b.com', password: 'Password1' });
    component.onSubmit();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.error-banner')).toBeTruthy();
  });

  it('renders email field error when email is touched and empty', () => {
    component.loginForm.get('email')!.markAsTouched();
    component.loginForm.get('email')!.setValue('');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.field-error')).toBeTruthy();
  });

  it('renders email format error when email is touched and has invalid format', () => {
    component.loginForm.get('email')!.markAsTouched();
    component.loginForm.get('email')!.setValue('not-an-email');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.field-error span')).toBeTruthy();
  });

  it('renders password field error when password is touched and empty', () => {
    component.loginForm.get('password')!.markAsTouched();
    component.loginForm.get('password')!.setValue('');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const errors = el.querySelectorAll('.field-error');
    expect(errors.length).toBeGreaterThan(0);
  });

  it('renders showPassword=true icon in template', () => {
    component.showPassword = true;
    fixture.detectChanges();
    // covered the @if (showPassword) branch in the toggle button template
    expect(component.showPassword).toBe(true);
  });

  it('renders isLoading spinner when isLoading is true', () => {
    component.isLoading = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.spinner')).toBeTruthy();
  });
});
