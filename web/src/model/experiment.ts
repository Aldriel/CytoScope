/**
 * Questions and changes about the set-up of an experiment, shared by the views. Every function
 * returns new values and leaves its arguments unchanged.
 */
import type { Messages } from '../i18n';
import { flatten, ROOT_ID } from './gating';
import {
  CELL_CYCLE_PHASES,
  CELL_FATES,
  type ApoptosisSettings,
  type CellCycleSettings,
  type ChannelView,
  type Experiment,
  type Metric,
  type SpilloverMatrix,
  type Workspace,
} from './types';

/** Share of the range of a channel used as first threshold, before any suggestion. */
const DEFAULT_THRESHOLD_SHARE = 0.01;

/** Groups of the samples, in the order of the tubes; controls have no group. */
export function groupsOf(experiment: Experiment): string[] {
  const groups: string[] = [];
  for (const sample of experiment.samples) {
    if (sample.role === 'SAMPLE' && sample.group !== '' && !groups.includes(sample.group)) {
      groups.push(sample.group);
    }
  }
  return groups;
}

/** Channels of the workspace, those of its first tube; empty without tubes. */
export const channelsOf = (workspace: Workspace | null): ChannelView[] =>
  workspace?.samples[0]?.channels ?? [];

/** Fluorescence channels of the workspace. */
export const fluorescenceOf = (workspace: Workspace | null): ChannelView[] =>
  channelsOf(workspace).filter((c) => c.kind === 'fluorescence');

/** First channel whose name or label matches a pattern, otherwise the one at a position. */
function channelLike(channels: ChannelView[], pattern: RegExp, fallback: number): ChannelView {
  return (
    channels.find((c) => pattern.test(c.label) || pattern.test(c.name)) ??
    channels[Math.min(fallback, channels.length - 1)]
  );
}

/** Population analyzed by default: the first gate under the root, or the root. */
const defaultParent = (experiment: Experiment) => experiment.gating.children[0]?.id ?? ROOT_ID;

/**
 * First set-up of the annexin V / PI test, guessed from the labels of the channels.
 *
 * @returns the settings, or null without fluorescence channel
 */
export function defaultApoptosis(workspace: Workspace): ApoptosisSettings | null {
  const channels = fluorescenceOf(workspace);
  if (channels.length === 0) {
    return null;
  }
  const annexin = channelLike(channels, /annexin|fitc/i, 0);
  const pi = channelLike(channels, /\bPI\b|propidium|PI-|PE-Texas|7-?AAD/i, 1);
  return {
    parentId: defaultParent(workspace.experiment),
    annexinChannel: annexin.name,
    piChannel: pi.name,
    annexinThreshold: annexin.range * DEFAULT_THRESHOLD_SHARE,
    piThreshold: pi.range * DEFAULT_THRESHOLD_SHARE,
  };
}

/**
 * First set-up of the cell cycle, guessed from the labels of the channels.
 *
 * @returns the settings, or null without fluorescence channel
 */
export function defaultCellCycle(workspace: Workspace): CellCycleSettings | null {
  const channels = fluorescenceOf(workspace);
  if (channels.length === 0) {
    return null;
  }
  return {
    parentId: defaultParent(workspace.experiment),
    dnaChannel: channelLike(channels, /dna|dapi|hoechst|\bPI\b|PI-|propidium/i, 0).name,
  };
}

/** A value that can be compared between the groups, with its name. */
export interface MetricOption {
  /** Kind and target, such as "CELL_FATE:LIVE", for a select element. */
  key: string;
  metric: Metric;
  label: string;
}

/** Key of a metric for a select element. */
export const metricKey = (metric: Metric) => `${metric.kind}:${metric.target}`;

/** Values that can be compared with the current set-up. */
export function metricOptions(experiment: Experiment, m: Messages): MetricOption[] {
  const options: Metric[] = [];
  if (experiment.apoptosis) {
    options.push({ kind: 'APOPTOTIC', target: '' });
    CELL_FATES.forEach((fate) => options.push({ kind: 'CELL_FATE', target: fate }));
  }
  if (experiment.cellCycle) {
    CELL_CYCLE_PHASES.forEach((phase) => options.push({ kind: 'CELL_CYCLE', target: phase }));
  }
  flatten(experiment.gating)
    .filter((p) => p.population.id !== ROOT_ID)
    .forEach((p) => options.push({ kind: 'POPULATION_PERCENT', target: p.population.id }));
  return options.map((metric) => ({
    key: metricKey(metric),
    metric,
    label: metricLabel(experiment, metric, m),
  }));
}

/** Name of a compared value. */
export function metricLabel(experiment: Experiment, metric: Metric, m: Messages): string {
  switch (metric.kind) {
    case 'APOPTOTIC':
      return m.metric.APOPTOTIC;
    case 'CELL_FATE':
      return m.metric.fate(m.fate[metric.target as keyof Messages['fate']]);
    case 'CELL_CYCLE':
      return m.metric.phase(m.phase[metric.target as keyof Messages['phase']]);
    case 'POPULATION_PERCENT': {
      const found = flatten(experiment.gating).find((p) => p.population.id === metric.target);
      return m.metric.population(found?.population.name ?? metric.target);
    }
  }
}

/**
 * Removes the analyses that refer to populations no longer in the tree, as the server would
 * refuse them, and the metric that no longer exists.
 */
export function withoutDanglingReferences(experiment: Experiment): Experiment {
  const ids = new Set(flatten(experiment.gating).map((p) => p.population.id));
  const apoptosis =
    experiment.apoptosis && ids.has(experiment.apoptosis.parentId) ? experiment.apoptosis : null;
  const cellCycle =
    experiment.cellCycle && ids.has(experiment.cellCycle.parentId) ? experiment.cellCycle : null;
  const metric = experiment.metric;
  const kept =
    metric === null ||
    (metric.kind === 'POPULATION_PERCENT' && ids.has(metric.target)) ||
    ((metric.kind === 'APOPTOTIC' || metric.kind === 'CELL_FATE') && apoptosis !== null) ||
    (metric.kind === 'CELL_CYCLE' && cellCycle !== null);
  return { ...experiment, apoptosis, cellCycle, metric: kept ? metric : null };
}

/** Matrix without spillover on the fluorescence channels, the start of a matrix edited by hand. */
export function identityMatrix(channels: readonly string[]): SpilloverMatrix {
  return {
    channels: [...channels],
    values: channels.map((_, i) => channels.map((__, j) => (i === j ? 1 : 0))),
  };
}

/** Changes one value of a matrix. */
export function withMatrixValue(
  matrix: SpilloverMatrix,
  row: number,
  column: number,
  value: number,
): SpilloverMatrix {
  return {
    ...matrix,
    values: matrix.values.map((r, i) =>
      i === row ? r.map((v, j) => (j === column ? value : v)) : r,
    ),
  };
}
