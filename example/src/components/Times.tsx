import React from 'react';
import { Text, View } from 'react-native';
import { StyleSheet } from 'react-native';
import { TimeProvider } from '../generated/modules';

const Times: React.FC = () => {
  const time = TimeProvider.useTime();
  const timeAsState = TimeProvider.useTimeAsState();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Time</Text>

      <Text style={styles.name}>{time?.toISOString()}</Text> 
      <Text style={styles.name}>{timeAsState?.toISOString()}</Text>
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
