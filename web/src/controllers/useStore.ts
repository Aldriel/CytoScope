import { useSyncExternalStore } from 'react';
import type { Store } from '../model/store';

/** Returns the value of a store of the Model; the component is drawn again when it changes. */
export function useStore<T>(store: Store<T>): T {
  return useSyncExternalStore(store.subscribe, store.get);
}
