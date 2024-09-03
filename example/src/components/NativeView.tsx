import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MyComposeView } from './nativeViews';

const NativeComposeView: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Native Compose View</Text>
      <View style={styles.nativeViewContainer}>
        <MyComposeView style={styles.nativeView} />
      </View>
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
  nativeViewContainer: {
    borderWidth: 1,
    borderColor: 'hsl(200, 10%, 50%)',
    width: '100%',
  },
  nativeView: {
    width: '100%',
    height: 100,
  },
});

export default NativeComposeView;
