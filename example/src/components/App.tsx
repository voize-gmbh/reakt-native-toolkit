import React from 'react';
import { SafeAreaView, StyleSheet } from 'react-native';
import Notifications from './Notifications';
import Name from './Name';

const App = () => {
  return (
    <SafeAreaView style={styles.container}>
      <Name />
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
