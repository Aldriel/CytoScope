import type { Messages } from '../i18n';
import { ApiError, type Api } from './api';
import { failure, fromError, info, READY, type StatusMessage } from './status';
import { Store } from './store';
import type {
  ApoptosisSettings,
  Examples,
  Experiment,
  Plot,
  PlotRequest,
  ReportFile,
  ReportRequest,
  Results,
  Workspace,
} from './types';

/** Lifetime of a workspace on the server, used before the server tells it. */
const DEFAULT_TIME_TO_LIVE = 30;

/** Extension of the files the application reads. */
const FCS_EXTENSION = /\.fcs$/i;

/** Results of the analyses, with the set-up they were computed from. */
export interface ComputedResults {
  experiment: Experiment;
  results: Results;
}

/**
 * Observable state of the web application, the MVC "Model" seen by the React views. The tubes
 * and the set-up live on the server, in a workspace kept in memory for a while after the last
 * request; the model holds a copy of the workspace, replaced by the answer to each request.
 * A change of the set-up is shown at once, then sent; if the server refuses it, the last state
 * it accepted comes back.
 */
export class AppModel {
  readonly status = new Store<StatusMessage>(READY);
  /** Whether a request is running. */
  readonly busy = new Store(false);
  /** Current workspace, or null before the first file or example. */
  readonly workspace = new Store<Workspace | null>(null);
  /** Example experiments, or null until downloaded. */
  readonly examples = new Store<Examples | null>(null);
  /** Last results, or null. */
  readonly results = new Store<ComputedResults | null>(null);

  /** Last state of the workspace accepted by the server. */
  private confirmed: Workspace | null = null;
  private runningTasks = 0;
  /** Number of the latest call of each operation. */
  private readonly calls = new Map<string, number>();

  /** @param api operations of the server */
  constructor(private readonly api: Api) {}

  // ----- Workspace -----

  /** Downloads the list of the examples, once. */
  async loadExamples(): Promise<void> {
    if (this.examples.get() !== null) {
      return;
    }
    try {
      this.examples.set(await this.api.examples());
    } catch (e) {
      this.status.set(fromError(e));
    }
  }

  /**
   * Opens an example experiment, already set up; the current workspace is closed.
   *
   * @param folder folder of the example on the server
   * @param name name of the example shown in the messages
   * @param language language of the names of the populations
   */
  openExample(folder: string, name: string, language: string): Promise<void> {
    return this.run('workspace', () => this.api.createWorkspace(folder, language), {
      progress: info((m) => m.status.opening(name)),
      onSuccess: (workspace) => {
        this.replace(workspace);
        this.status.set(info((m) => m.status.opened(name, workspace.samples.length)));
      },
    });
  }

  /**
   * Loads FCS files, one after the other, into the current workspace, or into a new one.
   *
   * @param language language of the names of the populations of a new workspace
   */
  loadFiles(files: readonly File[], language: string): Promise<void> {
    const wrong = files.find((file) => !FCS_EXTENSION.test(file.name));
    if (wrong) {
      this.status.set(failure((m) => m.error.notFcs(wrong.name)));
      return Promise.resolve();
    }
    if (files.length === 0) {
      return Promise.resolve();
    }
    return this.run(
      'workspace',
      async (progress) => {
        let workspace = this.confirmed ?? (await this.api.createWorkspace(null, language));
        for (const [index, file] of files.entries()) {
          progress(info((m) => m.status.loading(file.name, index + 1, files.length)));
          workspace = await this.api.upload(workspace.id, file);
        }
        return workspace;
      },
      {
        onSuccess: (workspace) => {
          this.replace(workspace);
          this.status.set(info((m) => m.status.loaded(files.length)));
        },
      },
    );
  }

  /** Removes a tube. */
  removeSample(sampleId: string): Promise<void> {
    const workspace = this.workspace.get();
    if (workspace === null) {
      return Promise.resolve();
    }
    const name = workspace.samples.find((s) => s.id === sampleId)?.name ?? sampleId;
    return this.run('workspace', () => this.api.removeSample(workspace.id, sampleId), {
      onSuccess: (updated) => {
        this.replace(updated);
        this.status.set(info((m) => m.status.removed(name)));
      },
    });
  }

  /** Closes the workspace: its files are forgotten by the server. */
  clear(): void {
    const workspace = this.confirmed;
    this.nextCall('workspace');
    this.nextCall('experiment');
    this.nextCall('results');
    this.replace(null);
    this.status.set(info((m) => m.status.cleared));
    if (workspace !== null) {
      this.api.deleteWorkspace(workspace.id).catch(() => undefined);
    }
  }

  // ----- Set-up -----

  /**
   * Changes the set-up of the experiment: the change is shown at once, then sent to the server.
   *
   * @param change new set-up, from the current one
   */
  updateExperiment(change: (experiment: Experiment) => Experiment): Promise<void> {
    const workspace = this.workspace.get();
    if (workspace === null) {
      return Promise.resolve();
    }
    const experiment = change(workspace.experiment);
    this.workspace.set({ ...workspace, experiment });
    return this.run('experiment', () => this.api.saveExperiment(workspace.id, experiment), {
      onSuccess: (updated) => {
        this.confirmed = updated;
        this.workspace.set(updated);
      },
      onFailure: () => this.workspace.set(this.confirmed),
    });
  }

