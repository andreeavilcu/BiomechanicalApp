import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';

describe('AppComponent', () => {
  let mockAuthService: { isLoggedIn: ReturnType<typeof vi.fn>; currentUser$: BehaviorSubject<null> };

  beforeEach(async () => {
    mockAuthService = {
      isLoggedIn: vi.fn().mockReturnValue(false),
      currentUser$: new BehaviorSubject(null),
    };

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('showSidebar is false when not logged in', () => {
    mockAuthService.isLoggedIn.mockReturnValue(false);
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.showSidebar).toBe(false);
  });

  it('showSidebar is true when logged in and url is not /auth or /home', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.showSidebar).toBe(true);
  });

  it('renders sidebar when showSidebar is true', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-sidebar')).toBeTruthy();
  });

  it('does not render sidebar when showSidebar is false', () => {
    mockAuthService.isLoggedIn.mockReturnValue(false);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-sidebar')).toBeFalsy();
  });
});
