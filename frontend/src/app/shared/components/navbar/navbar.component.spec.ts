import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { NavbarComponent } from './navbar.component';
import { AuthService } from '../../../core/services/auth.service';
import { AuthResponse } from '../../../core/models/auth.model';
import { UserRole } from '../../../core/models/user.model';

const makeUser = (role: UserRole, firstName = 'Ana', lastName = 'Pop'): AuthResponse => ({
  accessToken: 'tok', refreshToken: 'rt', role,
  userId: 1, email: 'a@b.com', firstName, lastName
});

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let currentUser$: BehaviorSubject<AuthResponse | null>;
  let mockAuthService: { currentUser$: BehaviorSubject<AuthResponse | null>; logout: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    currentUser$ = new BehaviorSubject<AuthResponse | null>(null);
    mockAuthService = { currentUser$, logout: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    // Each test emits to currentUser$ BEFORE detectChanges so ngOnInit's subscription
    // gets the value immediately (BehaviorSubject replays current value on subscribe).
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('visibleNavItems is empty when no current user', () => {
    fixture.detectChanges();
    expect(component.visibleNavItems).toEqual([]);
  });

  it('visibleNavItems returns only PATIENT-role items for patient user', () => {
    currentUser$.next(makeUser(UserRole.PATIENT));
    fixture.detectChanges();
    const items = component.visibleNavItems;
    expect(items.every(i => i.roles.includes(UserRole.PATIENT))).toBe(true);
    expect(items.some(i => i.label === 'My Patients')).toBe(false);
  });

  it('visibleNavItems includes My Patients for specialist', () => {
    currentUser$.next(makeUser(UserRole.SPECIALIST));
    fixture.detectChanges();
    expect(component.visibleNavItems.some(i => i.label === 'My Patients')).toBe(true);
  });

  it('userInitials returns first letters of first + last name uppercased', () => {
    currentUser$.next(makeUser(UserRole.PATIENT, 'Maria', 'Ionescu'));
    fixture.detectChanges();
    expect(component.userInitials).toBe('MI');
  });

  it('userInitials is empty string when no user', () => {
    fixture.detectChanges();
    expect(component.userInitials).toBe('');
  });

  it('roleLabel returns Patient for PATIENT role', () => {
    currentUser$.next(makeUser(UserRole.PATIENT));
    fixture.detectChanges();
    expect(component.roleLabel).toBe('Patient');
  });

  it('roleLabel returns Specialist for SPECIALIST role', () => {
    currentUser$.next(makeUser(UserRole.SPECIALIST));
    fixture.detectChanges();
    expect(component.roleLabel).toBe('Specialist');
  });

  it('roleLabel returns Researcher for RESEARCHER role', () => {
    currentUser$.next(makeUser(UserRole.RESEARCHER));
    fixture.detectChanges();
    expect(component.roleLabel).toBe('Researcher');
  });

  it('roleLabel returns Administrator for ADMIN role', () => {
    currentUser$.next(makeUser(UserRole.ADMIN));
    fixture.detectChanges();
    expect(component.roleLabel).toBe('Administrator');
  });

  it('toggleMobileMenu opens mobile menu and closes user menu', () => {
    fixture.detectChanges();
    component.isUserMenuOpen = true;
    component.toggleMobileMenu();
    expect(component.isMobileMenuOpen).toBe(true);
    expect(component.isUserMenuOpen).toBe(false);
  });

  it('toggleMobileMenu closes mobile menu when already open', () => {
    fixture.detectChanges();
    component.isMobileMenuOpen = true;
    component.toggleMobileMenu();
    expect(component.isMobileMenuOpen).toBe(false);
  });

  it('toggleUserMenu opens user menu and closes mobile menu', () => {
    fixture.detectChanges();
    component.isMobileMenuOpen = true;
    component.toggleUserMenu();
    expect(component.isUserMenuOpen).toBe(true);
    expect(component.isMobileMenuOpen).toBe(false);
  });

  it('closeMobileMenu closes mobile menu', () => {
    fixture.detectChanges();
    component.isMobileMenuOpen = true;
    component.closeMobileMenu();
    expect(component.isMobileMenuOpen).toBe(false);
  });

  it('logout calls authService.logout and closes menus', () => {
    fixture.detectChanges();
    component.isUserMenuOpen = true;
    component.isMobileMenuOpen = true;
    component.logout();
    expect(mockAuthService.logout).toHaveBeenCalled();
    expect(component.isUserMenuOpen).toBe(false);
    expect(component.isMobileMenuOpen).toBe(false);
  });

  it('renders dropdown-menu when isUserMenuOpen is true', () => {
    currentUser$.next(makeUser(UserRole.PATIENT));
    component.isUserMenuOpen = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dropdown-menu')).toBeTruthy();
  });

  it('does not render dropdown-menu when isUserMenuOpen is false', () => {
    fixture.detectChanges();
    component.isUserMenuOpen = false;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dropdown-menu')).toBeFalsy();
  });

  it('renders nav-links for patient user', () => {
    currentUser$.next(makeUser(UserRole.PATIENT));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.nav-link').length).toBeGreaterThan(0);
  });

  it('renders nav-links for specialist user including My Patients', () => {
    currentUser$.next(makeUser(UserRole.SPECIALIST));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const labels = Array.from(el.querySelectorAll('.nav-link span')).map(s => s.textContent?.trim());
    expect(labels).toContain('My Patients');
  });

  it('renders user initials in user-avatar', () => {
    currentUser$.next(makeUser(UserRole.PATIENT, 'Maria', 'Ionescu'));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.user-avatar')?.textContent?.trim()).toBe('MI');
  });
});
