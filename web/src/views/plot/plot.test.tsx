import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { messagesOf, MessagesContext } from '../../i18n';
import type { Gate } from '../../model/types';
import { axis, plot } from '../../test/fixtures';
import { FlowPlot, type DrawnGate, type Tool } from './FlowPlot';
import {
  clampX,
  clampY,
  densityPixels,
  distance,
  heat,
  histogramPath,
  MARGIN,
  PLOT_SIZE,
  positionX,
  positionY,
  px,
  py,
  visibleLabels,
} from './geometry';

const en = messagesOf('en');

/** SVG abscissa and ordinate of a value of the linear test axes (0 to 1000). */
const sx = (value: number) => px(value / 1000);
const sy = (value: number) => py(value / 1000);

describe('geometry', () => {
  it('skips the tick labels that would overlap near zero', () => {
    const logicle = {
      ...axis('FL1-A', 'LOGICLE'),
      ticks: [
        { value: -100, position: 0.02, label: '-10²' },
        { value: -10, position: 0.14, label: '-10' },
        { value: 0, position: 0.2, label: '0' },
        { value: 10, position: 0.25, label: '10' },
        { value: 100, position: 0.3, label: '10²' },
        { value: 1000, position: 0.5, label: '10³' },
        { value: 5000, position: 0.6, label: '' },
      ],
    };
    const labels = (horizontal: boolean) =>
      [...visibleLabels(logicle, horizontal)].sort((a, b) => a - b);

    expect(labels(true)).toEqual([0.02, 0.2, 0.3, 0.5]);
    expect(labels(false)).toEqual([0.02, 0.14, 0.2, 0.25, 0.3, 0.5]);
  });

  it('converts between positions and SVG coordinates', () => {
    expect(px(0)).toBe(MARGIN.left);
    expect(py(0)).toBe(MARGIN.top + PLOT_SIZE);
    expect(positionX(px(0.3))).toBeCloseTo(0.3);
    expect(positionY(py(0.7))).toBeCloseTo(0.7);
    expect(clampX(-10)).toBe(px(0));
    expect(clampY(1e6)).toBe(py(0));
    expect(distance({ x: 0, y: 0 }, { x: 3, y: 4 })).toBe(5);
  });

  it('colours the densities from blue to red', () => {
    expect(heat(0)).toEqual([30, 60, 200]);
    expect(heat(1)).toEqual([230, 30, 20]);
    expect(heat(2)).toEqual([230, 30, 20]);
    const pixels = densityPixels({ bins: 2, counts: [0, 3, 1, 0], max: 3 });
    expect(Array.from(pixels.slice(0, 4))).toEqual([230, 30, 20, 255]);
    expect(pixels[7]).toBe(0);
    expect(pixels[15]).toBe(255);
    expect(densityPixels({ bins: 1, counts: [0], max: 0 })[3]).toBe(0);
  });

  it('draws a histogram as steps', () => {
    const path = histogramPath([0, 2]);
    expect(path.startsWith(`M${px(0)},${py(0)}`)).toBe(true);
    expect(path.endsWith('Z')).toBe(true);
    expect(histogramPath([0, 0])).toContain(`V${py(0).toFixed(1)}`);
  });
});

interface Handlers {
  onCreate: ReturnType<typeof vi.fn>;
  onMove: ReturnType<typeof vi.fn>;
  onOpen: ReturnType<typeof vi.fn>;
}

function draw(tool: Tool, gates: DrawnGate[] = [], histogram = false) {
  const handlers: Handlers = { onCreate: vi.fn(), onMove: vi.fn(), onOpen: vi.fn() };
  const view = render(
    <MessagesContext.Provider value={en}>
      <FlowPlot
        plot={plot(histogram ? { x: 'PI-A', y: null } : {})}
        gates={gates}
        tool={tool}
        label="Plot"
        {...handlers}
      />
    </MessagesContext.Provider>,
  );
  const svg = screen.getByRole('application', { name: 'Plot' });
  const at = (x: number, y: number) => ({ clientX: sx(x), clientY: sy(y) });
  return { ...handlers, svg, view, at };
}

