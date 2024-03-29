import { act, renderHook } from '@testing-library/react-hooks';

import useFlow, { setErrorInterceptor } from './useFlow';
import { Next1 } from './useFlow.types';

describe('useFlow', () => {
  const errorInterceptor = jest.fn(async () => {});
  setErrorInterceptor(errorInterceptor);

  it('returns updated value when promise resolves', async () => {
    let promiseResolve: (value: string) => void = () => {};

    const next = () =>
      new Promise<string>((resolve) => {
        promiseResolve = resolve;
      });

    const cancel = async () => {};

    const { result } = renderHook(() => useFlow(next, cancel, 'test'));

    expect(result.current).toBe(null);

    await act(async () => promiseResolve('{"i": 0}'));
    expect(result.current).toEqual({ i: 0 });

    await act(async () => promiseResolve('{"i": 1}'));
    expect(result.current).toEqual({ i: 1 });

    await act(async () => promiseResolve('{"i": 2}'));
    expect(result.current).toEqual({ i: 2 });
  });

  it('hook result reference does not change when value does not change', async () => {
    let promiseResolve: (value: string) => void = () => {};

    const next = () =>
      new Promise<string>((resolve) => {
        promiseResolve = resolve;
      });

    const cancel = async () => {};

    const { result } = renderHook(() => useFlow(next, cancel, 'test'));

    expect(result.current).toBe(null);

    await act(async () => promiseResolve('{"a": "a"}'));
    expect(result.current).toEqual({ a: 'a' });

    const currentResult = result.current;

    await act(async () => promiseResolve('{"a": "a"}'));
    expect(result.current).toBe(currentResult); // checks for reference
  });

  it('catches errors in next', async () => {
    let promiseReject: (reason?: any) => void = () => {};

    const next = () =>
      new Promise<string>((resolve, reject) => {
        promiseReject = reject;
      });

    const cancel = async () => {};

    const { result } = renderHook(() => useFlow(next, cancel, 'foo'));

    expect(result.current).toBe(null);

    const error = new Error('error');
    await act(async () => promiseReject(error));

    expect(errorInterceptor).toHaveBeenCalledTimes(1);
    expect(errorInterceptor).toHaveBeenCalledWith(error, 'foo');

    expect(result.current).toBe(null);
  });

  it('cancel flow subscription on unmount', async () => {
    let promiseResolve: (value: string) => void = () => {};

    const next = () =>
      new Promise<string>((resolve) => {
        promiseResolve = resolve;
      });

    const cancel = jest.fn(async () => {});

    const { result, unmount } = renderHook(() => useFlow(next, cancel, 'test'));

    expect(result.current).toBe(null);

    await act(async () => promiseResolve('{"i": 0}'));
    expect(result.current).toEqual({ i: 0 });
    unmount();

    expect(cancel).toHaveBeenCalledTimes(1);
  });

  it('resubscribes when next value is same as previous value', async () => {
    let promiseResolve: (value: string) => void = () => {};

    const next = () =>
      new Promise<string>((resolve) => {
        promiseResolve = resolve;
      });

    const cancel = async () => {};

    const { result } = renderHook(() => useFlow(next, cancel, 'test'));

    expect(result.current).toBe(null);

    await act(async () => promiseResolve('{"i": 0}'));
    expect(result.current).toEqual({ i: 0 });

    // return same value which should trigger resubscription
    await act(async () => promiseResolve('{"i": 0}'));
    expect(result.current).toEqual({ i: 0 });

    await act(async () => promiseResolve('{"i": 1}'));
    expect(result.current).toEqual({ i: 1 });
  });

  describe('arguments', () => {
    it('returns null when arguments change until next value is available', async () => {
      let promiseResolve: (value: string) => void = () => {};

      const next: Next1<string, number> = () =>
        new Promise((resolve) => {
          promiseResolve = resolve;
        });

      const cancel = async () => {};

      let arg = 1;

      const { result, rerender } = renderHook(() =>
        useFlow(next, cancel, 'test', arg),
      );

      expect(result.current).toBe(null);

      await act(async () => promiseResolve('"value1"'));

      expect(result.current).toBe('value1');

      arg = 2;
      rerender();

      expect(result.current).toBe(null);

      await act(async () => promiseResolve('"value2"'));

      expect(result.current).toBe('value2');
    });
  });
});
