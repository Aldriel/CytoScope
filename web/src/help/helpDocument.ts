/**
 * User guide parsed from a Markdown file into sections, one per level-1 heading: the parser of
 * the desktop application (HelpDocument), so both guides are written the same way.
 *
 * Supported syntax: #/##/### headings, paragraphs, "-" and "1." lists, ">" notes, pipe tables,
 * fenced code blocks, images alone on their line, and inline **bold**, *italic*, `code` and
 * [links](url). A comment <!-- section: id --> before a level-1 heading gives the section a stable
 * identifier, used to open the guide on the section of a tab.
 */

/** Run of text with a single inline style; `url` is the target of a link. */
export interface Span {
  text: string;
  style: 'PLAIN' | 'BOLD' | 'ITALIC' | 'CODE' | 'LINK';
  url?: string;
}

/** Block-level element of a help section. */
export type Block =
  | { kind: 'heading'; level: number; spans: Span[] }
  | { kind: 'paragraph'; spans: Span[] }
  | { kind: 'note'; spans: Span[] }
  | { kind: 'list'; numbered: boolean; items: Span[][] }
  | { kind: 'image'; alt: string; path: string }
  | { kind: 'table'; header: Span[][]; rows: Span[][][] }
  | { kind: 'code'; text: string };

/** Section of the guide, introduced by a level-1 heading. */
export interface HelpSection {
  id: string;
  title: string;
  blocks: Block[];
}

/** A section matching a search, with the number of occurrences of the query words. */
export interface SearchHit {
  section: HelpSection;
  occurrences: number;
}

const SECTION_ID = /^<!--\s*section:\s*([\w-]+)\s*-->$/;
const HEADING = /^(#{1,3})\s+(.+)$/;
const IMAGE = /^!\[([^\]]*)]\(([^)]+)\)$/;
const BULLET = /^[-*]\s+(.+)$/;
const NUMBERED = /^\d+\.\s+(.+)$/;
const TABLE_SEPARATOR = /^\|[\s|:-]+\|?$/;
const INLINE = /\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)|\*([^*\s][^*]*?)\*/g;

/** Concatenates the text of formatted spans. */
export const spanText = (spans: Span[]) => spans.map((s) => s.text).join('');

/** Returns the text of a block without markup, used by the search. */
function blockText(block: Block): string {
  switch (block.kind) {
    case 'list':
      return block.items.map(spanText).join('\n');
    case 'image':
      return block.alt;
    case 'table':
      return [block.header, ...block.rows].map((row) => row.map(spanText).join(' ')).join('\n');
    case 'code':
      return block.text;
    default:
      return spanText(block.spans);
  }
}

/** Returns the title and text of a section without markup. */
export const sectionText = (section: HelpSection) =>
  section.title + '\n' + section.blocks.map(blockText).join('\n');

/**
 * Lower-cases a text and removes accents character by character, so the result has the same
 * length as the input and positions found in it are valid in the original text.
 */
export function fold(text: string): string {
  let folded = '';
  for (let i = 0; i < text.length; i++) {
    const c = text.charAt(i);
    folded += c.charCodeAt(0) < 128 ? c.toLowerCase() : c.normalize('NFD').charAt(0).toLowerCase();
  }
  return folded;
}

/** Returns the distinct words of a query, folded with {@link fold}. */
export function queryWords(query: string): string[] {
  if (query.trim() === '') {
    return [];
  }
  return [...new Set(fold(query.trim()).split(/\s+/))].filter((w) => w !== '');
}

function countOccurrences(text: string, word: string): number {
  let count = 0;
  for (let i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + word.length)) {
    count++;
  }
  return count;
}

/**
 * Searches the guide. Every word of the query must appear in a section for it to match; the
 * comparison ignores case and accents.
 *
 * @returns matching sections in document order with their number of occurrences; every section
 *     (with a count of zero) when the query is blank
 */
export function search(sections: HelpSection[], query: string): SearchHit[] {
  const words = queryWords(query);
  const hits: SearchHit[] = [];
  for (const section of sections) {
    const text = fold(sectionText(section));
    let total = 0;
    let all = true;
    for (const word of words) {
      const count = countOccurrences(text, word);
      if (count === 0) {
        all = false;
        break;
      }
      total += count;
    }
    if (all) {
      hits.push({ section, occurrences: total });
    }
  }
  return hits;
}

/** Splits a text into runs that match or do not match the folded search words. */
export function highlight(text: string, words: string[]): { text: string; match: boolean }[] {
  if (words.length === 0 || text === '') {
    return [{ text, match: false }];
  }
  const folded = fold(text);
  const marked = new Array<boolean>(text.length).fill(false);
  for (const word of words) {
    for (let i = folded.indexOf(word); i >= 0; i = folded.indexOf(word, i + word.length)) {
      marked.fill(true, i, i + word.length);
    }
  }
  const segments: { text: string; match: boolean }[] = [];
  let start = 0;
  for (let i = 1; i <= text.length; i++) {
    if (i === text.length || marked[i] !== marked[start]) {
      segments.push({ text: text.slice(start, i), match: marked[start] });
      start = i;
    }
  }
  return segments;
}

