// Auth types
export interface User {
  userId: string
  email: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  confirmPassword: string  // Client-side only validation
}

export interface AuthResponse {
  token: string
  userId: string
  email: string
  expiresIn: number
}

// Upload types
export interface PhotoMetadata {
  filename: string
  fileSizeBytes: number
  mimeType: string
}

export interface InitializeUploadRequest {
  photos: PhotoMetadata[]
}

export interface PhotoUploadData {
  photoId: string
  filename: string
  uploadUrl: string
}

export interface InitializeUploadResponse {
  jobId: string
  photos: PhotoUploadData[]
}

export type PhotoStatus = 'PENDING' | 'UPLOADING' | 'COMPLETED' | 'FAILED'
export type UploadJobStatus = 'IN_PROGRESS' | 'COMPLETED' | 'PARTIAL_FAILURE'

export interface PhotoStatusInfo {
  photoId: string
  filename: string
  status: PhotoStatus
  size: number
  createdAt: string
}

export interface UploadJobStatusResponse {
  jobId: string
  status: UploadJobStatus
  totalPhotos: number
  completedPhotos: number
  failedPhotos: number
  photos: PhotoStatusInfo[]
  createdAt: string
  updatedAt: string
}

// Photo types
export interface Photo {
  photoId: string
  filename: string
  originalFilename: string
  fileSizeBytes: number
  mimeType: string
  status: string
  downloadUrl: string
  createdAt: string
  uploadCompletedAt: string | null
  tags?: string[]
}

// Upload progress tracking (client-side only)
export interface UploadProgress {
  file: File
  photoId?: string
  filename: string
  status: 'pending' | 'uploading' | 'completed' | 'failed'
  progress: number // 0-100
  error?: string
}
