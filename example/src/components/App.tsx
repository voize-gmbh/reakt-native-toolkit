import React from 'react';
import { SafeAreaView, ScrollView, StyleSheet } from 'react-native';
import Notifications from './Notifications';
import Name from './Name';
import Times from './Times';
import NativeComposeView from './NativeView';

const App = () => {
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView>
        <Name />
        <Times />
        <Notifications />
        <NativeComposeView />
      </ScrollView>
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
