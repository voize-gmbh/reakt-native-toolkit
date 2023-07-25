import React, { useState } from 'react';
import { Button, Text, View } from 'react-native';
import { EmitterSubscription } from 'react-native';
import { StyleSheet } from 'react-native';
import { NotificationDemo } from '../generated/modules';

const Notifications: React.FC = () => {
  const [notifications, setNotifications] = useState<string[]>([]);
  const [subscription, setSubscription] = useState<EmitterSubscription | null>(
    null,
  );

  const subscribe = () => {
    const listener = NotificationDemo.addEventListener(
      'notification',
      (notification) => {
        console.log('notification', notification);
        setNotifications((existingNotifications: string[]) => [
          ...existingNotifications,
          notification,
        ]);
      },
    );

    setSubscription(listener);
  };

  const unsubscribe = () => {
    if (subscription) {
      subscription.remove();
      setSubscription(null);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Notifications</Text>
      {subscription ? (
        <Button title="Unsubscribe" onPress={unsubscribe} />
      ) : (
        <Button title="Subscribe" onPress={subscribe} />
      )}
      {notifications.map((notification, index) => (
        <View key={index}>
          <Text>{notification}</Text>
        </View>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginTop: 20,
    backgroundColor: 'hsl(200, 10%, 95%)',
    padding: 20,
    borderRadius: 15,
    alignItems: 'center',
  },
  title: {
    fontSize: 30,
    fontWeight: 'bold',
    marginBottom: 12,
  },
});

export default Notifications;
