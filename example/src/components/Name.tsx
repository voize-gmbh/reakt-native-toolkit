import React, { useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import { useFlow } from 'reakt-native-toolkit';
import NameManager from '../nativeModules/NameManager';

const Name: React.FC = () => {
  const [input, setInput] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const name = useFlow(NameManager.name);

  const setNameInNative = async () => {
    try {
      setErrorMessage(null);
      await NameManager.setName(input);
    } catch (error) {
      setErrorMessage((error as any).message);
    }
  };

  return (
    <View style={styles.container}>
      {errorMessage && <Text style={styles.error}>{errorMessage}</Text>}
      <View style={styles.nameContainer}>
        <Text style={styles.title}>Name</Text>
        <Text style={[styles.name, !name && styles.nameUnknown]}>
          {name || 'not set'}
        </Text>
      </View>
      <View style={styles.divider} />
      <TextInput
        placeholder="Enter name here..."
        placeholderTextColor={styles.nameUnknown.color}
        value={input}
        onChangeText={setInput}
        style={styles.textInput}
      />
      <Button title="Set name" onPress={setNameInNative} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: 'hsl(200, 10%, 95%)',
    padding: 20,
    borderRadius: 15,
    alignItems: 'center',
  },
  divider: {
    marginVertical: 30,
    borderTopWidth: 1,
    borderColor: 'hsl(200, 10%, 90%)',
    alignSelf: 'stretch',
  },
  title: {
    fontSize: 30,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  name: {
    fontSize: 24,
    color: 'black',
  },
  nameUnknown: {
    color: 'hsl(200, 10%, 80%)',
  },
  error: {
    color: 'red',
    marginBottom: 20,
    fontSize: 22,
  },
  textInput: {
    textAlign: 'center',
    alignSelf: 'stretch',
    borderWidth: 2,
    borderRadius: 10,
    borderColor: 'hsl(200, 10%, 90%)',
    height: 60,
    marginBottom: 20,
    fontSize: 26,
    backgroundColor: 'white',
  },
  nameContainer: {
    alignSelf: 'stretch',
    alignItems: 'center',
    justifyContent: 'center',
  },
});

export default Name;
