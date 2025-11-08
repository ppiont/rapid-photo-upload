import { useState, useCallback, useEffect } from 'react'
import { photoService } from '../services/photoService'
import type { PhotoResponse } from '../types'

export function usePhotos(pageSize = 30) {
  const [photos, setPhotos] = useState<PhotoResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [totalCount, setTotalCount] = useState(0)

  const loadPhotos = useCallback(
    async (pageNumber: number, isRefresh = false) => {
      if (loading || (!hasMore && !isRefresh)) return

      setLoading(true)
      setError(null)

      try {
        const response = await photoService.getPhotos(pageNumber, pageSize)

        console.log('[usePhotos] Loaded page', pageNumber, 'with', response.photos.length, 'photos, total:', response.totalElements)
        console.log('[usePhotos] Photo IDs:', response.photos.map(p => p.photoId))

        if (isRefresh) {
          setPhotos(response.photos)
          setPage(0)
          setHasMore(response.hasMore)
          setTotalCount(response.totalElements)
        } else {
          setPhotos((prev) => {
            // Deduplicate by ID
            const existingIds = new Set(prev.map(p => p.photoId))
            const newPhotos = response.photos.filter(p => !existingIds.has(p.photoId))
            console.log('[usePhotos] Adding', newPhotos.length, 'new photos, filtered', response.photos.length - newPhotos.length, 'duplicates')
            return [...prev, ...newPhotos]
          })
          setHasMore(response.hasMore)
          setTotalCount(response.totalElements)
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load photos')
      } finally {
        setLoading(false)
        setRefreshing(false)
      }
    },
    [loading, hasMore, pageSize]
  )

  const refresh = useCallback(async () => {
    setRefreshing(true)
    await loadPhotos(0, true)
  }, [loadPhotos])

  const loadMore = useCallback(() => {
    if (!loading && hasMore) {
      const nextPage = page + 1
      setPage(nextPage)
      loadPhotos(nextPage)
    }
  }, [loading, hasMore, page, loadPhotos])

  const deletePhoto = useCallback(async (photoId: string) => {
    await photoService.deletePhoto(photoId)
    setPhotos((prev) => prev.filter((p) => p.photoId !== photoId))
  }, [])

  useEffect(() => {
    loadPhotos(0, true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return {
    photos,
    loading,
    refreshing,
    hasMore,
    totalCount,
    error,
    refresh,
    loadMore,
    deletePhoto,
  }
}
