import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
  MyComposeView,
  MySecondComposeView,
} from '../generated/nativeComponents';
import { com } from '../generated/models';

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
          nullableStringProp={null}
          boolProp={true}
          intProp={42}
          floatProp={42.42}
          doubleProp={42.42}
          objectProp={{
            stringProp: 'hello',
            boolProp: true,
            intProp: 42,
            floatProp: 42.42,
            doubleProp: 42.42,
          }}
          listProp={[
            {
              stringProp: 'hello',
              boolProp: true,
              intProp: 42,
              floatProp: 42.42,
              doubleProp: 42.42,
            },
          ]}
          enumProp={com.myrnproject.shared.Enum.Option1}
          textFieldValue={text}
          onTextFieldValueChange={setText}
          onButtonPress={() => setCounter((prev) => prev + 1)}
          onTestParams={(...args) => {
            console.log(args);
          }}
        />
      </View>
      <View style={{ marginBottom: 20 }}>
        <Text>Counter: {counter}</Text>
        <Text>Compose text input value: {text}</Text>
      </View>
      {[1, 2, 3].map((_, i) => (
        <MySecondComposeView
          key={`item-${i}`}
          index={i}
          onPress={() => console.log('Pressed', i)}
          style={styles.nativeListItem}
        />
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
  nativeViewContainer: {
    borderWidth: 1,
    borderColor: 'hsl(200, 10%, 50%)',
    width: '100%',
    marginBottom: 12,
  },
  nativeView: {
    width: '100%',
    height: 400,
  },
  nativeListItem: {
    width: '100%',
    height: 30,
    marginBottom: 8,
  },
});

export default NativeComposeView;
