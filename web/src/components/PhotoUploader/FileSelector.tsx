import { useRef, type ChangeEvent } from 'react'

interface FileSelectorProps {
  onFilesSelected: (files: File[]) => void
  disabled?: boolean
}

export function FileSelector({ onFilesSelected, disabled }: FileSelectorProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || [])
    if (files.length > 0) {
      onFilesSelected(files)
    }
  }

  const handleClick = () => {
    fileInputRef.current?.click()
  }

  return (
    <div className="file-selector">
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*"
        onChange={handleFileChange}
        style={{ display: 'none' }}
        disabled={disabled}
      />
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled}
        className="btn-primary btn-large"
      >
        Select Photos
      </button>
      <p className="help-text">
        Select up to 100 photos to upload (JPEG, PNG, etc.)
      </p>
    </div>
  )
}
