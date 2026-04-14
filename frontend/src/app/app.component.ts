import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AuthService } from './core/services/auth.service';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [CommonModule, RouterOutlet, NavbarComponent],
    template: `
    @if (showNavbar) {
      <app-navbar />
    }
    <main [class.with-navbar]="showNavbar">
      <router-outlet />
    </main>
  `,
    styles: [`
    main.with-navbar {
      min-height: calc(100vh - 64px);
    }
  `]
})
export class AppComponent {
    private authService = inject(AuthService);
    private router = inject(Router);

    get showNavbar(): boolean {
        return this.authService.isLoggedIn() 
        && !this.router.url.startsWith('/auth')
        && !this.router.url.startsWith('/home');  
}
}