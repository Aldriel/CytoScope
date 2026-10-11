import { useState } from 'react';
import { usePlot } from '../controllers/usePlot';
import { useResults } from '../controllers/useResults';
import { useStore } from '../controllers/useStore';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';
import {
  defaultApoptosis,
  defaultCellCycle,
  fluorescenceOf,
  groupsOf,
  metricKey,
  metricOptions,
  withoutDanglingReferences,
} from '../model/experiment';
import { flatten } from '../model/gating';
import {
  CELL_CYCLE_PHASES,
  QUADRANTS,
  type ApoptosisSettings,
  type CellFate,
  type CellCycleSettings,
  type Experiment,
  type Quadrant,
} from '../model/types';
import { HelpButton } from './common/HelpButton';
import { parseNumber, TextField } from './common/TextField';
import { FlowPlot, type DrawnGate } from './plot/FlowPlot';

/** State of the cells in each quadrant of the annexin V / PI plot. */
const FATES: Record<Quadrant, CellFate> = {
  NEGATIVE_NEGATIVE: 'LIVE',
  POSITIVE_NEGATIVE: 'EARLY_APOPTOTIC',
  POSITIVE_POSITIVE: 'LATE_APOPTOTIC',
  NEGATIVE_POSITIVE: 'NECROTIC',
};

interface Props {
  model: AppModel;
  /** Whether the tab is shown, to compute the results it previews. */
  active: boolean;
  onHelp: () => void;
}

/**
 * "Analyses" tab: the annexin V / PI test, with its thresholds dragged on a plot, the cell cycle,
 * and the value compared between the groups.
 */
