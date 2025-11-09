import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePhotoUpload } from '@/hooks/usePhotoUpload'
import { FileSelector } from './FileSelector'
import { UploadProgress } from './UploadProgress'
import { UploadQueue } from './UploadQueue'

export function PhotoUploader() {
  const [selectedFiles, setSelectedFiles] = useState<File[]>([])
  const [error, setError] = useState('')
  const { uploadPhotos, uploadProgress, isUploading, reset, cancelUpload } = usePhotoUpload()
  const navigate = useNavigate()

  const handleFilesSelected = (files: File[]) => {
    setError('')

    // Validate file count
    if (files.length > 500) {
      setError('Maximum 500 photos allowed per upload')
      return
    }

    // Validate file types
    const invalidFiles = files.filter((file) => !file.type.startsWith('image/'))
    if (invalidFiles.length > 0) {
      setError('Only image files are allowed')
      return
    }

    setSelectedFiles(files)
  }

  const handleUpload = async () => {
    if (selectedFiles.length === 0) {
      setError('Please select files to upload')
      return
    }

    setError('')

    try {
      await uploadPhotos(selectedFiles)
    } catch (err: any) {
      setError(err.response?.data?.message || 'Upload failed. Please try again.')
    }
  }

  const handleReset = () => {
    setSelectedFiles([])
    setError('')
    reset()
  }

  const handleViewGallery = () => {
    navigate('/gallery')
  }

  const isUploadComplete = uploadProgress.length > 0 && !isUploading
  const hasFailures = uploadProgress.some((p) => p.status === 'failed')

  return (
    <div className="photo-uploader">
      <div className="uploader-header">
        <h1>Upload Photos</h1>
        <p>Upload up to 500 photos at once</p>
      </div>

      {!isUploading && uploadProgress.length === 0 && (
        <div className="uploader-content">
          <FileSelector onFilesSelected={handleFilesSelected} disabled={isUploading} />

          {selectedFiles.length > 0 && (
            <div className="selected-files-info">
              <p>
                <strong>{selectedFiles.length}</strong> file(s) selected
              </p>
              <button
                onClick={handleUpload}
                disabled={isUploading}
                className="btn-primary btn-large"
              >
                Start Upload
              </button>
              <button onClick={handleReset} className="btn-secondary">
                Clear Selection
              </button>
            </div>
          )}

          {error && <div className="error-message">{error}</div>}
        </div>
      )}

      {uploadProgress.length > 0 && (
        <div className="upload-status">
          <UploadProgress progress={uploadProgress} />
          <UploadQueue queue={uploadProgress} />

          {isUploading && (
            <div className="upload-actions">
              <button onClick={cancelUpload} className="btn-danger">
                Cancel Upload
              </button>
            </div>
          )}

          {isUploadComplete && (
            <div className="upload-complete-actions">
              <button onClick={handleViewGallery} className="btn-primary">
                View Gallery
              </button>
              <button onClick={handleReset} className="btn-secondary">
                Upload More Photos
              </button>
            </div>
          )}

          {isUploadComplete && hasFailures && (
            <div className="warning-message">
              Some uploads failed. You can try uploading them again.
            </div>
          )}
        </div>
      )}
    </div>
  )
}
