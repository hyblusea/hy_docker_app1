import { useState, useEffect, useCallback, useRef } from 'react'
import { listStrategies, listValidStrategies } from '../api/strategy'
import type { Strategy } from '../types/strategy'

interface CacheState {
  cache: Strategy[] | null
  fetchPromise: Promise<Strategy[]> | null
  version: number
}

const allCache: Record<string, CacheState> = {
  all: { cache: null, fetchPromise: null, version: 0 },
  valid: { cache: null, fetchPromise: null, version: 0 },
}

function useCachedStrategyList(cacheKey: string, fetchFn: () => Promise<Strategy[]>) {
  const [strategies, setStrategies] = useState<Strategy[]>(allCache[cacheKey].cache || [])
  const [loading, setLoading] = useState(!allCache[cacheKey].cache)
  const mountedRef = useRef(true)
  const lastVersionRef = useRef(allCache[cacheKey].version)

  useEffect(() => {
    mountedRef.current = true
    const state = allCache[cacheKey]

    const load = () => {
      if (state.cache) {
        setStrategies(state.cache)
        setLoading(false)
        lastVersionRef.current = state.version
        return
      }
      if (!state.fetchPromise) {
        state.fetchPromise = fetchFn()
      }
      state.fetchPromise
        .then((list) => {
          state.cache = list
          lastVersionRef.current = state.version
          if (mountedRef.current) {
            setStrategies(list)
            setLoading(false)
          }
        })
        .catch(() => {
          state.fetchPromise = null
          if (mountedRef.current) {
            setLoading(false)
          }
        })
    }

    load()

    const interval = setInterval(() => {
      if (lastVersionRef.current !== state.version) {
        load()
      }
    }, 200)

    return () => {
      mountedRef.current = false
      clearInterval(interval)
    }
  }, [cacheKey, fetchFn])

  const invalidate = useCallback(() => {
    const state = allCache[cacheKey]
    state.cache = null
    state.fetchPromise = null
    state.version++
    setLoading(true)
    const p = fetchFn()
    state.fetchPromise = p
    p.then((list) => {
      state.cache = list
      lastVersionRef.current = state.version
      if (mountedRef.current) {
        setStrategies(list)
        setLoading(false)
      }
    }).catch(() => {
      state.fetchPromise = null
      if (mountedRef.current) {
        setLoading(false)
      }
    })
  }, [cacheKey, fetchFn])

  return { strategies, loading, invalidate }
}

export function useStrategies() {
  return useCachedStrategyList('all', listStrategies)
}

export function useValidStrategies() {
  return useCachedStrategyList('valid', listValidStrategies)
}

export function clearStrategiesCache() {
  allCache.all.cache = null
  allCache.all.fetchPromise = null
  allCache.all.version++
}

export function clearValidStrategiesCache() {
  allCache.valid.cache = null
  allCache.valid.fetchPromise = null
  allCache.valid.version++
}
