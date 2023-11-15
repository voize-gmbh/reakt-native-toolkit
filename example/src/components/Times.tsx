import React, { useState } from 'react';
import { Button, Text, View } from 'react-native';
import { StyleSheet } from 'react-native';
import { TimeProvider } from '../generated/modules';

const Times: React.FC = () => {
  const time = TimeProvider.useTime();
  const timeAsState = TimeProvider.useTimeAsState();
  const [showAlarm, setShowAlarm] = useState(false);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Time</Text>
      <Button title="Toggle alarm" onPress={() => setShowAlarm(!showAlarm)} />
      {showAlarm && <AlarmClock />}
      <Text style={styles.name}>{time?.toISOString()}</Text>
      <Text style={styles.name}>{timeAsState?.toISOString()}</Text>
    </View>
  );
};

const AlarmClock = () => {
  const [alarmAt, setAlarmAt] = useState(new Date());
  const isAfter = TimeProvider.useIsAfter(alarmAt.toISOString());

  return (
    <View>
      <Text>{alarmAt.toISOString()}</Text>
      <Button
        title="Snooze"
        onPress={() => {
          // add 10 seconds
          const newTime = new Date(alarmAt.getTime());
          newTime.setSeconds(newTime.getSeconds() + 10);
          setAlarmAt(newTime);
        }}
      />
      <Text style={styles.name}>{isAfter ? 'true' : 'false'}</Text>
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
  name: {
    fontSize: 20,
    marginBottom: 12,
  },
});

export default Times;
