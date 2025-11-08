import { apiClient } from './api'
import type { Photo } from '@/types'

export const photoService = {
  async getPhotos(page = 0, size = 20): Promise<Photo[]> {
    const response = await apiClient.get<Photo[]>('/photos', {
      params: { page, size },
    })
    return response.data
  },

  async getPhotoById(photoId: string): Promise<Photo> {
    const response = await apiClient.get<Photo>(`/photos/${photoId}`)
    return response.data
  },

  async addTags(photoId: string, tags: string[]): Promise<void> {
    await apiClient.post(`/photos/${photoId}/tags`, { tags })
  },

  async removeTag(photoId: string, tag: string): Promise<void> {
    await apiClient.delete(`/photos/${photoId}/tags/${encodeURIComponent(tag)}`)
  },

  async deletePhoto(photoId: string): Promise<void> {
    await apiClient.delete(`/photos/${photoId}`)
  },
}
