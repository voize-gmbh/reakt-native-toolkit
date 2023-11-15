import { useEffect, useMemo, useState } from 'react';
import _ from 'lodash';

import useIsMounted from './useIsMounted';
import useMemoArray from './useMemoArray';
import { Next, Next1, Next2, NextX } from './useFlow.types';

type Unsubscribe = (subscriptionId: string) => Promise<void>;

function useFlow<T>(next: Next<T>, unsubscribe: Unsubscribe): T | null;
function useFlow<T, T1>(
  next: Next1<T, T1>,
  unsubscribe: Unsubscribe,
  arg1: T1,
): T | null;
function useFlow<T, T1, T2>(
  next: Next2<T, T1, T2>,
  unsubscribe: Unsubscribe,
  arg1: T1,
  arg2: T2,
): T | null;
function useFlow<T>(
  next: NextX<T>,
  unsubscribe: Unsubscribe,
  ...args: any[]
): T | null;
function useFlow<T>(next: NextX<T>, unsubscribe: Unsubscribe, ...args: any[]) {
  const [subscriptionId] = useState(_.uniqueId());
  const memoizedArgs = useMemoArray(args);
  const isMounted = useIsMounted();

  const [{ value, args: valueArgs }, setState] = useState<{
    value: string | null;
    args: any[];
  }>({ value: null, args: memoizedArgs });

  /**
   * When args change, reset the value to null.
   */
  useEffect(() => {
    if (!_.isEqual(memoizedArgs, valueArgs)) {
      setState({ value: null, args: memoizedArgs });
    }
  }, [memoizedArgs, value, valueArgs]);

  useEffect(() => {
    (async () => {
      try {
        const nextState = await next(subscriptionId, value, ...memoizedArgs);

        if (isMounted.current) {
          setState((previousState) => {
            if (!_.isEqual(memoizedArgs, previousState.args)) {
              /**
               * We wait until the above useEffect hook resets the value to null before we use any new value.
               */
              return previousState;
            } else {
              return { value: nextState, args: memoizedArgs };
            }
          });
        }
      } catch (error) {
        /**
         * We ignore this error because it is thrown when the subscription is cancelled.
         */
        if (
          error instanceof Error &&
          error.message.includes(
            'ReactNativeUseFlowSubscriptionCancellationException',
          )
        ) {
          return;
        }

        console.error(`Next value promise in useFlow was rejected: ${error}`);
      }
    })();
  }, [next, value, memoizedArgs, isMounted, subscriptionId]);

  // cancel subscription on unmount
  useEffect(() => {
    return () => {
      void unsubscribe(subscriptionId);
    };
  }, [subscriptionId, unsubscribe]);

  return useMemo<T | null>(
    () =>
      !_.isEqual(memoizedArgs, valueArgs) || value == null
        ? null
        : JSON.parse(value),
    [memoizedArgs, value, valueArgs],
  );
}

export default useFlow;
