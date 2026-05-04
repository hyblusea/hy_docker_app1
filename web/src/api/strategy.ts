import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Strategy } from '../types/strategy'

export async function listStrategies(): Promise<Strategy[]> {
  return apiGet<Strategy[]>('/strategy/list')
}

export async function listValidStrategies(): Promise<Strategy[]> {
  return apiGet<Strategy[]>('/strategy/list-valid')
}

export async function getStrategy(id: number): Promise<Strategy> {
  return apiGet<Strategy>(`/strategy/${id}`)
}

export async function createStrategy(strategy: Omit<Strategy, 'id' | 'created_at' | 'updated_at'>): Promise<Strategy> {
  return apiPost<Strategy>('/strategy', strategy)
}

export async function updateStrategy(id: number, strategy: Partial<Strategy>): Promise<Strategy> {
  return apiPut<Strategy>(`/strategy/${id}`, strategy)
}

export async function deleteStrategy(id: number): Promise<void> {
  await apiDelete(`/strategy/${id}`)
}

export interface AiGenerateResult {
  suggestedName: string
  code: string
  valid: boolean
  compileError: string
}

export async function aiGenerateStrategy(buyDesc: string, sellDesc: string): Promise<AiGenerateResult> {
  return apiPost<AiGenerateResult>('/strategy/ai-generate', { buyDesc, sellDesc })
}

export function aiGenerateStrategyStream(
  buyDesc: string,
  sellDesc: string,
  onThinking: (chunk: string) => void,
  onResult: (result: AiGenerateResult) => void,
  onError: (msg: string) => void,
): AbortController {
  const controller = new AbortController()

  fetch('/api/strategy/ai-generate-stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ buyDesc, sellDesc }),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        onError(`请求失败: HTTP ${res.status}`)
        return
      }
      const reader = res.body?.getReader()
      if (!reader) { onError('无法读取流'); return }
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const parts = buffer.split('\n\n')
        buffer = parts.pop() || ''

        for (const part of parts) {
          currentEvent = ''
          let dataStr = ''
          for (const line of part.split('\n')) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              dataStr = line.slice(5).trim()
            }
          }
          if (!dataStr) continue
          if (currentEvent === 'thinking') {
            onThinking(dataStr)
          } else if (currentEvent === 'result') {
            try { onResult(JSON.parse(dataStr)) } catch { onError('解析结果失败') }
          } else if (currentEvent === 'error') {
            onError(dataStr)
          }
        }
      }
    })
    .catch((e) => {
      if (e.name !== 'AbortError') onError(e.message || '请求失败')
    })

  return controller
}
