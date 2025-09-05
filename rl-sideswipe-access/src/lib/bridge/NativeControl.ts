import { NativeModules } from 'react-native';

interface PermissionStatus {
  [key: string]: boolean;
}

interface DetectionStatistics {
  isDetecting: boolean;
  framesProcessed: number;
  ballsDetected: number;
  lastDetectionTime: number;
  averageFPS: number;
  templatesLoaded: number;
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
  getDetectionStatistics(): Promise<DetectionStatistics>;
  resetDetectionStatistics(): Promise<void>;
  captureTemplateAtPosition(x: number, y: number): Promise<boolean>;
  enableManualBallPositioning(): Promise<boolean>;
  getTemplateCount(): Promise<number>;
}

const NativeControl = NativeModules.NativeControlModule as
  | NativeControlInterface
  | undefined;

const Fallback: NativeControlInterface = {
  isServiceEnabled: async () => {
    console.warn('NativeControlModule not linked - using fallback');
    return false;
  },
  openAccessibilitySettings: () => {
    console.warn('NativeControlModule not linked - cannot open accessibility settings');
  },
  openOverlaySettings: () => {
    console.warn('NativeControlModule not linked - cannot open overlay settings');
  },
  checkPermissions: async () => {
    console.warn('NativeControlModule not linked - permissions check failed');
    return false;
  },
  requestPermissions: async () => {
    console.warn('NativeControlModule not linked - cannot request permissions');
  },
  getDetailedPermissionStatus: async () => {
    console.warn('NativeControlModule not linked - cannot get permission status');
    return {};
  },
  checkAllRequiredPermissions: async () => {
    console.warn('NativeControlModule not linked - cannot check permissions');
    return {};
  },
  hasMediaProjectionPermission: async () => {
    console.warn('NativeControlModule not linked - cannot check media projection');
    return false;
  },
  start: async () => {
    console.warn('NativeControlModule not linked - cannot start service');
  },
  stop: async () => {
    console.warn('NativeControlModule not linked - cannot stop service');
  },
  checkBatteryOptimization: async () => {
    console.warn('NativeControlModule not linked - cannot check battery optimization');
    return false;
  },
  openBatteryOptimizationSettings: async () => {
    console.warn('NativeControlModule not linked - cannot open battery settings');
  },
  isAccessibilityServiceActuallyRunning: async () => {
    console.warn('NativeControlModule not linked - cannot check accessibility service');
    return false;
  },
  debugPermissionSystem: async () => {
    console.warn('NativeControlModule not linked - cannot debug permissions');
    return {};
  },
  getDetectionStatistics: async () => {
    console.warn('NativeControlModule not linked - cannot get detection stats');
    return {
      isDetecting: false,
      framesProcessed: 0,
      ballsDetected: 0,
      lastDetectionTime: 0,
      averageFPS: 0,
      templatesLoaded: 0,
    };
  },
  resetDetectionStatistics: async () => {
    console.warn('NativeControlModule not linked - cannot reset detection stats');
  },
  captureTemplateAtPosition: async () => {
    console.warn('NativeControlModule not linked - cannot capture template');
    return false;
  },
  enableManualBallPositioning: async () => {
    console.warn('NativeControlModule not linked - cannot enable manual positioning');
    return false;
  },
  getTemplateCount: async () => {
    console.warn('NativeControlModule not linked - cannot get template count');
    return 0;
  },
};

export default NativeControl ?? Fallback;
