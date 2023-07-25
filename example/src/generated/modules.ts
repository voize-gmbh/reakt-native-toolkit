import {Enum, Test, TestSealedType} from './models';
import {EmitterSubscription, NativeEventEmitter, NativeModules} from 'react-native';


interface NativeE2ETestInterface {

  testDefaultTypes(
      string: string,
      int: number,
      long: number,
      float: number,
      double: number,
      boolean: boolean,
      byte: number,
      char: number,
      short: number
  ): Promise<string>;

  testDefaultTypesNullable(
      string: string | null,
      int: number | null,
      long: number | null,
      float: number | null,
      double: number | null,
      boolean: boolean | null,
      byte: number | null,
      char: number | null,
      short: number | null
  ): Promise<string | null>;

  testListAndMap(
      list: Array<string>,
      map: Record<string, string>,
      nestedList: Array<Array<string>>,
      nestedMap: Record<string, Record<string, string>>,
      complexList: Array<string>,
      complexMap: Record<string, string>
  ): Promise<Array<number>>;

  testListAndMapNullable(
      list: Array<string> | null,
      map: Record<string, string> | null,
      nestedList: Array<Array<string>> | null,
      nestedMap: Record<string, Record<string, string>> | null,
      complexList: Array<string> | null,
      complexMap: Record<string, string> | null,
      listNullable: Array<string | null>,
      mapNullable: Record<string, string | null>,
      nestedListNullable: Array<Array<string | null> | null>,
      nestedMapNullable: Record<string, Record<string, string | null> | null>,
      complexListNullable: Array<string>,
      complexMapNullable: Record<string, string>
  ): Promise<Array<number | null>>;

  example(input: string, testEnum: string): Promise<string>;

  testKotlinDateTime(instant: string, localDateTime: string): Promise<string>;

}

interface E2ETestInterface {

  testDefaultTypes(
      string: string,
      int: number,
      long: number,
      float: number,
      double: number,
      boolean: boolean,
      byte: number,
      char: number,
      short: number
  ): Promise<string>;

  testDefaultTypesNullable(
      string: string | null,
      int: number | null,
      long: number | null,
      float: number | null,
      double: number | null,
      boolean: boolean | null,
      byte: number | null,
      char: number | null,
      short: number | null
  ): Promise<string | null>;

  testListAndMap(
      list: Array<string>,
      map: Record<string, string>,
      nestedList: Array<Array<string>>,
      nestedMap: Record<string, Record<string, string>>,
      complexList: Array<Test>,
      complexMap: Record<string, Test>
  ): Promise<Array<number>>;

  testListAndMapNullable(
      list: Array<string> | null,
      map: Record<string, string> | null,
      nestedList: Array<Array<string>> | null,
      nestedMap: Record<string, Record<string, string>> | null,
      complexList: Array<Test> | null,
      complexMap: Record<string, Test> | null,
      listNullable: Array<string | null>,
      mapNullable: Record<string, string | null>,
      nestedListNullable: Array<Array<string | null> | null>,
      nestedMapNullable: Record<string, Record<string, string | null> | null>,
      complexListNullable: Array<Test | null>,
      complexMapNullable: Record<string, Test | null>
  ): Promise<Array<number | null>>;

  example(input: TestSealedType, testEnum: Enum | null): Promise<Test>;

  testKotlinDateTime(instant: string, localDateTime: string): Promise<string>;

}

interface NativeNameManagerInterface {

  setName(name: string): Promise<void>;

  getName(): Promise<string | null>;

  name(previous: string | null): Promise<string>;

}

interface NameManagerInterface {

  setName(name: string): Promise<void>;

  getName(): Promise<string | null>;

  name(previous: string | null): Promise<string>;

}

interface NativeNotificationDemoInterface {
}

interface NotificationDemoInterface {

  addEventListener(key: string, listener: (result: any) => void): EmitterSubscription;

}

type _workaround = NativeEventEmitter;

const NativeE2ETest = NativeModules.E2ETest as NativeE2ETestInterface
export const E2ETest: E2ETestInterface = {
  testDefaultTypes: NativeE2ETest.testDefaultTypes,
  testDefaultTypesNullable: NativeE2ETest.testDefaultTypesNullable,
  testListAndMap: NativeE2ETest.testListAndMap,
  testListAndMapNullable: NativeE2ETest.testListAndMapNullable,
  example: (input: TestSealedType, testEnum: Enum | null) => NativeE2ETest.example(JSON.stringify(input), JSON.stringify(testEnum)).then(JSON.parse),
  testKotlinDateTime: (instant: string, localDateTime: string) => NativeE2ETest.testKotlinDateTime(JSON.stringify(instant), JSON.stringify(localDateTime)).then(JSON.parse)
}
const NativeNameManager = NativeModules.NameManager as NativeNameManagerInterface
export const NameManager: NameManagerInterface = {
  setName: NativeNameManager.setName,
  getName: NativeNameManager.getName,
  name: NativeNameManager.name
}
const NativeNotificationDemo = NativeModules.NotificationDemo as NativeNotificationDemoInterface
export const NotificationDemo: NotificationDemoInterface = {
  addEventListener: (key: string, listener: (result: any) => void) => {
    const eventEmitter = new NativeEventEmitter(NativeNotificationDemo as any);
    return eventEmitter.addListener(key, listener);
  }
}