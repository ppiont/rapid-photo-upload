import { apiClient } from './api'
import type {
  InitializeUploadRequest,
  InitializeUploadResponse,
  UploadJobStatusResponse,
} from '@/types'

export const uploadService = {
  async initialize(request: InitializeUploadRequest): Promise<InitializeUploadResponse> {
    const response = await apiClient.post<InitializeUploadResponse>(
      '/upload/initialize',
      request
    )
    return response.data
  },

  async startPhotoUpload(photoId: string): Promise<void> {
    await apiClient.put(`/upload/photos/${photoId}/start`)
  },

  async completePhotoUpload(photoId: string): Promise<void> {
    await apiClient.put(`/upload/photos/${photoId}/complete`)
  },

  async failPhotoUpload(photoId: string, error: string): Promise<void> {
    await apiClient.put(`/upload/photos/${photoId}/fail`, { error })
  },

  async getJobStatus(jobId: string): Promise<UploadJobStatusResponse> {
    const response = await apiClient.get<UploadJobStatusResponse>(
      `/upload/jobs/${jobId}/status`
    )
    return response.data
  },
}
