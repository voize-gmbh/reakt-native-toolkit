import { useEffect, useRef } from 'react';

/**
 * Memorize an array and return the memoized reference if the values of the array do not change.
 * Can not be used if the values of the array are complex computed.
 *
 * @param next the array to memorize
 * @returns an array with equal values to the given one
 */
function useMemoArray<T>(next: T[]): T[] {
  const previousRef = useRef<T[]>();
  const previous = previousRef.current;

  const isEqual =
    previous === next ||
    (!!previous &&
      previous.length === next.length &&
      previous.every((element, index) => element === next[index]));

  useEffect(() => {
    if (!isEqual) {
      previousRef.current = next;
    }
  });
  return isEqual ? previous! : next;
}

export default useMemoArray;
