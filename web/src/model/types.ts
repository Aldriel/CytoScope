/**
 * Data exchanged with the REST API (server module), with the same names as the Java records.
 * Values that are not numbers (an empty population, a missing dose) arrive as null.
 */

export type ErrorKind =
  'PARSING' | 'INVALID_INPUT' | 'NOT_FOUND' | 'EXPIRED' | 'TOO_LARGE' | 'BUSY' | 'UNEXPECTED';

export type SampleRole = 'SAMPLE' | 'UNSTAINED' | 'SINGLE_STAIN';
export type CompensationSource = 'NONE' | 'FILE' | 'CONTROLS' | 'EDITED';
export type ScaleType = 'LINEAR' | 'LOG' | 'LOGICLE' | 'ARCSINH';
export type Quadrant =
  'NEGATIVE_NEGATIVE' | 'POSITIVE_NEGATIVE' | 'POSITIVE_POSITIVE' | 'NEGATIVE_POSITIVE';
export type CellFate = 'LIVE' | 'EARLY_APOPTOTIC' | 'LATE_APOPTOTIC' | 'NECROTIC';
export type CellCyclePhase = 'SUB_G1' | 'G0_G1' | 'S' | 'G2_M' | 'ABOVE_G2';
export type MetricKind = 'POPULATION_PERCENT' | 'CELL_FATE' | 'APOPTOTIC' | 'CELL_CYCLE';
export type AnalysisIssue =
  | 'NO_METRIC'
  | 'NO_CONTROL_GROUP'
  | 'TOO_FEW_GROUPS'
  | 'TOO_FEW_REPLICATES'
  | 'METRIC_UNAVAILABLE'
  | 'TOO_FEW_DOSES'
  | 'NO_G1_PEAK';

export const QUADRANTS: readonly Quadrant[] = [
  'NEGATIVE_NEGATIVE',
  'POSITIVE_NEGATIVE',
  'POSITIVE_POSITIVE',
  'NEGATIVE_POSITIVE',
];
export const CELL_FATES: readonly CellFate[] = [
  'LIVE',
  'EARLY_APOPTOTIC',
  'LATE_APOPTOTIC',
  'NECROTIC',
];
export const CELL_CYCLE_PHASES: readonly CellCyclePhase[] = [
  'SUB_G1',
  'G0_G1',
  'S',
  'G2_M',
  'ABOVE_G2',
];
export const SCALE_TYPES: readonly ScaleType[] = ['LINEAR', 'LOG', 'LOGICLE', 'ARCSINH'];
export const ROLES: readonly SampleRole[] = ['SAMPLE', 'UNSTAINED', 'SINGLE_STAIN'];
export const COMPENSATION_SOURCES: readonly CompensationSource[] = [
  'NONE',
  'FILE',
  'CONTROLS',
  'EDITED',
];

export interface RectangleGate {
  type: 'rectangle';
  xChannel: string;
  yChannel: string;
  xMin: number;
  xMax: number;
  yMin: number;
  yMax: number;
}

export interface PolygonGate {
  type: 'polygon';
  xChannel: string;
  yChannel: string;
  xs: number[];
  ys: number[];
}

export interface QuadrantGate {
  type: 'quadrant';
  xChannel: string;
  yChannel: string;
  xThreshold: number;
  yThreshold: number;
  quadrant: Quadrant;
}

export interface IntervalGate {
  type: 'interval';
  xChannel: string;
  min: number;
  max: number;
}

export type Gate = RectangleGate | PolygonGate | QuadrantGate | IntervalGate;

export interface Population {
  id: string;
  name: string;
  /** Null for the root, which holds all the events. */
  gate: Gate | null;
  children: Population[];
}

export interface SampleInfo {
  id: string;
  name: string;
  role: SampleRole;
  group: string;
  dose: number | null;
  stainChannel: string;
}

export interface SpilloverMatrix {
  channels: string[];
  /** values[i][j]: share of the dye of channel i read in channel j. */
  values: number[][];
}

