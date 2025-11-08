import type { UploadProgress } from '@/types'

interface UploadQueueProps {
  queue: UploadProgress[]
}

export function UploadQueue({ queue }: UploadQueueProps) {
  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'completed':
        return '✓'
      case 'failed':
        return '✗'
      case 'uploading':
        return '↑'
      case 'pending':
        return '○'
      default:
        return ''
    }
  }

  const getStatusClass = (status: string) => {
    return `status-${status}`
  }

  return (
    <div className="upload-queue">
      <h3>Files ({queue.length})</h3>
      <div className="queue-list">
        {queue.map((item, index) => (
          <div key={item.photoId || index} className={`queue-item ${getStatusClass(item.status)}`}>
            <div className="item-header">
              <span className="status-icon">{getStatusIcon(item.status)}</span>
              <span className="filename">{item.filename}</span>
              <span className="file-size">{formatFileSize(item.file.size)}</span>
            </div>
            {item.status === 'uploading' && (
              <div className="item-progress">
                <div className="progress-bar-small">
                  <div className="progress-fill" style={{ width: `${item.progress}%` }}></div>
                </div>
                <span className="progress-text">{item.progress}%</span>
              </div>
            )}
            {item.status === 'failed' && item.error && (
              <div className="item-error">
                <span className="error-text">{item.error}</span>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
