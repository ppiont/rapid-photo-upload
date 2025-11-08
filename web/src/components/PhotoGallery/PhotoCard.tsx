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
        <img src={photo.downloadUrl} alt={photo.filename} loading="lazy" />
        {photo.status === 'UPLOADING' && (
          <div className="photo-status uploading">Uploading...</div>
        )}
        {photo.status === 'FAILED' && (
          <div className="photo-status failed">Failed</div>
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
