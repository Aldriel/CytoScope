import { useStore } from '../controllers/useStore';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';
import { channelsOf, fluorescenceOf, identityMatrix, withMatrixValue } from '../model/experiment';
import { COMPENSATION_SOURCES, type CompensationSource } from '../model/types';
import { HelpButton } from './common/HelpButton';
import { parseNumber, TextField } from './common/TextField';

interface Props {
  model: AppModel;
  onHelp: () => void;
}

/** Percentage shown in a cell of the matrix, without useless decimals. */
const percent = (value: number) => String(Math.round(value * 10_000) / 100);

/**
 * "Compensation" tab: where the spillover matrix comes from (the files, the single-stain
 * controls or the user), and the matrix itself, editable cell by cell.
 */
export function CompensationView({ model, onHelp }: Props) {
  const m = useMessages();
  const workspace = useStore(model.workspace);
  if (workspace === null || workspace.samples.length === 0) {
    return (
      <div className="tab-content">
        <div className="row">
          <p className="placeholder grow">{m.compensation.needsTubes}</p>
          <HelpButton onClick={onHelp} />
        </div>
      </div>
    );
  }
  const { experiment } = workspace;
  const matrix = experiment.matrix;
  const channels = channelsOf(workspace);
  const displayName = (name: string) => channels.find((c) => c.name === name)?.displayName ?? name;
  const controls = experiment.samples.filter((s) => s.role === 'SINGLE_STAIN').length;

  const choose = (source: CompensationSource) => {
    switch (source) {
      case 'NONE':
        return model.updateExperiment((e) => ({ ...e, compensation: 'NONE', matrix: null }));
      case 'FILE':
        return model.restoreFileMatrix();
      case 'CONTROLS':
        return model.computeMatrix();
      case 'EDITED':
        return model.updateExperiment((e) => ({
          ...e,
          compensation: 'EDITED',
          matrix: e.matrix ?? identityMatrix(fluorescenceOf(workspace).map((c) => c.name)),
        }));
    }
  };

  const edit = (row: number, column: number, text: string) => {
    const value = parseNumber(text);
    if (value !== null && value !== undefined) {
      void model.updateExperiment((e) => ({
        ...e,
        compensation: 'EDITED',
        matrix: e.matrix && withMatrixValue(e.matrix, row, column, value / 100),
      }));
    }
  };

  return (
    <div className="tab-content">
      <div className="row">
        <label htmlFor="compensation-source">{m.compensation.source}</label>
        <select
          id="compensation-source"
          value={experiment.compensation}
          onChange={(e) => void choose(e.target.value as CompensationSource)}
        >
          {COMPENSATION_SOURCES.map((source) => (
            <option key={source} value={source}>
              {m.source[source]}
            </option>
          ))}
        </select>
        <button type="button" disabled={controls === 0} onClick={() => void model.computeMatrix()}>
          {m.compensation.fromControls}
        </button>
        <span className="muted">{m.compensation.controls(controls)}</span>
        <span className="grow" />
        <HelpButton onClick={onHelp} />
      </div>

      {matrix === null ? (
        <p className="placeholder">{m.compensation.none}</p>
      ) : (
        <>
          <p className="muted">{m.compensation.caption}</p>
          <div className="table-scroll">
            <table className="matrix" aria-label={m.tab.compensation}>
              <thead>
                <tr>
                  <th />
                  {matrix.channels.map((c) => (
                    <th key={c} className="num">
                      {displayName(c)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {matrix.channels.map((from, i) => (
                  <tr key={from}>
                    <th>{displayName(from)}</th>
                    {matrix.channels.map((to, j) => (
                      <td key={to} className="num">
                        {i === j ? (
                          '100'
                        ) : (
                          <TextField
                            numeric
                            className={matrix.values[i][j] > 0.05 ? 'high' : undefined}
                            label={m.compensation.cell(displayName(from), displayName(to))}
                            value={percent(matrix.values[i][j])}
                            onCommit={(text) => edit(i, j, text)}
                          />
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
