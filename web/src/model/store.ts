/**
 * Observable value of the Model, the counterpart of a JavaFX property: the views subscribe to it
 * (see controllers/useStore.ts) and are drawn again when it changes.
 */
export class Store<T> {
  private readonly listeners = new Set<() => void>();

  constructor(private value: T) {}

  /** Returns the current value. */
  readonly get = (): T => this.value;

  /** Replaces the value and notifies the listeners, unless it is the same value. */
  set(value: T): void {
    if (!Object.is(value, this.value)) {
      this.value = value;
      this.listeners.forEach((listener) => listener());
    }
  }

  /**
   * Calls `listener` after each change.
   *
   * @returns a function removing the listener
   */
  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
}
