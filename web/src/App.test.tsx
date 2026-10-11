/**
 * Integration tests of the application: the views, their controllers and the model, with a fake
 * server. Each test uses the interface as a user would, with the mouse and the keyboard.
 */
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { ApiError } from './model/api';
import { LOCALE_KEY } from './model/settings';
import type { Experiment, Gate } from './model/types';
import { fakeApi, results } from './test/fixtures';
import { px, py } from './views/plot/geometry';

let api: ReturnType<typeof fakeApi>;
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  api = fakeApi();
  user = userEvent.setup();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
});

function renderApp(languages = ['en-CA']) {
  return render(<App api={api} storage={localStorage} languages={languages} />);
}

const statusText = () => screen.getByRole('status').textContent;
const panel = () => screen.getAllByRole('tabpanel').find((p) => !p.hidden) as HTMLElement;
const inPanel = () => within(panel());

/** Last set-up sent to the server. */
const lastSaved = (): Experiment => {
  const calls = (api.saveExperiment as ReturnType<typeof vi.fn>).mock.calls;
  return calls[calls.length - 1][1] as Experiment;
};

/** Opens the cadmium example and shows a tab. */
async function openExample(tab?: string) {
  renderApp();
  await user.click(await screen.findByRole('button', { name: /Cadmium on splenocytes/ }));
  await screen.findByRole('table', { name: 'Tubes' });
  if (tab) {
    await user.click(screen.getByRole('tab', { name: tab }));
  }
}

/** Opens a menu of the menu bar, then follows the path of its items. */
async function chooseMenu(...path: string[]) {
  await user.click(screen.getByRole('menuitem', { name: path[0] }));
  for (const label of path.slice(1, -1)) {
    await user.click(screen.getByRole('menuitem', { name: new RegExp(`^${label}`) }));
  }
  const last = path[path.length - 1];
  fireEvent.click(
    screen.queryByRole('menuitemradio', { name: last }) ??
      screen.getByRole('menuitem', { name: new RegExp(`^${last}`) }),
  );
  await act(() => Promise.resolve());
}

/** Coordinates of a value of the linear test axes (0 to 1000) on a plot. */
const at = (x: number, y: number) => ({ clientX: px(x / 1000), clientY: py(y / 1000) });

