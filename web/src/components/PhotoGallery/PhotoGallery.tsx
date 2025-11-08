import { useState } from 'react'
import { usePhotos } from '@/hooks/usePhotos'
import type { Photo } from '@/types'
import { PhotoCard } from './PhotoCard'
import { PhotoModal } from './PhotoModal'

export function PhotoGallery() {
  const [selectedPhoto, setSelectedPhoto] = useState<Photo | null>(null)
  const { photos, isLoading, error, refresh } = usePhotos()

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
        {photos && photos.length > 0 && (
          <p className="gallery-info">
            {photos.length} photo{photos.length !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {isLoading && (
        <div className="loading-container">
          <div className="spinner"></div>
          <p>Loading photos...</p>
        </div>
      )}

      {!isLoading && photos && photos.length === 0 && (
        <div className="empty-state">
          <p>No photos yet. Upload some photos to get started!</p>
        </div>
      )}

      {!isLoading && photos && photos.length > 0 && (
        <div className="gallery-grid">
          {photos.map((photo) => (
            <PhotoCard key={photo.photoId} photo={photo} onClick={() => handlePhotoClick(photo)} />
          ))}
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