export interface ApoptosisSettings {
  parentId: string;
  annexinChannel: string;
  piChannel: string;
  annexinThreshold: number;
  piThreshold: number;
}

export interface CellCycleSettings {
  parentId: string;
  dnaChannel: string;
}

export interface Metric {
  kind: MetricKind;
  target: string;
}

export interface Experiment {
  samples: SampleInfo[];
  compensation: CompensationSource;
  matrix: SpilloverMatrix | null;
  gating: Population;
  apoptosis: ApoptosisSettings | null;
  cellCycle: CellCycleSettings | null;
  metric: Metric | null;
  controlGroup: string;
}

export interface ChannelView {
  name: string;
  label: string;
  displayName: string;
  range: number;
  kind: 'scatter' | 'time' | 'fluorescence';
  defaultScale: ScaleType;
}

export interface SampleView {
  id: string;
  name: string;
  eventCount: number;
  channels: ChannelView[];
}

export interface Workspace {
  id: string;
  /** Folder of the example it was opened from, or null for the visitor's files. */
  example: string | null;
  simulated: boolean;
  language: string;
  timeToLiveMinutes: number;
  samples: SampleView[];
  experiment: Experiment;
}

export interface ExampleInfo {
  folder: string;
  simulated: boolean;
  tubes: number;
}

export interface Examples {
  examples: ExampleInfo[];
  attribution: string;
}

export interface PlotRequest {
  sampleId: string;
  populationId: string;
  x: string;
  /** Null for a histogram. */
  y: string | null;
  xScale: ScaleType | null;
  yScale: ScaleType | null;
  bins?: number;
}

export interface Tick {
  value: number;
  position: number;
  label: string;
}

export interface Axis {
  channel: string;
  displayName: string;
  scale: ScaleType;
  ticks: Tick[];
  /** Value at each position i / 256, increasing. */
  values: number[];
}

export interface Density {
  bins: number;
  /** counts[x * bins + y], y going upward. */
  counts: number[];
  max: number;
}

export interface PopulationStats {
  populationId: string;
  count: number;
  percentOfParent: number | null;
  percentOfTotal: number | null;
}

export interface Plot {
  sampleId: string;
  populationId: string;
  events: number;
  x: Axis;
  y: Axis | null;
  density: Density | null;
  histogram: number[] | null;
  populations: PopulationStats[];
}

export interface CellCycleView {
  g1Peak: number;
  g2Peak: number;
  g1Cv: number;
  bounds: number[];
  percents: Record<CellCyclePhase, number | null>;
  total: number;
}

export interface SampleResult {
  id: string;
  eventCount: number;
  populations: PopulationStats[];
  apoptosis: Record<CellFate, number | null> | null;
  cellCycle: CellCycleView | null;
  cellCycleIssue: AnalysisIssue | null;
}

export interface Anova {
  groupCount: number;
  totalCount: number;
  betweenSquares: number;
  withinSquares: number;
  f: number;
  pValue: number;
}

export interface GroupResult {
  name: string;
  values: number[];
  mean: number;
  standardDeviation: number | null;
  difference: number | null;
  adjustedP: number | null;
  stars: string;
}

export interface Comparison {
  controlGroup: string;
  anova: Anova;
  groups: GroupResult[];
}

export interface DosePoint {
  sampleName: string;
  dose: number;
  value: number;
}

export interface Fit {
  bottom: number;
  top: number;
  ec50: number;
  hill: number;
  ec50Low: number | null;
  ec50High: number | null;
  rSquared: number;
  pointCount: number;
}

export interface Curve {
  points: DosePoint[];
  fit: Fit;
}

export interface Results {
  samples: SampleResult[];
  comparison: Comparison | null;
  comparisonIssue: AnalysisIssue | null;
  doseResponse: Curve | null;
  doseResponseIssue: AnalysisIssue | null;
}

export interface ReportRequest {
  title: string;
  analyst: string;
  notes: string;
  language: string;
  timeZone: string;
}

export interface ReportFile {
  content: Blob;
  fileName: string;
}
