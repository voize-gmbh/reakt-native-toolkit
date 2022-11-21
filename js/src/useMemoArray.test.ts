import { renderHook } from '@testing-library/react-hooks';

import useMemoArray from './useMemoArray';

enum ExpectType {
  NEXT = 'next',
  INITIAL = 'initial',
}

interface TestCase<T> {
  name: string;
  initial: T[];
  next: T[];
  expected: ExpectType;
}

describe('useMemoArray', () => {
  it.each([
    {
      name: 'should maintain the reference if both old and new array are empty',
      initial: [],
      next: [],
      expected: ExpectType.INITIAL,
    },
    {
      name: 'should maintain the reference of the old array if the new array has the same items',
      initial: [1, 2, 3],
      next: [1, 2, 3],
      expected: ExpectType.INITIAL,
    },
    {
      name: 'should should change the reference of the old array if the new array has a different length',
      initial: [1, 2, 3],
      next: [1, 2],
      expected: ExpectType.NEXT,
    },
    {
      name: 'should change the reference to the new array if the new array has the different items',
      initial: [1, 2, 3],
      next: [2, 3, 4],
      expected: ExpectType.NEXT,
    },
    {
      name: 'does not maintain the reference for arrays of complex types',
      initial: [{ id: 1 }, { id: 2 }, { id: 3 }],
      next: [{ id: 1 }, { id: 2 }, { id: 3 }],
      expected: ExpectType.NEXT,
    },
  ] as TestCase<any>[])('$name', ({ initial, next, expected }) => {
    let value = initial;

    // make sure value has the same reference as initialValue
    expect(value).toBe(initial);

    const { result, rerender } = renderHook(() => useMemoArray(value));

    // make sure next value has a different reference than value
    expect(next).not.toBe(value);
    expect(next).not.toBe(initial);

    value = next;
    rerender();

    // expect that the reference of the initial value is maintained
    expect(result.current).toBe(
      expected === ExpectType.INITIAL ? initial : next,
    );
  });
});
