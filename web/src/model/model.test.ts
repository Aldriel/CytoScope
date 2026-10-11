import { describe, expect, it } from 'vitest';
import { messagesOf } from '../i18n';
import { axis, experiment, workspace } from '../test/fixtures';
import { toPosition, toValue } from './axis';
import {
  channelsOf,
  defaultApoptosis,
  defaultCellCycle,
  fluorescenceOf,
  groupsOf,
  identityMatrix,
  metricKey,
  metricLabel,
  metricOptions,
  withMatrixValue,
  withoutDanglingReferences,
} from './experiment';
import {
  add,
  addQuadrants,
  drawableChildren,
  find,
  flatten,
  gateChannels,
  mapPoints,
  moveGate,
  nextId,
  parentOf,
  remove,
  rename,
} from './gating';
import type { Gate, Population } from './types';

const en = messagesOf('en');

describe('axis', () => {
  const linear = axis('FSC-A');
  const log = { values: Array.from({ length: 257 }, (_, i) => 10 ** (i / 64)) };

  it('converts between values and positions', () => {
    expect(toValue(linear, 0.5)).toBeCloseTo(500);
    expect(toPosition(linear, 250)).toBeCloseTo(0.25);
    expect(toPosition(log, 100)).toBeCloseTo(0.5);
    expect(toValue(log, 0.25)).toBeCloseTo(10);
  });

  it('extends the end intervals outside the axis', () => {
    expect(toValue(linear, 1.5)).toBeCloseTo(1500);
    expect(toValue(linear, -0.1)).toBeCloseTo(-100);
    expect(toPosition(linear, 2000)).toBeCloseTo(2);
    expect(toPosition(linear, -500)).toBeCloseTo(-0.5);
  });

  it('copes with flat parts of the table', () => {
    expect(toPosition({ values: [0, 0, 1] }, 0)).toBeCloseTo(0.5);
  });
});

describe('gating', () => {
  const rect: Gate = {
    type: 'rectangle',
    xChannel: 'FITC-A',
    yChannel: 'PI-A',
    xMin: 1,
    xMax: 2,
    yMin: 3,
    yMax: 4,
  };
  const tree = experiment().gating;

  it('lists, finds and names the populations', () => {
    expect(flatten(tree).map((p) => [p.population.id, p.depth, p.parentId])).toEqual([
      ['all', 0, null],
      ['p1', 1, 'all'],
    ]);
    expect(find(tree, 'p1')?.name).toBe('Cells');
    expect(find(tree, 'p9')).toBeNull();
    expect(parentOf(tree, 'p1')?.id).toBe('all');
    expect(parentOf(tree, 'all')).toBeNull();
    expect(nextId(tree)).toBe('p2');
    expect(nextId({ ...tree, children: [] })).toBe('p1');
  });

  it('adds, renames and removes populations without changing the old tree', () => {
    const added = add(tree, 'p1', 'Annexin+', rect);
    expect(find(added, 'p2')?.name).toBe('Annexin+');
    expect(parentOf(added, 'p2')?.id).toBe('p1');
    expect(tree.children[0].children).toHaveLength(0);
    expect(find(rename(added, 'p2', 'Dead'), 'p2')?.name).toBe('Dead');
    expect(find(remove(added, 'p1'), 'p2')).toBeNull();
    expect(flatten(remove(added, 'p2'))).toHaveLength(2);
  });

  it('adds the four regions of a quadrant cross and moves them together', () => {
    const names = ['A', 'B', 'C', 'D'];
    const crossed = addQuadrants(tree, 'p1', names, 'FITC-A', 'PI-A', 10, 20);
    const regions = find(crossed, 'p1')!.children;
    expect(regions.map((r) => r.name)).toEqual(names);
    const moved = moveGate(crossed, regions[1].id, {
      ...(regions[1].gate as Extract<Gate, { type: 'quadrant' }>),
      xThreshold: 50,
      yThreshold: 60,
    });
    for (const region of find(moved, 'p1')!.children) {
      expect(region.gate).toMatchObject({ xThreshold: 50, yThreshold: 60 });
    }
    const withRect = add(crossed, 'p1', 'Box', rect);
    const box = find(withRect, 'p1')!.children[4];
    const movedBox = moveGate(withRect, box.id, { ...rect, xMin: 0 });
    expect(find(movedBox, box.id)?.gate).toMatchObject({ xMin: 0 });
    expect(find(movedBox, regions[0].id)?.gate).toMatchObject({ xThreshold: 10 });
  });

  it('tells which gates can be drawn on a plot', () => {
    const interval: Gate = { type: 'interval', xChannel: 'PI-A', min: 1, max: 2 };
    const parent: Population = {
      id: 'all',
      name: 'All',
      gate: null,
      children: [
        { id: 'a', name: 'a', gate: rect, children: [] },
        { id: 'b', name: 'b', gate: interval, children: [] },
        { id: 'c', name: 'c', gate: null, children: [] },
      ],
    };
    expect(drawableChildren(parent, 'FITC-A', 'PI-A').map((p) => p.id)).toEqual(['a']);
    expect(drawableChildren(parent, 'PI-A', null).map((p) => p.id)).toEqual(['b']);
    expect(drawableChildren(parent, 'FITC-A', null)).toEqual([]);
    expect(gateChannels(rect)).toEqual(['FITC-A', 'PI-A']);
    expect(gateChannels(interval)).toEqual(['PI-A', null]);
  });

  it('moves every point of every kind of gate', () => {
    const double = (v: number) => v * 2;
    const plus = (v: number) => v + 1;
    expect(mapPoints(rect, double, plus)).toMatchObject({ xMin: 2, xMax: 4, yMin: 4, yMax: 5 });
    expect(
      mapPoints({ type: 'polygon', xChannel: 'a', yChannel: 'b', xs: [1], ys: [2] }, double, plus),
    ).toMatchObject({ xs: [2], ys: [3] });
    expect(
      mapPoints(
        {
          type: 'quadrant',
          xChannel: 'a',
          yChannel: 'b',
          xThreshold: 1,
          yThreshold: 1,
          quadrant: 'NEGATIVE_NEGATIVE',
        },
        double,
        plus,
      ),
    ).toMatchObject({ xThreshold: 2, yThreshold: 2 });
    expect(
      mapPoints({ type: 'interval', xChannel: 'a', min: 1, max: 3 }, double, plus),
    ).toMatchObject({ min: 2, max: 6 });
  });
});

