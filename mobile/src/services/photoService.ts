import { apiClient } from './api'
import type { Photo, PhotoResponse, PhotoListResponse } from '../types'

export const photoService = {
  async getPhotos(page = 0, size = 20): Promise<PhotoListResponse> {
    const response = await apiClient.get<PhotoListResponse>('/api/photos', {
      params: { page, size },
    })
    // Backend now returns proper pagination metadata
    return response.data
  },

  async getPhotoById(photoId: string): Promise<Photo> {
    const response = await apiClient.get<Photo>(`/api/photos/${photoId}`)
    return response.data
  },

  async addTags(photoId: string, tags: string[]): Promise<void> {
    await apiClient.post(`/api/photos/${photoId}/tags`, { tags })
  },

  async removeTag(photoId: string, tag: string): Promise<void> {
    await apiClient.delete(`/api/photos/${photoId}/tags/${encodeURIComponent(tag)}`)
  },

  async deletePhoto(photoId: string): Promise<void> {
    await apiClient.delete(`/api/photos/${photoId}`)
  },
}
