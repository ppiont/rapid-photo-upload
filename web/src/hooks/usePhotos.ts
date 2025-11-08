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
  const observerRef = useRef<IntersectionObserver | null>(null)

  const fetchPhotos = async (pageNum: number, append = false) => {
    if (append) {
      setIsLoadingMore(true)
    } else {
      setIsLoading(true)
    }
    setError('')

    try {
      const photoList = await photoService.getPhotos(pageNum, pageSize)

      if (append) {
        setPhotos(prev => [...prev, ...photoList])
      } else {
        setPhotos(photoList)
      }

      // If we got fewer photos than the page size, we've reached the end
      setHasMore(photoList.length === pageSize)
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to fetch photos')
    } finally {
      setIsLoading(false)
      setIsLoadingMore(false)
    }
  }

  useEffect(() => {
    fetchPhotos(0, false)
  }, [])

  const loadMore = useCallback(() => {
    if (!isLoadingMore && hasMore) {
      const nextPage = page + 1
      setPage(nextPage)
      fetchPhotos(nextPage, true)
    }
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
    refresh,
    lastPhotoRef,
  }
}
