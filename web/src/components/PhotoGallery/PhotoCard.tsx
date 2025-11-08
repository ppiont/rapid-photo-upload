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

  const handleImageLoad = () => {
    setIsImageLoaded(true)
  }

  const handleImageError = () => {
    setHasError(true)
  }

  // Get file extension for unsupported format display
  const getFileExtension = (): string => {
    const ext = photo.filename?.split('.').pop()?.toUpperCase()
    return ext || 'UNKNOWN'
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
            <div className="unsupported-format-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <polyline points="21 15 16 10 5 21" />
              </svg>
            </div>
            <p className="format-name">.{getFileExtension()}</p>
            <span className="format-hint">Unsupported format</span>
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
