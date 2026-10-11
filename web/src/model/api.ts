/**
 * Access to the REST API of the Java server (server module), which runs the calculations of the
 * shared core module. During development, Vite forwards /api to http://127.0.0.1:7072 (see
 * vite.config.ts).
 */
import type {
  ApoptosisSettings,
  ErrorKind,
  Examples,
  Experiment,
  Plot,
  PlotRequest,
  ReportFile,
  ReportRequest,
  Results,
  Workspace,
} from './types';

const BASE = '/api';

/** Name proposed for a report when the server gives none. */
const DEFAULT_REPORT_NAME = 'CytoScope.pdf';

/** Error returned by the API, or raised when the server cannot be reached (status 0). */
export class ApiError extends Error {
  /**
   * @param message technical explanation, in English, given by the server
   * @param status HTTP status code, or 0 if the server did not answer
   * @param kind category of the failure, translated for the user
   */
  constructor(
    message: string,
    readonly status: number,
    readonly kind: ErrorKind | null,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Sends a request; every failure, including an error status, becomes an ApiError. */
async function send(path: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(BASE + path, init);
  } catch {
    throw new ApiError('Server unreachable', 0, null);
  }
  if (!response.ok) {
    let error: { error?: string; kind?: ErrorKind } = {};
    try {
      error = (await response.json()) ?? {};
    } catch {
      // Not a JSON answer, e.g. from a proxy: the status says enough.
    }
    throw new ApiError(
      error.error ?? `HTTP ${response.status}`,
      response.status,
      error.kind ?? null,
    );
  }
  return response;
}

/** Sends a request and decodes the JSON answer. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await send(path, init);
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(`Invalid answer (HTTP ${response.status})`, response.status, null);
  }
}

/** Options of a request with a JSON body. */
const jsonBody = (body: unknown, method = 'POST'): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

/**
 * Reads the file name of a Content-Disposition header: the UTF-8 name (filename*) when given,
 * otherwise the plain one.
 *
 * @returns the name, or null if the header gives none
 */
export function attachmentName(header: string | null): string | null {
  if (header === null) {
    return null;
  }
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim());
    } catch {
      // Malformed encoding: fall back on the plain name.
    }
  }
  const plain = /filename\s*=\s*"([^"]*)"/i.exec(header) ?? /filename\s*=\s*([^;]+)/i.exec(header);
  return plain ? plain[1].trim() : null;
}

const workspacePath = (id: string) => `/workspaces/${encodeURIComponent(id)}`;

/** Operations of the REST API. */
export const api = {
  /** The example experiments and the credit of their data. */
  examples() {
    return request<Examples>('/examples');
  },

  /**
   * Opens a workspace on the server, where the files stay for a while after the last request.
   *
   * @param example folder of an example to open, or null for an empty workspace
   * @param language language of the population names the server creates
   */
  createWorkspace(example: string | null, language: string) {
    return request<Workspace>('/workspaces', jsonBody({ example, language }));
  },

  /** Closes a workspace: its files are forgotten at once. */
  async deleteWorkspace(id: string): Promise<void> {
    await send(workspacePath(id), { method: 'DELETE' });
  },

  /** Loads an FCS file into a workspace. */
  upload(id: string, file: File) {
    return request<Workspace>(
      `${workspacePath(id)}/samples?name=${encodeURIComponent(file.name)}`,
      { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: file },
    );
  },

  /** Removes a tube from a workspace. */
  removeSample(id: string, sampleId: string) {
    return request<Workspace>(`${workspacePath(id)}/samples/${encodeURIComponent(sampleId)}`, {
      method: 'DELETE',
    });
  },

  /** Replaces the set-up of the experiment. */
  saveExperiment(id: string, experiment: Experiment) {
    return request<Workspace>(`${workspacePath(id)}/experiment`, jsonBody(experiment, 'PUT'));
  },

  /** Computes the spillover matrix from the single-stain controls. */
  matrixFromControls(id: string) {
    return request<Workspace>(`${workspacePath(id)}/compensation/controls`, { method: 'POST' });
  },

  /** Uses the spillover matrix stored in the files again. */
  matrixFromFile(id: string) {
    return request<Workspace>(`${workspacePath(id)}/compensation/file`, { method: 'POST' });
  },

  /** Proposes the thresholds of the annexin V / PI test from the controls. */
  suggestThresholds(id: string, settings: ApoptosisSettings) {
    return request<Workspace>(`${workspacePath(id)}/apoptosis/suggest`, jsonBody(settings));
  },

  /** Counts the events of a plot. */
  plot(id: string, plot: PlotRequest) {
    return request<Plot>(`${workspacePath(id)}/plot`, jsonBody(plot));
  },

  /** Runs every analysis of the experiment. */
  results(id: string) {
    return request<Results>(`${workspacePath(id)}/results`);
  },

  /** Address of the FCS file holding the events of a population of a tube. */
  exportUrl(id: string, sampleId: string, populationId: string) {
    return (
      `${BASE}${workspacePath(id)}/samples/${encodeURIComponent(sampleId)}/fcs` +
      `?population=${encodeURIComponent(populationId)}`
    );
  },

  /** The PDF report. */
  async report(id: string, body: ReportRequest): Promise<ReportFile> {
    const response = await send(`${workspacePath(id)}/report`, jsonBody(body));
    return {
      content: await response.blob(),
      fileName: attachmentName(response.headers.get('Content-Disposition')) ?? DEFAULT_REPORT_NAME,
    };
  },
};

/** Operations of the REST API, replaced by fakes in the tests. */
export type Api = typeof api;
