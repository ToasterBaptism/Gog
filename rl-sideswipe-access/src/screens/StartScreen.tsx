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
  const [detectionStats, setDetectionStats] = useState({
    isDetecting: false,
    framesProcessed: 0,
    ballsDetected: 0,
    lastDetectionTime: 0,
    averageFPS: 0,
    templatesLoaded: 0,
  });
  const mountedRef = useRef(true);
  const startCheckTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startAttemptRef = useRef(0);
  const statsIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

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
      if (statsIntervalRef.current) {
        clearInterval(statsIntervalRef.current);
        statsIntervalRef.current = null;
      }
      subscription?.remove();
    };
  }, []);

  // Update detection statistics when service is active
  useEffect(() => {
    if (isActive && serviceEnabled) {
      // Start polling for statistics
      statsIntervalRef.current = setInterval(async () => {
        try {
          const stats = await NativeControl.getDetectionStatistics();
          setDetectionStats(stats);
        } catch (error) {
          console.log('Failed to get detection statistics:', error);
        }
      }, 1000); // Update every second
    } else {
      // Clear statistics when service is not active
      if (statsIntervalRef.current) {
        clearInterval(statsIntervalRef.current);
        statsIntervalRef.current = null;
      }
      setDetectionStats({
        isDetecting: false,
        framesProcessed: 0,
        ballsDetected: 0,
        lastDetectionTime: 0,
        averageFPS: 0,
        templatesLoaded: 0,
      });
    }

    return () => {
      if (statsIntervalRef.current) {
        clearInterval(statsIntervalRef.current);
        statsIntervalRef.current = null;
      }
    };
  }, [isActive, serviceEnabled]);

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
    if (isBusy) return;
    setIsBusy(true);
    console.log('handleStartStop called, isActive:', isActive);
    console.log('serviceEnabled:', serviceEnabled, 'permissionsGranted:', permissionsGranted);
    
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
          setIsBusy(false);
          return;
        }
      } catch (e) {
        console.warn('Failed to check permissions:', e);
        // Continue with the old check as fallback
        if (!serviceEnabled || !permissionsGranted) {
          console.log('Permissions not ready (fallback check), showing overlay');
          setShowPermissionOverlay(true);
          setIsBusy(false);
          return;
        }
      }
    }

    try {
      if (isActive) {
        console.log('Stopping service...');
        setStatusText('Stopping...');
        
        // Clear any pending timeouts
        if (startCheckTimeoutRef.current) {
          clearTimeout(startCheckTimeoutRef.current);
          startCheckTimeoutRef.current = null;
        }
        
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
        setStatusText('Capturing... (Service is running in background)');
        
        // Show success message
        Alert.alert(
          'Service Started!', 
          'The screen capture service is now running in the background.\n\n' +
          '✅ You can now minimize this app\n' +
          '✅ Open Rocket League Sideswipe\n' +
          '✅ The app will monitor for the ball automatically\n\n' +
          'Note: The current version uses a stub AI engine for testing. ' +
          'Ball detection is not yet implemented but the service is capturing frames.',
          [{ text: 'OK' }]
        );
        
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
      
      // Clear any pending timeouts on error
      if (startCheckTimeoutRef.current) {
        clearTimeout(startCheckTimeoutRef.current);
        startCheckTimeoutRef.current = null;
      }
      
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

  const resetStatistics = async () => {
    try {
      await NativeControl.resetDetectionStatistics();
      // Immediately update the display
      const stats = await NativeControl.getDetectionStatistics();
      setDetectionStats(stats);
    } catch (error) {
      console.log('Failed to reset statistics:', error);
    }
  };

  const enableManualBallPositioning = async () => {
    try {
      await NativeControl.enableManualBallPositioning();
      Alert.alert(
        '🎯 Manual Ball Positioning Enabled',
        'You can now tap on the ball in the game to capture it as a template for better detection.\n\n' +
        '1. Go to your game\n' +
        '2. Find a clear view of the ball\n' +
        '3. Tap directly on the ball\n' +
        '4. The app will learn this ball pattern'
      );
    } catch (error) {
      console.log('Failed to enable manual positioning:', error);
      Alert.alert('Error', 'Failed to enable manual ball positioning');
    }
  };

  const captureTemplateAtCenter = async () => {
    try {
      // Capture template at screen center (common ball position)
      await NativeControl.captureTemplateAtPosition(0.5, 0.5);
      Alert.alert(
        '📸 Template Captured',
        'Ball template captured at screen center. This will help improve ball detection accuracy.'
      );
      
      // Update template count
      const stats = await NativeControl.getDetectionStatistics();
      setDetectionStats(stats);
    } catch (error) {
      console.log('Failed to capture template:', error);
      Alert.alert('Error', 'Failed to capture ball template');
    }
  };

  const formatLastDetection = (timestamp: number) => {
    if (timestamp === 0) return 'Never';
    const now = Date.now();
    const diff = now - timestamp;
    if (diff < 1000) return 'Just now';
    if (diff < 60000) return `${Math.floor(diff / 1000)}s ago`;
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
    return `${Math.floor(diff / 3600000)}h ago`;
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

        {/* Detection Statistics */}
        {isActive && serviceEnabled && (
          <View style={styles.statsContainer}>
            <Text style={styles.statsTitle}>🎯 Detection Statistics</Text>
            
            <View style={styles.statsGrid}>
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Status</Text>
                <Text style={[styles.statValue, { color: detectionStats.isDetecting ? '#4CAF50' : '#FF9800' }]}>
                  {detectionStats.isDetecting ? '🟢 Active' : '🟡 Standby'}
                </Text>
              </View>
              
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Templates</Text>
                <Text style={styles.statValue}>{detectionStats.templatesLoaded}</Text>
              </View>
              
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Frames</Text>
                <Text style={styles.statValue}>{detectionStats.framesProcessed.toLocaleString()}</Text>
              </View>
              
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Balls Found</Text>
                <Text style={[styles.statValue, { color: detectionStats.ballsDetected > 0 ? '#4CAF50' : '#666' }]}>
                  {detectionStats.ballsDetected}
                </Text>
              </View>
              
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>FPS</Text>
                <Text style={styles.statValue}>{detectionStats.averageFPS.toFixed(1)}</Text>
              </View>
              
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Last Detection</Text>
                <Text style={styles.statValue}>{formatLastDetection(detectionStats.lastDetectionTime)}</Text>
              </View>
            </View>
            
            <TouchableOpacity style={styles.resetButton} onPress={resetStatistics}>
              <Text style={styles.resetButtonText}>🔄 Reset Stats</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.templateButton} onPress={enableManualBallPositioning}>
              <Text style={styles.templateButtonText}>🎯 Enable Manual Ball Capture</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.templateButton} onPress={captureTemplateAtCenter}>
              <Text style={styles.templateButtonText}>📸 Capture Ball Template</Text>
            </TouchableOpacity>
          </View>
        )}
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
  statsContainer: {
    marginTop: 20,
    padding: 16,
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#333',
  },
  statsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#ffffff',
    textAlign: 'center',
    marginBottom: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  statItem: {
    width: '48%',
    marginBottom: 12,
    padding: 8,
    backgroundColor: '#2a2a2a',
    borderRadius: 8,
  },
  statLabel: {
    fontSize: 12,
    color: '#999',
    marginBottom: 4,
  },
  statValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ffffff',
  },
  resetButton: {
    marginTop: 12,
    padding: 10,
    backgroundColor: '#333',
    borderRadius: 8,
    alignItems: 'center',
  },
  resetButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  templateButton: {
    marginTop: 8,
    padding: 12,
    backgroundColor: '#FF6B35',
    borderRadius: 8,
    alignItems: 'center',
  },
  templateButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
});

export default StartScreen;