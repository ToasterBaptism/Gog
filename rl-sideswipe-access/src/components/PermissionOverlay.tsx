import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
} from 'react-native';
import NativeControl from '../lib/bridge/NativeControl';

interface PermissionOverlayProps {
  visible: boolean;
  onClose: () => void;
}

const PermissionOverlay: React.FC<PermissionOverlayProps> = ({
  visible,
  onClose,
}) => {
  const [serviceEnabled, setServiceEnabled] = useState(false);
  const [permissionsGranted, setPermissionsGranted] = useState(false);

  useEffect(() => {
    if (visible) {
      checkStatus();
    }
  }, [visible]);

  const checkStatus = async () => {
    try {
      const enabled = await NativeControl.isServiceEnabled();
      const permissions = await NativeControl.checkPermissions();
      setServiceEnabled(enabled);
      setPermissionsGranted(permissions);
    } catch (error) {
      console.error('Failed to check status:', error);
    }
  };

  const handleEnableAccessibility = () => {
    NativeControl.openAccessibilitySettings();
  };

  const handleRequestPermissions = async () => {
    try {
      await NativeControl.requestPermissions();
      // Check status after a delay to see if permissions were granted
      setTimeout(checkStatus, 1000);
    } catch (error) {
      console.error('Failed to request permissions:', error);
    }
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
            <Text style={[styles.stepNumber, serviceEnabled && styles.stepCompleted]}>
              {serviceEnabled ? '✓' : '1.'}
            </Text>
            <Text style={styles.stepText}>Enable Accessibility Service</Text>
            <TouchableOpacity
              style={[styles.button, serviceEnabled && styles.buttonCompleted]}
              onPress={handleEnableAccessibility}
              disabled={serviceEnabled}>
              <Text style={styles.buttonText}>
                {serviceEnabled ? 'Completed' : 'Open Settings'}
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

          {serviceEnabled && permissionsGranted && (
            <View style={styles.successMessage}>
              <Text style={styles.successText}>✅ All permissions granted!</Text>
              <Text style={styles.successSubtext}>You can now start the app</Text>
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