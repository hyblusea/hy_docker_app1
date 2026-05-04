export interface Strategy {
  id?: number
  name: string
  language: string
  code: string
  valid?: boolean
  compile_error?: string
  created_by?: string
  created_at?: string
  updated_at?: string
}
