import { useState, useRef } from 'react'
import * as ImagePicker from 'expo-image-picker'
import { uploadService } from '../services/uploadService'
import type { UploadProgress, PhotoMetadata } from '../types'

export function usePhotoUpload() {
  const [selectedPhotos, setSelectedPhotos] = useState<UploadProgress[]>([])
  const [uploading, setUploading] = useState(false)
  const [jobId, setJobId] = useState<string | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)

  const pickPhotos = async () => {
    // Request permissions
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (status !== 'granted') {
      throw new Error('Permission to access photos was denied')
    }

    // Launch image picker with multiple selection (up to 500)
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      quality: 1,
      selectionLimit: 500,
      allowsEditing: false,
    })

    if (!result.canceled && result.assets) {
      // Upload photos as-is (no conversion)
      const photos: UploadProgress[] = result.assets.map((asset) => ({
        uri: asset.uri,
        filename: asset.fileName || asset.uri.split('/').pop() || 'photo.jpg',
        status: 'pending' as const,
        progress: 0,
      }))

      console.log('[usePhotoUpload] Selected', photos.length, 'photos for upload')
      setSelectedPhotos(photos)
      return photos
    }

    return []
  }

  const uploadPhotos = async () => {
    if (selectedPhotos.length === 0) return

    setUploading(true)
    let hasErrors = false

    // Create abort controller for cancellation
    const abortController = new AbortController()
    abortControllerRef.current = abortController

    try {
      // Step 1: Initialize upload
      console.log('[usePhotoUpload] Initializing upload for', selectedPhotos.length, 'photos')
      const metadata: PhotoMetadata[] = selectedPhotos.map((photo) => {
        // Detect mime type from filename
        const ext = photo.filename.toLowerCase().split('.').pop()
        let mimeType = 'image/jpeg'
        if (ext === 'png') mimeType = 'image/png'
        else if (ext === 'heic' || ext === 'heif') mimeType = 'image/heic'
        else if (ext === 'gif') mimeType = 'image/gif'
        else if (ext === 'webp') mimeType = 'image/webp'

        return {
          filename: photo.filename,
          fileSizeBytes: 2000000,
          mimeType,
        }
      })

      const initResponse = await uploadService.initializeUpload({ photos: metadata })
      console.log('[usePhotoUpload] Upload initialized, jobId:', initResponse.jobId)
      console.log('[usePhotoUpload] Backend returned photos:', initResponse.photos.map(p => ({ id: p.photoId, filename: p.filename })))
      setJobId(initResponse.jobId)

      // Map photos by index (backend returns in same order as sent)
      const photosWithIds = selectedPhotos.map((photo, index) => {
        const photoData = initResponse.photos[index]
        if (!photoData) {
          console.error('[usePhotoUpload] No photo data at index', index, 'for', photo.filename)
        }
        return {
          ...photo,
          photoId: photoData?.photoId,
          uploadUrl: photoData?.uploadUrl,
        }
      })

      console.log('[usePhotoUpload] Photos with IDs:', photosWithIds.map(p => ({ filename: p.filename, photoId: p.photoId, hasUploadUrl: !!p.uploadUrl })))
      setSelectedPhotos(photosWithIds)

      // Step 2: Upload to S3 in batches of 10 to avoid overwhelming the network
      const batchSize = 10
      for (let i = 0; i < photosWithIds.length; i += batchSize) {
        const batch = photosWithIds.slice(i, i + batchSize)

        await Promise.all(
          batch.map(async (photo, index) => {
            const actualIndex = i + index

            if (!photo.photoId || !photo.uploadUrl) {
              console.error('[usePhotoUpload] Missing photo data for', photo.filename, { photoId: photo.photoId, hasUploadUrl: !!photo.uploadUrl })
              hasErrors = true
              updatePhotoStatus(actualIndex, 'failed', 0, 'Missing upload data')
              return
            }

            try {
              // Mark as uploading
              console.log('[usePhotoUpload] Starting upload for', photo.filename)
              updatePhotoStatus(actualIndex, 'uploading', 0)
              await uploadService.startUpload(photo.photoId)

              // Upload to S3 with correct mime type
              console.log('[usePhotoUpload] Uploading to S3:', photo.uri)

              const photoMeta = metadata[actualIndex]
              await uploadService.uploadToS3(
                photo.uploadUrl,
                photo.uri,
                photoMeta?.mimeType || 'image/jpeg',
                (progress) => {
                  updatePhotoStatus(actualIndex, 'uploading', progress)
                },
                abortController.signal
              )

              // Mark as completed
              console.log('[usePhotoUpload] Upload completed for', photo.filename)
              await uploadService.completeUpload(photo.photoId)
              updatePhotoStatus(actualIndex, 'completed', 100)
            } catch (error) {
              // Mark as failed
              console.error('[usePhotoUpload] Upload failed for', photo.filename, error)
              hasErrors = true
              if (photo.photoId) {
                await uploadService.failUpload(
                  photo.photoId,
                  error instanceof Error ? error.message : 'Upload failed'
                )
              }
              updatePhotoStatus(actualIndex, 'failed', 0, error instanceof Error ? error.message : 'Upload failed')
            }
          })
        )
      }

      // Throw error if any photos failed
      if (hasErrors) {
        throw new Error('Some photos failed to upload')
      }
    } catch (err: unknown) {
      console.error('[usePhotoUpload] Upload failed:', err)
      // Check if error is due to abort
      const error = err as { name?: string }
      if (error.name === 'AbortError') {
        // Upload was cancelled, don't throw
        return
      }
      throw error
    } finally {
      setUploading(false)
      abortControllerRef.current = null
    }
  }

  const cancelUpload = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      setUploading(false)

      // Mark all pending/uploading photos as failed
      setSelectedPhotos((prev) =>
        prev.map((photo) =>
          photo.status === 'pending' || photo.status === 'uploading'
            ? { ...photo, status: 'failed', error: 'Upload cancelled by user' }
            : photo
        )
      )
    }
  }

  const updatePhotoStatus = (
    index: number,
    status: UploadProgress['status'],
    progress: number,
    error?: string
  ) => {
    setSelectedPhotos((prev) =>
      prev.map((photo, i) =>
        i === index ? { ...photo, status, progress, error } : photo
      )
    )
  }

  const reset = () => {
    setSelectedPhotos([])
    setUploading(false)
    setJobId(null)
  }

  const stats = {
    total: selectedPhotos.length,
    completed: selectedPhotos.filter((p) => p.status === 'completed').length,
    failed: selectedPhotos.filter((p) => p.status === 'failed').length,
    uploading: selectedPhotos.filter((p) => p.status === 'uploading').length,
    pending: selectedPhotos.filter((p) => p.status === 'pending').length,
  }

  return {
    selectedPhotos,
    uploading,
    jobId,
    stats,
    pickPhotos,
    uploadPhotos,
    reset,
    cancelUpload,
  }
}
