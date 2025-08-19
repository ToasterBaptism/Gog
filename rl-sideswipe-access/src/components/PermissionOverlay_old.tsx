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
  const serviceEnabled = !!permissionStatus.ACCESSIBILITY_SERVICE;
  const serviceActuallyRunning = !!permissionStatus.ACCESSIBILITY_SERVICE; // Approximation
  const permissionsGranted = !!permissionStatus.RECORD_AUDIO && !!permissionStatus.SYSTEM_ALERT_WINDOW;
  const batteryOptimized = !(permissionStatus.BATTERY_OPTIMIZATION_IGNORED ?? false);

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

  const handleEnableAccessibility = handleAccessibilitySettings;

  const handleAccessibilitySettings = () => {
    NativeControl.openAccessibilitySettings();
  };

  const handleRequestPermissions = async () => {
    try {
      await NativeControl.requestPermissions();
      // Re-check status after a short delay to see if permissions were granted
      setTimeout(checkPermissions, 1000);
    } catch (error) {
      console.error('Failed to request permissions:', error);
    }
  };

  const handleBatteryOptimization = () => {
    NativeControl.openBatteryOptimizationSettings();
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.container}>
          <Text style={styles.title}>Setup Required</Text>
          
          <View style={styles.step}>
            <Text style={[styles.stepNumber, serviceEnabled && serviceActuallyRunning && styles.stepCompleted]}>
              {serviceEnabled && serviceActuallyRunning ? '✓' : '1.'}
            </Text>
            <Text style={styles.stepText}>Enable Accessibility Service</Text>
            {serviceEnabled && !serviceActuallyRunning && (
              <Text style={styles.warningText}>
                ⚠️ Service enabled but not running - may be killed by system
              </Text>
            )}
            <TouchableOpacity
              style={[styles.button, serviceEnabled && serviceActuallyRunning && styles.buttonCompleted]}
              onPress={handleEnableAccessibility}
              disabled={serviceEnabled && serviceActuallyRunning}>
              <Text style={styles.buttonText}>
                {serviceEnabled && serviceActuallyRunning ? 'Completed' : 'Open Settings'}
              </Text>
            </TouchableOpacity>
          </View>

          <View style={styles.step}>
            <Text style={[styles.stepNumber, permissionsGranted && styles.stepCompleted]}>
              {permissionsGranted ? '✓' : '2.'}
            </Text>
            <Text style={styles.stepText}>Grant App Permissions</Text>
            <Text style={styles.stepSubtext}>
              Microphone, Vibration, Notifications, Overlay
            </Text>
            <TouchableOpacity
              style={[styles.button, permissionsGranted && styles.buttonCompleted]}
              onPress={handleRequestPermissions}
              disabled={permissionsGranted}>
              <Text style={styles.buttonText}>
                {permissionsGranted ? 'Completed' : 'Grant Permissions'}
              </Text>
            </TouchableOpacity>
          </View>

          <View style={styles.step}>
            <Text style={[styles.stepNumber, !batteryOptimized && styles.stepCompleted]}>
              {!batteryOptimized ? '✓' : '3.'}
            </Text>
            <Text style={styles.stepText}>Disable Battery Optimization</Text>
            <Text style={styles.stepSubtext}>
              Prevents Android from killing the accessibility service
            </Text>
            <TouchableOpacity
              style={[styles.button, !batteryOptimized && styles.buttonCompleted]}
              onPress={handleBatteryOptimization}
              disabled={!batteryOptimized}>
              <Text style={styles.buttonText}>
                {!batteryOptimized ? 'Completed' : 'Open Settings'}
              </Text>
            </TouchableOpacity>
          </View>

          {serviceEnabled && serviceActuallyRunning && permissionsGranted && !batteryOptimized && (
            <View style={styles.successMessage}>
              <Text style={styles.successText}>✅ All setup completed!</Text>
              <Text style={styles.successSubtext}>Your accessibility service should stay active</Text>
            </View>
          )}

          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Text style={styles.closeButtonText}>Cancel</Text>
          </TouchableOpacity>
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
    padding: 24,
    margin: 20,
    minWidth: 300,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 24,
    color: '#333333',
  },
  step: {
    marginBottom: 20,
  },
  stepNumber: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#007AFF',
    marginBottom: 8,
  },
  stepCompleted: {
    color: '#28a745',
  },
  stepText: {
    fontSize: 16,
    color: '#333333',
    marginBottom: 4,
  },
  stepSubtext: {
    fontSize: 12,
    color: '#666666',
    marginBottom: 12,
  },
  warningText: {
    fontSize: 12,
    color: '#ff6b35',
    marginBottom: 8,
    fontWeight: '500',
  },
  button: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 20,
    alignItems: 'center',
  },
  buttonCompleted: {
    backgroundColor: '#28a745',
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
  successMessage: {
    backgroundColor: '#d4edda',
    borderColor: '#c3e6cb',
    borderWidth: 1,
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
    alignItems: 'center',
  },
  successText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#155724',
    marginBottom: 4,
  },
  successSubtext: {
    fontSize: 14,
    color: '#155724',
  },
  closeButton: {
    marginTop: 12,
    alignItems: 'center',
  },
  closeButtonText: {
    color: '#666666',
    fontSize: 16,
  },
});

export default PermissionOverlay;