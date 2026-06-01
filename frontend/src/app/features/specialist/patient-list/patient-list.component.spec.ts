import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { PatientListComponent } from './patient-list.component';
import { SpecialistService } from '../../../core/services/specialist.service';
import { UserDTO, UserRole, Gender } from '../../../core/models/user.model';

const makePatient = (id: number, firstName = 'Ana', lastName = 'Pop', email = `p${id}@test.com`): UserDTO => ({
  id, email, firstName, lastName, dateOfBirth: '1990-01-01',
  gender: Gender.FEMALE, heightCm: 165, role: UserRole.PATIENT, isActive: true
});

describe('PatientListComponent', () => {
  let component: PatientListComponent;
  let fixture: ComponentFixture<PatientListComponent>;
  let mockSpecialistService: {
    getMyPatients: ReturnType<typeof vi.fn>;
    assignPatient: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(async () => {
    mockSpecialistService = {
      getMyPatients: vi.fn().mockReturnValue(of([makePatient(1), makePatient(2, 'Ion', 'Mihai')])),
      assignPatient: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PatientListComponent],
      providers: [
        provideRouter([]),
        { provide: SpecialistService, useValue: mockSpecialistService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads patients on init', () => {
    expect(component.allPatients.length).toBe(2);
    expect(component.filteredPatients.length).toBe(2);
    expect(component.isLoading).toBe(false);
  });

  it('applyFilter shows all patients when query is empty', () => {
    component.searchQuery = '';
    component.applyFilter();
    expect(component.filteredPatients.length).toBe(2);
  });

  it('applyFilter filters by firstName', () => {
    component.searchQuery = 'ion';
    component.applyFilter();
    expect(component.filteredPatients.length).toBe(1);
    expect(component.filteredPatients[0].firstName).toBe('Ion');
  });

  it('applyFilter filters by email', () => {
    component.searchQuery = 'p1@';
    component.applyFilter();
    expect(component.filteredPatients.length).toBe(1);
  });

  it('viewPatient navigates to /specialist/patients/:id', () => {
    component.viewPatient(3);
    expect(router.navigate).toHaveBeenCalledWith(['/specialist/patients', 3]);
  });

  it('getInitials returns uppercase first letters', () => {
    const patient = makePatient(1, 'Maria', 'Ionescu');
    expect(component.getInitials(patient)).toBe('MI');
  });

  it('getGenderLabel returns correct labels', () => {
    expect(component.getGenderLabel(Gender.MALE)).toBe('Male');
    expect(component.getGenderLabel(Gender.FEMALE)).toBe('Female');
    expect(component.getGenderLabel(Gender.OTHER)).toBe('Other');
  });

  it('openAssignModal resets assign state and shows modal', () => {
    component.assignEmail = 'existing@test.com';
    component.assignError = 'some error';
    component.openAssignModal();
    expect(component.showAssignModal).toBe(true);
    expect(component.assignEmail).toBe('');
    expect(component.assignError).toBeNull();
  });

  it('closeAssignModal hides modal', () => {
    component.showAssignModal = true;
    component.closeAssignModal();
    expect(component.showAssignModal).toBe(false);
  });

  it('submitAssign does nothing when assignEmail is empty', () => {
    component.assignEmail = '  ';
    component.submitAssign();
    expect(mockSpecialistService.assignPatient).not.toHaveBeenCalled();
  });

  it('submitAssign calls assignPatient and reloads on success', () => {
    mockSpecialistService.assignPatient.mockReturnValue(of(void 0));
    component.assignEmail = 'new@patient.com';
    component.submitAssign();
    expect(mockSpecialistService.assignPatient).toHaveBeenCalledWith('new@patient.com', undefined);
    expect(component.assignSuccess).toBe(true);
  });

  it('submitAssign passes referral reason when provided', () => {
    mockSpecialistService.assignPatient.mockReturnValue(of(void 0));
    component.assignEmail = 'new@patient.com';
    component.assignReason = 'Back pain';
    component.submitAssign();
    expect(mockSpecialistService.assignPatient).toHaveBeenCalledWith('new@patient.com', 'Back pain');
  });

  it('submitAssign sets assignError on failure', () => {
    mockSpecialistService.assignPatient.mockReturnValue(throwError(() => ({ message: 'Not found' })));
    component.assignEmail = 'x@y.com';
    component.submitAssign();
    expect(component.assignError).toBe('Not found');
    expect(component.isAssigning).toBe(false);
  });

  it('sets errorMessage when getMyPatients fails', () => {
    mockSpecialistService.getMyPatients.mockReturnValue(throwError(() => ({ message: 'Error' })));
    const f2 = TestBed.createComponent(PatientListComponent);
    f2.detectChanges();
    expect(f2.componentInstance.errorMessage).toBe('Error');
    expect(f2.componentInstance.isLoading).toBe(false);
  });

  it('renders loading spinner when isLoading is true', () => {
    component.isLoading = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.sk-patient')).toBeTruthy();
  });

  it('renders error-banner when errorMessage is set', () => {
    component.errorMessage = 'Something went wrong';
    component.isLoading = false;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.error-banner')).toBeTruthy();
  });

  it('renders empty-state with no-patients message when no patients and no search', () => {
    component.filteredPatients = [];
    component.searchQuery = '';
    component.isLoading = false;
    component.errorMessage = null;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state')).toBeTruthy();
    expect(el.querySelector('.empty-state h3')?.textContent).toContain('No patients assigned yet');
  });

  it('renders empty-state with no-results message when search finds nothing', () => {
    component.filteredPatients = [];
    component.searchQuery = 'xyz';
    component.isLoading = false;
    component.errorMessage = null;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state h3')?.textContent).toContain('No results for');
  });

  it('renders patient-grid with patient cards when filteredPatients is populated', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.patient-grid')).toBeTruthy();
    expect(el.querySelectorAll('.patient-card').length).toBe(2);
  });

  it('renders assign modal when showAssignModal is true', () => {
    component.showAssignModal = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.modal-overlay')).toBeTruthy();
    expect(el.querySelector('.modal')).toBeTruthy();
  });

  it('renders assign-success message in modal after successful assignment', () => {
    component.showAssignModal = true;
    component.assignSuccess = true;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.assign-success')).toBeTruthy();
  });

  it('renders assignError in modal when assignError is set', () => {
    component.showAssignModal = true;
    component.assignSuccess = false;
    component.assignError = 'Patient not found';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.assign-error')).toBeTruthy();
  });
});
