export interface UserDto {
  id: string;
  username: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  user: UserDto;
}

export interface HealthResponse {
  status: string;
  application: string;
  timestamp: string;
}

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
}
