import { NativeModules } from 'react-native';

interface PermissionStatus {
  [key: string]: boolean;
}

interface NativeControlInterface {
  isServiceEnabled(): Promise<boolean>;
  openAccessibilitySettings(): void;
  checkPermissions(): Promise<boolean>;
  requestPermissions(): Promise<void>;
  getDetailedPermissionStatus(): Promise<PermissionStatus>;
  hasMediaProjectionPermission(): Promise<boolean>;
  start(): Promise<void>;
  stop(): Promise<void>;
  checkBatteryOptimization(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<void>;
  isAccessibilityServiceActuallyRunning(): Promise<boolean>;
  debugPermissionSystem(): Promise<any>;
}

const { NativeControlModule } = NativeModules;

export default NativeControlModule as NativeControlInterface;