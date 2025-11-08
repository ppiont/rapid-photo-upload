import React, { useState, useEffect } from 'react'
import {
  View,
  Text,
  StyleSheet,
  Image,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  Dimensions,
} from 'react-native'
import { photoService } from '../services/photoService'
import { TagInput } from '../components/TagInput'
import type { NativeStackNavigationProp } from '@react-navigation/native-stack'
import type { RouteProp } from '@react-navigation/native'
import type { RootStackParamList } from '../navigation/AppNavigator'
import type { PhotoResponse } from '../types'

type PhotoDetailScreenNavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'PhotoDetail'
>
type PhotoDetailScreenRouteProp = RouteProp<RootStackParamList, 'PhotoDetail'>

interface Props {
  navigation: PhotoDetailScreenNavigationProp
  route: PhotoDetailScreenRouteProp
}

const SCREEN_WIDTH = Dimensions.get('window').width

export default function PhotoDetailScreen({ navigation, route }: Props) {
  const { photoId } = route.params
  const [photo, setPhoto] = useState<PhotoResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadPhoto()
  }, [photoId])

  const loadPhoto = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await photoService.getPhotoById(photoId)
      setPhoto(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load photo')
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = () => {
    Alert.alert('Delete Photo', 'Are you sure you want to delete this photo?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            setDeleting(true)
            await photoService.deletePhoto(photoId)
            navigation.goBack()
          } catch (err) {
            Alert.alert(
              'Error',
              err instanceof Error ? err.message : 'Failed to delete photo'
            )
          } finally {
            setDeleting(false)
          }
        },
      },
    ])
  }

  const handleAddTag = async (tag: string) => {
    try {
      await photoService.addTags(photoId, [tag])
      // Refresh photo data to get updated tags
      await loadPhoto()
    } catch (err) {
      Alert.alert('Error', err instanceof Error ? err.message : 'Failed to add tag')
      throw err
    }
  }

  const handleRemoveTag = async (tag: string) => {
    try {
      await photoService.removeTag(photoId, tag)
      // Refresh photo data to get updated tags
      await loadPhoto()
    } catch (err) {
      Alert.alert('Error', err instanceof Error ? err.message : 'Failed to remove tag')
      throw err
    }
  }

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#3b82f6" />
      </View>
    )
  }

  if (error || !photo) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.errorText}>{error || 'Photo not found'}</Text>
        <TouchableOpacity style={styles.retryButton} onPress={loadPhoto}>
          <Text style={styles.retryButtonText}>Retry</Text>
        </TouchableOpacity>
      </View>
    )
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      <Image
        source={{ uri: photo.downloadUrl }}
        style={styles.image}
        resizeMode="contain"
      />

      <View style={styles.infoContainer}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Details</Text>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Filename</Text>
            <Text style={styles.detailValue}>{photo.filename}</Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Size</Text>
            <Text style={styles.detailValue}>{formatFileSize(photo.fileSizeBytes)}</Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Uploaded</Text>
            <Text style={styles.detailValue}>{formatDate(photo.createdAt)}</Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Status</Text>
            <View
              style={[
                styles.statusBadge,
                photo.status === 'COMPLETED' && styles.statusBadgeSuccess,
              ]}
            >
              <Text style={styles.statusText}>{photo.status}</Text>
            </View>
          </View>
        </View>

        <TagInput
          tags={photo.tags || []}
          onAddTag={handleAddTag}
          onRemoveTag={handleRemoveTag}
        />

        <TouchableOpacity
          style={[styles.deleteButton, deleting && styles.deleteButtonDisabled]}
          onPress={handleDelete}
          disabled={deleting}
        >
          {deleting ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.deleteButtonText}>Delete Photo</Text>
          )}
        </TouchableOpacity>
      </View>
    </ScrollView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  contentContainer: {
    flexGrow: 1,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f9fafb',
    padding: 32,
  },
  image: {
    width: SCREEN_WIDTH,
    height: SCREEN_WIDTH,
    backgroundColor: '#000',
  },
  infoContainer: {
    backgroundColor: '#fff',
    padding: 16,
    flex: 1,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 12,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  detailLabel: {
    fontSize: 14,
    color: '#6b7280',
    fontWeight: '500',
  },
  detailValue: {
    fontSize: 14,
    color: '#111827',
    fontWeight: '400',
    flex: 1,
    textAlign: 'right',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
    backgroundColor: '#e5e7eb',
  },
  statusBadgeSuccess: {
    backgroundColor: '#d1fae5',
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#10b981',
  },
  deleteButton: {
    backgroundColor: '#ef4444',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  deleteButtonDisabled: {
    opacity: 0.6,
  },
  deleteButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  errorText: {
    fontSize: 16,
    color: '#ef4444',
    marginBottom: 16,
    textAlign: 'center',
  },
  retryButton: {
    backgroundColor: '#3b82f6',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  retryButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
})
