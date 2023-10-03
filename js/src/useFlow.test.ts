import { act, renderHook } from '@testing-library/react-hooks';

import useFlow from './useFlow';
import { Next1, Next2 } from './useFlow.types';

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

  describe('arguments', () => {
    it('returns null when arguments change until next value is available', async () => {
      let promiseResolve: (value: string) => void = () => {};

      const next: Next1<string, number> = () =>
        new Promise((resolve) => {
          promiseResolve = resolve;
        });

      let arg = 1;

      const { result, rerender } = renderHook(() => useFlow(next, arg));

      expect(result.current).toBe(null);

      await act(async () => promiseResolve('"value1"'));

      expect(result.current).toBe('value1');

      arg = 2;
      rerender();

      expect(result.current).toBe(null);

      await act(async () => promiseResolve('"value2"'));

      expect(result.current).toBe('value2');
    });

    describe('primitive type arguments', () => {
      it('does not serialize number argument', async () => {
        let promiseResolve: (value: string) => void = () => {};

        const next: Next1<string, number> = jest.fn(
          () =>
            new Promise((resolve) => {
              promiseResolve = resolve;
            }),
        );

        renderHook(() => useFlow(next, 1));

        await act(async () => promiseResolve('next value'));

        expect(next).toHaveBeenCalledWith(null, 1);
      });

      it('does not serialize boolean argument', async () => {
        let promiseResolve: (value: string) => void = () => {};

        const next: Next1<string, boolean> = jest.fn(
          () =>
            new Promise((resolve) => {
              promiseResolve = resolve;
            }),
        );

        renderHook(() => useFlow(next, false));

        await act(async () => promiseResolve('next value'));

        expect(next).toHaveBeenCalledWith(null, false);
      });

      it('does not serialize string argument', async () => {
        let promiseResolve: (value: string) => void = () => {};

        const next: Next1<string, string> = jest.fn(
          () =>
            new Promise((resolve) => {
              promiseResolve = resolve;
            }),
        );

        renderHook(() => useFlow(next, '1'));

        await act(async () => promiseResolve('next value'));

        expect(next).toHaveBeenCalledWith(null, '1');
      });
    });

    describe('object type arguments', () => {
      it('serializes array arguments', async () => {
        let promiseResolve: (value: string) => void = () => {};
        const next: Next1<string, number[]> = jest.fn(
          () =>
            new Promise((resolve) => {
              promiseResolve = resolve;
            }),
        );

        renderHook(() => useFlow(next, [1, 2, 3]));

        await act(async () => promiseResolve('next value'));

        expect(next).toHaveBeenCalledWith(null, '[1,2,3]');
      });

      it('serializes record arguments', async () => {
        let promiseResolve: (value: string) => void = () => {};

        const next: Next1<string, Record<string, number>> = jest.fn(
          () =>
            new Promise((resolve) => {
              promiseResolve = resolve;
            }),
        );

        renderHook(() => useFlow(next, { i: 1 }));

        await act(async () => promiseResolve('next value'));

        expect(next).toHaveBeenCalledWith(null, '{"i":1}');
      });
    });

    it('handles mixed type arguments', async () => {
      let promiseResolve: (value: string) => void = () => {};

      const next: Next2<string, number, Record<string, number>> = jest.fn(
        () =>
          new Promise((resolve) => {
            promiseResolve = resolve;
          }),
      );

      renderHook(() => useFlow(next, 1, { i: 1 }));

      await act(async () => promiseResolve('next value'));

      expect(next).toHaveBeenCalledWith(null, 1, '{"i":1}');
    });
  });
});
