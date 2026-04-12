import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserDTO, UserRole } from '../models/user.model';
import { SystemStatsDTO } from '../models/admin.model';

@Injectable({
  providedIn: 'root',
})
export class AdminService {
  private readonly API_URL = '/api/admin';
  private http = inject(HttpClient);
 
  getSystemStats(): Observable<SystemStatsDTO> {
    return this.http.get<SystemStatsDTO>(`${this.API_URL}/stats`);
  }

  getAllUsers(role?: UserRole): Observable<UserDTO[]> {
    let params = new HttpParams();
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<UserDTO[]>(`${this.API_URL}/users`, { params });
  }
 
  updateUserRole(userId: number, newRole: UserRole): Observable<UserDTO> {
    const params = new HttpParams().set('newRole', newRole);
    return this.http.put<UserDTO>(`${this.API_URL}/users/${userId}/role`, null, { params });
  }

  toggleUserStatus(userId: number, active: boolean): Observable<UserDTO> {
    const params = new HttpParams().set('active', active.toString());
    return this.http.put<UserDTO>(`${this.API_URL}/users/${userId}/status`, null, { params });
  }
}
