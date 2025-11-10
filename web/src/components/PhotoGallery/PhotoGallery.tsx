import { useState, useMemo, useEffect } from 'react'
import { useInfinitePhotos } from '@/hooks/usePhotos'
import type { Photo } from '@/types'
import { PhotoCard } from './PhotoCard'
import { PhotoModal } from './PhotoModal'
import { PhotoSkeleton } from './PhotoSkeleton'

// Browser-displayable image formats (formats that can be shown in <img> tag)
// Note: HEIC is accepted by backend but NOT displayable by browsers
const BROWSER_DISPLAYABLE_FORMATS = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

const isBrowserDisplayable = (mimeType: string): boolean => {
  if (!mimeType) return false
  const normalized = mimeType.toLowerCase().trim()
  return BROWSER_DISPLAYABLE_FORMATS.includes(normalized)
}

export function PhotoGallery() {
  const [selectedPhoto, setSelectedPhoto] = useState<Photo | null>(null)
  const [hideUnsupported, setHideUnsupported] = useState(() => {
    const saved = localStorage.getItem('hideUnsupportedPhotos')
    return saved === 'true'
  })
  const { photos, isLoading, isLoadingMore, error, hasMore, totalCount, refresh, lastPhotoRef } = useInfinitePhotos()

  // Filter photos based on hideUnsupported setting
  const filteredPhotos = useMemo(() => {
    if (!hideUnsupported) return photos
    return photos.filter(photo => isBrowserDisplayable(photo.mimeType))
  }, [photos, hideUnsupported])

  // Save preference to localStorage
  useEffect(() => {
    localStorage.setItem('hideUnsupportedPhotos', String(hideUnsupported))
  }, [hideUnsupported])

  const handlePhotoClick = (photo: Photo) => {
    setSelectedPhoto(photo)
  }

  const handleCloseModal = () => {
    setSelectedPhoto(null)
  }

  const handlePhotoUpdated = () => {
    refresh()
  }

  const toggleHideUnsupported = () => {
    setHideUnsupported(!hideUnsupported)
  }

  return (
    <div className="photo-gallery">
      {photos.length > 0 && (
        <div className="gallery-filter">
          <label className="toggle-switch">
            <input
              type="checkbox"
              checked={hideUnsupported}
              onChange={toggleHideUnsupported}
            />
            <span className="toggle-slider"></span>
            <span className="toggle-label">Hide unsupported</span>
          </label>
        </div>
      )}

      <div className="gallery-header">
        <h1>Photo Gallery</h1>
        {totalCount > 0 && (
          <p className="gallery-info">
            {totalCount} photo{totalCount !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {filteredPhotos.length === 0 && !isLoading && (
        <div className="empty-state">
          {photos.length > 0 && hideUnsupported ? (
            <p>All photos are in browser-unsupported formats (e.g., HEIC). Toggle off "Hide unsupported" to view them.</p>
          ) : (
            <p>No photos yet. Upload some photos to get started!</p>
          )}
        </div>
      )}

      <div className="gallery-grid">
        {/* Show skeletons on initial load */}
        {isLoading && photos.length === 0 && (
          <>
            {Array.from({ length: 12 }).map((_, i) => (
              <PhotoSkeleton key={`skeleton-${i}`} />
            ))}
          </>
        )}

        {/* Show actual photos */}
        {filteredPhotos.map((photo, index) => {
          // Attach ref to the last LOADED photo (not last filtered)
          // This ensures infinite scroll triggers even when photos are filtered
          const photoIndexInOriginal = photos.findIndex(p => p.photoId === photo.photoId)
          const isLastLoadedPhoto = photoIndexInOriginal === photos.length - 1

          if (isLastLoadedPhoto) {
            return (
              <div key={photo.photoId} ref={lastPhotoRef}>
                <PhotoCard photo={photo} onClick={() => handlePhotoClick(photo)} index={index} />
              </div>
            )
          }
          return (
            <PhotoCard key={photo.photoId} photo={photo} onClick={() => handlePhotoClick(photo)} index={index} />
          )
        })}

        {/* Show skeletons while loading more */}
        {isLoadingMore && (
          <>
            {Array.from({ length: 8 }).map((_, i) => (
              <PhotoSkeleton key={`skeleton-more-${i}`} />
            ))}
          </>
        )}
      </div>

      {/* Show end indicator */}
      {!hasMore && filteredPhotos.length > 0 && (
        <div className="end-of-gallery">
          <p>You've reached the end</p>
        </div>
      )}

      {selectedPhoto && (
        <PhotoModal
          photo={selectedPhoto}
          onClose={handleCloseModal}
          onPhotoUpdated={handlePhotoUpdated}
        />
      )}
    </div>
  )
}
