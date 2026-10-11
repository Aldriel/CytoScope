import { saveFile } from '../controllers/navigator';
import { useStore } from '../controllers/useStore';
import { LOCALES, messagesOf, useMessages, type Locale } from '../i18n';
import type { AppModel } from '../model/AppModel';
import { HelpButton } from './common/HelpButton';

/** Time zone of the browser, such as "America/Toronto"; empty for the default of the server. */
export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || '';

interface Props {
  model: AppModel;
  options: ReportOptions;
  onChange: (changes: Partial<ReportOptions>) => void;
  onHelp: () => void;
}

/** Content of the report form, kept by the application for the Ctrl+S shortcut. */
export interface ReportOptions {
  title: string;
  analyst: string;
  notes: string;
  language: Locale;
}

/** Writes the report and has the browser save it. */
export async function downloadReport(model: AppModel, options: ReportOptions): Promise<void> {
  const file = await model.writeReport({ ...options, timeZone: browserTimeZone() });
  if (file !== null) {
    saveFile(file.content, file.fileName);
  }
}

/** "Report" tab: title, analyst, notes and language of the PDF report. */
export function ReportView({ model, options, onChange: change, onHelp }: Props) {
  const m = useMessages();
  const workspace = useStore(model.workspace);
  const busy = useStore(model.busy);
  const ready = (workspace?.samples.length ?? 0) > 0;

  return (
    <div className="tab-content">
      <div className="row">
        <p className="report-intro grow">{m.report.intro}</p>
        <HelpButton onClick={onHelp} />
      </div>
      <div className="report-form">
        <label htmlFor="report-title">{m.report.title}</label>
        <input
          id="report-title"
          value={options.title}
          placeholder={messagesOf(options.language).report.titlePrompt}
          onChange={(e) => change({ title: e.target.value })}
        />
        <label htmlFor="report-language">{m.report.language}</label>
        <select
          id="report-language"
          value={options.language}
          onChange={(e) => change({ language: e.target.value as Locale })}
        >
          {LOCALES.map((l) => (
            <option key={l} value={l}>
              {messagesOf(l).languageName}
            </option>
          ))}
        </select>
        <label htmlFor="report-analyst">{m.report.analyst}</label>
        <input
          id="report-analyst"
          value={options.analyst}
          placeholder={m.report.analystPrompt}
          onChange={(e) => change({ analyst: e.target.value })}
        />
        <label htmlFor="report-notes">{m.report.notes}</label>
        <textarea
          id="report-notes"
          rows={4}
          value={options.notes}
          placeholder={m.report.notesPrompt}
          onChange={(e) => change({ notes: e.target.value })}
        />
      </div>
      <div className="row">
        <button
          type="button"
          className="primary"
          disabled={!ready || busy}
          onClick={() => void downloadReport(model, options)}
        >
          {m.report.download}
        </button>
        {!ready && <span className="muted">{m.report.needsTubes}</span>}
      </div>
    </div>
  );
}
