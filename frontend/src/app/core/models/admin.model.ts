export interface SystemStatsDTO {
  totalUsers: number;
  totalPatients: number;
  totalSpecialists: number;
  totalResearchers: number;
  totalAdmins: number;
  activeUsers: number;
}
export type ActionState = 'idle' | 'loading' | 'success' | 'error';

export interface UserAction {
  userId: number;
  type: 'role' | 'status';
  state: ActionState;
}
 
