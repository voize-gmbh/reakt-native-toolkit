import { Next, Next1, Next2, NextX } from './useFlow.types';

const resolvablePromise = <T>() => {
  let resolve: (value: T | PromiseLike<T>) => void = () => {};
  let reject: (reason?: any) => void = () => {};
  const promise = new Promise<T>((resolveArg, rejectArg) => {
    resolve = resolveArg;
    reject = rejectArg;
  });
  return { promise, resolve, reject };
};

export interface State<T> {
  value: T | null;
  first: Promise<T>;
}

function flowAsState<T>(next: Next<T>): State<T>;
function flowAsState<T, T1>(next: Next1<T, T1>, arg1: T1): State<T>;
function flowAsState<T, T1, T2>(
  next: Next2<T, T1, T2>,
  arg1: T1,
  arg2: T2,
): State<T>;
function flowAsState<T>(next: NextX<T>, ...args: any[]) {
  const { promise, resolve } = resolvablePromise<T>();
  const state: State<T> = { value: null, first: promise };

  const setState = (encodedValue: string) => {
    const parsedValue = JSON.parse(encodedValue);
    state.value = parsedValue;
    resolve(parsedValue);
  };

  const loop = async () => {
    let encodedValue: string | null = null;
    // eslint-disable-next-line no-constant-condition
    while (true) {
      encodedValue = await next(encodedValue, ...args);
      setState(encodedValue);
    }
  };

  loop();

  return state;
}

export default flowAsState;
