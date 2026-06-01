import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LayoutService {
  private _collapsed = signal<boolean>(
    localStorage.getItem('sidebar-collapsed') === 'true'
  );

  readonly isCollapsed = this._collapsed.asReadonly();

  toggleCollapsed(): void {
    const next = !this._collapsed();
    this._collapsed.set(next);
    localStorage.setItem('sidebar-collapsed', String(next));
  }
}
