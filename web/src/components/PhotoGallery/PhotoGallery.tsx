import { useState } from 'react'
import { useInfinitePhotos } from '@/hooks/usePhotos'
import type { Photo } from '@/types'
import { PhotoCard } from './PhotoCard'
import { PhotoModal } from './PhotoModal'
import { PhotoSkeleton } from './PhotoSkeleton'

export function PhotoGallery() {
  const [selectedPhoto, setSelectedPhoto] = useState<Photo | null>(null)
  const { photos, isLoading, isLoadingMore, error, hasMore, refresh, lastPhotoRef } = useInfinitePhotos()

  const handlePhotoClick = (photo: Photo) => {
    setSelectedPhoto(photo)
  }

  const handleCloseModal = () => {
    setSelectedPhoto(null)
  }

  const handlePhotoUpdated = () => {
    refresh()
  }

  return (
    <div className="photo-gallery">
      <div className="gallery-header">
        <h1>Photo Gallery</h1>
        {photos.length > 0 && (
          <p className="gallery-info">
            {photos.length} photo{photos.length !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {photos.length === 0 && !isLoading && (
        <div className="empty-state">
          <p>No photos yet. Upload some photos to get started!</p>
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
        {photos.map((photo, index) => {
          // Attach ref to last photo for infinite scroll
          if (index === photos.length - 1) {
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
      {!hasMore && photos.length > 0 && (
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