describe('Tubes tab', () => {
  it('opens an example and edits the role, group and dose of the tubes', async () => {
    await openExample();
    expect(screen.getByText(/Simulated data/)).toBeTruthy();
    expect(screen.getByText('4 tubes, 2 groups')).toBeTruthy();
    expect(statusText()).toBe(
      'Example “Cadmium on splenocytes, dose series (simulated)” opened: 4 tubes.',
    );

    await user.selectOptions(screen.getByLabelText('Role TMZ 1'), 'UNSTAINED');
    expect(lastSaved().samples[1].role).toBe('UNSTAINED');

    const group = screen.getByLabelText('Group NC 1');
    await user.clear(group);
    await user.type(group, ' Control {Enter}');
    expect(lastSaved().samples[0].group).toBe('Control');

    const dose = screen.getByLabelText('Dose NC 1');
    await user.clear(dose);
    await user.type(dose, '2,5');
    await user.tab();
    expect(lastSaved().samples[0].dose).toBe(2.5);
    const saves = (api.saveExperiment as ReturnType<typeof vi.fn>).mock.calls.length;
    await user.clear(dose);
    await user.type(dose, 'abc{Enter}');
    await user.type(screen.getByLabelText('Dose TMZ 1'), '{Backspace}x{Escape}');
    await user.tab();
    expect(api.saveExperiment).toHaveBeenCalledTimes(saves);
    await user.clear(dose);
    await user.tab();
    expect(lastSaved().samples[0].dose).toBeNull();

    await user.selectOptions(screen.getByLabelText('Fluorochrome Annexin only'), 'PI-A');
    expect(lastSaved().samples[3].stainChannel).toBe('PI-A');

    await user.click(screen.getByRole('button', { name: 'Remove tube NC 1' }));
    await waitFor(() => expect(statusText()).toBe('Tube NC 1 removed.'));
    expect(api.removeSample).toHaveBeenCalledWith('w1', 's1');
  });

  it('loads files chosen or dropped, and shows the credit of real data', async () => {
    renderApp();
    expect(screen.getByText(/Drop FCS files here/)).toBeTruthy();
    const input = screen.getByTestId('file-input');
    fireEvent.change(input, { target: { files: [new File(['x'], 'a.fcs')] } });
    await waitFor(() => expect(statusText()).toBe('1 file read.'));
    expect(api.createWorkspace).toHaveBeenCalledWith(null, 'en');

    const tab = panel().firstElementChild as HTMLElement;
    fireEvent.dragOver(tab);
    expect(tab.className).toContain('dropping');
    fireEvent.dragLeave(tab);
    fireEvent.drop(tab, { dataTransfer: { files: [new File(['x'], 'b.fcs')] } });
    await waitFor(() => expect(api.upload).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: /U-87MG, annexin/ }));
    expect(await screen.findByText(/^Real data: Hassan et al\. \(2026\)/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Dataset' }).getAttribute('href')).toBe(
      'https://doi.org/10.17632/d4r5h95356.1',
    );
  });

  it('opens files and examples from the menus, and starts a new experiment', async () => {
    renderApp();
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => undefined);
    await chooseMenu('File', 'Open FCS files…');
    expect(click).toHaveBeenCalled();
    fireEvent.keyDown(window, { key: 'o', ctrlKey: true });
    expect(click).toHaveBeenCalledTimes(2);
    await screen.findByRole('button', { name: /Cadmium on splenocytes/ });
    await chooseMenu('File', 'Example experiments', 'U-87MG, annexin V / PI');
    await waitFor(() => expect(api.createWorkspace).toHaveBeenCalledWith('u87-apoptosis', 'en'));
    await chooseMenu('File', 'New experiment');
    expect(statusText()).toBe('New experiment: no tube loaded.');
    expect(api.deleteWorkspace).toHaveBeenCalledWith('w1');
  });
});

describe('Compensation tab', () => {
  it('chooses where the matrix comes from and edits it', async () => {
    renderApp();
    await user.click(screen.getByRole('tab', { name: 'Compensation' }));
    expect(screen.getByText('Load tubes to set the compensation.')).toBeTruthy();
    await user.click(screen.getByRole('tab', { name: 'Tubes' }));
    await user.click(await screen.findByRole('button', { name: /Cadmium on splenocytes/ }));
    await user.click(screen.getByRole('tab', { name: 'Compensation' }));
    expect(await screen.findByText(/No compensation/)).toBeTruthy();
    expect(screen.getByText('1 single stain available.')).toBeTruthy();

    const source = screen.getByLabelText('Compensation', { selector: 'select' });
    await user.selectOptions(source, 'CONTROLS');
    expect(await screen.findByRole('table', { name: 'Compensation' })).toBeTruthy();
    const cell = screen.getByLabelText('FITC-A (Annexin V) read in PI-A (PI)');
    expect(cell).toHaveProperty('value', '12');
    expect(cell.className).toContain('high');
    await user.clear(cell);
    await user.type(cell, '8.5{Enter}');
    expect(lastSaved().compensation).toBe('EDITED');
    expect(lastSaved().matrix?.values[0][1]).toBeCloseTo(0.085);
    await user.clear(cell);
    await user.tab();

    await user.selectOptions(source, 'FILE');
    await waitFor(() => expect(api.matrixFromFile).toHaveBeenCalled());
    await user.selectOptions(source, 'NONE');
    expect(lastSaved().matrix).toBeNull();
    await user.selectOptions(source, 'EDITED');
    expect(lastSaved().matrix?.channels).toEqual(['FITC-A', 'PI-A']);
    await user.click(screen.getByRole('button', { name: 'Compute from the single stains' }));
    expect(api.matrixFromControls).toHaveBeenCalledTimes(2);
  });
});

