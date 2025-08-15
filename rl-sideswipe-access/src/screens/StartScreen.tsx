import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  AppState,
} from 'react-native';
import NativeControl from '../lib/bridge/NativeControl';
import PermissionOverlay from '../components/PermissionOverlay';

const StartScreen: React.FC = () => {
  const [isActive, setIsActive] = useState(false);
  const [serviceEnabled, setServiceEnabled] = useState(false);
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [showPermissionOverlay, setShowPermissionOverlay] = useState(false);
  const [statusText, setStatusText] = useState('Service inactive');

  useEffect(() => {
    checkServiceStatus();
    
    const handleAppStateChange = (nextAppState: string) => {
      if (nextAppState === 'active') {
        checkServiceStatus();
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => subscription?.remove();
  }, []);

  const checkServiceStatus = async () => {
    try {
      const enabled = await NativeControl.isServiceEnabled();
      const permissions = await NativeControl.checkPermissions();
      
      setServiceEnabled(enabled);
      setPermissionsGranted(permissions);
      
      if (!enabled) {
        setStatusText('Needs Accessibility Service');
        setIsActive(false);
      } else if (!permissions) {
        setStatusText('Needs App Permissions');
        setIsActive(false);
      } else if (isActive) {
        setStatusText('Capturing...');
      } else {
        setStatusText('Ready to start');
      }
    } catch (error) {
      console.error('Failed to check service status:', error);
      setStatusText('Service inactive');
    }
  };

  const handleStartStop = async () => {
    if (!serviceEnabled || !permissionsGranted) {
      setShowPermissionOverlay(true);
      return;
    }

    try {
      if (isActive) {
        await NativeControl.stop();
        setIsActive(false);
        setStatusText('Ready to start');
      } else {
        await NativeControl.start();
        setIsActive(true);
        setStatusText('Capturing...');
      }
    } catch (error) {
      console.error('Failed to start/stop service:', error);
      Alert.alert('Error', 'Failed to start/stop service. Please check permissions.');
    }
  };

  const handlePermissionOverlayClose = () => {
    setShowPermissionOverlay(false);
    checkServiceStatus();
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>RL Sideswipe Access</Text>
        
        <TouchableOpacity
          style={[
            styles.startButton,
            isActive && styles.startButtonActive,
          ]}
          onPress={handleStartStop}>
          <Text style={[
            styles.startButtonText,
            isActive && styles.startButtonTextActive,
          ]}>
            {isActive ? 'Stop' : 'Start'}
          </Text>
        </TouchableOpacity>

        <Text style={styles.statusText}>{statusText}</Text>
      </View>

      <PermissionOverlay
        visible={showPermissionOverlay}
        onClose={handlePermissionOverlayClose}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333333',
    marginBottom: 60,
    textAlign: 'center',
  },
  startButton: {
    backgroundColor: '#007AFF',
    borderRadius: 50,
    width: 120,
    height: 120,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 30,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  startButtonActive: {
    backgroundColor: '#FF3B30',
  },
  startButtonText: {
    color: '#ffffff',
    fontSize: 20,
    fontWeight: 'bold',
  },
  startButtonTextActive: {
    color: '#ffffff',
  },
  statusText: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
  },
});

export default StartScreen;