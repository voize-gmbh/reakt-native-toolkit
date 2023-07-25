
export enum TestSealedTypeType {
  TestSealedTypeOption1 = 'option1',
  TestSealedTypeOption2 = 'option2',
  TestSealedTypeOption3 = 'option3'
}

interface TestSealedTypeBase<T extends TestSealedTypeType> {

  type: T;

}

export interface TestSealedTypeOption1 extends TestSealedTypeBase<TestSealedTypeType.TestSealedTypeOption1> {

  name: string;

  nested: TestSealedTypeOption1Nested;

}

export interface TestSealedTypeOption2 extends TestSealedTypeBase<TestSealedTypeType.TestSealedTypeOption2> {

  number: number;

  nonNested: NonNested;

}

export interface TestSealedTypeOption3 extends TestSealedTypeBase<TestSealedTypeType.TestSealedTypeOption3> {
}

export type TestSealedType = TestSealedTypeOption1 | TestSealedTypeOption2 | TestSealedTypeOption3;

export enum Enum {
  Option1 = 'Option1',
  OPTION2 = 'OPTION2',
  OPTION_3 = 'OPTION_3'
}

export interface Test {

  name: string;

  list: Array<TestNested>;

  map: Record<string, TestNested>;

  long: number;

  bar: number;

}

export interface TestSealedTypeOption1Nested {

  nullable: string | null;

}

export interface NonNested {

  bar: string;

}

export interface TestNested {

  name: string;

  age: number;

}