const RECT: Gate = {
  type: 'rectangle',
  xChannel: 'FSC-A',
  yChannel: 'SSC-A',
  xMin: 100,
  xMax: 300,
  yMin: 100,
  yMax: 300,
};

describe('FlowPlot', () => {
  it('draws the axes, the density image and the gates with their share', () => {
    const context = {
      createImageData: (w: number, h: number) => ({ data: new Uint8ClampedArray(w * h * 4) }),
      putImageData: vi.fn(),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/png;base64,');
    const { svg } = draw('select', [
      { id: 'p1', name: 'Cells', gate: RECT },
      {
        id: 'p2',
        name: 'Poly',
        gate: {
          type: 'polygon',
          xChannel: 'FSC-A',
          yChannel: 'SSC-A',
          xs: [1, 2, 3],
          ys: [1, 5, 1],
        },
        percent: 12.34,
      },
    ]);
    expect(context.putImageData).toHaveBeenCalled();
    expect(svg.querySelector('image')?.getAttribute('href')).toBe('data:image/png;base64,');
    expect(screen.getByText('Cells 90.0%', { selector: 'text' })).toBeTruthy();
    expect(screen.getByText('Poly 12.3%', { selector: 'text' })).toBeTruthy();
    expect(screen.getByText('FSC-A')).toBeTruthy();
    expect(screen.getAllByText('500')).toHaveLength(2);
  });

  it('draws a rectangle by dragging', () => {
    const { svg, at, onCreate } = draw('rectangle');
    fireEvent.pointerDown(svg, at(200, 800));
    fireEvent.pointerMove(svg, at(600, 400));
    expect(svg.querySelector('rect.draft')).not.toBeNull();
    fireEvent.pointerUp(svg);
    const gate = onCreate.mock.calls[0][0] as Extract<Gate, { type: 'rectangle' }>;
    expect(gate.type).toBe('rectangle');
    expect(gate.xMin).toBeCloseTo(200);
    expect(gate.xMax).toBeCloseTo(600);
    expect(gate.yMin).toBeCloseTo(400);
    expect(gate.yMax).toBeCloseTo(800);
  });

  it('ignores a click or a flat drag with the rectangle tool', () => {
    const { svg, at, onCreate } = draw('rectangle');
    fireEvent.pointerDown(svg, at(200, 200));
    fireEvent.pointerUp(svg);
    fireEvent.pointerDown(svg, at(200, 200));
    fireEvent.pointerMove(svg, at(600, 200));
    fireEvent.pointerUp(svg);
    fireEvent.pointerUp(svg);
    expect(onCreate).not.toHaveBeenCalled();
  });

  it('draws a polygon closed on its first vertex or by a double-click', () => {
    const { svg, at, onCreate } = draw('polygon');
    fireEvent.pointerDown(svg, at(100, 100));
    fireEvent.pointerDown(svg, at(100, 100));
    fireEvent.pointerDown(svg, at(500, 100));
    fireEvent.pointerMove(svg, at(400, 400));
    fireEvent.pointerDown(svg, at(300, 600));
    expect(svg.querySelector('polyline.draft')?.getAttribute('points')?.split(' ')).toHaveLength(4);
    fireEvent.pointerDown(svg, at(101, 101));
    const gate = onCreate.mock.calls[0][0] as Extract<Gate, { type: 'polygon' }>;
    expect(gate.xs.map(Math.round)).toEqual([100, 500, 300]);
    expect(gate.ys.map(Math.round)).toEqual([100, 100, 600]);

    fireEvent.pointerDown(svg, at(100, 100));
    fireEvent.pointerDown(svg, at(500, 100));
    fireEvent.doubleClick(svg);
    expect(onCreate).toHaveBeenCalledTimes(1);
    fireEvent.pointerDown(svg, at(500, 500));
    fireEvent.doubleClick(svg);
    expect(onCreate).toHaveBeenCalledTimes(2);
  });

  it('cancels a polygon with Escape', () => {
    const { svg, at, onCreate } = draw('polygon');
    fireEvent.pointerDown(svg, at(100, 100));
    fireEvent.keyDown(window, { key: 'a' });
    expect(svg.querySelector('polyline.draft')).not.toBeNull();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(svg.querySelector('polyline.draft')).toBeNull();
    fireEvent.doubleClick(svg);
    expect(onCreate).not.toHaveBeenCalled();
  });

  it('places a quadrant cross with a click', () => {
    const { svg, at, onCreate } = draw('quadrant');
    fireEvent.pointerDown(svg, at(250, 750));
    expect(onCreate.mock.calls[0][0]).toMatchObject({
      type: 'quadrant',
      quadrant: 'NEGATIVE_NEGATIVE',
    });
    expect(onCreate.mock.calls[0][0].xThreshold).toBeCloseTo(250);
    expect(onCreate.mock.calls[0][0].yThreshold).toBeCloseTo(750);
  });

  it('draws intervals on a histogram, and nothing else', () => {
    const interval: DrawnGate = {
      id: 'p3',
      name: 'G1',
      gate: { type: 'interval', xChannel: 'PI-A', min: 100, max: 200 },
    };
    const { svg, at, onCreate } = draw('interval', [interval], true);
    expect(svg.querySelector('path.histogram')).not.toBeNull();
    expect(screen.getByText('G1 —', { selector: 'text' })).toBeTruthy();
    fireEvent.pointerDown(svg, at(600, 500));
    fireEvent.pointerMove(svg, at(300, 500));
    expect(svg.querySelector('rect.draft')).not.toBeNull();
    fireEvent.pointerUp(svg);
    expect(onCreate.mock.calls[0][0].min).toBeCloseTo(300);
    expect(onCreate.mock.calls[0][0].max).toBeCloseTo(600);
  });

  it('cannot draw two-channel gates on a histogram', () => {
    for (const tool of ['rectangle', 'polygon', 'quadrant'] as const) {
      const { svg, at, onCreate, view } = draw(
        tool,
        [{ id: 'p1', name: 'Cells', gate: RECT }],
        true,
      );
      fireEvent.pointerDown(svg, at(100, 100));
      fireEvent.pointerMove(svg, at(600, 600));
      fireEvent.pointerUp(svg);
      fireEvent.pointerDown(svg, at(600, 100));
      fireEvent.pointerDown(svg, at(100, 600));
      fireEvent.doubleClick(svg);
      expect(onCreate).not.toHaveBeenCalled();
      expect(screen.queryByText(/Cells/)).toBeNull();
      view.unmount();
    }
  });

  it('opens a gate clicked and moves a gate dragged', () => {
    const { svg, at, onOpen, onMove } = draw('select', [{ id: 'p1', name: 'Cells', gate: RECT }]);
    const shape = svg.querySelector('[data-gate-id="p1"] rect')!;
    fireEvent.pointerDown(shape, at(200, 200));
    fireEvent.pointerUp(svg);
    expect(onOpen).toHaveBeenCalledWith('p1');

    fireEvent.pointerDown(shape, at(200, 200));
    fireEvent.pointerMove(svg, at(300, 400));
    expect(Number(svg.querySelector('[data-gate-id="p1"] rect')?.getAttribute('x'))).toBeCloseTo(
      sx(200),
    );
    fireEvent.pointerUp(svg);
    const moved = onMove.mock.calls[0][1] as Extract<Gate, { type: 'rectangle' }>;
    expect(moved.xMin).toBeCloseTo(200);
    expect(moved.yMax).toBeCloseTo(500);

    fireEvent.pointerDown(svg, at(800, 800));
    fireEvent.pointerMove(svg, at(900, 900));
    fireEvent.pointerUp(svg);
    expect(onOpen).toHaveBeenCalledTimes(1);
    expect(onMove).toHaveBeenCalledTimes(1);
  });

  it('moves the four regions of a quadrant cross together, but not locked gates', () => {
    const region = (id: string, quadrant: Extract<Gate, { type: 'quadrant' }>['quadrant']) => ({
      id,
      name: id,
      gate: {
        type: 'quadrant' as const,
        xChannel: 'FSC-A',
        yChannel: 'SSC-A',
        xThreshold: 500,
        yThreshold: 500,
        quadrant,
      },
    });
    const other: DrawnGate = {
      id: 'q9',
      name: 'other',
      gate: { ...region('q9', 'NEGATIVE_NEGATIVE').gate, xThreshold: 100 },
    };
    const locked: DrawnGate = { ...region('lock', 'NEGATIVE_NEGATIVE'), locked: true };
    const { svg, at, onMove, onOpen } = draw('select', [
      region('q1', 'NEGATIVE_NEGATIVE'),
      region('q2', 'POSITIVE_NEGATIVE'),
      region('q3', 'POSITIVE_POSITIVE'),
      region('q4', 'NEGATIVE_POSITIVE'),
      other,
      locked,
      { id: 'iv', name: 'iv', gate: { type: 'interval', xChannel: 'FSC-A', min: 1, max: 2 } },
    ]);
    fireEvent.pointerDown(svg.querySelector('[data-gate-id="q3"] rect')!, at(700, 700));
    fireEvent.pointerMove(svg, at(800, 600));
    const lines = svg.querySelectorAll('[data-gate-id="q1"] line');
    expect(Number(lines[0].getAttribute('x1'))).toBeCloseTo(sx(600));
    expect(Number(svg.querySelector('[data-gate-id="q9"] line')?.getAttribute('x1'))).toBeCloseTo(
      sx(100),
    );
    fireEvent.pointerUp(svg);
    expect(onMove.mock.calls[0][0]).toBe('q3');
    expect(onMove.mock.calls[0][1].xThreshold).toBeCloseTo(600);
    expect(onMove.mock.calls[0][1].yThreshold).toBeCloseTo(400);

    fireEvent.pointerDown(svg.querySelector('[data-gate-id="lock"] rect')!, at(100, 100));
    fireEvent.pointerUp(svg);
    expect(onOpen).not.toHaveBeenCalled();
  });

  it('moves an interval along the horizontal axis only', () => {
    const { svg, at, onMove } = draw(
      'select',
      [{ id: 'iv', name: 'G1', gate: { type: 'interval', xChannel: 'PI-A', min: 100, max: 200 } }],
      true,
    );
    fireEvent.pointerDown(svg.querySelector('[data-gate-id="iv"] rect')!, at(150, 500));
    fireEvent.pointerMove(svg, at(250, 900));
    fireEvent.pointerUp(svg);
    expect(onMove.mock.calls[0][1].min).toBeCloseTo(200);
    expect(onMove.mock.calls[0][1].max).toBeCloseTo(300);
  });

  it('reads pointer positions on a scaled plot', () => {
    vi.spyOn(SVGElement.prototype, 'getBoundingClientRect').mockReturnValue({
      left: 10,
      top: 20,
      width: 195,
      height: 188,
    } as DOMRect);
    const { svg, onCreate } = draw('quadrant');
    fireEvent.pointerDown(svg, { clientX: 10 + px(0.5) / 2, clientY: 20 + py(0.5) / 2 });
    expect(onCreate.mock.calls[0][0].xThreshold).toBeCloseTo(500);
    expect(onCreate.mock.calls[0][0].yThreshold).toBeCloseTo(500);
  });
});