describe('Gating tab', () => {
  it('shows the hierarchy and draws a gate under the population shown', async () => {
    await openExample('Gating');
    const tree = screen.getByRole('tree');
    expect(await within(tree).findByText('10,000')).toBeTruthy();
    expect(within(tree).getByText('90.0%')).toBeTruthy();
    const svg = await screen.findByRole('application', { name: 'SSC-A versus FSC-A' });

    await user.click(screen.getByRole('button', { name: 'Rectangle' }));
    expect(screen.getByText('Drag to draw a rectangle.', { selector: 'span' })).toBeTruthy();
    fireEvent.pointerDown(svg, at(200, 200));
    fireEvent.pointerMove(svg, at(400, 400));
    fireEvent.pointerUp(svg);
    const added = lastSaved().gating.children[1];
    expect(added).toMatchObject({ id: 'p2', name: 'Population 2' });
    expect(screen.getByRole('button', { name: 'Select' }).getAttribute('aria-pressed')).toBe(
      'true',
    );

    await user.click(screen.getByRole('button', { name: 'Quadrants' }));
    fireEvent.pointerDown(await screen.findByRole('application'), at(500, 500));
    expect(
      lastSaved()
        .gating.children.map((p) => p.name)
        .slice(2),
    ).toEqual(['FSC-A− SSC-A−', 'FSC-A+ SSC-A−', 'FSC-A+ SSC-A+', 'FSC-A− SSC-A+']);
  });

  it('moves, opens, renames, exports and deletes populations', async () => {
    await openExample('Gating');
    const svg = await screen.findByRole('application', { name: 'SSC-A versus FSC-A' });
    const cells = svg.querySelector('[data-gate-id="p1"] rect')!;
    fireEvent.pointerDown(cells, at(500, 500));
    fireEvent.pointerMove(svg, at(600, 500));
    fireEvent.pointerUp(svg);
    expect(
      (lastSaved().gating.children[0].gate as Extract<Gate, { type: 'rectangle' }>).xMin,
    ).toBeCloseTo(200);

    fireEvent.pointerDown(svg.querySelector('[data-gate-id="p1"] rect')!, at(500, 500));
    fireEvent.pointerUp(svg);
    const tree = screen.getByRole('tree');
    await waitFor(() =>
      expect(within(tree).getByRole('treeitem', { selected: true }).textContent).toContain('Cells'),
    );
    await waitFor(() =>
      expect(api.plot).toHaveBeenLastCalledWith(
        'w1',
        expect.objectContaining({ populationId: 'p1' }),
      ),
    );

    await user.click(within(tree).getByRole('button', { name: 'Rename' }));
    const name = screen.getByLabelText('New name');
    await user.clear(name);
    await user.type(name, 'Singlets{Enter}');
    expect(lastSaved().gating.children[0].name).toBe('Singlets');
    await user.click(within(tree).getByRole('button', { name: 'Rename' }));
    await user.type(screen.getByLabelText('New name'), 'x{Escape}');
    expect(screen.queryByLabelText('New name')).toBeNull();
    await user.click(within(tree).getByRole('button', { name: 'Rename' }));
    await user.clear(screen.getByLabelText('New name'));
    await user.tab();
    expect(lastSaved().gating.children[0].name).toBe('Singlets');

    await user.click(within(tree).getByRole('button', { name: 'Export as FCS' }));
    expect(api.exportUrl).toHaveBeenCalledWith('w1', 's1', 'p1');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();

    await user.click(within(tree).getByRole('button', { name: 'Delete' }));
    expect(lastSaved().gating.children).toHaveLength(0);
    await waitFor(() =>
      expect(within(tree).getByRole('treeitem', { selected: true }).textContent).toContain(
        'All events',
      ),
    );
  });

  it('changes the tube, the axes and their scales, and draws intervals on histograms', async () => {
    await openExample('Gating');
    await screen.findByRole('application');
    await user.selectOptions(screen.getByLabelText('Tube'), 's2');
    await user.selectOptions(screen.getByLabelText('X axis'), 'FITC-A');
    await user.selectOptions(screen.getByLabelText('Scale X axis'), 'LOGICLE');
    await waitFor(() =>
      expect(api.plot).toHaveBeenLastCalledWith(
        'w1',
        expect.objectContaining({ sampleId: 's2', x: 'FITC-A', xScale: 'LOGICLE' }),
      ),
    );
    expect(screen.getByRole('button', { name: 'Interval' })).toHaveProperty('disabled', true);
    await user.selectOptions(screen.getByLabelText('Y axis'), '');
    const histogram = await screen.findByRole('application', {
      name: 'Histogram of FITC-A (Annexin V)',
    });
    expect(screen.getByRole('button', { name: 'Rectangle' })).toHaveProperty('disabled', true);
    expect(screen.getByRole('button', { name: 'Rectangle' }).title).toBe(
      'This tool is drawn on a dot plot: choose a Y axis.',
    );
    await user.click(screen.getByRole('button', { name: 'Interval' }));
    fireEvent.pointerDown(histogram, at(100, 500));
    fireEvent.pointerMove(histogram, at(300, 500));
    fireEvent.pointerUp(histogram);
    expect(lastSaved().gating.children[1].gate).toMatchObject({
      type: 'interval',
      xChannel: 'FITC-A',
    });
    await user.selectOptions(screen.getByLabelText('Y axis'), 'PI-A');
    await user.selectOptions(screen.getByLabelText('Scale Y axis'), 'ARCSINH');
    await waitFor(() =>
      expect(api.plot).toHaveBeenLastCalledWith(
        'w1',
        expect.objectContaining({ yScale: 'ARCSINH' }),
      ),
    );
    expect(screen.getByRole('button', { name: 'Interval' }).title).toBe(
      'An interval is drawn on a histogram (empty Y axis).',
    );
  });

  it('asks for tubes first', async () => {
    renderApp();
    fireEvent.keyDown(window, { code: 'Digit3', key: '3', altKey: true });
    expect(await screen.findByText('Load tubes to draw gates.')).toBeTruthy();
  });
});

