import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AggregateMetricsDTO, PostureTrendDTO } from '../models/research.model';

@Injectable({
  providedIn: 'root',
})
export class ResearchService {
  private http = inject(HttpClient);
  private readonly base = '/api/research';

  getAggregateMetrics(from?: string, to?: string): Observable<AggregateMetricsDTO> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to)   params = params.set('to', to);
    return this.http.get<AggregateMetricsDTO>(`${this.base}/metrics/aggregate`, { params });
  }

  getPostureTrends(lastDays: number = 90): Observable<PostureTrendDTO[]> {
    const params = new HttpParams().set('lastDays', lastDays.toString());
    return this.http.get<PostureTrendDTO[]>(`${this.base}/posture-trends`, { params });
  }

  exportCsv(): Observable<string> {
    return this.http.get(`${this.base}/export/csv`, { responseType: 'text' });
  }
}