export function AnalysisView({ model, active, onHelp }: Props) {
  const m = useMessages();
  const workspace = useStore(model.workspace);
  const { results } = useResults(model, active);
  const [tube, setTube] = useState<string | null>(null);

  const experiment = workspace?.experiment ?? null;
  const sampleId =
    workspace?.samples.some((s) => s.id === tube) === true
      ? tube
      : ((experiment?.samples.find((s) => s.role === 'SAMPLE') ?? experiment?.samples[0])?.id ??
        null);
  const apoptosis = experiment?.apoptosis ?? null;
  const cellCycle = experiment?.cellCycle ?? null;

  const apoptosisPlot = usePlot(
    model,
    active && apoptosis && sampleId
      ? {
          sampleId,
          populationId: apoptosis.parentId,
          x: apoptosis.annexinChannel,
          y: apoptosis.piChannel,
          xScale: null,
          yScale: null,
        }
      : null,
  );
  const cyclePlot = usePlot(
    model,
    active && cellCycle && sampleId
      ? {
          sampleId,
          populationId: cellCycle.parentId,
          x: cellCycle.dnaChannel,
          y: null,
          xScale: null,
          yScale: null,
        }
      : null,
  );

  if (workspace === null || experiment === null || experiment.samples.length === 0) {
    return (
      <div className="tab-content">
        <div className="row">
          <p className="placeholder grow">{m.analysis.needsTubes}</p>
          <HelpButton onClick={onHelp} />
        </div>
      </div>
    );
  }

  const update = (change: (e: Experiment) => Experiment) =>
    void model.updateExperiment((e) => withoutDanglingReferences(change(e)));
  const setApoptosis = (changes: Partial<ApoptosisSettings>) =>
    update((e) => ({ ...e, apoptosis: e.apoptosis && { ...e.apoptosis, ...changes } }));
  const setCellCycle = (changes: Partial<CellCycleSettings>) =>
    update((e) => ({ ...e, cellCycle: e.cellCycle && { ...e.cellCycle, ...changes } }));

  const sampleResult = results?.samples.find((s) => s.id === sampleId) ?? null;
  const fluorescence = fluorescenceOf(workspace);
  const populations = flatten(experiment.gating);
  const options = metricOptions(experiment, m);
  const groups = groupsOf(experiment);

  const fateGates: DrawnGate[] = apoptosis
    ? QUADRANTS.map((quadrant) => ({
        id: `fate-${quadrant}`,
        name: m.fate[FATES[quadrant]],
        percent: sampleResult?.apoptosis?.[FATES[quadrant]] ?? null,
        gate: {
          type: 'quadrant',
          xChannel: apoptosis.annexinChannel,
          yChannel: apoptosis.piChannel,
          xThreshold: apoptosis.annexinThreshold,
          yThreshold: apoptosis.piThreshold,
          quadrant,
        },
      }))
    : [];

  const cycle = sampleResult?.cellCycle ?? null;
  const phaseGates: DrawnGate[] =
    cellCycle && cycle
      ? CELL_CYCLE_PHASES.slice(0, 4).map((phase, i) => ({
          id: `phase-${phase}`,
          name: m.phase[phase],
          percent: cycle.percents[phase],
          locked: true,
          gate: {
            type: 'interval',
            xChannel: cellCycle.dnaChannel,
            min: i === 0 ? Math.min(0, cycle.bounds[0]) : cycle.bounds[i - 1],
            max: cycle.bounds[i],
          },
        }))
      : [];

  const populationSelect = (id: string, value: string, onChange: (id: string) => void) => (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
      {populations.map(({ population, depth }) => (
        <option key={population.id} value={population.id}>
          {'\u00a0\u00a0'.repeat(depth) + population.name}
        </option>
      ))}
    </select>
  );
  const channelSelect = (id: string, value: string, onChange: (name: string) => void) => (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
      {fluorescence.map((c) => (
        <option key={c.name} value={c.name}>
          {c.displayName}
        </option>
      ))}
    </select>
  );
  const threshold = (id: string, label: string, value: number, key: keyof ApoptosisSettings) => (
    <>
      <label htmlFor={id}>{label}</label>
      <TextField
        numeric
        id={id}
        label={label}
        value={String(Math.round(value * 100) / 100)}
        onCommit={(text) => {
          const parsed = parseNumber(text);
          if (parsed !== null && parsed !== undefined) {
            setApoptosis({ [key]: parsed });
          }
        }}
      />
    </>
  );

  return (
    <div className="tab-content analysis">
      <div className="row">
        <label htmlFor="analysis-tube">{m.analysis.preview}</label>
        <select id="analysis-tube" value={sampleId ?? ''} onChange={(e) => setTube(e.target.value)}>
          {workspace.samples.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <span className="grow" />
        <HelpButton onClick={onHelp} />
      </div>

      <section className="analysis-section">
        <h3>
          <label className="check">
            <input
              type="checkbox"
              checked={apoptosis !== null}
              disabled={fluorescence.length === 0}
              onChange={(e) =>
                update((ex) => ({
                  ...ex,
                  apoptosis: e.target.checked ? defaultApoptosis(workspace) : null,
                }))
              }
            />
            {m.analysis.apoptosis}
          </label>
        </h3>
        {apoptosis && (
          <div className="analysis-body">
            <div className="form-grid">
              <label htmlFor="apoptosis-parent">{m.analysis.parent}</label>
              {populationSelect('apoptosis-parent', apoptosis.parentId, (parentId) =>
                setApoptosis({ parentId }),
              )}
              <label htmlFor="apoptosis-annexin">{m.analysis.annexin}</label>
              {channelSelect('apoptosis-annexin', apoptosis.annexinChannel, (annexinChannel) =>
                setApoptosis({ annexinChannel }),
              )}
              <label htmlFor="apoptosis-pi">{m.analysis.pi}</label>
              {channelSelect('apoptosis-pi', apoptosis.piChannel, (piChannel) =>
                setApoptosis({ piChannel }),
              )}
              {threshold(
                'annexin-threshold',
                m.analysis.annexinThreshold,
                apoptosis.annexinThreshold,
                'annexinThreshold',
              )}
              {threshold(
                'pi-threshold',
                m.analysis.piThreshold,
                apoptosis.piThreshold,
                'piThreshold',
              )}
              <span />
              <button
                type="button"
                title={m.tip.suggest}
                onClick={() => void model.suggestThresholds(apoptosis)}
              >
                {m.analysis.suggest}
              </button>
              <p className="muted small span-2">{m.analysis.apoptosisCaption}</p>
            </div>
            {apoptosisPlot && (
              <FlowPlot
                plot={apoptosisPlot}
                gates={fateGates}
                tool="select"
                label={m.analysis.apoptosis}
                onMove={(_, gate) => {
                  if (gate.type === 'quadrant') {
                    setApoptosis({
                      annexinThreshold: gate.xThreshold,
                      piThreshold: gate.yThreshold,
                    });
                  }
                }}
              />
            )}
          </div>
        )}
      </section>

      <section className="analysis-section">
        <h3>
          <label className="check">
            <input
              type="checkbox"
              checked={cellCycle !== null}
              disabled={fluorescence.length === 0}
              onChange={(e) =>
                update((ex) => ({
                  ...ex,
                  cellCycle: e.target.checked ? defaultCellCycle(workspace) : null,
                }))
              }
            />
            {m.analysis.cellCycle}
          </label>
        </h3>
        {cellCycle && (
          <div className="analysis-body">
            <div className="form-grid">
              <label htmlFor="cycle-parent">{m.analysis.parent}</label>
              {populationSelect('cycle-parent', cellCycle.parentId, (parentId) =>
                setCellCycle({ parentId }),
              )}
              <label htmlFor="cycle-dna">{m.analysis.dna}</label>
              {channelSelect('cycle-dna', cellCycle.dnaChannel, (dnaChannel) =>
                setCellCycle({ dnaChannel }),
              )}
              <p className="muted small span-2">{m.analysis.cellCycleCaption}</p>
              {sampleResult?.cellCycleIssue && (
                <p className="warning span-2">{m.issue[sampleResult.cellCycleIssue]}</p>
              )}
            </div>
            {cyclePlot && (
              <FlowPlot
                plot={cyclePlot}
                gates={phaseGates}
                tool="select"
                label={m.analysis.cellCycle}
              />
            )}
          </div>
        )}
      </section>

      <section className="analysis-section">
        <h3>{m.analysis.comparison}</h3>
        <div className="form-grid">
          <label htmlFor="metric">{m.analysis.metric}</label>
          <select
            id="metric"
            value={experiment.metric ? metricKey(experiment.metric) : ''}
            onChange={(e) =>
              update((ex) => ({
                ...ex,
                metric: options.find((o) => o.key === e.target.value)?.metric ?? null,
              }))
            }
          >
            <option value="">{m.analysis.noMetric}</option>
            {options.map((o) => (
              <option key={o.key} value={o.key}>
                {o.label}
              </option>
            ))}
          </select>
          <label htmlFor="control-group">{m.analysis.controlGroup}</label>
          <select
            id="control-group"
            value={experiment.controlGroup}
            onChange={(e) => update((ex) => ({ ...ex, controlGroup: e.target.value }))}
          >
            {!groups.includes(experiment.controlGroup) && (
              <option value={experiment.controlGroup}>{m.analysis.noMetric}</option>
            )}
            {groups.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
          <p className="muted small span-2">{m.analysis.comparisonCaption}</p>
        </div>
      </section>
    </div>
  );
}
