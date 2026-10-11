/** Languages in which the footer can be displayed. */
export type FooterLanguage = 'fr' | 'en';

const EMAIL = 'contact@maximeethier.com';
const KOFI_URL = 'https://ko-fi.com/K1S228CL7A';
/** Licence text, published next to the application by the build (see vite.config.ts). */
const LICENSE_URL = `${import.meta.env.BASE_URL}LICENSE.txt`;

const TEXTS = {
  fr: {
    author: '© 2026 Maxime Ethier – Consultant en Bio-informatique',
    website: 'https://www.maximeethier.com',
    license: 'Licence Apache 2.0',
    licenseTitle: 'Lire la licence sous laquelle cette application est distribuée',
    availability: 'Disponible pour des contrats ou un emploi en bio-informatique.',
    kofi: 'Soutenez-moi sur Ko-fi',
    kofiTitle: 'Soutenir le développement de cette application par un don sur Ko-fi',
  },
  en: {
    author: '© 2026 Maxime Ethier – Biocomputing Consultant',
    website: 'https://www.maximeethier.com/en',
    license: 'Apache License 2.0',
    licenseTitle: 'Read the license under which this application is distributed',
    availability: 'Available for bioinformatics contracts or employment.',
    kofi: 'Support me on Ko-fi',
    kofiTitle: 'Support the development of this application with a donation on Ko-fi',
  },
} satisfies Record<FooterLanguage, Record<string, string>>;

/**
 * Discreet footer: the licence at the left, then, centred in the remaining space on one line
 * (more if the window is narrow), author and contact, availability and the Ko-fi button. The
 * button reproduces the official Ko-fi widget, whose script writes into the page with
 * document.write and cannot run in a React application; its cup is drawn inline, so the page
 * loads nothing from Ko-fi.
 */
export function Footer({ language = 'fr' }: { language?: FooterLanguage }) {
  const t = TEXTS[language];
  return (
    <footer className="app-footer">
      <a
        className="footer-license"
        href={LICENSE_URL}
        target="_blank"
        rel="noopener noreferrer"
        title={t.licenseTitle}
      >
        {t.license}
      </a>
      <div className="footer-main">
        <div className="footer-author">
          <a href={t.website} target="_blank" rel="noopener noreferrer">
            {t.author}
          </a>
          <span aria-hidden="true"> · </span>
          <a href={`mailto:${EMAIL}`}>{EMAIL}</a>
        </div>
        <div className="footer-tagline">{t.availability}</div>
        <div className="footer-support">
          <a
            className="kofi-button"
            href={KOFI_URL}
            target="_blank"
            rel="noopener noreferrer"
            title={t.kofiTitle}
          >
            <svg viewBox="0 0 14 11" aria-hidden="true">
              <path d="M1 1h10v4.5A3.5 3.5 0 0 1 7.5 9h-3A3.5 3.5 0 0 1 1 5.5z" />
              <path d="M11 2h1.2a2.2 2.2 0 0 1 0 4.4H11V5.2h1.2a1 1 0 0 0 0-2H11z" />
            </svg>
            {t.kofi}
          </a>
        </div>
      </div>
    </footer>
  );
}
