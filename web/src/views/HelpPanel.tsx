import { useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useMessages, type Locale } from '../i18n';
import { guideOf, imageUrl } from '../help/guides';
import {
  fold,
  highlight,
  queryWords,
  search,
  spanText,
  type Block,
  type Span,
} from '../help/helpDocument';
import { isShortcut } from './keys';

/** Screenshots are taken at this scale and drawn smaller, so they stay sharp. */
const SCREENSHOT_SCALE = 1.5;
/** Table columns whose cells never exceed this length are not wrapped. */
const SHORT_COLUMN_CHARS = 40;

interface Props {
  locale: Locale;
  /** Section shown when the panel opens, and each time the user asks for another one. */
  request: { section: string; serial: number };
  onClose: () => void;
}

/**
 * User guide, docked on the right of the window like the help window of the desktop
 * application: a search field, the list of sections with the number of occurrences found, and
 * the selected section with the words searched highlighted.
 *
 * Ctrl+F (⌘F on a Mac) moves to the search field; Escape clears the search, then closes the
 * guide.
 */
export function HelpPanel({ locale, request, onClose }: Props) {
  const m = useMessages();
  const sections = guideOf(locale);
  const [query, setQuery] = useState('');
  const [chosen, setChosen] = useState(request.section);
  const searchField = useRef<HTMLInputElement>(null);
  const content = useRef<HTMLDivElement>(null);

  // A new request (F1, a "?" button) shows its section with an empty search.
  useEffect(() => {
    setQuery('');
    setChosen(request.section);
    searchField.current?.focus();
  }, [request]);

  const hits = useMemo(() => search(sections, query), [sections, query]);
  const words = useMemo(() => queryWords(query), [query]);
  const hit = hits.find((h) => h.section.id === chosen) ?? hits[0] ?? null;

  // Shows the top of the section, or the first word found.
  useLayoutEffect(() => {
    const pane = content.current;
    if (!pane) {
      return;
    }
    pane.scrollTop = 0;
    const first = pane.querySelector<HTMLElement>('mark, .help-code.highlight');
    if (first) {
      const offset = first.getBoundingClientRect().top - pane.getBoundingClientRect().top;
      pane.scrollTop = Math.max(0, offset - 60);
    }
  }, [hit, words]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (isShortcut(e) && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        searchField.current?.focus();
        searchField.current?.select();
      } else if (e.key === 'Escape' && !document.querySelector('.modal, .context-menu')) {
        if (searchField.current?.value) {
          setQuery('');
        } else {
          onClose();
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const occurrences = hits.reduce((total, h) => total + h.occurrences, 0);

  return (
    <aside className="help-panel" aria-label={m.help.title}>
      <div className="help-title-bar">
        <span>{m.help.title}</span>
        <button className="close-button" title={m.dialog.close} onClick={onClose}>
          ✕
        </button>
      </div>
      <div className="help-search-bar">
        <label htmlFor="help-search">{m.help.searchLabel}</label>
        <input
          id="help-search"
          ref={searchField}
          type="search"
          value={query}
          placeholder={m.help.searchPrompt}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button
          disabled={query === ''}
          onClick={() => {
            setQuery('');
            searchField.current?.focus();
          }}
        >
          {m.help.clear}
        </button>
        <span className="muted">
          {query.trim() === '' ? '' : m.help.results(hits.length, occurrences)}
        </span>
      </div>
      <div className="help-body">
        <ul className="help-sections" role="listbox" aria-label={m.help.title}>
          {hits.map((h) => (
            <li
              key={h.section.id}
              role="option"
              aria-selected={h === hit}
              className={h === hit ? 'selected' : undefined}
              onClick={() => setChosen(h.section.id)}
            >
              {h.occurrences > 0 ? `${h.section.title}  (${h.occurrences})` : h.section.title}
            </li>
          ))}
        </ul>
        <div className="help-content" ref={content}>
          {hit === null ? (
            <p className="help-section">{m.help.none(query.trim())}</p>
          ) : (
            <article className="help-section">
              <h1 className="help-title">{marked(hit.section.title, words)}</h1>
              {hit.section.blocks.map((block, index) => (
                <HelpBlock key={index} block={block} words={words} />
              ))}
            </article>
          )}
        </div>
      </div>
    </aside>
  );
}

/** Text with the words searched highlighted. */
function marked(text: string, words: string[]): ReactNode {
  return highlight(text, words).map((segment, index) =>
    segment.match ? <mark key={index}>{segment.text}</mark> : segment.text,
  );
}

/** Formatted text; links open in a new tab of the browser. */
function Spans({ spans, words }: { spans: Span[]; words: string[] }) {
  return (
    <>
      {spans.map((span, index) => {
        switch (span.style) {
          case 'LINK':
            return (
              <a key={index} href={span.url} target="_blank" rel="noopener noreferrer">
                {span.text}
              </a>
            );
          case 'BOLD':
            return <strong key={index}>{marked(span.text, words)}</strong>;
          case 'ITALIC':
            return <em key={index}>{marked(span.text, words)}</em>;
          case 'CODE':
            return <code key={index}>{marked(span.text, words)}</code>;
          default:
            return <span key={index}>{marked(span.text, words)}</span>;
        }
      })}
    </>
  );
}

function HelpBlock({ block, words }: { block: Block; words: string[] }) {
  switch (block.kind) {
    case 'heading':
      return block.level === 2 ? (
        <h2>
          <Spans spans={block.spans} words={words} />
        </h2>
      ) : (
        <h3>
          <Spans spans={block.spans} words={words} />
        </h3>
      );
    case 'paragraph':
      return (
        <p>
          <Spans spans={block.spans} words={words} />
        </p>
      );
    case 'note':
      return (
        <div className="help-note">
          <Spans spans={block.spans} words={words} />
        </div>
      );
    case 'list': {
      const items = block.items.map((item, index) => (
        <li key={index}>
          <Spans spans={item} words={words} />
        </li>
      ));
      return block.numbered ? <ol>{items}</ol> : <ul>{items}</ul>;
    }
    case 'image':
      return <HelpImage alt={block.alt} path={block.path} />;
    case 'table':
      return <HelpTable header={block.header} rows={block.rows} words={words} />;
    case 'code': {
      const found = words.some((w) => fold(block.text).includes(w));
      return <pre className={found ? 'help-code highlight' : 'help-code'}>{block.text}</pre>;
    }
  }
}

/** Screenshot of the guide, drawn at its natural size divided by the screenshot scale. */
function HelpImage({ alt, path }: { alt: string; path: string }) {
  const url = imageUrl(path);
  const [width, setWidth] = useState<number | undefined>(undefined);
  if (url === null) {
    return (
      <p>
        <em>[{alt}]</em>
      </p>
    );
  }
  return (
    <div className="help-image">
      <img
        src={url}
        alt={alt}
        width={width}
        onLoad={(e) => setWidth(Math.round(e.currentTarget.naturalWidth / SCREENSHOT_SCALE))}
      />
    </div>
  );
}

function HelpTable({
  header,
  rows,
  words,
}: {
  header: Span[][];
  rows: Span[][][];
  words: string[];
}) {
  // Short columns (numbers, names, file names) keep their text on one line.
  const nowrap = header.map(
    (cell, c) =>
      c < header.length - 1 &&
      [cell, ...rows.map((row) => row[c] ?? [])].every(
        (spans) => spanText(spans).length <= SHORT_COLUMN_CHARS,
      ),
  );
  return (
    <table className="help-table">
      <thead>
        <tr>
          {header.map((cell, c) => (
            <th key={c} className={nowrap[c] ? 'nowrap' : undefined}>
              <Spans spans={cell} words={words} />
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, r) => (
          <tr key={r}>
            {row.map((cell, c) => (
              <td key={c} className={nowrap[c] ? 'nowrap' : undefined}>
                <Spans spans={cell} words={words} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
