import {
  EmitterSubscription,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';

interface Notification {
  addEventListener: (
    key: string,
    listener: (result: any) => void,
  ) => EmitterSubscription;
}

const NativeNotification = NativeModules.NotificationDemo;

const eventEmitter = new NativeEventEmitter(NativeNotification as any);

export default {
  ...NativeNotification,
  addEventListener(key: string, listener: (result: any) => void) {
    return eventEmitter.addListener(key, listener);
  },
} as Notification;
