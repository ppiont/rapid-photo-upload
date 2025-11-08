import { useState } from 'react'
import type { Photo } from '@/types'

interface PhotoCardProps {
  photo: Photo
  onClick: () => void
  index?: number
}

export function PhotoCard({ photo, onClick, index = 0 }: PhotoCardProps) {
  const [isImageLoaded, setIsImageLoaded] = useState(false)
  const [hasError, setHasError] = useState(false)

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString)
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
  }

  const handleImageLoad = () => {
    setIsImageLoaded(true)
  }

  const handleImageError = () => {
    setHasError(true)
    console.error('Image failed to load:', photo.downloadUrl)
  }

  return (
    <div
      className="photo-card"
      onClick={onClick}
      style={{ animationDelay: `${(index % 12) * 0.05}s` }}
    >
      <div className="photo-thumbnail">
        {photo.downloadUrl && !hasError ? (
          <>
            {/* Show skeleton while loading */}
            {!isImageLoaded && (
              <div className="photo-loading-skeleton">
                <div className="skeleton-shimmer"></div>
              </div>
            )}
            {/* Hide image until fully loaded */}
            <img
              src={photo.downloadUrl}
              alt={photo.originalFilename}
              loading="lazy"
              crossOrigin="anonymous"
              onLoad={handleImageLoad}
              onError={handleImageError}
              style={{ opacity: isImageLoaded ? 1 : 0 }}
            />
          </>
        ) : (
          <div className="photo-placeholder">
            <p>No preview available</p>
            <span>Status: {photo.status}</span>
          </div>
        )}
        {photo.status === 'UPLOADING' && (
          <div className="photo-status uploading">Uploading...</div>
        )}
        {photo.status === 'FAILED' && (
          <div className="photo-status failed">Failed</div>
        )}
        {photo.status === 'PENDING' && (
          <div className="photo-status pending">Pending</div>
        )}
      </div>
    </div>
  )
}
