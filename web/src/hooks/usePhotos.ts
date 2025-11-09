import { useState, useEffect, useCallback, useRef } from 'react'
import type { Photo } from '@/types'
import { photoService } from '@/services/photoService'

export function useInfinitePhotos(pageSize = 100) {
  const [photos, setPhotos] = useState<Photo[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [hasMore, setHasMore] = useState(true)
  const [page, setPage] = useState(0)
  const [totalCount, setTotalCount] = useState(0)
  const observerRef = useRef<IntersectionObserver | null>(null)

  const fetchPhotos = async (pageNum: number, append = false) => {
    if (append) {
      setIsLoadingMore(true)
    } else {
      setIsLoading(true)
    }
    setError('')

    try {
      const response = await photoService.getPhotos(pageNum, pageSize)

      if (append) {
        setPhotos(prev => [...prev, ...response.photos])
      } else {
        setPhotos(response.photos)
      }

      // Use backend response metadata
      setHasMore(response.hasMore)
      setTotalCount(response.totalElements)
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      setError(error.response?.data?.message || 'Failed to fetch photos')
    } finally {
      setIsLoading(false)
      setIsLoadingMore(false)
    }
  }

  useEffect(() => {
    fetchPhotos(0, false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Auto-load more photos if there's space on the screen
  useEffect(() => {
    if (!isLoading && !isLoadingMore && hasMore && photos.length > 0) {
      // Check if we have vertical scroll
      const hasVerticalScroll = document.documentElement.scrollHeight > window.innerHeight

      // If no vertical scroll and we have more photos, load them
      if (!hasVerticalScroll) {
        setTimeout(() => loadMore(), 100)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [photos.length, isLoading, isLoadingMore, hasMore])

  const loadMore = useCallback(() => {
    if (!isLoadingMore && hasMore) {
      const nextPage = page + 1
      setPage(nextPage)
      fetchPhotos(nextPage, true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, isLoadingMore, hasMore])

  const refresh = () => {
    setPage(0)
    setHasMore(true)
    fetchPhotos(0, false)
  }

  // Intersection Observer callback ref
  const lastPhotoRef = useCallback((node: HTMLDivElement) => {
    if (isLoadingMore) return

    if (observerRef.current) {
      observerRef.current.disconnect()
    }

    observerRef.current = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && hasMore) {
        loadMore()
      }
    })

    if (node) {
      observerRef.current.observe(node)
    }
  }, [isLoadingMore, hasMore, loadMore])

  return {
    photos,
    isLoading,
    isLoadingMore,
    error,
    hasMore,
    totalCount,
    refresh,
    lastPhotoRef,
  }
}
