export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

const BASE_URL = '/api'

let onUnauthorized: (() => void) | null = null

export function setOnUnauthorized(cb: () => void) {
  onUnauthorized = cb
}

export async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    ...options,
  })
  if (res.status === 401) {
    onUnauthorized?.()
    throw new UnauthorizedError('未登录或会话已过期')
  }
  if (res.status === 403) {
    const body = await res.json().catch(() => ({ msg: '权限不足' }))
    throw new ForbiddenError(body.msg || '权限不足')
  }
  if (!res.ok) {
    const contentType = res.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const body = await res.json().catch(() => ({ msg: `HTTP ${res.status}` }))
      throw new Error(body.msg || `HTTP ${res.status}`)
    }
    if (res.status === 502 || res.status === 503 || res.status === 504) {
      throw new Error('服务器暂时无法响应，请稍后再试')
    }
    throw new Error(`请求失败: HTTP ${res.status}`)
  }
  return res.json()
}

export class UnauthorizedError extends Error {
  constructor(msg: string) {
    super(msg)
    this.name = 'UnauthorizedError'
  }
}

export class ForbiddenError extends Error {
  constructor(msg: string) {
    super(msg)
    this.name = 'ForbiddenError'
  }
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const resp = await fetchJson<ApiResponse<T>>(`${BASE_URL}${path}`, { signal })
  if (resp.code !== 0) {
    throw new Error(resp.msg || '请求失败')
  }
  return resp.data
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const resp = await fetchJson<ApiResponse<T>>(`${BASE_URL}${path}`, {
    method: 'POST',
    body: body ? JSON.stringify(body) : undefined,
  })
  if (resp.code !== 0) {
    throw new Error(resp.msg || '请求失败')
  }
  return resp.data
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const resp = await fetchJson<ApiResponse<T>>(`${BASE_URL}${path}`, {
    method: 'PUT',
    body: body ? JSON.stringify(body) : undefined,
  })
  if (resp.code !== 0) {
    throw new Error(resp.msg || '请求失败')
  }
  return resp.data
}

export async function apiDelete<T = void>(path: string): Promise<T> {
  const resp = await fetchJson<ApiResponse<T>>(`${BASE_URL}${path}`, { method: 'DELETE' })
  if (resp.code !== 0) {
    throw new Error(resp.msg || '请求失败')
  }
  return resp.data
}
