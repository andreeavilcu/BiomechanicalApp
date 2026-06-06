import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { ScanUploadComponent } from './scan-upload.component';
import { ScanService } from '../../../core/services/scan.service';
import { AuthService } from '../../../core/services/auth.service';
import { AuthResponse } from '../../../core/models/auth.model';
import { UserRole } from '../../../core/models/user.model';
import { AnalysisResultDTO, ProcessingStatus, RiskLevel } from '../../../core/models/scan.model';

const makeUser = (): AuthResponse => ({
  accessToken: 'tok', refreshToken: 'rt', role: UserRole.PATIENT,
  userId: 1, email: 'a@b.com', firstName: 'Ana', lastName: 'Pop', heightCm: 170
});

const makePlyFile = (name = 'scan.ply', sizeBytes = 1024): File =>
  new File(['x'.repeat(sizeBytes)], name, { type: 'application/octet-stream' });

const makeResult = (): AnalysisResultDTO => ({
  sessionId: 99, scanDate: '2025-01-01T10:00:00', status: ProcessingStatus.COMPLETED,
  errorMessage: null, processingMethod: 'LIDAR', aiConfidenceScore: 0.9, scalingFactor: 1,
  qAngleLeft: 10, qAngleRight: 10, fhpAngle: 5, fhpDistanceCm: 2, shoulderAsymmetryCm: 1,
  globalPostureScore: 80, riskLevel: RiskLevel.LOW,
  recommendations: [], globalFeedback: '', medicalDisclaimer: false,
  evolution: null, keypoints: [], targetHeightMeters: null
});

describe('ScanUploadComponent', () => {
  let component: ScanUploadComponent;
  let fixture: ComponentFixture<ScanUploadComponent>;
  let mockScanService: { uploadScan: ReturnType<typeof vi.fn> };
  let mockAuthService: { getCurrentUser: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    mockScanService = { uploadScan: vi.fn() };
    mockAuthService = { getCurrentUser: vi.fn().mockReturnValue(makeUser()) };

    await TestBed.configureTestingModule({
      imports: [ScanUploadComponent],
      providers: [
        provideRouter([]),
        { provide: ScanService, useValue: mockScanService },
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScanUploadComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('setFile via onFileSelected accepts .ply files', () => {
    const file = makePlyFile();
    const event = { target: { files: [file] } } as unknown as Event;
    component.onFileSelected(event);
    expect(component.selectedFile).toBe(file);
    expect(component.errorMessage).toBeNull();
  });

  it('setFile rejects non-.ply files', () => {
    const file = new File(['data'], 'scan.obj', { type: 'application/octet-stream' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onFileSelected(event);
    expect(component.selectedFile).toBeNull();
    expect(component.errorMessage).toBe('Invalid format. Only .ply files are accepted.');
  });

  it('setFile rejects files over 500MB', () => {
    const bigFile = { name: 'scan.ply', size: 501 * 1024 * 1024 } as File;
    const event = { target: { files: [bigFile] } } as unknown as Event;
    component.onFileSelected(event);
    expect(component.selectedFile).toBeNull();
    expect(component.errorMessage).toBe('File exceeds the 500MB limit.');
  });

  it('fileSizeMB returns 0 when no file selected', () => {
    component.selectedFile = null;
    expect(component.fileSizeMB).toBe('0');
  });

  it('fileSizeMB returns size in MB', () => {
    component.selectedFile = makePlyFile('scan.ply', 1024 * 1024);
    expect(component.fileSizeMB).toBe('1.0');
  });

  it('removeFile clears file and resets state', () => {
    component.selectedFile = makePlyFile();
    component.result = makeResult();
    component.removeFile();
    expect(component.selectedFile).toBeNull();
    expect(component.result).toBeNull();
    expect(component.processingStatus).toBe('idle');
    expect(component.errorMessage).toBeNull();
  });

  it('upload does nothing when no file selected', () => {
    component.selectedFile = null;
    component.upload();
    expect(mockScanService.uploadScan).not.toHaveBeenCalled();
  });

  it('upload does nothing when no current user', () => {
    mockAuthService.getCurrentUser.mockReturnValue(null);
    component.selectedFile = makePlyFile();
    component.upload();
    expect(mockScanService.uploadScan).not.toHaveBeenCalled();
  });

  it('upload sets processingStatus to completed on HttpResponse event', () => {
    const result = makeResult();
    mockScanService.uploadScan.mockReturnValue(of(new HttpResponse({ status: 200, body: result })));
    component.selectedFile = makePlyFile();
    component.upload();
    expect(component.result).toEqual(result);
    expect(component.processingStatus).toBe('completed');
    expect(component.isUploading).toBe(false);
  });

  it('upload updates progress on UploadProgress event', () => {
    const progressEvent = { type: HttpEventType.UploadProgress, loaded: 50, total: 100 };
    mockScanService.uploadScan.mockReturnValue(of(progressEvent));
    component.selectedFile = makePlyFile();
    component.upload();
    expect(component.uploadProgress).toBe(50);
  });

  it('upload sets processingStatus to error on failure', () => {
    mockScanService.uploadScan.mockReturnValue(throwError(() => ({ message: 'Upload failed' })));
    component.selectedFile = makePlyFile();
    component.upload();
    expect(component.processingStatus).toBe('error');
    expect(component.errorMessage).toBe('Upload failed');
    expect(component.isUploading).toBe(false);
  });

  it('viewResult navigates to /scans/:sessionId', () => {
    component.result = makeResult();
    component.viewResult();
    expect(router.navigate).toHaveBeenCalledWith(['/scans', 99]);
  });

  it('viewResult does nothing when result is null', () => {
    component.result = null;
    component.viewResult();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('renders drop-zone when no file selected', () => {
    component.selectedFile = null;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.drop-zone')).toBeTruthy();
  });

  it('renders file-preview section when selectedFile is set', () => {
    component.selectedFile = makePlyFile();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.file-preview')).toBeTruthy();
    expect(el.querySelector('.file-name')).toBeTruthy();
  });

  it('renders remove button when processing is idle', () => {
    component.selectedFile = makePlyFile();
    component.processingStatus = 'idle';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.btn-remove')).toBeTruthy();
  });

  it('renders progress section when uploading', () => {
    component.selectedFile = makePlyFile();
    component.processingStatus = 'uploading';
    component.uploadProgress = 50;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.progress-section')).toBeTruthy();
  });

  it('renders processing spinner when processingStatus is processing', () => {
    component.selectedFile = makePlyFile();
    component.processingStatus = 'processing';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.processing-text')).toBeTruthy();
  });

  it('renders status-success when completed', () => {
    component.selectedFile = makePlyFile();
    component.processingStatus = 'completed';
    component.result = makeResult();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.status-success')).toBeTruthy();
  });

  it('renders status-error when error', () => {
    component.selectedFile = makePlyFile();
    component.processingStatus = 'error';
    component.errorMessage = 'Upload failed';
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.status-error')).toBeTruthy();
  });
});
