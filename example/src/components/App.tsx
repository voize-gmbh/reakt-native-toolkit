import React from 'react';
import { SafeAreaView, StyleSheet } from 'react-native';
import Notifications from './Notifications';
import Name from './Name';
import Times from './Times';

const App = () => {
  return (
    <SafeAreaView style={styles.container}>
      <Name />
      <Times />
      <Notifications />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    margin: 20,
  },
});

export default App;
