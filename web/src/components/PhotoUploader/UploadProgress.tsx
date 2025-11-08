import type { UploadProgress as UploadProgressType } from '@/types'

interface UploadProgressProps {
  progress: UploadProgressType[]
}

export function UploadProgress({ progress }: UploadProgressProps) {
  const totalFiles = progress.length
  const completedFiles = progress.filter((p) => p.status === 'completed').length
  const failedFiles = progress.filter((p) => p.status === 'failed').length
  const uploadingFiles = progress.filter((p) => p.status === 'uploading').length
  const pendingFiles = progress.filter((p) => p.status === 'pending').length

  const overallProgress = totalFiles > 0
    ? Math.round((completedFiles / totalFiles) * 100)
    : 0

  return (
    <div className="upload-progress">
      <div className="upload-summary">
        <h3>Upload Progress</h3>
        <div className="progress-stats">
          <div className="stat">
            <span className="stat-label">Total:</span>
            <span className="stat-value">{totalFiles}</span>
          </div>
          <div className="stat success">
            <span className="stat-label">Completed:</span>
            <span className="stat-value">{completedFiles}</span>
          </div>
          {uploadingFiles > 0 && (
            <div className="stat uploading">
              <span className="stat-label">Uploading:</span>
              <span className="stat-value">{uploadingFiles}</span>
            </div>
          )}
          {failedFiles > 0 && (
            <div className="stat error">
              <span className="stat-label">Failed:</span>
              <span className="stat-value">{failedFiles}</span>
            </div>
          )}
          {pendingFiles > 0 && (
            <div className="stat pending">
              <span className="stat-label">Pending:</span>
              <span className="stat-value">{pendingFiles}</span>
            </div>
          )}
        </div>
        <div className="progress-bar-container">
          <div className="progress-bar" style={{ width: `${overallProgress}%` }}></div>
        </div>
        <p className="progress-percentage">{overallProgress}% Complete</p>
      </div>
    </div>
  )
}
