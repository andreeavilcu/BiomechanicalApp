import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();
  private nextId = 0;

  success(message: string): void { this.show('success', message); }
  error(message: string): void { this.show('error', message, 6000); }
  warning(message: string): void { this.show('warning', message); }
  info(message: string): void { this.show('info', message); }

  private show(type: ToastType, message: string, duration = 4000): void {
    const id = this.nextId++;
    this._toasts.update(t => [...t, { id, type, message }]);
    setTimeout(() => this.dismiss(id), duration);
  }

  dismiss(id: number): void {
    this._toasts.update(t => t.filter(toast => toast.id !== id));
  }
}
