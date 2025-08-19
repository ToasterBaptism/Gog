import { NativeModules } from 'react-native';

interface PermissionStatus {
  [key: string]: boolean;
}

interface NativeControlInterface {
  isServiceEnabled(): Promise<boolean>;
  openAccessibilitySettings(): void;
  openOverlaySettings(): void;
  checkPermissions(): Promise<boolean>;
  requestPermissions(): Promise<void>;
  getDetailedPermissionStatus(): Promise<PermissionStatus>;
  checkAllRequiredPermissions(): Promise<any>;
  hasMediaProjectionPermission(): Promise<boolean>;
  start(): Promise<void>;
  stop(): Promise<void>;
  checkBatteryOptimization(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<void>;
  isAccessibilityServiceActuallyRunning(): Promise<boolean>;
  debugPermissionSystem(): Promise<any>;
}

const NativeControl = NativeModules.NativeControlModule as NativeControlInterface | undefined;

const Fallback: NativeControlInterface = {
  isServiceEnabled: async () => false,
  openAccessibilitySettings: () => { throw new Error('NativeControlModule not linked'); },
  openOverlaySettings: () => { throw new Error('NativeControlModule not linked'); },
  checkPermissions: async () => false,
  requestPermissions: async () => { throw new Error('NativeControlModule not linked'); },
  getDetailedPermissionStatus: async () => ({}),
  checkAllRequiredPermissions: async () => ({}),
  hasMediaProjectionPermission: async () => false,
  start: async () => { throw new Error('NativeControlModule not linked'); },
  stop: async () => { throw new Error('NativeControlModule not linked'); },
  checkBatteryOptimization: async () => false,
  openBatteryOptimizationSettings: async () => { throw new Error('NativeControlModule not linked'); },
  isAccessibilityServiceActuallyRunning: async () => false,
  debugPermissionSystem: async () => ({}),
};

export default (NativeControl ?? Fallback);