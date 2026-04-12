export enum UserRole {
  PATIENT = 'PATIENT',
  SPECIALIST = 'SPECIALIST',
  RESEARCHER = 'RESEARCHER',
  ADMIN = 'ADMIN'
}
 
export enum Gender {
  MALE = 'MALE',
  FEMALE = 'FEMALE',
  OTHER = 'OTHER'
}
 
export interface UserDTO {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: Gender;
  heightCm: number | null;
  age?: number;
  role?: UserRole;
  isActive?: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}