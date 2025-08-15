import { NativeModules } from 'react-native';

interface NativeControlInterface {
  isServiceEnabled(): Promise<boolean>;
  openAccessibilitySettings(): void;
  checkPermissions(): Promise<boolean>;
  requestPermissions(): Promise<void>;
  hasMediaProjectionPermission(): Promise<boolean>;
  start(): Promise<void>;
  stop(): Promise<void>;
  checkBatteryOptimization(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<void>;
  isAccessibilityServiceActuallyRunning(): Promise<boolean>;
}

const { NativeControlModule } = NativeModules;

export default NativeControlModule as NativeControlInterface;