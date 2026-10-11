import type { Locale } from '../i18n';
import guideEn from './guide_en.md?raw';
import guideFr from './guide_fr.md?raw';
import { parseGuide, type HelpSection } from './helpDocument';

const SOURCES: Record<Locale, string> = { en: guideEn, fr: guideFr };

/** Screenshots of the guides, by path relative to this folder ("images/fr/01-antibody.png"). */
const IMAGES = import.meta.glob<string>('./images/**/*.png', {
  query: '?url',
  import: 'default',
  eager: true,
});

const parsed = new Map<Locale, HelpSection[]>();

/** Returns the sections of the user guide written in a language, parsed once. */
export function guideOf(locale: Locale): HelpSection[] {
  let sections = parsed.get(locale);
  if (sections === undefined) {
    sections = parseGuide(SOURCES[locale]);
    parsed.set(locale, sections);
  }
  return sections;
}

/** Returns the address of a screenshot of the guide, or null if it does not exist. */
export const imageUrl = (path: string): string | null => IMAGES[`./${path}`] ?? null;
