import { apiGet, apiPost, apiPut, apiDelete } from './client'

export interface AuthUser {
  id: number
  username: string
  role: string
  status: string
  created_at?: string
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const encoded = btoa(password)
  return apiPost('/auth/login', { username, password: encoded })
}

export async function logout(): Promise<void> {
  await apiPost('/auth/logout')
}

export async function getMe(signal?: AbortSignal): Promise<AuthUser> {
  return apiGet('/auth/me', signal)
}

export async function register(username: string, password: string): Promise<AuthUser> {
  const encoded = btoa(password)
  return apiPost('/auth/register', { username, password: encoded })
}

export async function listUsers(signal?: AbortSignal): Promise<AuthUser[]> {
  return apiGet('/user/list', signal)
}

export async function updateUserStatus(id: number, status: string): Promise<AuthUser> {
  return apiPut(`/user/${id}/status`, { status })
}

export async function deleteUser(id: number): Promise<void> {
  await apiDelete(`/user/${id}`)
}

export async function createUser(username: string, password: string, role: string): Promise<AuthUser> {
  const encoded = btoa(password)
  return apiPost('/user', { username, password: encoded, role })
}

export async function getUserPassword(id: number): Promise<string> {
  return apiGet(`/user/${id}/password`)
}
