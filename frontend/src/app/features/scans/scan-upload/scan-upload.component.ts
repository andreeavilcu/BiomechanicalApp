import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpEventType } from '@angular/common/http';
import { ScanService } from '../../../core/services/scan.service';
import { AuthService } from '../../../core/services/auth.service';
import { AnalysisResultDTO } from '../../../core/models/scan.model';

@Component({
  selector: 'app-scan-upload',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './scan-upload.component.html',
  styleUrl: './scan-upload.component.scss',
})
export class ScanUploadComponent {
  private scanService = inject(ScanService);
  private authService = inject(AuthService)
  private router = inject(Router);

  selectedFile: File | null = null;
  isDragOver = false;
  isUploading = false;
  uploadProgress = 0;
  processingStatus: 'idle' | 'uploading' | 'processing' | 'completed' | 'error' = 'idle';
  errorMessage: string | null = null;
  result: AnalysisResultDTO | null = null;

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.setFile(input.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.setFile(event.dataTransfer.files[0]);
    }
  }

  private setFile(file: File): void {
    this.errorMessage = null;

    const validExtensions = ['.ply'];
    const fileName = file.name.toLowerCase();
    const isValid = validExtensions.some(ext => fileName.endsWith(ext));

    if (!isValid) {
      this.errorMessage = 'Invalid format. Only .ply files are accepted.';
      return;
    }

    if (file.size > 200 * 1024 * 1024) {
      this.errorMessage = 'File exceeds the 200MB limit.';
      return;
    }

    this.selectedFile = file;
    this.result = null;
    this.processingStatus = 'idle';
  }

  removeFile(): void {
    this.selectedFile = null;
    this.result = null;
    this.processingStatus = 'idle';
    this.errorMessage = null;
  }

  get fileSizeMB(): string {
    if (!this.selectedFile) return '0';
    return (this.selectedFile.size / (1024 * 1024)).toFixed(1);
  }

  upload(): void {
    if (!this.selectedFile) return;

    const user = this.authService.getCurrentUser();
    if (!user) return;

    this.isUploading = true;
    this.uploadProgress = 0;
    this.processingStatus = 'uploading';
    this.errorMessage = null;

    const heightCm = typeof user.heightCm === 'number' ? user.heightCm : 170;

    this.scanService.uploadScan(
      this.selectedFile,
      user.userId,
      heightCm,
      'LIDAR'
    ).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          this.uploadProgress = event.total
            ? Math.round((event.loaded / event.total) * 100)
            : 0;
          if (this.uploadProgress >= 100) {
            this.processingStatus = 'processing';
          }
        } else if (event.type === HttpEventType.Response) {
          this.result = event.body as AnalysisResultDTO;
          this.processingStatus = 'completed';
          this.isUploading = false;
        }
      },
      error: (err) => {
        this.isUploading = false;
        this.processingStatus = 'error';
        this.errorMessage = err.message || 'Upload failed.';
      }
    });
  }

  viewResult(): void {
    if (this.result) {
      this.router.navigate(['/scans', this.result.sessionId]);
    }
  }

}
