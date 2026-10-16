import { post, get } from './http'
import type {
  AuthVO,
  LoginPayload,
  RegisterPayload,
  UpdateProfilePayload,
  UserVO,
} from './types'

export const authApi = {
  register: (payload: RegisterPayload) => post<AuthVO>('/auth/register', payload),
  login: (payload: LoginPayload) => post<AuthVO>('/auth/login', payload),
  logout: () => post<void>('/auth/logout'),
  me: () => get<UserVO>('/auth/me'),
}

export const profileApi = {
  me: () => get<UserVO>('/profile'),
  update: (payload: UpdateProfilePayload) => post<UserVO>('/profile', payload),
}
