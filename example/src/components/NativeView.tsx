import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { MyComposeView } from './nativeViews';

const NativeComposeView: React.FC = () => {
  const [text, setText] = React.useState('hello');
  const [counter, setCounter] = React.useState(0);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Native Compose View</Text>
      <View style={styles.nativeViewContainer}>
        <MyComposeView
          style={styles.nativeView}
          message="Hello from React Native!"
          boolProp={true}
          intProp={42}
          longProp={42}
          floatProp={42.42}
          doubleProp={42.42}
          objectProp={{
            stringProp: 'hello',
            boolProp: true,
            intProp: 42,
            floatProp: 42.42,
            doubleProp: 42.42,
          }}
          textFieldValue={text}
          onTextFieldValueChange={setText}
          onButtonPress={() => setCounter((prev) => prev + 1)}
          onTestParams={(...args) => {
            console.log(args);
          }}
        />
      </View>
      <Text>Counter: {counter}</Text>
      <Text>Compose text input value: {text}</Text>
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
    height: 300,
  },
});

export default NativeComposeView;
