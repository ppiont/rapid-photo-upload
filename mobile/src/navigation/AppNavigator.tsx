import React from 'react'
import { View, Text, ActivityIndicator, StyleSheet, Platform } from 'react-native'
import { NavigationContainer } from '@react-navigation/native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { useAuth } from '../contexts/AuthContext'

// Placeholder screens - will implement these in later tasks
import LoginScreen from '../screens/LoginScreen'
import RegisterScreen from '../screens/RegisterScreen'
import UploadScreen from '../screens/UploadScreen'
import GalleryScreen from '../screens/GalleryScreen'
import PhotoDetailScreen from '../screens/PhotoDetailScreen'

export type RootStackParamList = {
  Login: undefined
  Register: undefined
  MainTabs: undefined
  PhotoDetail: { photoId: string }
}

export type TabParamList = {
  Upload: undefined
  Gallery: undefined
}

const Stack = createNativeStackNavigator<RootStackParamList>()
const Tab = createBottomTabNavigator<TabParamList>()

function MainTabs() {
  return (
    <Tab.Navigator
      screenOptions={{
        tabBarActiveTintColor: '#007AFF',
        tabBarInactiveTintColor: '#8E8E93',
        tabBarStyle: {
          backgroundColor: '#F9F9F9',
          borderTopColor: '#C6C6C8',
          borderTopWidth: 0.5,
          paddingTop: 8,
          height: Platform.OS === 'ios' ? 88 : 60,
        },
        tabBarLabelStyle: {
          fontSize: 10,
          fontWeight: '500',
          marginBottom: Platform.OS === 'ios' ? 0 : 8,
        },
        headerStyle: {
          backgroundColor: '#F9F9F9',
          borderBottomColor: '#C6C6C8',
          borderBottomWidth: 0.5,
        },
        headerTintColor: '#000',
        headerTitleStyle: {
          fontWeight: '600',
          fontSize: 17,
        },
        headerShadowVisible: false,
      }}
    >
      <Tab.Screen
        name="Upload"
        component={UploadScreen}
        options={{
          title: 'Upload',
          tabBarLabel: 'Upload',
          tabBarIcon: ({ color }) => (
            <Text style={{ fontSize: 24 }}>📤</Text>
          ),
        }}
      />
      <Tab.Screen
        name="Gallery"
        component={GalleryScreen}
        options={{
          title: 'Gallery',
          tabBarLabel: 'Gallery',
          tabBarIcon: ({ color }) => (
            <Text style={{ fontSize: 24 }}>🖼️</Text>
          ),
        }}
      />
    </Tab.Navigator>
  )
}

export default function AppNavigator() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    )
  }

  return (
    <NavigationContainer>
      <Stack.Navigator
        screenOptions={{
          headerShown: false,
          gestureEnabled: true,
          animation: 'default',
        }}
      >
        {!user ? (
          // Auth screens
          <>
            <Stack.Screen
              name="Login"
              component={LoginScreen}
              options={{ headerShown: true, title: 'Login' }}
            />
            <Stack.Screen
              name="Register"
              component={RegisterScreen}
              options={{ headerShown: true, title: 'Register' }}
            />
          </>
        ) : (
          // App screens
          <>
            <Stack.Screen name="MainTabs" component={MainTabs} />
            <Stack.Screen
              name="PhotoDetail"
              component={PhotoDetailScreen}
              options={{
                headerShown: true,
                title: 'Photo Details',
                headerStyle: {
                  backgroundColor: '#F9F9F9',
                },
                headerTintColor: '#007AFF',
                headerTitleStyle: {
                  fontWeight: '600',
                  fontSize: 17,
                },
                headerShadowVisible: false,
              }}
            />
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  )
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f9fafb',
  },
})
