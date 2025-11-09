import React, { useState } from 'react'
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from 'react-native'

interface TagInputProps {
  tags: string[]
  onAddTag: (tag: string) => Promise<void>
  onRemoveTag: (tag: string) => Promise<void>
}

export function TagInput({ tags, onAddTag, onRemoveTag }: TagInputProps) {
  const [inputValue, setInputValue] = useState('')
  const [loading, setLoading] = useState(false)

  const handleAddTag = async () => {
    const trimmedTag = inputValue.trim()
    if (!trimmedTag) return

    // Check if tag already exists
    if (tags.includes(trimmedTag)) {
      setInputValue('')
      return
    }

    try {
      setLoading(true)
      await onAddTag(trimmedTag)
      setInputValue('')
    } catch {
      // Error handling is done in parent component
    } finally {
      setLoading(false)
    }
  }

  const handleRemoveTag = async (tag: string) => {
    try {
      setLoading(true)
      await onRemoveTag(tag)
    } catch {
      // Error handling is done in parent component
    } finally {
      setLoading(false)
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Tags</Text>

      <View style={styles.inputContainer}>
        <TextInput
          style={styles.input}
          value={inputValue}
          onChangeText={setInputValue}
          placeholder="Add a tag..."
          placeholderTextColor="#9ca3af"
          onSubmitEditing={handleAddTag}
          returnKeyType="done"
          editable={!loading}
        />
        <TouchableOpacity
          style={[styles.addButton, loading && styles.addButtonDisabled]}
          onPress={handleAddTag}
          disabled={loading || !inputValue.trim()}
        >
          {loading ? (
            <ActivityIndicator size="small" color="#fff" />
          ) : (
            <Text style={styles.addButtonText}>Add</Text>
          )}
        </TouchableOpacity>
      </View>

      {tags.length > 0 && (
        <View style={styles.tagsContainer}>
          {tags.map((tag, index) => (
            <View key={index} style={styles.tag}>
              <Text style={styles.tagText}>{tag}</Text>
              <TouchableOpacity
                style={styles.removeButton}
                onPress={() => handleRemoveTag(tag)}
                disabled={loading}
              >
                <Text style={styles.removeButtonText}>✕</Text>
              </TouchableOpacity>
            </View>
          ))}
        </View>
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  label: {
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 12,
  },
  inputContainer: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 12,
  },
  input: {
    flex: 1,
    backgroundColor: '#f9fafb',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: '#111827',
  },
  addButton: {
    backgroundColor: '#3b82f6',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    minWidth: 70,
  },
  addButtonDisabled: {
    opacity: 0.6,
  },
  addButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  tagsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  tag: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#dbeafe',
    paddingLeft: 12,
    paddingRight: 4,
    paddingVertical: 6,
    borderRadius: 16,
    gap: 6,
  },
  tagText: {
    fontSize: 14,
    color: '#1e40af',
    fontWeight: '500',
  },
  removeButton: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: '#3b82f6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  removeButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
})
