import { useEffect } from 'react';
import type { AppModel } from '../model/AppModel';
import type { Results } from '../model/types';
import { useStore } from './useStore';

/**
 * Results of the current set-up, computed again by the server when a tab showing them is open
 * and the set-up has changed since the last calculation.
 *
 * @param active whether the tab is shown
 * @returns the last results, possibly of an older set-up (fresh false), or null
 */
export function useResults(
  model: AppModel,
  active: boolean,
): { results: Results | null; fresh: boolean } {
  const workspace = useStore(model.workspace);
  const computed = useStore(model.results);
  const experiment = workspace?.experiment;
  const hasTubes = (workspace?.samples.length ?? 0) > 0;
  const fresh = computed !== null && computed.experiment === experiment;

  useEffect(() => {
    if (active && hasTubes && !fresh) {
      void model.refreshResults();
    }
  }, [model, active, hasTubes, fresh, experiment]);

  return { results: computed?.results ?? null, fresh };
}
