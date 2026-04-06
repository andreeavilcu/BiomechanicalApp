import { UserRole, Gender } from './user.model';

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    dateOfBirth: string;
    gender: Gender;
    heightCm: number;
}

export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    role: UserRole;
    userId: number;
    email: string;
    firstName: string;
    lastName: string;
}