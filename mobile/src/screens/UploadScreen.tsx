import React, { useState } from 'react'
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  Image,
  ActivityIndicator,
  Platform,
} from 'react-native'
import { usePhotoUpload } from '../hooks/usePhotoUpload'
import type { CompositeNavigationProp } from '@react-navigation/native'
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs'
import type { NativeStackNavigationProp } from '@react-navigation/native-stack'
import type { RootStackParamList, TabParamList } from '../navigation/AppNavigator'

type UploadScreenNavigationProp = CompositeNavigationProp<
  BottomTabNavigationProp<TabParamList, 'Upload'>,
  NativeStackNavigationProp<RootStackParamList>
>

interface Props {
  navigation: UploadScreenNavigationProp
}

export default function UploadScreen({ navigation }: Props) {
  const { selectedPhotos, uploading, stats, pickPhotos, uploadPhotos, reset } = usePhotoUpload()

  const handlePickPhotos = async () => {
    try {
      await pickPhotos()
    } catch (error) {
      Alert.alert('Error', error instanceof Error ? error.message : 'Failed to pick photos')
    }
  }

  const handleUpload = async () => {
    try {
      await uploadPhotos()
      // Only show alert if there were failures
      if (stats.failed > 0) {
        Alert.alert(
          'Upload Complete',
          `${stats.completed} succeeded, ${stats.failed} failed`
        )
      }
    } catch (error) {
      console.error('[UploadScreen] Upload error:', error)
      Alert.alert('Error', error instanceof Error ? error.message : 'Upload failed')
    }
  }

  const renderStats = () => {
    if (selectedPhotos.length === 0) return null

    const overallProgress = selectedPhotos.length > 0
      ? Math.round(((stats.completed + stats.failed) / stats.total) * 100)
      : 0

    return (
      <View style={styles.statsContainer}>
        <Text style={styles.statsTitle}>Upload Progress</Text>
        <View style={styles.progressBar}>
          <View style={[styles.progressFill, { width: `${overallProgress}%` }]} />
        </View>
        <Text style={styles.progressText}>{overallProgress}%</Text>

        <View style={styles.statsRow}>
          <View style={styles.stat}>
            <Text style={styles.statValue}>{stats.total}</Text>
            <Text style={styles.statLabel}>Total</Text>
          </View>
          <View style={styles.stat}>
            <Text style={[styles.statValue, { color: '#10b981' }]}>{stats.completed}</Text>
            <Text style={styles.statLabel}>Completed</Text>
          </View>
          <View style={styles.stat}>
            <Text style={[styles.statValue, { color: '#3b82f6' }]}>{stats.uploading}</Text>
            <Text style={styles.statLabel}>Uploading</Text>
          </View>
          <View style={styles.stat}>
            <Text style={[styles.statValue, { color: '#ef4444' }]}>{stats.failed}</Text>
            <Text style={styles.statLabel}>Failed</Text>
          </View>
        </View>
      </View>
    )
  }

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollView}>
        <View style={styles.content}>
          {selectedPhotos.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyText}>No photos selected</Text>
              <Text style={styles.emptySubtext}>Select up to 500 photos to upload</Text>
            </View>
          ) : (
            <>
              {renderStats()}
              <View style={styles.photoGrid}>
                {selectedPhotos.map((photo, index) => (
                  <View key={index} style={styles.photoItem}>
                    <Image source={{ uri: photo.uri }} style={styles.thumbnail} />
                    {photo.status === 'uploading' && (
                      <View style={styles.overlay}>
                        <ActivityIndicator color="#fff" />
                        <Text style={styles.overlayText}>{Math.round(photo.progress)}%</Text>
                      </View>
                    )}
                    {photo.status === 'completed' && (
                      <View style={[styles.badge, styles.badgeSuccess]}>
                        <Text style={styles.badgeText}>✓</Text>
                      </View>
                    )}
                    {photo.status === 'failed' && (
                      <View style={[styles.badge, styles.badgeError]}>
                        <Text style={styles.badgeText}>✕</Text>
                      </View>
                    )}
                  </View>
                ))}
              </View>
            </>
          )}
        </View>
      </ScrollView>

      <View style={styles.footer}>
        {selectedPhotos.length === 0 ? (
          <TouchableOpacity style={styles.button} onPress={handlePickPhotos}>
            <Text style={styles.buttonText}>Select Photos</Text>
          </TouchableOpacity>
        ) : uploading ? (
          <TouchableOpacity style={[styles.button, styles.buttonDisabled]} disabled>
            <ActivityIndicator color="#fff" />
          </TouchableOpacity>
        ) : stats.completed + stats.failed === stats.total ? (
          <>
            <TouchableOpacity
              style={styles.buttonSecondary}
              onPress={() => navigation.navigate('Gallery')}
            >
              <Text style={styles.buttonSecondaryText}>View Gallery</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.button} onPress={reset}>
              <Text style={styles.buttonText}>Upload More</Text>
            </TouchableOpacity>
          </>
        ) : (
          <>
            <TouchableOpacity style={styles.buttonSecondary} onPress={reset}>
              <Text style={styles.buttonSecondaryText}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.button} onPress={handleUpload}>
              <Text style={styles.buttonText}>Upload {stats.total} Photos</Text>
            </TouchableOpacity>
          </>
        )}
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    padding: 16,
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 80,
  },
  emptyText: {
    fontSize: 20,
    fontWeight: '600',
    color: '#000000',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 15,
    color: '#8E8E93',
    textAlign: 'center',
  },
  statsContainer: {
    backgroundColor: '#F9F9F9',
    borderRadius: 12,
    padding: 20,
    marginBottom: 20,
  },
  statsTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#000000',
    marginBottom: 16,
  },
  progressBar: {
    height: 6,
    backgroundColor: '#E5E5EA',
    borderRadius: 3,
    overflow: 'hidden',
    marginBottom: 12,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#007AFF',
  },
  progressText: {
    textAlign: 'center',
    fontSize: 15,
    fontWeight: '600',
    color: '#8E8E93',
    marginBottom: 20,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  stat: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 28,
    fontWeight: '700',
    color: '#000000',
  },
  statLabel: {
    fontSize: 11,
    color: '#8E8E93',
    marginTop: 4,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  photoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    margin: -1,
  },
  photoItem: {
    width: '33.333%',
    aspectRatio: 1,
    position: 'relative',
    padding: 1,
  },
  thumbnail: {
    width: '100%',
    height: '100%',
    borderRadius: 0,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    borderRadius: 0,
    justifyContent: 'center',
    alignItems: 'center',
  },
  overlayText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '600',
    marginTop: 6,
  },
  badge: {
    position: 'absolute',
    top: 6,
    right: 6,
    width: 26,
    height: 26,
    borderRadius: 13,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000000AA',
  },
  badgeSuccess: {
    backgroundColor: '#34C759',
  },
  badgeError: {
    backgroundColor: '#FF3B30',
  },
  badgeText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  footer: {
    flexDirection: 'row',
    padding: 16,
    paddingBottom: Platform.OS === 'ios' ? 34 : 16,
    backgroundColor: '#F9F9F9',
    borderTopWidth: 0.5,
    borderTopColor: '#C6C6C8',
  },
  button: {
    flex: 1,
    backgroundColor: '#007AFF',
    borderRadius: 12,
    paddingVertical: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 12,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '600',
  },
  buttonSecondary: {
    flex: 1,
    backgroundColor: '#E5E5EA',
    borderRadius: 12,
    paddingVertical: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonSecondaryText: {
    color: '#000000',
    fontSize: 17,
    fontWeight: '600',
  },
})
