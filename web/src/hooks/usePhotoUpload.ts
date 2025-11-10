import { useState, useRef } from 'react'
import type { UploadProgress } from '@/types'
import { uploadService } from '@/services/uploadService'
import { s3Service } from '@/services/s3Service'

// Normalize mime type based on file extension to handle mismatches
// (e.g., HEIC files renamed to .PNG still report type as image/heic)
function normalizeMimeType(filename: string, browserMimeType: string): string {
  // Extract extension (handle edge cases: no extension, multiple dots, etc.)
  const parts = filename.toLowerCase().split('.')
  const ext = parts.length > 1 ? parts[parts.length - 1] : ''

  // Map extensions to standard MIME types (must match backend validation)
  const extensionMap: Record<string, string> = {
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'jpe': 'image/jpeg',  // Alternative JPEG extension
    'png': 'image/png',
    'gif': 'image/gif',
    'webp': 'image/webp',
    'heic': 'image/heic',
    'heif': 'image/heic',  // HEIF uses same MIME as HEIC
  }

  // Priority: extension-based MIME > browser MIME (if valid) > fallback to jpeg
  if (ext && extensionMap[ext]) {
    return extensionMap[ext]
  }

  // Validate browser MIME type against backend-accepted formats
  const validMimeTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic']
  const normalizedBrowserType = browserMimeType?.toLowerCase().trim()

  if (normalizedBrowserType && validMimeTypes.includes(normalizedBrowserType)) {
    return normalizedBrowserType
  }

  // Fallback: default to JPEG for unknown types (most permissive)
  console.warn(`Unknown file type for "${filename}" (browser reported: "${browserMimeType}"), defaulting to image/jpeg`)
  return 'image/jpeg'
}

export function usePhotoUpload() {
  const [uploadProgress, setUploadProgress] = useState<UploadProgress[]>([])
  const [jobId, setJobId] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const abortControllerRef = useRef<AbortController | null>(null)

  const uploadPhotos = async (files: File[]) => {
    setIsUploading(true)
    setJobId(null)

    // Create abort controller for cancellation
    const abortController = new AbortController()
    abortControllerRef.current = abortController

    // Initialize progress tracking for all files
    const initialProgress: UploadProgress[] = files.map((file) => ({
      file,
      filename: file.name,
      status: 'pending',
      progress: 0,
    }))
    setUploadProgress(initialProgress)

    try {
      // Step 1: Initialize upload job
      const initResponse = await uploadService.initialize({
        photos: files.map((file) => ({
          filename: file.name,
          fileSizeBytes: file.size,
          mimeType: normalizeMimeType(file.name, file.type),
        })),
      })

      setJobId(initResponse.jobId)

      // Update progress with photo IDs
      const progressWithIds = initialProgress.map((progress, index) => ({
        ...progress,
        photoId: initResponse.photos[index].photoId,
      }))
      setUploadProgress(progressWithIds)

      // Step 2: Upload all photos concurrently
      const uploadPromises = initResponse.photos.map(async (photoData, index) => {
        const file = files[index]

        try {
          // Mark as uploading
          await uploadService.startPhotoUpload(photoData.photoId)
          updateProgress(photoData.photoId, { status: 'uploading', progress: 0 })

          // Upload to S3 with progress tracking
          await s3Service.uploadToS3(photoData.uploadUrl, file, (progress) => {
            updateProgress(photoData.photoId, { progress })
          }, abortController.signal)

          // Mark as completed
          await uploadService.completePhotoUpload(photoData.photoId)
          updateProgress(photoData.photoId, { status: 'completed', progress: 100 })
        } catch (err: unknown) {
          // Mark as failed
          const error = err as { response?: { data?: { message?: string } }, message?: string, name?: string }
          const errorMessage = error.response?.data?.message || error.message || 'Upload failed'
          await uploadService.failPhotoUpload(photoData.photoId, errorMessage)
          updateProgress(photoData.photoId, { status: 'failed', error: errorMessage })
        }
      })

      await Promise.all(uploadPromises)
    } catch (err: unknown) {
      // Check if error is due to abort
      const error = err as { name?: string }
      if (error.name === 'CanceledError' || error.name === 'AbortError') {
        // Upload was cancelled, don't throw
        return
      }
      throw error
    } finally {
      setIsUploading(false)
      abortControllerRef.current = null
    }
  }

  const cancelUpload = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      setIsUploading(false)

      // Mark all pending/uploading photos as failed
      setUploadProgress((prev) =>
        prev.map((item) =>
          item.status === 'pending' || item.status === 'uploading'
            ? { ...item, status: 'failed', error: 'Upload cancelled by user' }
            : item
        )
      )
    }
  }

  const updateProgress = (
    photoId: string,
    update: Partial<Omit<UploadProgress, 'file' | 'filename' | 'photoId'>>
  ) => {
    setUploadProgress((prev) =>
      prev.map((item) =>
        item.photoId === photoId
          ? { ...item, ...update }
          : item
      )
    )
  }

  const reset = () => {
    setUploadProgress([])
    setJobId(null)
    setIsUploading(false)
  }

  return {
    uploadPhotos,
    uploadProgress,
    jobId,
    isUploading,
    reset,
    cancelUpload,
  }
}
