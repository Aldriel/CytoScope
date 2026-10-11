import { useState, type DragEvent } from 'react';
import { DATASET_URL } from '../controllers/navigator';
import { useStore } from '../controllers/useStore';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';
import { fluorescenceOf, groupsOf } from '../model/experiment';
import { ROLES, type SampleInfo } from '../model/types';
import { HelpButton } from './common/HelpButton';
import { parseNumber, TextField } from './common/TextField';

interface Props {
  model: AppModel;
  /** Opens the file chooser. */
  onOpen: () => void;
  /** Loads files dropped on the tab. */
  onFiles: (files: File[]) => void;
  onExample: (folder: string) => void;
  onHelp: () => void;
}

/**
 * "Tubes" tab: loading of the FCS files or of an example, then the role, group and dose of each
 * tube, which the server guesses from the file names.
 */
export function TubesView({ model, onOpen, onFiles, onExample, onHelp }: Props) {
  const m = useMessages();
  const workspace = useStore(model.workspace);
  const examples = useStore(model.examples);
  const [dragging, setDragging] = useState(false);
  const experiment = workspace?.experiment ?? null;
  const fluorescence = fluorescenceOf(workspace);

  const change = (id: string, changes: Partial<SampleInfo>) =>
    void model.updateExperiment((e) => ({
      ...e,
      samples: e.samples.map((s) => (s.id === id ? { ...s, ...changes } : s)),
    }));

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    onFiles(Array.from(e.dataTransfer.files));
  };

  return (
    <div
      className={dragging ? 'tab-content dropping' : 'tab-content'}
      onDragOver={(e) => {
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <div className="row">
        <button type="button" className="primary" onClick={onOpen}>
          {m.tubes.open}
        </button>
        <span className="muted">{m.tubes.examples}</span>
        {examples?.examples.map((example) => (
          <button key={example.folder} type="button" onClick={() => onExample(example.folder)}>
            {m.example[example.folder] ?? example.folder}
          </button>
        ))}
        <span className="grow" />
        <HelpButton onClick={onHelp} />
      </div>

      {workspace?.example && (
        <p className={workspace.simulated ? 'banner simulated' : 'banner'}>
          {workspace.simulated ? (
            m.tubes.simulated
          ) : (
            <>
              {m.tubes.real}{' '}
              <a href={DATASET_URL} target="_blank" rel="noreferrer">
                {m.tubes.dataset}
              </a>
            </>
          )}
        </p>
      )}

      {experiment === null || experiment.samples.length === 0 ? (
        <div className="drop-zone">{m.tubes.drop}</div>
      ) : (
        <>
          <p className="muted">
            {m.tubes.summary(experiment.samples.length, groupsOf(experiment).length)}
          </p>
          <div className="table-scroll">
            <table className="tubes" aria-label={m.tab.tubes}>
              <thead>
                <tr>
                  <th>{m.column.tube}</th>
                  <th className="num">{m.column.events}</th>
                  <th title={m.tip.role}>{m.column.role}</th>
                  <th title={m.tip.group}>{m.column.group}</th>
                  <th title={m.tip.dose}>{m.column.dose}</th>
                  <th>{m.column.stain}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {experiment.samples.map((sample) => {
                  const view = workspace?.samples.find((s) => s.id === sample.id);
                  return (
                    <tr key={sample.id}>
                      <td>{sample.name}</td>
                      <td className="num">{m.count(view?.eventCount ?? 0)}</td>
                      <td>
                        <select
                          aria-label={`${m.column.role} ${sample.name}`}
                          value={sample.role}
                          onChange={(e) =>
                            change(sample.id, { role: e.target.value as SampleInfo['role'] })
                          }
                        >
                          {ROLES.map((role) => (
                            <option key={role} value={role}>
                              {m.role[role]}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <TextField
                          label={`${m.column.group} ${sample.name}`}
                          value={sample.group}
                          onCommit={(group) => change(sample.id, { group: group.trim() })}
                        />
                      </td>
                      <td>
                        <TextField
                          numeric
                          className="dose"
                          label={`${m.column.dose} ${sample.name}`}
                          value={sample.dose === null ? '' : String(sample.dose)}
                          onCommit={(text) => {
                            const dose = parseNumber(text);
                            if (dose !== undefined && (dose === null || dose >= 0)) {
                              change(sample.id, { dose });
                            }
                          }}
                        />
                      </td>
                      <td>
                        {sample.role === 'SINGLE_STAIN' ? (
                          <select
                            aria-label={`${m.column.stain} ${sample.name}`}
                            value={sample.stainChannel}
                            onChange={(e) => change(sample.id, { stainChannel: e.target.value })}
                          >
                            <option value="">{m.tubes.noStain}</option>
                            {fluorescence.map((c) => (
                              <option key={c.name} value={c.name}>
                                {c.displayName}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <span className="muted">{m.tubes.noStain}</span>
                        )}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="link"
                          aria-label={m.tubes.removeTube(sample.name)}
                          onClick={() => void model.removeSample(sample.id)}
                        >
                          {m.tubes.remove}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
      <p className="muted small">{m.tubes.privacy(workspace?.timeToLiveMinutes ?? 30)}</p>
    </div>
  );
}
