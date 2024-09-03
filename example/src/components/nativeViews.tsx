import React, { useCallback } from 'react';
import { requireNativeComponent, ViewProps } from 'react-native';

const MyComposeViewNative = requireNativeComponent<any>('MyComposeView');

interface MyDataClass {
  stringProp: string;
  boolProp: boolean;
  intProp: number;
  floatProp: number;
  doubleProp: number;
}

interface Props extends ViewProps {
  message: string;
  textFieldValue: string;
  boolProp: boolean;
  intProp: number;
  longProp: number;
  floatProp: number;
  doubleProp: number;
  objectProp: MyDataClass;
  onTextFieldValueChange: (value: string) => void;
  onButtonPress: () => void;
  onTestParams: (
    stringParam: string,
    boolParam: boolean,
    intParam: number,
    floatParam: number,
    doubleParam: number,
    objectParam: MyDataClass,
  ) => void;
}

export const MyComposeView = ({
  onTextFieldValueChange,
  onButtonPress,
  onTestParams,
  objectProp,
  ...props
}: Props) => {
  const nativeOnTextFieldValueChange = useCallback(
    (event: any) => {
      (onTextFieldValueChange as any)(...event.nativeEvent.args);
    },
    [onTextFieldValueChange],
  );

  const nativeOnButtonPress = useCallback(
    (event: any) => {
      (onButtonPress as any)(...event.nativeEvent.args);
    },
    [onButtonPress],
  );

  const nativeOnTestParams = useCallback(
    (event: any) => {
      (onTestParams as any)(...event.nativeEvent.args);
    },
    [onTestParams],
  );

  return (
    <MyComposeViewNative
      {...props}
      objectProp={JSON.stringify(objectProp)}
      onTextFieldValueChange={nativeOnTextFieldValueChange}
      onButtonPress={nativeOnButtonPress}
      onTestParams={nativeOnTestParams}
    />
  );
};