  /** Computes the spillover matrix from the single-stain controls. */
  computeMatrix(): Promise<void> {
    return this.workspaceAction(
      (id) => this.api.matrixFromControls(id),
      (m) => m.status.matrix,
    );
  }

  /** Uses the spillover matrix stored in the files again. */
  restoreFileMatrix(): Promise<void> {
    return this.workspaceAction(
      (id) => this.api.matrixFromFile(id),
      (m) => m.status.fileMatrix,
    );
  }

  /** Proposes the thresholds of the annexin V / PI test from the controls. */
  suggestThresholds(settings: ApoptosisSettings): Promise<void> {
    return this.workspaceAction(
      (id) => this.api.suggestThresholds(id, settings),
      (m) => m.status.thresholds,
    );
  }

  // ----- Plots and results -----

  /**
   * Counts the events of a plot. Failures are reported in the status bar.
   *
   * @returns the plot, or null without workspace or after a failure
   */
  async plot(request: PlotRequest): Promise<Plot | null> {
    const workspace = this.workspace.get();
    if (workspace === null) {
      return null;
    }
    try {
      return await this.api.plot(workspace.id, request);
    } catch (e) {
      this.fail(e);
      return null;
    }
  }

  /** Runs every analysis of the current set-up. */
  refreshResults(): Promise<void> {
    const workspace = this.workspace.get();
    if (workspace === null) {
      return Promise.resolve();
    }
    const experiment = workspace.experiment;
    return this.run('results', () => this.api.results(workspace.id), {
      progress: info((m) => m.status.analyzing),
      onSuccess: (results) => {
        this.results.set({ experiment, results });
        this.status.set(info((m) => m.status.analyzed));
      },
    });
  }

  /** Address of the FCS file of a population of a tube, or null without workspace. */
  exportUrl(sampleId: string, populationId: string): string | null {
    const workspace = this.workspace.get();
    return workspace === null ? null : this.api.exportUrl(workspace.id, sampleId, populationId);
  }

  /**
   * Has the server write the PDF report.
   *
   * @returns the file, or null without tubes or after a failure
   */
  async writeReport(request: ReportRequest): Promise<ReportFile | null> {
    const workspace = this.workspace.get();
    if (workspace === null || workspace.samples.length === 0) {
      this.status.set(failure((m) => m.error.noTubes));
      return null;
    }
    let file: ReportFile | null = null;
    await this.run('report', () => this.api.report(workspace.id, request), {
      progress: info((m) => m.status.writingReport),
      onSuccess: (report) => {
        file = report;
        this.status.set(info((m) => m.status.reportSaved(report.fileName)));
      },
    });
    return file;
  }

  // ----- Shared -----

  /** Runs a request whose answer is the updated workspace. */
  private workspaceAction(
    work: (id: string) => Promise<Workspace>,
    done: (m: Messages) => string,
  ): Promise<void> {
    const workspace = this.workspace.get();
    if (workspace === null) {
      return Promise.resolve();
    }
    return this.run('experiment', () => work(workspace.id), {
      onSuccess: (updated) => {
        this.confirmed = updated;
        this.workspace.set(updated);
        this.status.set(info(done));
      },
    });
  }

  /** Shows another workspace, or none, and forgets the results of the previous one. */
  private replace(workspace: Workspace | null) {
    this.confirmed = workspace;
    this.workspace.set(workspace);
    this.results.set(null);
  }

  /**
   * Reports a failure. When the server has forgotten the workspace, the model forgets it too, so
   * that the next file opens a new one.
   */
  private fail(e: unknown) {
    if (e instanceof ApiError && e.kind === 'EXPIRED') {
      const minutes = this.confirmed?.timeToLiveMinutes ?? DEFAULT_TIME_TO_LIVE;
      this.replace(null);
      this.status.set(failure((m) => m.status.expired(minutes)));
    } else {
      this.status.set(fromError(e));
    }
  }

  /**
   * Runs a request: shows its progress, counts it as running, then publishes its answer if no
   * newer call of the same operation has started meanwhile.
   */
  private async run<T>(
    operation: string,
    work: (progress: (message: StatusMessage) => void) => Promise<T>,
    handlers: {
      progress?: StatusMessage;
      onSuccess: (result: T) => void;
      onFailure?: () => void;
    },
  ): Promise<void> {
    const call = this.nextCall(operation);
    this.runningTasks++;
    this.busy.set(true);
    if (handlers.progress) {
      this.status.set(handlers.progress);
    }
    try {
      const result = await work((message) => this.status.set(message));
      if (this.calls.get(operation) === call) {
        handlers.onSuccess(result);
      }
    } catch (e) {
      if (this.calls.get(operation) === call) {
        handlers.onFailure?.();
        this.fail(e);
      }
    } finally {
      this.runningTasks--;
      this.busy.set(this.runningTasks > 0);
    }
  }

  private nextCall(operation: string): number {
    const call = (this.calls.get(operation) ?? 0) + 1;
    this.calls.set(operation, call);
    return call;
  }
}
