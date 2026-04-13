import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserDTO } from '../models/user.model';
import { AnalysisResultDTO } from '../models/scan.model';

@Injectable({
  providedIn: 'root',
})
export class SpecialistService {
  private readonly API_URL = '/api/patients';
  private http = inject(HttpClient);

  getMyPatients(): Observable<UserDTO[]> {
    return this.http.get<UserDTO[]>(`${this.API_URL}/my-patients`);
  }

  getPatientHistory(patientId: number): Observable<AnalysisResultDTO[]> {
    return this.http.get<AnalysisResultDTO[]>(`${this.API_URL}/${patientId}/history`);
  }

  getPatientReport(patientId: number, sessionId: number): Observable<AnalysisResultDTO> {
    return this.http.get<AnalysisResultDTO>(`${this.API_URL}/${patientId}/reports/${sessionId}`);
  }

  getClinicalNotes(patientId: number): Observable<string> {
    return this.http.get(`${this.API_URL}/${patientId}/notes`, { responseType: 'text' });
  }

  saveClinicalNotes(patientId: number, notes: string): Observable<void> {
    return this.http.put<void>(
      `${this.API_URL}/${patientId}/notes`,
      notes,
      { headers: { 'Content-Type': 'application/json' } }
    );
  }

  assignPatient(patientEmail: string, referralReason?: string): Observable<void> {
    let params = new HttpParams().set('patientEmail', patientEmail);
    if (referralReason) {
      params = params.set('referralReason', referralReason);
    }
    return this.http.post<void>(`${this.API_URL}/assign`, null, { params });
  }
}