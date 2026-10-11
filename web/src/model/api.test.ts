import { afterEach, describe, expect, it, vi } from 'vitest';
import { experiment } from '../test/fixtures';
import { api, ApiError, attachmentName } from './api';

/** Replaces fetch by a stub answering with the given status, body and headers. */
function stubFetch(status: number, body: BodyInit | null, headers?: HeadersInit) {
  const fetchStub = vi.fn().mockResolvedValue(new Response(body, { status, headers }));
  vi.stubGlobal('fetch', fetchStub);
  return fetchStub;
}

/** URL and options of the only request sent. */
const sent = (fetchStub: ReturnType<typeof stubFetch>) =>
  fetchStub.mock.calls[0] as [string, RequestInit];

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api', () => {
  it('reads the examples', async () => {
    const fetchStub = stubFetch(200, '{"examples":[],"attribution":"x"}');
    expect(await api.examples()).toEqual({ examples: [], attribution: 'x' });
    expect(sent(fetchStub)[0]).toBe('/api/examples');
  });

  it('opens and closes workspaces', async () => {
    const created = stubFetch(200, '{"id":"w1"}');
    expect(await api.createWorkspace('cadmium', 'fr')).toEqual({ id: 'w1' });
    const [url, init] = sent(created);
    expect(url).toBe('/api/workspaces');
    expect(init.method).toBe('POST');
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(JSON.parse(init.body as string)).toEqual({ example: 'cadmium', language: 'fr' });

    const deleted = stubFetch(200, '{"deleted":true}');
    await api.deleteWorkspace('w/1');
    expect(sent(deleted)).toEqual(['/api/workspaces/w%2F1', { method: 'DELETE' }]);
  });

  it('uploads FCS files as raw bodies, with their name', async () => {
    const fetchStub = stubFetch(200, '{"id":"w1"}');
    const file = new File(['FCS3.1'], 'NC 1.fcs');
    await api.upload('w1', file);
    const [url, init] = sent(fetchStub);
    expect(url).toBe('/api/workspaces/w1/samples?name=NC%201.fcs');
    expect(init.body).toBe(file);
    expect(init.headers).toEqual({ 'Content-Type': 'application/octet-stream' });
  });

  it('sends the changes of the experiment', async () => {
    const calls: [() => Promise<unknown>, string, string][] = [
      [() => api.removeSample('w1', 's2'), '/api/workspaces/w1/samples/s2', 'DELETE'],
      [() => api.saveExperiment('w1', experiment()), '/api/workspaces/w1/experiment', 'PUT'],
      [() => api.matrixFromControls('w1'), '/api/workspaces/w1/compensation/controls', 'POST'],
      [() => api.matrixFromFile('w1'), '/api/workspaces/w1/compensation/file', 'POST'],
      [
        () =>
          api.suggestThresholds('w1', {
            parentId: 'p1',
            annexinChannel: 'FITC-A',
            piChannel: 'PI-A',
            annexinThreshold: 1,
            piThreshold: 2,
          }),
        '/api/workspaces/w1/apoptosis/suggest',
        'POST',
      ],
      [
        () =>
          api.plot('w1', {
            sampleId: 's1',
            populationId: 'all',
            x: 'FSC-A',
            y: null,
            xScale: null,
            yScale: null,
          }),
        '/api/workspaces/w1/plot',
        'POST',
      ],
    ];
    for (const [call, path, method] of calls) {
      const fetchStub = stubFetch(200, '{}');
      await call();
      const [url, init] = sent(fetchStub);
      expect(url).toBe(path);
      expect(init.method).toBe(method);
    }
    const results = stubFetch(200, '{"samples":[]}');
    expect(await api.results('w1')).toEqual({ samples: [] });
    expect(sent(results)[0]).toBe('/api/workspaces/w1/results');
    expect(api.exportUrl('w1', 's 1', 'p1')).toBe(
      '/api/workspaces/w1/samples/s%201/fcs?population=p1',
    );
  });

  it('downloads the report with the name given by the server', async () => {
    stubFetch(200, '%PDF', {
      'Content-Disposition':
        'attachment; filename="r.pdf"; filename*=UTF-8\'\'Rapport%20%C3%A9t%C3%A9.pdf',
    });
    const report = await api.report('w1', {
      title: 'Rapport été',
      analyst: '',
      notes: '',
      language: 'fr',
      timeZone: 'America/Montreal',
    });
    expect(report.fileName).toBe('Rapport été.pdf');
    expect(report.content.size).toBe('%PDF'.length);

    stubFetch(200, '%PDF');
    const unnamed = await api.report('w1', {
      title: '',
      analyst: '',
      notes: '',
      language: 'en',
      timeZone: '',
    });
    expect(unnamed.fileName).toBe('CytoScope.pdf');
  });

  it('turns error answers into ApiError', async () => {
    stubFetch(410, '{"error":"Workspace expired","kind":"EXPIRED"}');
    await expect(api.results('w1')).rejects.toMatchObject({
      message: 'Workspace expired',
      status: 410,
      kind: 'EXPIRED',
    });
    stubFetch(502, '<html>Bad gateway</html>');
    await expect(api.examples()).rejects.toMatchObject({ message: 'HTTP 502', kind: null });
    stubFetch(500, 'null');
    await expect(api.examples()).rejects.toMatchObject({ message: 'HTTP 500' });
    stubFetch(200, 'not json');
    await expect(api.examples()).rejects.toMatchObject({ message: 'Invalid answer (HTTP 200)' });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    const error = await api.examples().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 0, name: 'ApiError' });
  });
});

describe('attachmentName', () => {
  it('reads the UTF-8 name, then the plain one', () => {
    expect(attachmentName(null)).toBeNull();
    expect(attachmentName('attachment')).toBeNull();
    expect(attachmentName('attachment; filename="a b.pdf"')).toBe('a b.pdf');
    expect(attachmentName('attachment; filename=plain.pdf')).toBe('plain.pdf');
    expect(attachmentName('attachment; filename="x.pdf"; filename*=UTF-8\'\'%E0%A4%A')).toBe(
      'x.pdf',
    );
  });
});
