import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  ScrollView,
} from 'react-native';
import NativeControl from '../lib/bridge/NativeControl';

interface PermissionOverlayProps {
  visible: boolean;
  onClose: () => void;
}

interface PermissionStatus {
  [key: string]: boolean;
}

const PermissionOverlay: React.FC<PermissionOverlayProps> = ({
  visible,
  onClose,
}) => {
  const [permissionStatus, setPermissionStatus] = useState<PermissionStatus>({});
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (visible) {
      checkPermissions();
    }
  }, [visible]);

  const checkPermissions = async () => {
    try {
      const status = await NativeControl.getDetailedPermissionStatus();
      setPermissionStatus(status);
      console.log('Detailed permission status:', status);
    } catch (error) {
      console.error('Failed to check permissions:', error);
    }
  };

  const handleAccessibilitySettings = () => {
    NativeControl.openAccessibilitySettings();
  };

  const handleRequestPermissions = async () => {
    try {
      setLoading(true);
      await NativeControl.requestPermissions();
      // Wait a bit for permissions to be processed
      setTimeout(checkPermissions, 1000);
    } catch (error) {
      console.error('Failed to request permissions:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBatteryOptimization = async () => {
    try {
      await NativeControl.openBatteryOptimizationSettings();
    } catch (error) {
      console.error('Failed to open battery optimization settings:', error);
    }
  };

  const getPermissionStatusText = (key: string): string => {
    return permissionStatus[key] ? '✅' : '❌';
  };

  const isAccessibilityEnabled = permissionStatus['ACCESSIBILITY_SERVICE'] || false;
  const isOverlayEnabled = permissionStatus['SYSTEM_ALERT_WINDOW'] || false;
  const isBatteryOptimized = permissionStatus['BATTERY_OPTIMIZATION_IGNORED'] || false;
  const hasBasicPermissions = [
    'android.permission.VIBRATE',
    'android.permission.RECORD_AUDIO',
    'android.permission.WAKE_LOCK',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION'
  ].every(perm => permissionStatus[perm]);

  const allPermissionsGranted = isAccessibilityEnabled && isOverlayEnabled && isBatteryOptimized && hasBasicPermissions;

  return (
    <Modal
      visible={visible}
      transparent={true}
      animationType="fade"
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.container}>
          <ScrollView showsVerticalScrollIndicator={false}>
            <Text style={styles.title}>Setup Required</Text>
            <Text style={styles.description}>
              Complete all steps below to use RL Sideswipe Access:
            </Text>

            <View style={styles.stepContainer}>
              <Text style={styles.stepNumber}>1</Text>
              <View style={styles.stepContent}>
                <Text style={styles.stepTitle}>
                  {getPermissionStatusText('ACCESSIBILITY_SERVICE')} Accessibility Service
                </Text>
                <Text style={styles.stepDescription}>
                  Enable "RL Sideswipe Access" in Accessibility settings
                </Text>
                <TouchableOpacity
                  style={[styles.button, isAccessibilityEnabled && styles.buttonDisabled]}
                  onPress={handleAccessibilitySettings}
                  disabled={isAccessibilityEnabled}>
                  <Text style={styles.buttonText}>
                    {isAccessibilityEnabled ? 'Enabled' : 'Open Settings'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.stepContainer}>
              <Text style={styles.stepNumber}>2</Text>
              <View style={styles.stepContent}>
                <Text style={styles.stepTitle}>
                  {hasBasicPermissions ? '✅' : '❌'} App Permissions
                </Text>
                <Text style={styles.stepDescription}>
                  Grant microphone, foreground service, and notification permissions
                </Text>
                <View style={styles.permissionList}>
                  <Text style={styles.permissionItem}>
                    {getPermissionStatusText('android.permission.RECORD_AUDIO')} Microphone
                  </Text>
                  <Text style={styles.permissionItem}>
                    {getPermissionStatusText('android.permission.FOREGROUND_SERVICE')} Foreground Service
                  </Text>
                  <Text style={styles.permissionItem}>
                    {getPermissionStatusText('android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION')} Screen Capture
                  </Text>
                  <Text style={styles.permissionItem}>
                    {getPermissionStatusText('android.permission.VIBRATE')} Vibration
                  </Text>
                </View>
                <TouchableOpacity
                  style={[styles.button, hasBasicPermissions && styles.buttonDisabled]}
                  onPress={handleRequestPermissions}
                  disabled={loading || hasBasicPermissions}>
                  <Text style={styles.buttonText}>
                    {loading ? 'Requesting...' : hasBasicPermissions ? 'Granted' : 'Grant Permissions'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.stepContainer}>
              <Text style={styles.stepNumber}>3</Text>
              <View style={styles.stepContent}>
                <Text style={styles.stepTitle}>
                  {getPermissionStatusText('SYSTEM_ALERT_WINDOW')} Overlay Permission
                </Text>
                <Text style={styles.stepDescription}>
                  Allow app to display overlay for ball tracking
                </Text>
                <TouchableOpacity
                  style={[styles.button, isOverlayEnabled && styles.buttonDisabled]}
                  onPress={handleRequestPermissions}
                  disabled={isOverlayEnabled}>
                  <Text style={styles.buttonText}>
                    {isOverlayEnabled ? 'Granted' : 'Grant Overlay'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.stepContainer}>
              <Text style={styles.stepNumber}>4</Text>
              <View style={styles.stepContent}>
                <Text style={styles.stepTitle}>
                  📹 Screen Capture Permission
                </Text>
                <Text style={styles.stepDescription}>
                  Screen capture permission is granted automatically when you click "Start"
                </Text>
                <View style={styles.infoBox}>
                  <Text style={styles.infoText}>
                    ℹ️ This permission cannot be granted in advance. It will be requested when you click the "Start" button.
                  </Text>
                </View>
              </View>
            </View>

            <View style={styles.stepContainer}>
              <Text style={styles.stepNumber}>5</Text>
              <View style={styles.stepContent}>
                <Text style={styles.stepTitle}>
                  {getPermissionStatusText('BATTERY_OPTIMIZATION_IGNORED')} Battery Optimization
                </Text>
                <Text style={styles.stepDescription}>
                  Disable battery optimization to prevent service interruption
                </Text>
                <TouchableOpacity
                  style={[styles.button, isBatteryOptimized && styles.buttonDisabled]}
                  onPress={handleBatteryOptimization}
                  disabled={isBatteryOptimized}>
                  <Text style={styles.buttonText}>
                    {isBatteryOptimized ? 'Disabled' : 'Battery Settings'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>

            {allPermissionsGranted && (
              <View style={styles.successContainer}>
                <Text style={styles.successText}>🎉 Ready to start!</Text>
                <Text style={styles.successDescription}>
                  You can now use the "Start" button. Screen capture permission will be requested automatically.
                </Text>
              </View>
            )}

            <TouchableOpacity
              style={styles.refreshButton}
              onPress={checkPermissions}>
              <Text style={styles.refreshButtonText}>🔄 Refresh Status</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.closeButton}
              onPress={onClose}>
              <Text style={styles.closeButtonText}>Done</Text>
            </TouchableOpacity>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  container: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 20,
    margin: 20,
    maxHeight: '90%',
    width: '90%',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333333',
    textAlign: 'center',
    marginBottom: 10,
  },
  description: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
    marginBottom: 20,
  },
  stepContainer: {
    flexDirection: 'row',
    marginBottom: 20,
    padding: 15,
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
  },
  stepNumber: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#007AFF',
    marginRight: 15,
    minWidth: 25,
  },
  stepContent: {
    flex: 1,
  },
  stepTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333333',
    marginBottom: 5,
  },
  stepDescription: {
    fontSize: 14,
    color: '#666666',
    marginBottom: 10,
  },
  permissionList: {
    marginBottom: 10,
  },
  permissionItem: {
    fontSize: 12,
    color: '#666666',
    marginBottom: 2,
  },
  button: {
    backgroundColor: '#007AFF',
    borderRadius: 6,
    paddingVertical: 8,
    paddingHorizontal: 16,
    alignSelf: 'flex-start',
  },
  buttonDisabled: {
    backgroundColor: '#28a745',
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  successContainer: {
    backgroundColor: '#d4edda',
    borderRadius: 8,
    padding: 15,
    marginBottom: 20,
    borderColor: '#c3e6cb',
    borderWidth: 1,
  },
  successText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#155724',
    textAlign: 'center',
    marginBottom: 5,
  },
  successDescription: {
    fontSize: 14,
    color: '#155724',
    textAlign: 'center',
  },
  infoBox: {
    backgroundColor: '#e7f3ff',
    borderRadius: 6,
    padding: 10,
    marginTop: 10,
    borderColor: '#b3d9ff',
    borderWidth: 1,
  },
  infoText: {
    fontSize: 12,
    color: '#0066cc',
    textAlign: 'center',
  },
  refreshButton: {
    backgroundColor: '#6c757d',
    borderRadius: 6,
    paddingVertical: 10,
    paddingHorizontal: 20,
    alignSelf: 'center',
    marginBottom: 15,
  },
  refreshButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  closeButton: {
    backgroundColor: '#dc3545',
    borderRadius: 6,
    paddingVertical: 12,
    paddingHorizontal: 30,
    alignSelf: 'center',
  },
  closeButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default PermissionOverlay;