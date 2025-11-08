import type { Photo } from '@/types'

interface PhotoCardProps {
  photo: Photo
  onClick: () => void
}

export function PhotoCard({ photo, onClick }: PhotoCardProps) {
  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString)
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
  }

  return (
    <div className="photo-card" onClick={onClick}>
      <div className="photo-thumbnail">
        {photo.downloadUrl ? (
          <img
            src={photo.downloadUrl}
            alt={photo.originalFilename}
            loading="lazy"
            crossOrigin="anonymous"
            onError={(e) => console.error('Image failed to load:', photo.downloadUrl, e)}
            onLoad={() => console.log('Image loaded successfully:', photo.originalFilename)}
          />
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
      <div className="photo-info">
        <h4 className="photo-filename" title={photo.originalFilename}>
          {photo.originalFilename}
        </h4>
        <p className="photo-meta">
          {formatFileSize(photo.fileSizeBytes)} • {formatDate(photo.createdAt)}
        </p>
      </div>
    </div>
  )
}
