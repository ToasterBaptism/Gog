import { NativeModules } from 'react-native';

interface NativeControlInterface {
  isServiceEnabled(): Promise<boolean>;
  openAccessibilitySettings(): void;
  start(): Promise<void>;
  stop(): Promise<void>;
}

const { NativeControlModule } = NativeModules;

export default NativeControlModule as NativeControlInterface;