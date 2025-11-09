import axios from 'axios'

export const s3Service = {
  async uploadToS3(
    presignedUrl: string,
    file: File,
    onProgress?: (progress: number) => void,
    signal?: AbortSignal
  ): Promise<void> {
    await axios.put(presignedUrl, file, {
      headers: {
        'Content-Type': file.type,
      },
      signal,
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          )
          onProgress(percentCompleted)
        }
      },
    })
  },
}
