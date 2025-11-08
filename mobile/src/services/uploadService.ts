import { apiClient } from './api'
import type {
  InitializeUploadRequest,
  InitializeUploadResponse,
  UploadJobStatusResponse,
} from '../types'

export const uploadService = {
  async initializeUpload(photos: InitializeUploadRequest): Promise<InitializeUploadResponse> {
    const response = await apiClient.post<InitializeUploadResponse>('/api/upload/initialize', photos)
    return response.data
  },

  async startUpload(photoId: string): Promise<void> {
    await apiClient.put(`/api/upload/photos/${photoId}/start`)
  },

  async completeUpload(photoId: string): Promise<void> {
    await apiClient.put(`/api/upload/photos/${photoId}/complete`)
  },

  async failUpload(photoId: string, errorMessage: string): Promise<void> {
    await apiClient.put(`/api/upload/photos/${photoId}/fail`, { errorMessage })
  },

  async getJobStatus(jobId: string): Promise<UploadJobStatusResponse> {
    const response = await apiClient.get<UploadJobStatusResponse>(`/api/upload/jobs/${jobId}/status`)
    return response.data
  },

  async uploadToS3(
    uploadUrl: string,
    fileUri: string,
    mimeType?: string,
    onProgress?: (progress: number) => void
  ): Promise<void> {
    try {
      console.log('[uploadService] Starting S3 upload:', { fileUri, mimeType, url: uploadUrl.substring(0, 100) })

      // Detect mime type from file extension if not provided
      const contentType = mimeType || (() => {
        const ext = fileUri.toLowerCase().split('.').pop()
        if (ext === 'png') return 'image/png'
        if (ext === 'heic' || ext === 'heif') return 'image/heic'
        if (ext === 'gif') return 'image/gif'
        if (ext === 'webp') return 'image/webp'
        return 'image/jpeg'
      })()

      // Use fetch to upload binary file directly to S3
      const response = await fetch(fileUri)
      const blob = await response.blob()

      const uploadResponse = await fetch(uploadUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': contentType,
        },
        body: blob,
      })

      console.log('[uploadService] S3 upload completed:', {
        status: uploadResponse.status,
        statusText: uploadResponse.statusText
      })

      if (!uploadResponse.ok) {
        const errorText = await uploadResponse.text()
        throw new Error(`S3 upload failed with status ${uploadResponse.status}: ${errorText}`)
      }
    } catch (error) {
      console.error('[uploadService] S3 upload failed:', error)
      throw error
    }
  },
}
