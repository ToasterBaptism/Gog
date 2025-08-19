import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  AppState,
  AppStateStatus,
} from 'react-native';
import NativeControl from '../lib/bridge/NativeControl';
import PermissionOverlay from '../components/PermissionOverlay';

const StartScreen: React.FC = () => {
  const [isActive, setIsActive] = useState(false);
  const [serviceEnabled, setServiceEnabled] = useState(false);
  const [permissionsGranted, setPermissionsGranted] = useState(false);
  const [showPermissionOverlay, setShowPermissionOverlay] = useState(false);
  const [statusText, setStatusText] = useState('Service inactive');
  const mountedRef = useRef(true);
  const startCheckTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startAttemptRef = useRef(0);

  useEffect(() => {
    checkServiceStatus();
    
    const handleAppStateChange = (nextAppState: AppStateStatus) => {
      if (nextAppState === 'active') {
        checkServiceStatus();
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => {
      mountedRef.current = false;
      if (startCheckTimeoutRef.current) {
        clearTimeout(startCheckTimeoutRef.current);
        startCheckTimeoutRef.current = null;
      }
      subscription?.remove();
    };
  }, []);

  const checkServiceStatus = async () => {
    try {
      const enabled = await NativeControl.isServiceEnabled();
      const actuallyRunning = await NativeControl.isAccessibilityServiceActuallyRunning();
      const permissions = await NativeControl.checkPermissions();
      const batteryIgnored = await NativeControl.checkBatteryOptimization();
      
      // Get detailed permission status for better debugging
      try {
        const detailedStatus = await NativeControl.getDetailedPermissionStatus();
        console.log('Detailed permission status:', detailedStatus);
      } catch (e) {
        console.warn('Could not get detailed permission status:', e);
      }
      
      setServiceEnabled(enabled);
      setPermissionsGranted(permissions);
      
      if (!enabled) {
        setStatusText('Needs Accessibility Service');
        setIsActive(false);
      } else if (enabled && !actuallyRunning) {
        setStatusText('Accessibility Service Not Running');
        setIsActive(false);
      } else if (!permissions) {
        setStatusText('Needs App Permissions');
        setIsActive(false);
      } else if (!batteryIgnored) {
        setStatusText('Battery Optimization Enabled');
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

  const [isBusy, setIsBusy] = useState(false);
  const handleStartStop = async () => {
    // IMMEDIATE DEBUG: Show alert to confirm button press is working
    Alert.alert('DEBUG', 'Start button was pressed! This confirms the button is working.');
    
    if (isBusy) {
      Alert.alert('DEBUG', 'Button is busy, returning early');
      return;
    }
    setIsBusy(true);
    console.log('handleStartStop called, isActive:', isActive);
    console.log('serviceEnabled:', serviceEnabled, 'permissionsGranted:', permissionsGranted);
    
    // Test if native module is available
    try {
      console.log('Testing native module availability...');
      const testResult = await NativeControl.isServiceEnabled();
      console.log('Native module test result:', testResult);
      Alert.alert('DEBUG', `Native module is working! Test result: ${testResult}`);
    } catch (e) {
      console.error('Native module test failed:', e);
      Alert.alert('DEBUG ERROR', `Native module failed: ${e.message}`);
      setIsBusy(false);
      return;
    }
    
    if (!isActive) {
      // Before starting, do a comprehensive permission check
      try {
        console.log('Checking all required permissions...');
        const permissionCheck = await NativeControl.checkAllRequiredPermissions();
        console.log('Permission check results:', permissionCheck);
        
        if (!permissionCheck.allPermissionsReady) {
          console.log('Not all permissions are ready, showing overlay');
          let missingItems = [];
          
          if (!permissionCheck.accessibilityService) {
            missingItems.push('Accessibility Service');
          }
          if (!permissionCheck.overlayPermission) {
            missingItems.push('Overlay Permission');
          }
          if (permissionCheck.missingRuntimePermissions && permissionCheck.missingRuntimePermissions.length > 0) {
            missingItems.push('Runtime Permissions');
          }
          if (!permissionCheck.batteryOptimizationIgnored) {
            missingItems.push('Battery Optimization');
          }
          
          Alert.alert(
            'Setup Required',
            `Please complete the following setup steps:\n\n• ${missingItems.join('\n• ')}\n\nTap "Setup" to configure these permissions.`,
            [
              { text: 'Cancel', style: 'cancel' },
              { text: 'Setup', onPress: () => setShowPermissionOverlay(true) }
            ]
          );
          return;
        }
      } catch (e) {
        console.warn('Failed to check permissions:', e);
        // Continue with the old check as fallback
        if (!serviceEnabled || !permissionsGranted) {
          console.log('Permissions not ready (fallback check), showing overlay');
          setShowPermissionOverlay(true);
          return;
        }
      }
    }

    try {
      if (isActive) {
        console.log('Stopping service...');
        setStatusText('Stopping...');
        await NativeControl.stop();
        setIsActive(false);
        setStatusText('Ready to start');
        console.log('Service stopped successfully');
      } else {
        console.log('Starting service...');
        setStatusText('Requesting screen capture...');
        
        console.log('Calling NativeControl.start()...');
        await NativeControl.start();
        console.log('NativeControl.start() completed successfully');
        
        setIsActive(true);
        setStatusText('Capturing...');
        
        // Check if service actually started after a short delay
        const attemptId = ++startAttemptRef.current;
        startCheckTimeoutRef.current = setTimeout(async () => {
          try {
            const actuallyRunning = await NativeControl.isAccessibilityServiceActuallyRunning();
            if (mountedRef.current && startAttemptRef.current === attemptId && !actuallyRunning) {
              setIsActive(false);
              setStatusText('Service failed to start');
              Alert.alert(
                'Service Failed', 
                'The accessibility service failed to start properly. Please check:\n\n' +
                '• Accessibility service is enabled\n' +
                '• Battery optimization is disabled\n' +
                '• Screen capture permission was granted\n\n' +
                'Try restarting the app if the issue persists.'
              );
            }
          } catch (e) {
            console.warn('Failed to check service status:', e);
          }
        }, 2000);
      }
    } catch (error: any) {
      console.error('Failed to start/stop service:', error);
      console.error('Error details:', JSON.stringify(error, null, 2));
      setIsActive(false);
      setStatusText('Error occurred');
      
      let errorMessage = 'Failed to start/stop service. ';
      let errorTitle = 'Error';
      
      if (error?.message?.includes('Screen capture permission denied')) {
        errorTitle = 'Permission Denied';
        errorMessage = 'Screen capture permission was denied. To use this app, you need to:\n\n' +
                      '1. Tap "Start" again\n' +
                      '2. When the system dialog appears, tap "Start now"\n' +
                      '3. Do not tap "Cancel" or press the back button\n\n' +
                      'The screen capture permission is required for the app to detect the ball.';
      } else if (error?.message?.includes('MediaProjection not supported')) {
        errorTitle = 'Not Supported';
        errorMessage = 'Screen capture is not supported on this device. This app requires Android 5.0 (API 21) or higher with MediaProjection support.';
      } else if (error?.message?.includes('Invalid activity context')) {
        errorTitle = 'App State Error';
        errorMessage = 'The app is not in the correct state. Please:\n\n' +
                      '1. Make sure the app is in the foreground\n' +
                      '2. Try closing and reopening the app\n' +
                      '3. Restart your device if the problem persists';
      } else if (error?.message?.includes('No activity available')) {
        errorTitle = 'App Not In Foreground';
        errorMessage = 'The app must be in the foreground to request screen capture.\n\n' +
                       '1. Bring the app to the foreground\n' +
                       '2. Tap "Start" again';
      } else if (error?.message?.includes('accessibility')) {
        errorTitle = 'Accessibility Service';
        errorMessage = 'Accessibility service is not properly enabled. Please check settings.';
      } else {
        errorMessage += `Please check all permissions and try again.\n\nTechnical details: ${error?.message || 'Unknown error'}`;
      }
      
      Alert.alert(errorTitle, errorMessage);
    } finally {
      setIsBusy(false);
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
          onPress={handleStartStop}
          disabled={isBusy}>
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