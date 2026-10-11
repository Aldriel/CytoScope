/**
 * Languages of the interface. Texts are typed: a text missing from a catalog, or called with the
 * wrong arguments, does not compile.
 */
import { createContext, useContext } from 'react';
import { en } from './en';
import { fr } from './fr';

/** Texts of the interface in one language; the French catalog defines the keys. */
export type Messages = typeof fr;

/** Languages of the interface, in the order of the Language menu. */
export const LOCALES = ['en', 'fr'] as const;
export type Locale = (typeof LOCALES)[number];

const CATALOGS: Record<Locale, Messages> = { en, fr };

/** Returns the texts of a language. */
export const messagesOf = (locale: Locale): Messages => CATALOGS[locale];

/** Tells whether a value names a supported language. */
export const isLocale = (value: unknown): value is Locale =>
  typeof value === 'string' && (LOCALES as readonly string[]).includes(value);

/**
 * Language used on the first visit: the one of the browser when it is supported, English
 * otherwise (like the desktop application with the system language).
 *
 * @param languages preferred languages of the browser, such as ["fr-CA", "en"]
 */
export function defaultLocale(languages: readonly string[]): Locale {
  const primary = languages[0]?.slice(0, 2).toLowerCase();
  return isLocale(primary) ? primary : 'en';
}

/** Texts of the current language, provided by the root of the application. */
export const MessagesContext = createContext<Messages>(fr);

/** Returns the texts of the current language. */
export const useMessages = () => useContext(MessagesContext);
