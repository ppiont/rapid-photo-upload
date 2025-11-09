import React, { useState, useEffect, useMemo } from 'react'
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Image,
  ActivityIndicator,
  RefreshControl,
  Dimensions,
  Switch,
  Platform,
} from 'react-native'
import AsyncStorage from '@react-native-async-storage/async-storage'
import { usePhotos } from '../hooks/usePhotos'
import type { CompositeNavigationProp } from '@react-navigation/native'
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs'
import type { NativeStackNavigationProp } from '@react-navigation/native-stack'
import type { RootStackParamList, TabParamList } from '../navigation/AppNavigator'
import type { PhotoResponse } from '../types'

// Platform-specific supported image formats
// iOS: JPEG, PNG, GIF, HEIC, HEIF, WebP
// Android: JPEG, PNG, GIF, WebP (no HEIC support)
const getSupportedFormats = (): string[] => {
  const baseFormats = [
    'image/jpeg',
    'image/jpg',
    'image/png',
    'image/gif',
    'image/webp',
  ]

  // Only iOS supports HEIC natively
  if (Platform.OS === 'ios') {
    return [...baseFormats, 'image/heic', 'image/heif']
  }

  return baseFormats
}

const SUPPORTED_FORMATS = getSupportedFormats()

const isSupportedFormat = (mimeType: string): boolean => {
  if (!mimeType) return false
  return SUPPORTED_FORMATS.includes(mimeType.toLowerCase())
}

type GalleryScreenNavigationProp = CompositeNavigationProp<
  BottomTabNavigationProp<TabParamList, 'Gallery'>,
  NativeStackNavigationProp<RootStackParamList>
>

interface Props {
  navigation: GalleryScreenNavigationProp
}

const SCREEN_WIDTH = Dimensions.get('window').width
const ITEM_SIZE = (SCREEN_WIDTH - 6) / 3 // 3 columns with 2px gaps

export default function GalleryScreen({ navigation }: Props) {
  const { photos, loading, refreshing, hasMore: _hasMore, totalCount, error, refresh, loadMore } = usePhotos()
  const [hideUnsupported, setHideUnsupported] = useState(false)

  // Load preference from AsyncStorage
  useEffect(() => {
    const loadPreference = async () => {
      try {
        const saved = await AsyncStorage.getItem('hideUnsupportedPhotos')
        if (saved !== null) {
          setHideUnsupported(saved === 'true')
        }
      } catch (err) {
        console.error('[GalleryScreen] Failed to load preference:', err)
      }
    }
    loadPreference()
  }, [])

  // Save preference to AsyncStorage
  const toggleHideUnsupported = async () => {
    const newValue = !hideUnsupported
    setHideUnsupported(newValue)
    try {
      await AsyncStorage.setItem('hideUnsupportedPhotos', String(newValue))
    } catch (err) {
      console.error('[GalleryScreen] Failed to save preference:', err)
    }
  }

  // Filter photos based on hideUnsupported setting
  const filteredPhotos = useMemo(() => {
    if (!hideUnsupported) return photos
    return photos.filter(photo => isSupportedFormat(photo.mimeType))
  }, [photos, hideUnsupported])

  const renderPhoto = ({ item }: { item: PhotoResponse }) => (
    <TouchableOpacity
      style={styles.photoItem}
      onPress={() => navigation.navigate('PhotoDetail', { photoId: item.photoId })}
      activeOpacity={0.8}
    >
      <Image
        source={{ uri: item.downloadUrl }}
        style={styles.thumbnail}
        resizeMode="cover"
        onError={(error) => {
          console.error('[GalleryScreen] Image failed to load:', {
            photoId: item.photoId,
            filename: item.filename,
            mimeType: item.mimeType,
            url: item.downloadUrl?.substring(0, 100),
            error,
          })
        }}
        onLoad={() => {
          console.log('[GalleryScreen] Image loaded successfully:', item.filename, item.mimeType)
        }}
      />
    </TouchableOpacity>
  )

  const renderFooter = () => {
    if (!loading || photos.length === 0) return null

    return (
      <View style={styles.footer}>
        <ActivityIndicator color="#3b82f6" />
      </View>
    )
  }

  const renderEmpty = () => {
    if (loading && photos.length === 0) {
      return (
        <View style={styles.emptyState}>
          <ActivityIndicator size="large" color="#3b82f6" />
          <Text style={styles.emptyText}>Loading photos...</Text>
        </View>
      )
    }

    if (error) {
      return (
        <View style={styles.emptyState}>
          <Text style={styles.errorText}>Error: {error}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={refresh}>
            <Text style={styles.retryButtonText}>Retry</Text>
          </TouchableOpacity>
        </View>
      )
    }

    if (photos.length > 0 && hideUnsupported) {
      return (
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>All photos are in unsupported formats</Text>
          <Text style={styles.emptySubtext}>Turn off the filter to view them</Text>
        </View>
      )
    }

    return (
      <View style={styles.emptyState}>
        <Text style={styles.emptyText}>No photos yet</Text>
        <Text style={styles.emptySubtext}>Upload some photos to get started</Text>
        <TouchableOpacity
          style={styles.uploadButton}
          onPress={() => navigation.navigate('Upload')}
        >
          <Text style={styles.uploadButtonText}>Go to Upload</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const renderHeader = () => {
    if (totalCount === 0) return null

    return (
      <View style={styles.filterContainer}>
        <View style={styles.filterRow}>
          <Text style={styles.filterLabel}>Hide unsupported formats</Text>
          <Switch
            value={hideUnsupported}
            onValueChange={toggleHideUnsupported}
            trackColor={{ false: '#d1d5db', true: '#93c5fd' }}
            thumbColor={hideUnsupported ? '#3b82f6' : '#f3f4f6'}
          />
        </View>
        <Text style={styles.photoCount}>
          {totalCount} photo{totalCount !== 1 ? 's' : ''}
        </Text>
      </View>
    )
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={filteredPhotos}
        renderItem={renderPhoto}
        keyExtractor={(item) => item.photoId}
        numColumns={3}
        columnWrapperStyle={styles.row}
        contentContainerStyle={filteredPhotos.length === 0 ? styles.emptyContainer : undefined}
        ListHeaderComponent={renderHeader}
        ListEmptyComponent={renderEmpty}
        ListFooterComponent={renderFooter}
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor="#3b82f6"
            colors={['#3b82f6']}
          />
        }
      />
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  filterContainer: {
    backgroundColor: '#fff',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e7eb',
    marginBottom: 2,
  },
  filterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  filterLabel: {
    fontSize: 14,
    color: '#111827',
    fontWeight: '500',
  },
  photoCount: {
    fontSize: 12,
    color: '#6b7280',
    textAlign: 'center',
  },
  row: {
    gap: 2,
    paddingHorizontal: 2,
    marginBottom: 2,
  },
  photoItem: {
    width: ITEM_SIZE,
    height: ITEM_SIZE,
    backgroundColor: '#e5e7eb',
  },
  thumbnail: {
    width: '100%',
    height: '100%',
  },
  footer: {
    paddingVertical: 20,
    alignItems: 'center',
  },
  emptyContainer: {
    flex: 1,
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
    marginTop: 16,
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#6b7280',
    marginBottom: 24,
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
  uploadButton: {
    backgroundColor: '#3b82f6',
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 8,
  },
  uploadButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
})
