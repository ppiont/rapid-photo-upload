import { useState, useEffect } from 'react'
import type { Photo } from '@/types'
import { photoService } from '@/services/photoService'

export function usePhotos(page = 0, size = 20) {
  const [photos, setPhotos] = useState<Photo[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  const fetchPhotos = async () => {
    setIsLoading(true)
    setError('')

    try {
      const photoList = await photoService.getPhotos(page, size)
      setPhotos(photoList)
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to fetch photos')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchPhotos()
  }, [page, size])

  const refresh = () => {
    fetchPhotos()
  }

  return {
    photos,
    isLoading,
    error,
    refresh,
  }
}
