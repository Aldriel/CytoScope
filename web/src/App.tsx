import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { DATASET_URL, HELP_SECTIONS, TAB_IDS, type TabId } from './controllers/navigator';
import { useGatingController } from './controllers/useGatingController';
import { useStore } from './controllers/useStore';
import { LOCALES, messagesOf, MessagesContext } from './i18n';
import { api as defaultApi, type Api } from './model/api';
import { AppModel } from './model/AppModel';
import { PRESET_FONT_SIZES, UiSettings, type SettingsStorage } from './model/settings';
import { AnalysisView } from './views/AnalysisView';
import { CompensationView } from './views/CompensationView';
import { AboutDialog, ShortcutsDialog } from './views/Dialogs';
import { Footer } from './views/Footer';
import { GatingView } from './views/GatingView';
import { HelpPanel } from './views/HelpPanel';
import { altShortcut, isShortcut, shortcut } from './views/keys';
import { MenuBar, type Menu } from './views/MenuBar';
import { downloadReport, ReportView, type ReportOptions } from './views/ReportView';
import { ResultsView } from './views/ResultsView';
import { StatusBar } from './views/StatusBar';
import { TubesView } from './views/TubesView';

const FCS_STANDARD = 'https://isac-net.org/page/Data-Standards';
const openUrl = (url: string) => window.open(url, '_blank', 'noopener,noreferrer');

interface Props {
  /** Operations of the server; replaced by a fake in the tests. */
  api?: Api;
  /** Where the settings are saved; localStorage by default. */
  storage?: SettingsStorage;
  /** Preferred languages of the browser. */
  languages?: readonly string[];
}

/**
 * Root of the web application: menu bar, one tab per step of the analysis, status bar and
 * footer, with the user guide docked on the right when it is open. Inactive tabs are hidden
 * rather than unmounted, so they keep their state.
 */
