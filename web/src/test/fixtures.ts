/**
 * Data of the unit tests, shaped like the answers of the server, and a fake API returning them.
 */
import { vi } from 'vitest';
import type { Api } from '../model/api';
import type {
  Axis,
  ChannelView,
  Examples,
  Experiment,
  Plot,
  PlotRequest,
  Results,
  ScaleType,
  Workspace,
} from '../model/types';

const channel = (
  name: string,
  label: string,
  kind: ChannelView['kind'],
  defaultScale: ScaleType,
): ChannelView => ({
  name,
  label,
  displayName: label === '' ? name : `${name} (${label})`,
  range: 1000,
  kind,
  defaultScale,
});

export const CHANNELS: ChannelView[] = [
  channel('FSC-A', '', 'scatter', 'LINEAR'),
  channel('SSC-A', '', 'scatter', 'LINEAR'),
  channel('FITC-A', 'Annexin V', 'fluorescence', 'LOGICLE'),
  channel('PI-A', 'PI', 'fluorescence', 'LOGICLE'),
];

/** Set-up of the test experiment: one gate (Cells), no analysis. */
export function experiment(): Experiment {
  return {
    samples: [
      { id: 's1', name: 'NC 1', role: 'SAMPLE', group: 'NC', dose: 0, stainChannel: '' },
      { id: 's2', name: 'TMZ 1', role: 'SAMPLE', group: 'TMZ', dose: 5, stainChannel: '' },
      { id: 's3', name: 'Unstained', role: 'UNSTAINED', group: '', dose: null, stainChannel: '' },
      {
        id: 's4',
        name: 'Annexin only',
        role: 'SINGLE_STAIN',
        group: '',
        dose: null,
        stainChannel: 'FITC-A',
      },
    ],
    compensation: 'NONE',
    matrix: null,
    gating: {
      id: 'all',
      name: 'All events',
      gate: null,
      children: [
        {
          id: 'p1',
          name: 'Cells',
          gate: {
            type: 'rectangle',
            xChannel: 'FSC-A',
            yChannel: 'SSC-A',
            xMin: 100,
            xMax: 900,
            yMin: 100,
            yMax: 900,
          },
          children: [],
        },
      ],
    },
    apoptosis: null,
    cellCycle: null,
    metric: null,
    controlGroup: 'NC',
  };
}

/** Workspace holding the four tubes of {@link experiment}. */
export function workspace(changes: Partial<Workspace> = {}): Workspace {
  const e = experiment();
  return {
    id: 'w1',
    example: null,
    simulated: false,
    language: 'en',
    timeToLiveMinutes: 30,
    samples: e.samples.map((s) => ({
      id: s.id,
      name: s.name,
      eventCount: 10_000,
      channels: CHANNELS,
    })),
    experiment: e,
    ...changes,
  };
}

/** Linear axis from 0 to 1000. */
export function axis(name: string, scale: ScaleType = 'LINEAR'): Axis {
  const found = CHANNELS.find((c) => c.name === name);
  return {
    channel: name,
    displayName: found?.displayName ?? name,
    scale,
    ticks: [
      { value: 0, position: 0, label: '0' },
      { value: 500, position: 0.5, label: '500' },
      { value: 1000, position: 1, label: '' },
    ],
    values: Array.from({ length: 257 }, (_, i) => (i * 1000) / 256),
  };
}

/** Plot answered for a request: a small density, or a histogram without Y axis. */
export function plot(request: Partial<PlotRequest> = {}): Plot {
  const x = request.x ?? 'FSC-A';
  const y = request.y === undefined ? 'SSC-A' : request.y;
  const bins = 4;
  return {
    sampleId: request.sampleId ?? 's1',
    populationId: request.populationId ?? 'all',
    events: 9_000,
    x: axis(x, request.xScale ?? 'LINEAR'),
    y: y === null ? null : axis(y, request.yScale ?? 'LINEAR'),
    density:
      y === null
        ? null
        : { bins, counts: Array.from({ length: bins * bins }, (_, i) => i % 5), max: 4 },
    histogram: y === null ? [0, 5, 20, 3] : null,
    populations: [
      { populationId: 'all', count: 10_000, percentOfParent: 100, percentOfTotal: 100 },
      { populationId: 'p1', count: 9_000, percentOfParent: 90, percentOfTotal: 90 },
    ],
  };
}