/** Splits a line of text into styled spans. */
export function inline(text: string): Span[] {
  const spans: Span[] = [];
  let last = 0;
  for (const m of text.matchAll(INLINE)) {
    const start = m.index ?? 0;
    if (start > last) {
      spans.push({ text: text.slice(last, start), style: 'PLAIN' });
    }
    if (m[1] !== undefined) {
      spans.push({ text: m[1], style: 'BOLD' });
    } else if (m[2] !== undefined) {
      spans.push({ text: m[2], style: 'CODE' });
    } else if (m[3] !== undefined) {
      spans.push({ text: m[3], style: 'LINK', url: m[4] });
    } else {
      spans.push({ text: m[5], style: 'ITALIC' });
    }
    last = start + m[0].length;
  }
  if (last < text.length) {
    spans.push({ text: text.slice(last), style: 'PLAIN' });
  }
  return spans;
}

const slug = (text: string) =>
  fold(text)
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');

const append = (current: string, text: string) =>
  current !== '' && text !== '' ? `${current} ${text}` : current + text;

/** Parses a guide written in the supported Markdown subset. */
export function parseGuide(markdown: string): HelpSection[] {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const sections: HelpSection[] = [];
  let index = 0;
  let pendingId: string | null = null;
  let current: HelpSection | null = null;

  const code = (): Block => {
    index++;
    const body: string[] = [];
    while (index < lines.length && !lines[index].trim().startsWith('```')) {
      body.push(lines[index]);
      index++;
    }
    index++; // closing fence
    return { kind: 'code', text: body.join('\n') };
  };

  const table = (): Block => {
    const rows: Span[][][] = [];
    while (index < lines.length && lines[index].trim().startsWith('|')) {
      const row = lines[index].trim();
      index++;
      if (TABLE_SEPARATOR.test(row)) {
        continue; // separator line under the header
      }
      const inner = row.slice(1, row.endsWith('|') ? -1 : undefined);
      rows.push(inner.split('|').map((cell) => inline(cell.trim())));
    }
    return { kind: 'table', header: rows[0], rows: rows.slice(1) };
  };

  const note = (): string => {
    let text = '';
    while (index < lines.length && lines[index].trim().startsWith('>')) {
      text = append(text, lines[index].trim().slice(1).trim());
      index++;
    }
    return text;
  };

  const items = (marker: RegExp): Span[][] => {
    const result: Span[][] = [];
    for (;;) {
      const m = index < lines.length ? marker.exec(lines[index].trim()) : null;
      if (m === null) {
        return result;
      }
      let text = m[1].trim();
      index++;
      // Indented lines continue the current item.
      while (
        index < lines.length &&
        lines[index].startsWith('  ') &&
        lines[index].trim() !== '' &&
        !BULLET.test(lines[index].trim()) &&
        !NUMBERED.test(lines[index].trim())
      ) {
        text = append(text, lines[index].trim());
        index++;
      }
      result.push(inline(text));
    }
  };

  const paragraph = (): string => {
    let text = lines[index].trim();
    index++;
    while (index < lines.length) {
      const line = lines[index].trim();
      if (
        line === '' ||
        ['#', '|', '>', '```', '<!--'].some((prefix) => line.startsWith(prefix)) ||
        IMAGE.test(line) ||
        BULLET.test(line) ||
        NUMBERED.test(line)
      ) {
        break;
      }
      text = append(text, line);
      index++;
    }
    return text;
  };

  while (index < lines.length) {
    const line = lines[index].trim();
    const sectionId = SECTION_ID.exec(line);
    const heading = HEADING.exec(line);
    const image = IMAGE.exec(line);
    if (sectionId) {
      pendingId = sectionId[1];
      index++;
    } else if (heading && heading[1].length === 1) {
      const title = heading[2].trim();
      current = { id: pendingId ?? slug(title), title, blocks: [] };
      sections.push(current);
      pendingId = null;
      index++;
    } else if (current === null || line === '' || line === '---' || line.startsWith('<!--')) {
      // Text before the first section, blank lines, rules and comments carry no content.
      index++;
    } else if (heading) {
      current.blocks.push({
        kind: 'heading',
        level: heading[1].length,
        spans: inline(heading[2].trim()),
      });
      index++;
    } else if (line.startsWith('```')) {
      current.blocks.push(code());
    } else if (image) {
      current.blocks.push({ kind: 'image', alt: image[1], path: image[2] });
      index++;
    } else if (line.startsWith('|')) {
      current.blocks.push(table());
    } else if (line.startsWith('>')) {
      current.blocks.push({ kind: 'note', spans: inline(note()) });
    } else if (BULLET.test(line)) {
      current.blocks.push({ kind: 'list', numbered: false, items: items(BULLET) });
    } else if (NUMBERED.test(line)) {
      current.blocks.push({ kind: 'list', numbered: true, items: items(NUMBERED) });
    } else {
      current.blocks.push({ kind: 'paragraph', spans: inline(paragraph()) });
    }
  }
  return sections;
}
