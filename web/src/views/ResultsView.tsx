import { useResults } from '../controllers/useResults';
import { useStore } from '../controllers/useStore';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';
import { metricLabel } from '../model/experiment';
import { CELL_CYCLE_PHASES, CELL_FATES } from '../model/types';
import { BarChart } from './charts/BarChart';
import { DoseCurve } from './charts/DoseCurve';
import { HelpButton } from './common/HelpButton';

interface Props {
  model: AppModel;
  /** Whether the tab is shown; the results are computed again only then. */
  active: boolean;
  onHelp: () => void;
}

/**
 * "Results" tab: the values of each tube, the comparison of the groups (ANOVA and Dunnett's
 * test) and the dose-response curve.
 */
export function ResultsView({ model, active, onHelp }: Props) {
  const m = useMessages();
  const workspace = useStore(model.workspace);
  const busy = useStore(model.busy);
  const { results, fresh } = useResults(model, active);

  if (workspace === null || workspace.samples.length === 0) {
    return (
      <div className="tab-content">
        <div className="row">
          <p className="placeholder grow">{m.results.needsTubes}</p>
          <HelpButton onClick={onHelp} />
        </div>
      </div>
    );
  }
  const { experiment } = workspace;
  const header = (
    <div className="row">
      <button type="button" disabled={busy} onClick={() => void model.refreshResults()}>
        {m.results.refresh}
      </button>
      {!fresh && <span className="muted">{m.results.computing}</span>}
      <span className="grow" />
      <HelpButton onClick={onHelp} />
    </div>
  );
  if (results === null) {
    return <div className="tab-content">{header}</div>;
  }

  const showFates = results.samples.some((s) => s.apoptosis !== null);
  const showCycle = experiment.cellCycle !== null;
  const metric = experiment.metric ? metricLabel(experiment, experiment.metric, m) : '';
  const info = new Map(experiment.samples.map((s) => [s.id, s]));
  const { comparison, doseResponse: curve } = results;

  return (
    <div className={fresh ? 'tab-content results' : 'tab-content results stale'}>
      {header}

      <h3>{m.results.perTube}</h3>
      <div className="table-scroll">
        <table className="results-table" aria-label={m.results.perTube}>
          <thead>
            <tr>
              <th>{m.column.tube}</th>
              <th>{m.column.group}</th>
              <th className="num">{m.column.dose}</th>
              <th className="num">{m.column.events}</th>
              {showFates &&
                CELL_FATES.map((f) => (
                  <th key={f} className="num">
                    {m.fate[f]}
                  </th>
                ))}
              {showCycle &&
                CELL_CYCLE_PHASES.slice(0, 4).map((p) => (
                  <th key={p} className="num">
                    {m.phase[p]}
                  </th>
                ))}
              {showCycle && <th className="num">{m.results.cv}</th>}
            </tr>
          </thead>
          <tbody>
            {results.samples.map((s) => {
              const sample = info.get(s.id);
              return (
                <tr key={s.id}>
                  <td>{sample?.name ?? s.id}</td>
                  <td>
                    {sample?.role === 'SAMPLE' ? sample.group : m.role[sample?.role ?? 'SAMPLE']}
                  </td>
                  <td className="num">{m.number(sample?.dose ?? null, 2)}</td>
                  <td className="num">{m.count(s.eventCount)}</td>
                  {showFates &&
                    CELL_FATES.map((f) => (
                      <td key={f} className="num">
                        {m.percent(s.apoptosis?.[f] ?? null)}
                      </td>
                    ))}
                  {showCycle &&
                    CELL_CYCLE_PHASES.slice(0, 4).map((p) => (
                      <td key={p} className="num">
                        {m.percent(s.cellCycle?.percents[p] ?? null)}
                      </td>
                    ))}
                  {showCycle && (
                    <td className="num">
                      {s.cellCycle ? m.percent(s.cellCycle.g1Cv) : m.results.noPeak}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <h3>{m.results.comparison}</h3>
      {comparison === null ? (
        <p className="placeholder">
          {m.results.noComparison(m.issue[results.comparisonIssue ?? 'NO_METRIC'])}
        </p>
      ) : (
        <>
          <p>
            <strong>{metric}</strong> ·{' '}
            {m.results.anova(
              m.number(comparison.anova.f, 2),
              comparison.anova.groupCount - 1,
              comparison.anova.totalCount - comparison.anova.groupCount,
              m.pValue(comparison.anova.pValue),
            )}
          </p>
          <div className="result-pair">
            <table className="results-table" aria-label={m.results.comparison}>
              <thead>
                <tr>
                  <th>{m.column.group}</th>
                  <th className="num">{m.column.replicates}</th>
                  <th className="num">{m.column.meanSd}</th>
                  <th className="num">{m.column.difference}</th>
                  <th className="num">{m.column.pAdjusted}</th>
                  <th>{m.column.significance}</th>
                </tr>
              </thead>
              <tbody>
                {comparison.groups.map((g) => (
                  <tr key={g.name}>
                    <td>
                      {g.name}
                      {g.name === comparison.controlGroup && (
                        <span className="muted"> ({m.results.control})</span>
                      )}
                    </td>
                    <td className="num">{g.values.length}</td>
                    <td className="num">
                      {m.number(g.mean, 1)} ± {m.number(g.standardDeviation, 1)}
                    </td>
                    <td className="num">{m.number(g.difference, 1)}</td>
                    <td className="num">
                      {g.adjustedP === null ? '—' : m.pValue(g.adjustedP).replace(/^= /, '')}
                    </td>
                    <td className="stars">{g.stars}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <BarChart
              groups={comparison.groups}
              controlGroup={comparison.controlGroup}
              label={metric}
            />
          </div>
          <p className="muted small">{m.results.stars(comparison.controlGroup)}</p>
        </>
      )}

      <h3>{m.results.doseResponse}</h3>
      {curve === null ? (
        <p className="placeholder">
          {m.results.noCurve(m.issue[results.doseResponseIssue ?? 'TOO_FEW_DOSES'])}
        </p>
      ) : (
        <div className="result-pair">
          <table className="results-table" aria-label={m.results.doseResponse}>
            <thead>
              <tr>
                <th>{m.column.parameter}</th>
                <th className="num">{m.column.value}</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>{m.results.fit.ec50}</td>
                <td className="num">
                  {m.number(curve.fit.ec50, 2)}
                  {curve.fit.ec50Low !== null &&
                    ` [${m.number(curve.fit.ec50Low, 2)} – ${m.number(curve.fit.ec50High, 2)}]`}
                </td>
              </tr>
              <tr>
                <td>{m.results.fit.bottom}</td>
                <td className="num">{m.number(curve.fit.bottom, 1)}</td>
              </tr>
              <tr>
                <td>{m.results.fit.top}</td>
                <td className="num">{m.number(curve.fit.top, 1)}</td>
              </tr>
              <tr>
                <td>{m.results.fit.hill}</td>
                <td className="num">{m.number(curve.fit.hill, 2)}</td>
              </tr>
              <tr>
                <td>{m.results.fit.rSquared}</td>
                <td className="num">{m.number(curve.fit.rSquared, 3)}</td>
              </tr>
              <tr>
                <td>{m.results.fit.points}</td>
                <td className="num">{curve.fit.pointCount}</td>
              </tr>
            </tbody>
          </table>
          <DoseCurve curve={curve} label={metric} />
        </div>
      )}
    </div>
  );
}