/** Results of every analysis of the test experiment. */
export function results(): Results {
  const sample = (id: string, live: number) => ({
    id,
    eventCount: 10_000,
    populations: plot().populations,
    apoptosis: { LIVE: live, EARLY_APOPTOTIC: 5, LATE_APOPTOTIC: 3, NECROTIC: null },
    cellCycle: {
      g1Peak: 200,
      g2Peak: 400,
      g1Cv: 4.2,
      bounds: [150, 250, 350, 450],
      percents: { SUB_G1: 2, G0_G1: 60, S: 20, G2_M: 17, ABOVE_G2: 1 },
      total: 9_000,
    },
    cellCycleIssue: null,
  });
  return {
    samples: [
      sample('s1', 90),
      sample('s2', 70),
      { ...sample('s3', 95), apoptosis: null, cellCycle: null, cellCycleIssue: 'NO_G1_PEAK' },
    ],
    comparison: {
      controlGroup: 'NC',
      anova: {
        groupCount: 2,
        totalCount: 6,
        betweenSquares: 10,
        withinSquares: 2,
        f: 20,
        pValue: 0.01,
      },
      groups: [
        {
          name: 'NC',
          values: [4, 5, 6],
          mean: 5,
          standardDeviation: 1,
          difference: null,
          adjustedP: null,
          stars: '',
        },
        {
          name: 'TMZ',
          values: [20, 21, 22],
          mean: 21,
          standardDeviation: 1,
          difference: 16,
          adjustedP: 0.0004,
          stars: '***',
        },
      ],
    },
    comparisonIssue: null,
    doseResponse: {
      points: [
        { sampleName: 'Cd 0', dose: 0, value: 5 },
        { sampleName: 'Cd 1', dose: 1, value: 10 },
        { sampleName: 'Cd 10', dose: 10, value: 50 },
        { sampleName: 'Cd 50', dose: 50, value: 80 },
      ],
      fit: {
        bottom: 5,
        top: 85,
        ec50: 8,
        hill: 1.5,
        ec50Low: 7.8,
        ec50High: 8.3,
        rSquared: 0.98,
        pointCount: 4,
      },
    },
    doseResponseIssue: null,
  };
}

export const EXAMPLES: Examples = {
  examples: [
    { folder: 'cadmium', simulated: true, tubes: 24 },
    { folder: 'u87-apoptosis', simulated: false, tubes: 18 },
  ],
  attribution: 'Hassan et al. (2026), CC BY 4.0',
};

/**
 * Fake API answering with the fixtures. The workspace it holds follows the changes, like the
 * server; each operation can be replaced in a test.
 */
export function fakeApi(overrides: Partial<Api> = {}): Api & { current: () => Workspace } {
  let state = workspace();
  const update = (changes: Partial<Workspace>) => {
    state = { ...state, ...changes };
    return Promise.resolve(state);
  };
  const api = {
    examples: vi.fn(() => Promise.resolve(EXAMPLES)),
    createWorkspace: vi.fn((example: string | null) =>
      update({ example, simulated: example === 'cadmium' }),
    ),
    deleteWorkspace: vi.fn(() => Promise.resolve()),
    upload: vi.fn(() => Promise.resolve(state)),
    removeSample: vi.fn((_: string, sampleId: string) =>
      update({
        samples: state.samples.filter((s) => s.id !== sampleId),
        experiment: {
          ...state.experiment,
          samples: state.experiment.samples.filter((s) => s.id !== sampleId),
        },
      }),
    ),
    saveExperiment: vi.fn((_: string, experiment: Experiment) => update({ experiment })),
    matrixFromControls: vi.fn(() =>
      update({
        experiment: {
          ...state.experiment,
          compensation: 'CONTROLS',
          matrix: {
            channels: ['FITC-A', 'PI-A'],
            values: [
              [1, 0.12],
              [0.02, 1],
            ],
          },
        },
      }),
    ),
    matrixFromFile: vi.fn(() =>
      update({
        experiment: {
          ...state.experiment,
          compensation: 'FILE',
          matrix: {
            channels: ['FITC-A', 'PI-A'],
            values: [
              [1, 0.1],
              [0, 1],
            ],
          },
        },
      }),
    ),
    suggestThresholds: vi.fn(() =>
      update({
        experiment: {
          ...state.experiment,
          apoptosis: state.experiment.apoptosis && {
            ...state.experiment.apoptosis,
            annexinThreshold: 123,
          },
        },
      }),
    ),
    plot: vi.fn((_: string, request: PlotRequest) => Promise.resolve(plot(request))),
    results: vi.fn(() => Promise.resolve(results())),
    exportUrl: vi.fn(
      (id: string, sample: string, population: string) =>
        `/api/workspaces/${id}/samples/${sample}/fcs?population=${population}`,
    ),
    report: vi.fn(() =>
      Promise.resolve({ content: new Blob(['%PDF']), fileName: 'CytoScope.pdf' }),
    ),
    ...overrides,
  };
  return { ...api, current: () => state };
}
