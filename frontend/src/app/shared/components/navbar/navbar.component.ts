import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { AuthResponse } from '../../../core/models/auth.model';
import { UserRole } from '../../../core/models/user.model';


interface NavItem {
    label: string;
    route: string;
    icon: string;
    roles: UserRole[];
}

@Component({
    selector: 'app-navbar',
    standalone: true,
    imports: [CommonModule, RouterLink, RouterLinkActive],
    templateUrl: './navbar.component.html',
    styleUrl: './navbar.component.scss'
})
export class NavbarComponent implements OnInit, OnDestroy {
    currentUser: AuthResponse | null = null;
    isMenuOpen = false;
    private subscription = new Subscription();

    navItems: NavItem[] = [
        {
            label: 'Dashboard',
            route: '/dashboard',
            icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-4 0h4',
            roles: [UserRole.PATIENT, UserRole.SPECIALIST, UserRole.RESEARCHER, UserRole.ADMIN]
        },
        {
            label: 'Scanare nouă',
            route: '/scans/upload',
            icon: 'M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12',
            roles: [UserRole.PATIENT, UserRole.SPECIALIST, UserRole.ADMIN]
        },
        {
            label: 'Istoric scanări',
            route: '/scans/history',
            icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2',
            roles: [UserRole.PATIENT, UserRole.SPECIALIST, UserRole.ADMIN]
        },
        {
            label: 'Pacienții mei',
            route: '/specialist/patients',
            icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
            roles: [UserRole.SPECIALIST]
        },
        {
            label: 'Cercetare',
            route: '/research',
            icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
            roles: [UserRole.RESEARCHER, UserRole.ADMIN]
        },
        {
            label: 'Administrare',
            route: '/admin',
            icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z',
            roles: [UserRole.ADMIN]
        }
    ];

    private authService = inject(AuthService);
    private router = inject(Router);

    ngOnInit(): void {
        this.subscription.add(
            this.authService.currentUser$.subscribe(user => {
                this.currentUser = user;
            })
        );
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
    }

    get visibleNavItems(): NavItem[] {
        if (!this.currentUser) return [];
        return this.navItems.filter(item =>
            item.roles.includes(this.currentUser!.role)
        );
    }

    get userInitials(): string {
        if (!this.currentUser) return '';
        return (this.currentUser.firstName[0] + this.currentUser.lastName[0]).toUpperCase();
    }

    get roleLabel(): string {
        if (!this.currentUser) return '';
        const labels: Record<UserRole, string> = {
            [UserRole.PATIENT]: 'Pacient',
            [UserRole.SPECIALIST]: 'Specialist',
            [UserRole.RESEARCHER]: 'Cercetător',
            [UserRole.ADMIN]: 'Administrator'
        };
        return labels[this.currentUser.role];
    }

    toggleMenu(): void {
        this.isMenuOpen = !this.isMenuOpen;
    }

    logout(): void {
        this.isMenuOpen = false;
        this.authService.logout();
    }
}