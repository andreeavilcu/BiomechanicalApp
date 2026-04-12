import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserDTO, ChangePasswordRequest } from '../models/user.model';

@Injectable({
  providedIn: 'root',
})
export class ProfileService {
  private readonly API_URL = '/api/patients';
  private http = inject(HttpClient);
 
  getProfile(): Observable<UserDTO> {
    return this.http.get<UserDTO>(`${this.API_URL}/profile`);
  }
 
  updateProfile(data: Partial<UserDTO>): Observable<UserDTO> {
    return this.http.put<UserDTO>(`${this.API_URL}/update_profile`, data);
  }
 
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/update_password`, request);
  }
}