import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AnalysisResultDTO } from '../models/scan.model';

@Injectable({
  providedIn: 'root',
})
export class ScanService {
  private readonly API_URL = '/api/scans';
  private  http = inject(HttpClient);

  uploadScan(file: File, userId: number, heightCm: number, scanType: string = 'LIDAR'): Observable<HttpEvent<AnalysisResultDTO>> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', userId.toString());
    formData.append('heightCm', heightCm.toString());
    formData.append('scanType', scanType);

    const req = new HttpRequest('POST', `${this.API_URL}/upload`, formData, {
      reportProgress: true
    });
    return this.http.request<AnalysisResultDTO>(req);
    
  }

  getMyHistory(): Observable<AnalysisResultDTO[]> {
    return this.http.get<AnalysisResultDTO[]>(`${this.API_URL}/my-history`);
  }

  getSession(sessionId: number): Observable<AnalysisResultDTO> {
    return this.http.get<AnalysisResultDTO>(`${this.API_URL}/${sessionId}`);
  }

  getUserHistory(userId: number): Observable<AnalysisResultDTO[]> {
    return this.http.get<AnalysisResultDTO[]>(`${this.API_URL}/user/${userId}/history`);
  }

  deleteSession(sessionId: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${sessionId}`);
  }
  
  getPointCloud(sessionId: number): Observable<ArrayBuffer> {
    return this.http.get(`${this.API_URL}/${sessionId}/point-cloud`, {
      responseType: 'arraybuffer'
    });
  }

}
