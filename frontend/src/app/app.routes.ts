import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { UserRole } from './core/models/user.model';

export const routes: Routes = [
  {
    path: 'home',
    loadComponent: () =>
      import('./features/landing/landing.component').then(m => m.LandingComponent)
  },

  // ── Auth ──────────────────────────────────────────────────────────────────
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () =>
          import('./features/auth/login/login.component').then(m => m.LoginComponent)
      },
      {
        path: 'register',
        loadComponent: () =>
          import('./features/auth/register/register.component').then(m => m.RegisterComponent)
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' }
    ]
  },


  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard-home/dashboard-home.component')
        .then(m => m.DashboardHomeComponent)
  },

  {
    path: 'scans',
    canActivate: [authGuard],
    children: [
      {
        path: 'upload',
        data: { roles: [UserRole.PATIENT, UserRole.SPECIALIST, UserRole.ADMIN] },
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/scans/scan-upload/scan-upload.component')
            .then(m => m.ScanUploadComponent)
      },
      {
        path: 'history',
        loadComponent: () =>
          import('./features/scans/scan-history/scan-history.component')
            .then(m => m.ScanHistoryComponent)
      },
      {
        path: ':sessionId',
        loadComponent: () =>
          import('./features/scans/scan-detail/scan-detail.component')
            .then(m => m.ScanDetailComponent)
      }
    ]
  },

  {
    path: 'specialist',
    canActivate: [authGuard],
    data: { roles: [UserRole.SPECIALIST, UserRole.ADMIN] },
    children: [
      {
        path: 'patients',
        loadComponent: () =>
          import('./features/specialist/patient-list/patient-list.component')
            .then(m => m.PatientListComponent)
      },
      {
        path: 'patients/:patientId',
        loadComponent: () =>
          import('./features/specialist/patient-detail/patient-detail.component')
            .then(m => m.PatientDetailComponent)
      }
    ]
  },

  {
    path: 'research',
    canActivate: [authGuard],
    data: { roles: [UserRole.RESEARCHER, UserRole.ADMIN] },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/research/aggregate-metrics/aggregate-metrics.component')
            .then(m => m.AggregateMetricsComponent)
      }
    ]
  },

  {
    path: 'admin',
    canActivate: [authGuard],
    data: { roles: [UserRole.ADMIN] },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin/user-management/user-management.component')
            .then(m => m.UserManagementComponent)
      }
    ]
  },

  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile.component')
        .then(m => m.ProfileComponent)
  },

  { path: '', redirectTo: '/home', pathMatch: 'full' },  
  { path: '**', redirectTo: '/home' }
];