export default function App({
  api = defaultApi,
  storage = window.localStorage,
  languages = window.navigator.languages,
}: Props) {
  const [settings] = useState(() => new UiSettings(storage, languages));
  const [model] = useState(() => new AppModel(api));
  const locale = useStore(settings.locale);
  const fontSize = useStore(settings.fontSize);
  const examples = useStore(model.examples);
  const m = messagesOf(locale);

  const [active, setActive] = useState<TabId>('tubes');
  const [help, setHelp] = useState<{ section: string; serial: number } | null>(null);
  const [dialog, setDialog] = useState<'about' | 'shortcuts' | null>(null);
  const [report, setReport] = useState<ReportOptions>({
    title: '',
    analyst: '',
    notes: '',
    language: locale,
  });
  const fileInput = useRef<HTMLInputElement>(null);
  const gating = useGatingController(model, active === 'gating');

  const showHelp = useCallback(
    (section: string) => setHelp((current) => ({ section, serial: (current?.serial ?? 0) + 1 })),
    [],
  );
  const closeHelp = useCallback(() => setHelp(null), []);

  useEffect(() => {
    void model.loadExamples();
  }, [model]);
  // The text size is the font size of the page: every other size is in em or rem.
  useLayoutEffect(() => {
    document.documentElement.style.fontSize = `${fontSize}px`;
  }, [fontSize]);
  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const chooseFiles = () => fileInput.current?.click();
  const loadFiles = (files: File[]) => {
    setActive('tubes');
    void model.loadFiles(files, locale);
  };
  const openExample = (folder: string) => {
    setActive('tubes');
    void model.openExample(folder, m.example[folder] ?? folder, locale);
  };
  const newExperiment = () => {
    setActive('tubes');
    model.clear();
  };
  const saveReport = () => {
    setActive('report');
    void downloadReport(model, report);
  };

  // Shortcuts of the window; Alt+1…6 replaces Ctrl+1…6, which browsers keep for their tabs.
  const keyActions = useRef<(e: KeyboardEvent) => void>(() => undefined);
  keyActions.current = (e: KeyboardEvent) => {
    if (document.querySelector('.modal')) {
      return;
    }
    const tabIndex = ['Digit1', 'Digit2', 'Digit3', 'Digit4', 'Digit5', 'Digit6'].indexOf(e.code);
    const key = e.key.toLowerCase();
    let action: (() => void) | null = null;
    if (e.key === 'F1') {
      action = () => showHelp(HELP_SECTIONS[active]);
    } else if (e.altKey && !e.ctrlKey && !e.metaKey && tabIndex >= 0) {
      action = () => setActive(TAB_IDS[tabIndex]);
    } else if (isShortcut(e)) {
      if (key === 'o') {
        action = chooseFiles;
      } else if (key === 's') {
        action = saveReport;
      } else if (e.key === '=' || e.key === '+' || e.code === 'NumpadAdd') {
        action = () => settings.increaseFontSize();
      } else if (e.key === '-' || e.code === 'NumpadSubtract') {
        action = () => settings.decreaseFontSize();
      } else if (e.key === '0' || e.code === 'Numpad0') {
        action = () => settings.resetFontSize();
      }
    }
    if (action) {
      e.preventDefault();
      action();
    }
  };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => keyActions.current(e);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const menus: Menu[] = [
    {
      label: m.menu.file,
      items: [
        { label: m.menu.newExperiment, action: newExperiment },
        { label: m.menu.open, shortcut: shortcut('O'), action: chooseFiles },
        {
          label: m.menu.examples,
          items: (examples?.examples ?? []).map((example) => ({
            label: m.example[example.folder] ?? example.folder,
            action: () => openExample(example.folder),
          })),
        },
        'separator',
        { label: m.menu.report, shortcut: shortcut('S'), action: saveReport },
      ],
    },
    {
      label: m.menu.options,
      items: [
        {
          label: m.menu.language,
          items: LOCALES.map((l) => ({
            label: messagesOf(l).languageName,
            checked: l === locale,
            action: () => settings.setLocale(l),
          })),
        },
        {
          label: m.menu.textSize,
          items: [
            ...PRESET_FONT_SIZES.map((size, index) => ({
              label: m.menu.textSizes[index],
              checked: size === fontSize,
              action: () => settings.setFontSize(size),
            })),
            'separator' as const,
            {
              label: m.menu.zoomIn,
              shortcut: shortcut('='),
              action: () => settings.increaseFontSize(),
            },
            {
              label: m.menu.zoomOut,
              shortcut: shortcut('−'),
              action: () => settings.decreaseFontSize(),
            },
            {
              label: m.menu.zoomReset,
              shortcut: shortcut('0'),
              action: () => settings.resetFontSize(),
            },
          ],
        },
      ],
    },
    {
      label: m.menu.help,
      items: [
        { label: m.menu.guide, shortcut: 'F1', action: () => showHelp(HELP_SECTIONS[active]) },
        { label: m.menu.shortcuts, action: () => setDialog('shortcuts') },
        'separator',
        { label: m.menu.fcsStandard, action: () => openUrl(FCS_STANDARD) },
        { label: m.menu.dataset, action: () => openUrl(DATASET_URL) },
        'separator',
        { label: m.menu.about, action: () => setDialog('about') },
      ],
    },
  ];

  const helpOf = (tab: TabId) => () => showHelp(HELP_SECTIONS[tab]);

  return (
    <MessagesContext.Provider value={m}>
      <div className="app">
        <MenuBar menus={menus} />
        <input
          ref={fileInput}
          type="file"
          accept=".fcs"
          multiple
          hidden
          data-testid="file-input"
          onChange={(e) => {
            loadFiles(Array.from(e.target.files ?? []));
            e.target.value = '';
          }}
        />
        <div className="app-body">
          <main className="app-main">
            <div className="tab-bar" role="tablist">
              {TAB_IDS.map((id, index) => (
                <button
                  key={id}
                  role="tab"
                  aria-selected={id === active}
                  className={id === active ? 'tab active' : 'tab'}
                  title={altShortcut(String(index + 1))}
                  onClick={() => setActive(id)}
                >
                  {m.tab[id]}
                </button>
              ))}
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'tubes'}>
              <TubesView
                model={model}
                onOpen={chooseFiles}
                onFiles={loadFiles}
                onExample={openExample}
                onHelp={helpOf('tubes')}
              />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'compensation'}>
              <CompensationView model={model} onHelp={helpOf('compensation')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'gating'}>
              <GatingView ctl={gating} onHelp={helpOf('gating')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'analysis'}>
              <AnalysisView
                model={model}
                active={active === 'analysis'}
                onHelp={helpOf('analysis')}
              />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'results'}>
              <ResultsView model={model} active={active === 'results'} onHelp={helpOf('results')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'report'}>
              <ReportView
                model={model}
                options={report}
                onChange={(changes) => setReport((r) => ({ ...r, ...changes }))}
                onHelp={helpOf('report')}
              />
            </div>
          </main>
          {help && <HelpPanel locale={locale} request={help} onClose={closeHelp} />}
        </div>
        <StatusBar model={model} />
        <Footer language={locale} />
      </div>
      {dialog === 'about' && <AboutDialog onClose={() => setDialog(null)} />}
      {dialog === 'shortcuts' && <ShortcutsDialog onClose={() => setDialog(null)} />}
    </MessagesContext.Provider>
  );
}