describe('Analyses tab', () => {
  it('sets up the annexin V / PI test and drags its thresholds', async () => {
    await openExample('Analyses');
    expect(screen.getByLabelText('Value compared')).toHaveProperty('value', '');
    await user.click(screen.getByRole('checkbox', { name: 'Annexin V / PI assay' }));
    expect(lastSaved().apoptosis).toEqual({
      parentId: 'p1',
      annexinChannel: 'FITC-A',
      piChannel: 'PI-A',
      annexinThreshold: 10,
      piThreshold: 10,
    });
    const plot = await screen.findByRole('application', { name: 'Annexin V / PI assay' });
    await waitFor(() => expect(screen.getByText('Live 90.0%', { selector: 'text' })).toBeTruthy());

    fireEvent.pointerDown(
      plot.querySelector('[data-gate-id="fate-POSITIVE_POSITIVE"] rect')!,
      at(500, 500),
    );
    fireEvent.pointerMove(plot, at(600, 700));
    fireEvent.pointerUp(plot);
    expect(lastSaved().apoptosis?.annexinThreshold).toBeCloseTo(110);
    expect(lastSaved().apoptosis?.piThreshold).toBeCloseTo(210);

    await user.selectOptions(screen.getByLabelText('Population analyzed'), 'all');
    expect(lastSaved().apoptosis?.parentId).toBe('all');
    await user.selectOptions(screen.getByLabelText('Annexin V channel'), 'PI-A');
    await user.selectOptions(screen.getByLabelText('PI channel'), 'FITC-A');
    expect(lastSaved().apoptosis).toMatchObject({ annexinChannel: 'PI-A', piChannel: 'FITC-A' });
    const threshold = screen.getByLabelText('Annexin V threshold');
    await user.clear(threshold);
    await user.type(threshold, '250{Enter}');
    expect(lastSaved().apoptosis?.annexinThreshold).toBe(250);
    await user.clear(screen.getByLabelText('PI threshold'));
    await user.tab();
    await user.click(screen.getByRole('button', { name: 'Suggest from the controls' }));
    await waitFor(() => expect(api.suggestThresholds).toHaveBeenCalled());

    await user.selectOptions(screen.getByLabelText('Value compared'), 'CELL_FATE:LIVE');
    expect(lastSaved().metric).toEqual({ kind: 'CELL_FATE', target: 'LIVE' });
    await user.click(screen.getByRole('checkbox', { name: 'Annexin V / PI assay' }));
    expect(lastSaved().apoptosis).toBeNull();
    expect(lastSaved().metric).toBeNull();
  });

  it('sets up the cell cycle and the comparison of the groups', async () => {
    await openExample('Analyses');
    await user.click(screen.getByRole('checkbox', { name: 'Cell cycle' }));
    expect(lastSaved().cellCycle).toEqual({ parentId: 'p1', dnaChannel: 'PI-A' });
    const histogram = await screen.findByRole('application', { name: 'Cell cycle' });
    await waitFor(() => expect(screen.getByText('G0/G1 60.0%', { selector: 'text' })).toBeTruthy());
    fireEvent.pointerDown(histogram.querySelector('[data-gate-id="phase-S"] rect')!, at(300, 500));
    fireEvent.pointerUp(histogram);

    await user.selectOptions(screen.getByLabelText('DNA channel'), 'FITC-A');
    await user.selectOptions(screen.getAllByLabelText('Population analyzed')[0], 'all');
    expect(lastSaved().cellCycle).toEqual({ parentId: 'all', dnaChannel: 'FITC-A' });

    await user.selectOptions(screen.getByLabelText('Preview'), 's3');
    expect(await screen.findByText('no G0/G1 peak found.')).toBeTruthy();

    await user.selectOptions(screen.getByLabelText('Value compared'), 'CELL_CYCLE:G2_M');
    expect(lastSaved().metric).toEqual({ kind: 'CELL_CYCLE', target: 'G2_M' });
    await user.selectOptions(screen.getByLabelText('Control group'), 'TMZ');
    expect(lastSaved().controlGroup).toBe('TMZ');
    await user.selectOptions(screen.getByLabelText('Value compared'), '');
    expect(lastSaved().metric).toBeNull();
  });

  it('offers a control group that no longer exists, and asks for tubes first', async () => {
    api.createWorkspace = vi.fn(() => {
      const ws = fakeApi().current();
      return Promise.resolve({ ...ws, experiment: { ...ws.experiment, controlGroup: 'Old' } });
    });
    await openExample('Analyses');
    expect(screen.getByLabelText('Control group')).toHaveProperty('value', 'Old');
    await chooseMenu('File', 'New experiment');
    expect(screen.getByText('Load tubes to set the analyses.')).toBeTruthy();
  });
});

