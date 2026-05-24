import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../../core/services/auth.service';
import { Gender } from '../../../core/models/user.model';
import { AuthResponse } from '../../../core/models/auth.model';
import { UserRole } from '../../../core/models/user.model';

const fakeUser: AuthResponse = {
  accessToken: 'tok', refreshToken: 'rt', role: UserRole.PATIENT,
  userId: 1, email: 'a@b.com', firstName: 'Ana', lastName: 'Pop'
};

const validFormValue = {
  email: 'test@example.com',
  password: 'Password1',
  confirmPassword: 'Password1',
  firstName: 'Ana',
  lastName: 'Pop',
  dateOfBirth: '1990-01-01',
  gender: Gender.FEMALE,
  heightCm: 165,
};

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let mockAuthService: { register: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    mockAuthService = { register: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('form is invalid when empty', () => {
    expect(component.registerForm.valid).toBe(false);
  });

  it('form is valid with all required fields filled', () => {
    component.registerForm.setValue(validFormValue);
    expect(component.registerForm.valid).toBe(true);
  });

  it('onSubmit marks form touched when invalid', () => {
    component.onSubmit();
    expect(component.registerForm.touched).toBe(true);
    expect(mockAuthService.register).not.toHaveBeenCalled();
  });

  it('onSubmit sets errorMessage when passwords do not match', () => {
    component.registerForm.setValue({ ...validFormValue, confirmPassword: 'Different1' });
    component.onSubmit();
    expect(component.errorMessage).toBe('Passwords do not match.');
    expect(mockAuthService.register).not.toHaveBeenCalled();
  });

  it('onSubmit calls authService.register without confirmPassword', () => {
    mockAuthService.register.mockReturnValue(of(fakeUser));
    component.registerForm.setValue(validFormValue);
    component.onSubmit();
    const calledWith = mockAuthService.register.mock.calls[0][0];
    expect(calledWith.confirmPassword).toBeUndefined();
    expect(calledWith.email).toBe('test@example.com');
  });

  it('onSubmit navigates to /dashboard on success', () => {
    mockAuthService.register.mockReturnValue(of(fakeUser));
    component.registerForm.setValue(validFormValue);
    component.onSubmit();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('onSubmit sets errorMessage on register failure', () => {
    mockAuthService.register.mockReturnValue(throwError(() => ({ message: 'Email already taken' })));
    component.registerForm.setValue(validFormValue);
    component.onSubmit();
    expect(component.errorMessage).toBe('Email already taken');
  });

  it('heightCm validates min=50 and max=250', () => {
    const ctrl = component.registerForm.get('heightCm')!;
    ctrl.setValue(30);
    expect(ctrl.valid).toBe(false);
    ctrl.setValue(300);
    expect(ctrl.valid).toBe(false);
    ctrl.setValue(170);
    expect(ctrl.valid).toBe(true);
  });

  it('getGenderLabel returns correct labels', () => {
    expect(component.getGenderLabel(Gender.MALE)).toBe('Male');
    expect(component.getGenderLabel(Gender.FEMALE)).toBe('Female');
    expect(component.getGenderLabel(Gender.OTHER)).toBe('Other');
  });

  it('togglePassword flips showPassword flag', () => {
    expect(component.showPassword).toBe(false);
    component.togglePassword();
    expect(component.showPassword).toBe(true);
  });
});
