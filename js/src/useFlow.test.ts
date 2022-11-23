import { act, renderHook } from '@testing-library/react-hooks';

import useFlow from './useFlow';

describe('useFlow', () => {
  it('returns updated value when promise resolves', async () => {
    let promiseResolve: (value: string) => void = () => {};

    const next = () =>
      new Promise<string>((resolve) => {
        promiseResolve = resolve;
      });

    const { result } = renderHook(() => useFlow(next));

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

    const { result } = renderHook(() => useFlow(next));

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

    const { result } = renderHook(() => useFlow(next));

    expect(result.current).toBe(null);

    await act(async () => promiseReject(new Error('error')));

    expect(result.current).toBe(null);
  });
});
