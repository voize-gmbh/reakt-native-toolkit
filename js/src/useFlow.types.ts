// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Next<T> = (currentValue: string | null) => Promise<string>;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Next1<T, T1> = (
  currentValue: string | null,
  arg1: T1,
) => Promise<string>;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type Next2<T, T1, T2> = (
  currentValue: string | null,
  arg1: T1,
  arg2: T2,
) => Promise<string>;

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type NextX<T> = (
  currentValue: string | null,
  ...args: any[]
) => Promise<string>;
