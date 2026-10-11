import { describe, expect, it, vi } from 'vitest';
import { messagesOf } from '../i18n';
import { fakeApi, workspace } from '../test/fixtures';
import { ApiError } from './api';
import { AppModel } from './AppModel';

const en = messagesOf('en');
const statusOf = (model: AppModel) => model.status.get().render(en);
const fcs = (name: string) => new File(['FCS3.1'], name);
const expired = () => Promise.reject(new ApiError('gone', 410, 'EXPIRED'));

/** Promise resolved by the test, to control the order of the answers. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}

describe('AppModel', () => {
  it('downloads the examples once and reports a failure', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    await model.loadExamples();
    await model.loadExamples();
    expect(api.examples).toHaveBeenCalledTimes(1);
    expect(model.examples.get()?.examples).toHaveLength(2);

    const failing = new AppModel(fakeApi({ examples: () => Promise.reject(new Error('down')) }));
    await failing.loadExamples();
    expect(failing.examples.get()).toBeNull();
    expect(statusOf(failing)).toBe('Error: unexpected error (down)');
  });

  it('opens an example and closes it', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    const opening = model.openExample('cadmium', 'Cadmium', 'fr');
    expect(model.busy.get()).toBe(true);
    expect(statusOf(model)).toBe('Opening the example “Cadmium”…');
    await opening;
    expect(model.busy.get()).toBe(false);
    expect(api.createWorkspace).toHaveBeenCalledWith('cadmium', 'fr');
    expect(model.workspace.get()?.example).toBe('cadmium');
    expect(statusOf(model)).toBe('Example “Cadmium” opened: 4 tubes.');

    model.clear();
    expect(model.workspace.get()).toBeNull();
    expect(api.deleteWorkspace).toHaveBeenCalledWith('w1');
    expect(statusOf(model)).toBe('New experiment: no tube loaded.');
    model.clear();
    expect(api.deleteWorkspace).toHaveBeenCalledTimes(1);
  });

  it('loads files one after the other into a new workspace, then into the same one', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    const statuses: string[] = [];
    model.status.subscribe(() => statuses.push(statusOf(model)));
    await model.loadFiles([fcs('a.fcs'), fcs('b.FCS')], 'en');
    expect(api.createWorkspace).toHaveBeenCalledWith(null, 'en');
    expect(api.upload).toHaveBeenCalledTimes(2);
    expect(statuses).toEqual([
      'Reading a.fcs (1 of 2)…',
      'Reading b.FCS (2 of 2)…',
      '2 files read.',
    ]);
    await model.loadFiles([fcs('c.fcs')], 'en');
    expect(api.createWorkspace).toHaveBeenCalledTimes(1);

    await model.loadFiles([], 'en');
    await model.loadFiles([fcs('notes.txt')], 'en');
    expect(api.upload).toHaveBeenCalledTimes(3);
    expect(statusOf(model)).toBe('Error: notes.txt is not an .fcs file.');
  });

  it('forgets an expired workspace, so that the next file opens a new one', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    await model.openExample('cadmium', 'Cadmium', 'en');
    api.upload = vi.fn(expired);
    await model.loadFiles([fcs('a.fcs')], 'en');
    expect(model.workspace.get()).toBeNull();
    expect(statusOf(model)).toBe(
      'Error: The server forgot your files after 30 minutes of inactivity; load them again to continue.',
    );
    expect(model.status.get().error).toBe(true);

    const fresh = new AppModel(fakeApi({ plot: expired }));
    expect(await fresh.plot({} as never)).toBeNull();
    fresh.workspace.set(workspace());
    expect(await fresh.plot({} as never)).toBeNull();
    expect(statusOf(fresh)).toContain('30 minutes');
  });

  it('removes a tube', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    await model.removeSample('s1');
    expect(api.removeSample).not.toHaveBeenCalled();
    await model.openExample('cadmium', 'Cadmium', 'en');
    await model.removeSample('s2');
    expect(model.workspace.get()?.samples.map((s) => s.id)).toEqual(['s1', 's3', 's4']);
    expect(statusOf(model)).toBe('Tube TMZ 1 removed.');
    await model.removeSample('s9');
    expect(statusOf(model)).toBe('Tube s9 removed.');
  });

  it('shows a change at once, then the answer of the server', async () => {
    const answer = deferred<ReturnType<typeof workspace>>();
    const api = fakeApi();
    const model = new AppModel(api);
    await model.updateExperiment((e) => e);
    expect(api.saveExperiment).not.toHaveBeenCalled();
    await model.openExample('cadmium', 'Cadmium', 'en');
    api.saveExperiment = vi.fn(() => answer.promise);
    const saving = model.updateExperiment((e) => ({ ...e, controlGroup: 'TMZ' }));
    expect(model.workspace.get()?.experiment.controlGroup).toBe('TMZ');
    answer.resolve(workspace({ id: 'w1', language: 'fr' }));
    await saving;
    expect(model.workspace.get()?.language).toBe('fr');
  });

  it('comes back to the last state accepted when the server refuses a change', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    await model.openExample('cadmium', 'Cadmium', 'en');
    api.saveExperiment = vi.fn(() =>
      Promise.reject(new ApiError('bad gate', 400, 'INVALID_INPUT')),
    );
    await model.updateExperiment((e) => ({ ...e, controlGroup: 'TMZ' }));
    expect(model.workspace.get()?.experiment.controlGroup).toBe('NC');
    expect(statusOf(model)).toBe('Error: invalid input (bad gate)');
  });

  it('keeps only the answer to the latest change', async () => {
    const first = deferred<ReturnType<typeof workspace>>();
    const api = fakeApi();
    const model = new AppModel(api);
    await model.openExample('cadmium', 'Cadmium', 'en');
    const save = api.saveExperiment;
    api.saveExperiment = vi.fn().mockReturnValueOnce(first.promise).mockImplementation(save);
    const slow = model.updateExperiment((e) => ({ ...e, controlGroup: 'A' }));
    await model.updateExperiment((e) => ({ ...e, controlGroup: 'B' }));
    first.resolve(workspace());
    await slow;
    expect(model.workspace.get()?.experiment.controlGroup).toBe('B');
  });

  it('computes the matrix, restores that of the files and suggests thresholds', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    await model.computeMatrix();
    expect(api.matrixFromControls).not.toHaveBeenCalled();
    await model.openExample('cadmium', 'Cadmium', 'en');
    await model.computeMatrix();
    expect(model.workspace.get()?.experiment.compensation).toBe('CONTROLS');
    expect(statusOf(model)).toBe('Compensation matrix computed from the single stains.');
    await model.restoreFileMatrix();
    expect(model.workspace.get()?.experiment.compensation).toBe('FILE');
    expect(statusOf(model)).toBe('Compensation matrix of the files restored.');
    const settings = {
      parentId: 'p1',
      annexinChannel: 'FITC-A',
      piChannel: 'PI-A',
      annexinThreshold: 1,
      piThreshold: 1,
    };
    await model.suggestThresholds(settings);
    expect(api.suggestThresholds).toHaveBeenCalledWith('w1', settings);
    expect(statusOf(model)).toBe('Thresholds suggested from the controls.');
  });

  it('counts plots and computes the results of the current set-up', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    await model.refreshResults();
    expect(api.results).not.toHaveBeenCalled();
    expect(model.exportUrl('s1', 'p1')).toBeNull();
    await model.openExample('cadmium', 'Cadmium', 'en');
    const plot = await model.plot({
      sampleId: 's1',
      populationId: 'all',
      x: 'FSC-A',
      y: 'SSC-A',
      xScale: null,
      yScale: null,
    });
    expect(plot?.density?.bins).toBe(4);
    await model.refreshResults();
    expect(model.results.get()?.experiment).toBe(model.workspace.get()?.experiment);
    expect(statusOf(model)).toBe('Results up to date.');
    expect(model.exportUrl('s1', 'p1')).toBe('/api/workspaces/w1/samples/s1/fcs?population=p1');
  });

  it('writes the report when there are tubes', async () => {
    const api = fakeApi();
    const model = new AppModel(api);
    const request = { title: '', analyst: '', notes: '', language: 'en', timeZone: '' };
    expect(await model.writeReport(request)).toBeNull();
    expect(statusOf(model)).toBe('Error: load tubes first.');
    await model.openExample('cadmium', 'Cadmium', 'en');
    const file = await model.writeReport(request);
    expect(file?.fileName).toBe('CytoScope.pdf');
    expect(statusOf(model)).toBe('Report CytoScope.pdf downloaded.');

    api.report = vi.fn(() => Promise.reject(new ApiError('busy', 503, 'BUSY')));
    expect(await model.writeReport(request)).toBeNull();
    expect(statusOf(model)).toContain('very busy');
  });
});
