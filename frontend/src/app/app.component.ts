import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { SidebarComponent } from './shared/components/sidebar/sidebar.component';
import { ToastComponent } from './shared/components/toast/toast.component';
import { ConfirmDialogComponent } from './shared/components/confirm-dialog/confirm-dialog.component';
import { AuthService } from './core/services/auth.service';
import { LayoutService } from './core/services/layout.service';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [CommonModule, RouterOutlet, SidebarComponent, ToastComponent, ConfirmDialogComponent],
    template: `
    @if (showSidebar) {
      <app-sidebar />
    }
    <main
      [class.with-sidebar]="showSidebar"
      [class.sidebar-collapsed]="showSidebar && layout.isCollapsed()"
    >
      <router-outlet />
    </main>
    <app-toast />
    <app-confirm-dialog />
  `,
    styles: [`
    main { min-height: 100vh; }

    main.with-sidebar {
      margin-left: 240px;
      transition: margin-left 0.22s ease;
    }

    main.with-sidebar.sidebar-collapsed {
      margin-left: 64px;
    }

    @media (max-width: 768px) {
      main.with-sidebar {
        margin-left: 0 !important;
        padding-top: 56px;
      }
    }
  `]
})
export class AppComponent {
    private authService = inject(AuthService);
    private router = inject(Router);
    readonly layout = inject(LayoutService);

    get showSidebar(): boolean {
        return this.authService.isLoggedIn()
            && !this.router.url.startsWith('/auth')
            && !this.router.url.startsWith('/home');
    }
}