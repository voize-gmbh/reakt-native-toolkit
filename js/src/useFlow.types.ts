// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Next<T> = (
  subscriptionId: string,
  currentValue: string | null,
) => Promise<string>;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Next1<T, T1> = (
  subscriptionId: string,
  currentValue: string | null,
  arg1: T1,
) => Promise<string>;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Next2<T, T1, T2> = (
  subscriptionId: string,
  currentValue: string | null,
  arg1: T1,
  arg2: T2,
) => Promise<string>;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type NextX<T> = (
  subscriptionId: string,
  currentValue: string | null,
  ...args: any[]
) => Promise<string>;
