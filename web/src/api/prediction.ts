import { apiGet, apiPost, apiDelete } from './client'

export interface TrainingTask {
  id: number
  symbol: string | null
  modelName: string | null
  modelVersionId: number | null
  status: string
  epochs: number | null
  batchSize: number | null
  seqLength: number | null
  predictionHorizon: number | null
  learningRate: number | null
  trainAll: boolean | null
  currentEpoch: number | null
  trainLoss: number | null
  validLoss: number | null
  bestValidLoss: number | null
  progressPct: number | null
  outputLog: string | null
  errorMessage: string | null
  startTime: string | null
  endTime: string | null
  createdAt: string
}

export interface ModelVersion {
  id: number
  modelName: string
  version: string
  status: string
  filePath: string | null
  metrics: string | null
  engineType: string | null
  bestValidLoss: number | null
  directionAccuracy: number | null
  createdAt: string
}

export interface PredictionResult {
  success: boolean
  symbol: string
  signal: string
  confidence: number
  prediction: number
  direction?: string
  magnitude?: number
  probabilities?: {
    down: number
    flat: number
    up: number
  }
}

export interface TrainingRequest {
  symbol?: string
  epochs?: number
  batchSize?: number
  seqLength?: number
  predictionHorizon?: number
  learningRate?: number
  modelName?: string
  trainAll?: boolean
  dbHost?: string
  dbPort?: number
  dbUser?: string
  dbPassword?: string
  dbName?: string
  startDate?: string
  endDate?: string
}

export interface LossHistoryItem {
  epoch: number
  train_loss: number
  val_loss: number
  train_acc: number | null
  val_acc: number | null
}

export const submitTraining = (req: TrainingRequest) =>
  apiPost<TrainingTask>('/training/submit', req)

export const submitAllTraining = (req: TrainingRequest) =>
  apiPost<TrainingTask>('/training/submit-all', req)

export const getTrainingTasks = () =>
  apiGet<TrainingTask[]>('/training/tasks')

export const getTrainingTask = (id: number) =>
  apiGet<TrainingTask>(`/training/tasks/${id}`)

export const cancelTrainingTask = (id: number) =>
  apiPost<boolean>(`/training/tasks/${id}/cancel`)

export const deleteTrainingTask = (id: number) =>
  apiDelete<boolean>(`/training/tasks/${id}`)

export const getTaskOutput = (id: number) =>
  apiGet<string>(`/training/tasks/${id}/output`)

export const getModelVersions = () =>
  apiGet<ModelVersion[]>('/training/models')

export const predict = (symbol: string) =>
  apiPost<PredictionResult>(`/prediction/predict/${symbol}`)

export const getPredictionHistory = (symbol: string) =>
  apiGet<any[]>(`/prediction/history/${symbol}`)

export const getLossHistory = (taskId: number) =>
  apiGet<LossHistoryItem[]>(`/prediction/loss-history?taskId=${taskId}`)
