import { useState, useEffect } from 'react'
import type { Photo } from '@/types'
import { photoService } from '@/services/photoService'

interface PhotoModalProps {
  photo: Photo
  onClose: () => void
  onPhotoUpdated: () => void
}

export function PhotoModal({ photo, onClose, onPhotoUpdated }: PhotoModalProps) {
  const [tags, setTags] = useState<string[]>(photo.tags || [])
  const [newTag, setNewTag] = useState('')
  const [isAddingTag, setIsAddingTag] = useState(false)
  const [isRemovingTag, setIsRemovingTag] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    setTags(photo.tags || [])
  }, [photo])

  const handleAddTag = async () => {
    if (!newTag.trim()) return

    const tagToAdd = newTag.trim()
    if (tags.includes(tagToAdd)) {
      alert('Tag already exists')
      return
    }

    setIsAddingTag(true)
    try {
      await photoService.addTags(photo.photoId, [tagToAdd])
      setTags([...tags, tagToAdd])
      setNewTag('')
      onPhotoUpdated()
    } catch (error: any) {
      alert(error.response?.data?.message || 'Failed to add tag')
    } finally {
      setIsAddingTag(false)
    }
  }

  const handleRemoveTag = async (tag: string) => {
    setIsRemovingTag(tag)
    try {
      await photoService.removeTag(photo.photoId, tag)
      setTags(tags.filter((t) => t !== tag))
      onPhotoUpdated()
    } catch (error: any) {
      alert(error.response?.data?.message || 'Failed to remove tag')
    } finally {
      setIsRemovingTag(null)
    }
  }

  const handleDownload = () => {
    window.open(photo.downloadUrl, '_blank')
  }

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to delete this photo? This action cannot be undone.')) {
      return
    }

    setIsDeleting(true)
    try {
      await photoService.deletePhoto(photo.photoId)
      onPhotoUpdated()
      onClose()
    } catch (error: any) {
      alert(error.response?.data?.message || 'Failed to delete photo')
    } finally {
      setIsDeleting(false)
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleAddTag()
    }
  }

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
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <button className="modal-close" onClick={onClose}>
          ×
        </button>

        <div className="modal-body">
          <div className="modal-image-container">
            <img src={photo.downloadUrl} alt={photo.filename} />
          </div>

          <div className="modal-info">
            <h2>{photo.originalFilename}</h2>
            <p className="photo-meta">
              {formatFileSize(photo.fileSizeBytes)} • {formatDate(photo.createdAt)}
            </p>
            <p className="photo-status-label">
              Status: <span className={`status-badge status-${photo.status.toLowerCase()}`}>
                {photo.status}
              </span>
            </p>

            <div className="modal-actions">
              <button onClick={handleDownload} className="btn-primary">
                Download
              </button>
              <button
                onClick={handleDelete}
                disabled={isDeleting}
                className="btn-danger"
              >
                {isDeleting ? 'Deleting...' : 'Delete'}
              </button>
            </div>

            <div className="photo-tags-section">
              <h3>Tags</h3>
              <div className="tags-list">
                {tags.length === 0 && <p className="no-tags">No tags yet</p>}
                {tags.map((tag) => (
                  <div key={tag} className="tag-item">
                    <span className="tag-badge">{tag}</span>
                    <button
                      onClick={() => handleRemoveTag(tag)}
                      disabled={isRemovingTag === tag}
                      className="tag-remove"
                      title="Remove tag"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
              <div className="tag-input-container">
                <input
                  type="text"
                  value={newTag}
                  onChange={(e) => setNewTag(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="Add a tag..."
                  disabled={isAddingTag}
                  className="tag-input"
                />
                <button
                  onClick={handleAddTag}
                  disabled={isAddingTag || !newTag.trim()}
                  className="btn-secondary btn-small"
                >
                  Add
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
