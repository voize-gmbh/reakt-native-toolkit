import React, { useState } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useFlow } from 'reakt-native-toolkit';
import NameManager from './NameManager';

const App = () => {
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
    <SafeAreaView style={styles.container}>
      <View style={styles.containerInner}>
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
        <TouchableOpacity onPress={setNameInNative}>
          <Text style={styles.buttonText}>Set name</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  containerInner: {
    margin: 30,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
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
    color: 'black',
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
  },
  nameContainer: {
    alignSelf: 'stretch',
    alignItems: 'center',
    justifyContent: 'center',
  },
  refreshButton: {
    position: 'absolute',
    right: 0,
  },
  refreshButtonText: {
    fontSize: 24,
    color: 'hsl(200, 100%, 60%)',
  },
  buttonText: {
    fontSize: 24,
    color: 'hsl(200, 100%, 60%)',
  },
});

export default App;