describe('experiment', () => {
  it('lists the groups of the samples and the channels', () => {
    const e = experiment();
    e.samples.push({ ...e.samples[0], id: 's5' }, { ...e.samples[0], id: 's6', group: '' });
    expect(groupsOf(e)).toEqual(['NC', 'TMZ']);
    expect(channelsOf(null)).toEqual([]);
    expect(channelsOf(workspace({ samples: [] }))).toEqual([]);
    expect(fluorescenceOf(workspace()).map((c) => c.name)).toEqual(['FITC-A', 'PI-A']);
  });

  it('guesses the analyses from the labels of the channels', () => {
    expect(defaultApoptosis(workspace())).toEqual({
      parentId: 'p1',
      annexinChannel: 'FITC-A',
      piChannel: 'PI-A',
      annexinThreshold: 10,
      piThreshold: 10,
    });
    expect(defaultCellCycle(workspace())).toEqual({ parentId: 'p1', dnaChannel: 'PI-A' });
    const scatterOnly = workspace();
    scatterOnly.samples = scatterOnly.samples.map((s) => ({
      ...s,
      channels: s.channels.filter((c) => c.kind === 'scatter'),
    }));
    expect(defaultApoptosis(scatterOnly)).toBeNull();
    expect(defaultCellCycle(scatterOnly)).toBeNull();
    const plain = workspace();
    plain.experiment.gating = { ...plain.experiment.gating, children: [] };
    plain.samples = plain.samples.map((s) => ({
      ...s,
      channels: s.channels.map((c) => ({ ...c, name: c.name.replace(/FITC|PI/, 'FL'), label: '' })),
    }));
    expect(defaultApoptosis(plain)).toMatchObject({
      parentId: 'all',
      annexinChannel: 'FL-A',
    });
  });

  it('offers the values that can be compared, with their names', () => {
    const e = experiment();
    expect(metricOptions(e, en).map((o) => o.key)).toEqual(['POPULATION_PERCENT:p1']);
    e.apoptosis = defaultApoptosis(workspace());
    e.cellCycle = defaultCellCycle(workspace());
    const options = metricOptions(e, en);
    expect(options).toHaveLength(1 + 4 + 5 + 1);
    expect(options.map((o) => o.label)).toContain('% of cells in G2/M');
    expect(options.map((o) => o.label)).toContain('% of live cells');
    expect(options[0].label).toBe('% of apoptotic cells');
    expect(metricLabel(e, { kind: 'POPULATION_PERCENT', target: 'p1' }, en)).toBe(
      '% of Cells in parent',
    );
    expect(metricLabel(e, { kind: 'POPULATION_PERCENT', target: 'p9' }, en)).toBe(
      '% of p9 in parent',
    );
    expect(metricKey({ kind: 'APOPTOTIC', target: '' })).toBe('APOPTOTIC:');
  });

  it('forgets the analyses and metric that no longer apply', () => {
    const e = experiment();
    e.apoptosis = { ...defaultApoptosis(workspace())!, parentId: 'p9' };
    e.cellCycle = defaultCellCycle(workspace());
    e.metric = { kind: 'APOPTOTIC', target: '' };
    const cleaned = withoutDanglingReferences(e);
    expect(cleaned.apoptosis).toBeNull();
    expect(cleaned.cellCycle).not.toBeNull();
    expect(cleaned.metric).toBeNull();
    const phase = withoutDanglingReferences({
      ...e,
      cellCycle: null,
      metric: { kind: 'CELL_CYCLE', target: 'S' },
    });
    expect(phase.metric).toBeNull();
    const population = withoutDanglingReferences({
      ...e,
      metric: { kind: 'POPULATION_PERCENT', target: 'p9' },
    });
    expect(population.metric).toBeNull();
    const kept = withoutDanglingReferences({ ...e, metric: { kind: 'CELL_CYCLE', target: 'S' } });
    expect(kept.metric).toEqual({ kind: 'CELL_CYCLE', target: 'S' });
    expect(withoutDanglingReferences(experiment()).metric).toBeNull();
  });

  it('builds and edits spillover matrices', () => {
    const identity = identityMatrix(['A', 'B']);
    expect(identity.values).toEqual([
      [1, 0],
      [0, 1],
    ]);
    expect(withMatrixValue(identity, 0, 1, 0.2).values).toEqual([
      [1, 0.2],
      [0, 1],
    ]);
    expect(identity.values[0][1]).toBe(0);
  });
});
