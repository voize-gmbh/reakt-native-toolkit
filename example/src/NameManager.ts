import { NativeModules } from 'react-native';
import { Next } from 'reakt-native-toolkit';

interface NameManager {
  name: Next<string>;
  setName: (name: string) => Promise<void>;
}

export default NativeModules.NameManager as NameManager;