describe('Results tab', () => {
  it('shows the values of each tube, the comparison and the dose-response curve', async () => {
    await openExample();
    await user.click(screen.getByRole('tab', { name: 'Analyses' }));
    await user.click(screen.getByRole('checkbox', { name: 'Cell cycle' }));
    await user.selectOptions(screen.getByLabelText('Value compared'), 'POPULATION_PERCENT:p1');
    await user.click(screen.getByRole('tab', { name: 'Results' }));

    const perTube = await screen.findByRole('table', { name: 'Results per tube' });
    const rows = within(perTube).getAllByRole('row').slice(1);
    expect(
      within(rows[0])
        .getAllByRole('cell')
        .map((c) => c.textContent),
    ).toEqual([
      'NC 1',
      'NC',
      '0.00',
      '10,000',
      '90.0%',
      '5.0%',
      '3.0%',
      '—',
      '2.0%',
      '60.0%',
      '20.0%',
      '17.0%',
      '4.2%',
    ]);
    expect(within(rows[2]).getAllByRole('cell')[1].textContent).toBe('Unstained control');
    expect(within(rows[2]).getAllByRole('cell')[12].textContent).toBe('no peak');

    expect(screen.getByText(/One-way ANOVA: F\(1, 4\) = 20\.00, p = 0\.010\./)).toBeTruthy();
    const comparison = screen.getByRole('table', { name: 'Group comparison' });
    expect(within(comparison).getByText('< 0.001')).toBeTruthy();
    expect(within(comparison).getByText('(control)')).toBeTruthy();
    expect(screen.getByText('7.80 – 8.30', { exact: false })).toBeTruthy();
    expect(screen.getAllByRole('img', { name: '% of Cells in parent' })).toHaveLength(2);

    const computed = (api.results as ReturnType<typeof vi.fn>).mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Compute again' }));
    expect(api.results).toHaveBeenCalledTimes(computed + 1);
  });

  it('explains why an analysis is missing', async () => {
    api.results = vi.fn(() =>
      Promise.resolve({
        ...results(),
        comparison: null,
        comparisonIssue: 'TOO_FEW_GROUPS' as const,
        doseResponse: null,
        doseResponseIssue: null,
        samples: results().samples.map((s) => ({ ...s, apoptosis: null })),
      }),
    );
    await openExample('Results');
    expect(
      await screen.findByText('The groups could not be compared: at least two groups are needed.'),
    ).toBeTruthy();
    expect(
      screen.getByText(
        'No dose-response curve: at least five tubes and two non-zero doses are needed.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('columnheader', { name: 'Live' })).toBeNull();
  });

  it('draws a curve without untreated tube, and a comparison without spread', async () => {
    const base = results();
    api.results = vi.fn(() =>
      Promise.resolve({
        ...base,
        comparison: {
          ...base.comparison!,
          groups: base.comparison!.groups.map((g) => ({
            ...g,
            standardDeviation: null,
            values: [g.mean],
            mean: -g.mean / 10,
          })),
        },
        doseResponse: {
          points: base.doseResponse!.points.filter((p) => p.dose > 0),
          fit: { ...base.doseResponse!.fit, ec50Low: null, ec50High: null, hill: -1 },
        },
      }),
    );
    await openExample('Results');
    await screen.findByRole('table', { name: 'Dose-response' });
    expect(document.querySelector('.axis-break')).toBeNull();
    expect(document.querySelector('.error-bar')).toBeNull();
  });

  it('asks for tubes first', async () => {
    renderApp();
    await user.click(screen.getByRole('tab', { name: 'Results' }));
    expect(screen.getByText('Load tubes to see the results.')).toBeTruthy();
  });
});

describe('Report tab', () => {
  it('downloads the report written in the chosen language', async () => {
    renderApp();
    await user.click(screen.getByRole('tab', { name: 'Report' }));
    expect(screen.getByText('Load tubes first.')).toBeTruthy();
    await user.click(screen.getByRole('tab', { name: 'Tubes' }));
    await user.click(await screen.findByRole('button', { name: /Cadmium on splenocytes/ }));
    await screen.findByRole('table', { name: 'Tubes' });
    await user.click(screen.getByRole('tab', { name: 'Report' }));

    await user.type(screen.getByLabelText('Title'), 'Cadmium');
    await user.type(screen.getByLabelText('Analyst'), 'M. Ethier');
    await user.type(screen.getByLabelText('Notes'), 'HepG2');
    await user.selectOptions(screen.getByLabelText('Language'), 'fr');
    expect(screen.getByLabelText('Title').getAttribute('placeholder')).toBe(
      'Rapport de cytométrie en flux',
    );
    await user.click(screen.getByRole('button', { name: 'Download the PDF report' }));
    await waitFor(() => expect(statusText()).toBe('Report CytoScope.pdf downloaded.'));
    expect(api.report).toHaveBeenCalledWith('w1', {
      title: 'Cadmium',
      analyst: 'M. Ethier',
      notes: 'HepG2',
      language: 'fr',
      timeZone: expect.any(String),
    });
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();

    fireEvent.keyDown(window, { key: 's', ctrlKey: true });
    await waitFor(() => expect(api.report).toHaveBeenCalledTimes(2));
    await chooseMenu('File', 'Download the report');
    await waitFor(() => expect(api.report).toHaveBeenCalledTimes(3));
  });

  it('does not save a report that failed', async () => {
    api.report = vi.fn(() => Promise.reject(new ApiError('boom', 500, 'UNEXPECTED')));
    await openExample('Report');
    await user.click(screen.getByRole('button', { name: 'Download the PDF report' }));
    await waitFor(() => expect(statusText()).toBe('Error: unexpected error (boom)'));
    expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
  });
});

describe('Application', () => {
  it('switches the language and the text size, and remembers them', async () => {
    renderApp(['fr-CA']);
    expect(screen.getByRole('tab', { name: 'Fenêtrage' })).toBeTruthy();
    expect(document.documentElement.lang).toBe('fr');
    await chooseMenu('Options', 'Langue', 'English');
    expect(screen.getByRole('tab', { name: 'Gating' })).toBeTruthy();
    expect(localStorage.getItem(LOCALE_KEY)).toBe('en');

    await chooseMenu('Options', 'Text size', 'Large');
    expect(document.documentElement.style.fontSize).toBe('16px');
    await chooseMenu('Options', 'Text size', 'Enlarge text');
    await chooseMenu('Options', 'Text size', 'Shrink text');
    expect(document.documentElement.style.fontSize).toBe('16px');
    fireEvent.keyDown(window, { key: '=', ctrlKey: true });
    expect(document.documentElement.style.fontSize).toBe('17px');
    fireEvent.keyDown(window, { key: '-', ctrlKey: true });
    fireEvent.keyDown(window, { key: '0', ctrlKey: true });
    expect(document.documentElement.style.fontSize).toBe('13px');
    await chooseMenu('Options', 'Text size', 'Default text size');
    fireEvent.keyDown(window, { key: 'q', ctrlKey: true });
  });

  it('switches tabs with Alt and a digit, and opens the guide at the section of the tab', async () => {
    renderApp();
    fireEvent.keyDown(window, { code: 'Digit5', key: '5', altKey: true });
    expect(screen.getByRole('tab', { name: 'Results' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.keyDown(window, { key: 'F1' });
    expect(await screen.findByRole('heading', { level: 1, name: 'Results tab' })).toBeTruthy();
    await user.click(inPanel().getByTitle('Help on this tab (F1)'));
    await user.click(screen.getByTitle('Close'));
    expect(screen.queryByRole('heading', { level: 1, name: 'Results tab' })).toBeNull();
    await chooseMenu('Help', 'User guide');
    expect(await screen.findByRole('heading', { level: 1, name: 'Results tab' })).toBeTruthy();
  });

  it('opens the dialogs and the links of the Help menu', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderApp();
    await chooseMenu('Help', 'About CytoScope');
    expect(screen.getByText(/Version 0\.1\.0/)).toBeTruthy();
    fireEvent.keyDown(window, { key: 'F1' });
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Close' }));
    await chooseMenu('Help', 'Keyboard shortcuts');
    expect(screen.getByText('Show tab 1 to 6')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Close' }));
    await chooseMenu('Help', 'FCS 3.1 standard');
    await chooseMenu('Help', 'U-87MG data');
    expect(open).toHaveBeenCalledWith(
      'https://doi.org/10.17632/d4r5h95356.1',
      '_blank',
      'noopener,noreferrer',
    );
  });

  it('tells when the server has forgotten the files', async () => {
    api.saveExperiment = vi.fn(() => Promise.reject(new ApiError('gone', 410, 'EXPIRED')));
    await openExample();
    await user.selectOptions(screen.getByLabelText('Role TMZ 1'), 'UNSTAINED');
    await waitFor(() => expect(statusText()).toContain('forgot your files after 30 minutes'));
    expect(screen.getByText(/Drop FCS files here/)).toBeTruthy();
  });
});
