import { useEffect, useMemo, useState } from 'react';
import _ from 'lodash';

import useIsMounted from './useIsMounted';
import useMemoArray from './useMemoArray';
import { Next, Next1, Next2, NextX } from './useFlow.types';

function useFlow<T>(next: Next<T>): T | null;
function useFlow<T, T1>(next: Next1<T, T1>, arg1: T1): T | null;
function useFlow<T, T1, T2>(
  next: Next2<T, T1, T2>,
  arg1: T1,
  arg2: T2,
): T | null;
function useFlow<T>(next: NextX<T>, ...args: any[]) {
  const memoizedArgs = useMemoArray(args);
  const isMounted = useIsMounted();

  const [{ value, args: valueArgs }, setState] = useState<{
    value: string | null;
    args: any[];
  }>({ value: null, args: memoizedArgs });

  useEffect(() => {
    if (!_.isEqual(memoizedArgs, valueArgs)) {
      setState({ value: null, args: memoizedArgs });
    }
  }, [memoizedArgs, value, valueArgs]);

  useEffect(() => {
    (async () => {
      try {
        const nextState = await next(value, ...memoizedArgs);

        if (isMounted.current) {
          setState((previousState) => {
            if (!_.isEqual(memoizedArgs, previousState.args)) {
              return previousState;
            } else {
              return { value: nextState, args: memoizedArgs };
            }
          });
        }
      } catch (error) {
        console.error(`Next value promise in useFlow was rejected: ${error}`);
      }
    })();
  }, [next, value, memoizedArgs]);

  return useMemo<T | null>(
    () =>
      !_.isEqual(memoizedArgs, valueArgs) || value == null
        ? null
        : JSON.parse(value),
    [memoizedArgs, value, valueArgs],
  );
}

export default useFlow;
