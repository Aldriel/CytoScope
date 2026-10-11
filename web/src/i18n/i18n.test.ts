import { describe, expect, it } from 'vitest';
import { defaultLocale, isLocale, LOCALES, messagesOf } from '.';

/** Describes the shape of a catalog: keys, kinds of values, lengths of lists and arities. */
function shape(value: unknown): unknown {
  if (typeof value === 'function') {
    return `function/${value.length}`;
  }
  if (Array.isArray(value)) {
    return value.map(shape);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, v]) => [key, shape(v)]),
    );
  }
  return typeof value;
}

/** Every text function of a catalog, with its path such as "status.opened". */
function functionsOf(value: unknown, path = ''): [string, (...args: unknown[]) => unknown][] {
  if (typeof value === 'function') {
    return [[path, value as (...args: unknown[]) => unknown]];
  }
  if (value !== null && typeof value === 'object') {
    return Object.entries(value).flatMap(([key, v]) =>
      functionsOf(v, path ? `${path}.${key}` : key),
    );
  }
  return [];
}

/** Arguments of the texts that do not take counts. */
const SPECIAL: Record<string, unknown[]> = {
  'gating.quadrantName': ['FITC-A', 'PI-A', 'POSITIVE_NEGATIVE'],
  'metric.fate': ['Live'],
};

describe('i18n', () => {
  it('gives the same texts in French and in English', () => {
    expect(shape(messagesOf('en'))).toEqual(shape(messagesOf('fr')));
  });

  it('writes every message with singular and plural counts', () => {
    for (const locale of LOCALES) {
      for (const [path, text] of functionsOf(messagesOf(locale))) {
        for (const count of [0, 1, 2.5]) {
          const written = text(...(SPECIAL[path] ?? Array<number>(text.length).fill(count)));
          expect(typeof written, `${locale}: ${path}`).toBe('string');
          expect(written, `${locale}: ${path}`).not.toBe('');
        }
      }
    }
  });

  it('agrees the nouns with their number and formats the numbers', () => {
    const en = messagesOf('en');
    const fr = messagesOf('fr');
    expect(en.status.loaded(1)).toBe('1 file read.');
    expect(en.status.loaded(3)).toBe('3 files read.');
    expect(fr.status.loaded(1)).toBe('1 fichier lu.');
    expect(fr.tubes.summary(2, 1)).toBe('2 tubes, 1 groupe');
    expect(en.percent(2.06)).toBe('2.1%');
    expect(fr.percent(2.06)).toMatch(/^2,1\s?%$/);
    expect(en.percent(null)).toBe('—');
    expect(en.number(null)).toBe('—');
    expect(en.number(8.084)).toBe('8.08');
    expect(en.pValue(0.0004)).toBe('< 0.001');
    expect(fr.pValue(0.0123)).toBe('= 0,012');
    expect(en.gating.quadrantName('Annexin V', 'PI', 'POSITIVE_NEGATIVE')).toBe('Annexin V+ PI−');
    expect(fr.metric.fate('Nécrotiques')).toBe('% de cellules nécrotiques');
  });

  it('chooses the language of the browser when it is supported', () => {
    expect(defaultLocale(['fr-CA', 'en'])).toBe('fr');
    expect(defaultLocale(['es'])).toBe('en');
    expect(defaultLocale([])).toBe('en');
    expect(isLocale('fr')).toBe(true);
    expect(isLocale('de')).toBe(false);
    expect(isLocale(null)).toBe(false);
  });
});
