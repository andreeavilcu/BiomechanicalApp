import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  message: string;
  detail?: string;
  confirmLabel?: string;
  cancelLabel?: string;
}

interface ConfirmState extends ConfirmOptions {
  resolve: (v: boolean) => void;
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private _state = signal<ConfirmState | null>(null);
  readonly state = this._state.asReadonly();

  open(options: ConfirmOptions): Promise<boolean> {
    return new Promise(resolve => {
      this._state.set({ ...options, resolve });
    });
  }

  respond(value: boolean): void {
    this._state()?.resolve(value);
    this._state.set(null);
  }
}
