import { useEffect, useRef, useState } from 'react';
import type { AppModel } from '../model/AppModel';
import type { Plot, PlotRequest } from '../model/types';
import { useStore } from './useStore';

/**
 * Plot counted by the server for a request, counted again when the set-up of the experiment
 * changes (gates, compensation). The previous plot stays shown until the new one arrives, and
 * an answer is ignored when a newer request has been sent meanwhile.
 *
 * @param request the plot to draw, or null to stop counting (the last plot is kept), for
 *     instance while its tab is hidden
 */
export function usePlot(model: AppModel, request: PlotRequest | null): Plot | null {
  const workspace = useStore(model.workspace);
  const [plot, setPlot] = useState<Plot | null>(null);
  const latest = useRef(0);
  const key = request === null ? null : JSON.stringify(request);
  const experiment = workspace?.experiment;

  useEffect(() => {
    const call = ++latest.current;
    if (key === null || experiment === undefined) {
      return;
    }
    void model.plot(JSON.parse(key) as PlotRequest).then((result) => {
      if (call === latest.current && result !== null) {
        setPlot(result);
      }
    });
  }, [model, key, experiment]);

  return plot;
}
