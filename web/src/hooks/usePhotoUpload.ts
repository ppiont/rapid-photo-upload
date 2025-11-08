import { useState } from 'react'
import type { UploadProgress } from '@/types'
import { uploadService } from '@/services/uploadService'
import { s3Service } from '@/services/s3Service'

export function usePhotoUpload() {
  const [uploadProgress, setUploadProgress] = useState<UploadProgress[]>([])
  const [jobId, setJobId] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)

  const uploadPhotos = async (files: File[]) => {
    setIsUploading(true)
    setJobId(null)

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
          mimeType: file.type,
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
          })

          // Mark as completed
          await uploadService.completePhotoUpload(photoData.photoId)
          updateProgress(photoData.photoId, { status: 'completed', progress: 100 })
        } catch (error: any) {
          // Mark as failed
          const errorMessage = error.response?.data?.message || error.message || 'Upload failed'
          await uploadService.failPhotoUpload(photoData.photoId, errorMessage)
          updateProgress(photoData.photoId, { status: 'failed', error: errorMessage })
        }
      })

      await Promise.all(uploadPromises)
    } catch (error: any) {
      console.error('Upload initialization failed:', error)
      throw error
    } finally {
      setIsUploading(false)
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
  }
}
