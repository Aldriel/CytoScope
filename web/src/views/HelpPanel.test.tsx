import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { parseGuide } from '../help/helpDocument';
import { messagesOf, MessagesContext } from '../i18n';
import { HelpPanel } from './HelpPanel';

/** A guide using every kind of block, with one screenshot that exists and one that does not. */
vi.mock('../help/guides', () => ({
  guideOf: () =>
    parseGuide(`<!-- section: antibody -->
# Antibody tab

Paste the **heavy** chain, *then* the \`light\` one; see [IMGT](https://www.imgt.org).

> A note.

## Regions

### CDRs

1. first
2. second

- one
- two

![Screenshot](images/en/01-antibody.png)

![Missing](images/en/absent.png)

<!-- section: formats -->
# File formats

| Format | File | Columns of a long description that never fits on a single line of the table |
|---|---|---|
| MaxQuant | evidence.txt | Sequence, Experiment, Intensity |

\`\`\`
Sequence,Sample,Intensity
\`\`\`
`),
  imageUrl: (path: string) => (path.includes('absent') ? null : `/${path}`),
}));

function renderPanel(section = 'antibody', onClose = vi.fn()) {
  const view = render(
    <MessagesContext.Provider value={messagesOf('en')}>
      <HelpPanel locale="en" request={{ section, serial: 1 }} onClose={onClose} />
    </MessagesContext.Provider>,
  );
  return { ...view, onClose };
}

describe('HelpPanel', () => {
  it('shows the section asked, with its formatting and screenshots', () => {
    const { container } = renderPanel();

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Antibody tab');
    expect(screen.getByRole('heading', { level: 2 }).textContent).toBe('Regions');
    expect(screen.getByRole('heading', { level: 3 }).textContent).toBe('CDRs');
    expect(container.querySelector('strong')?.textContent).toBe('heavy');
    expect(container.querySelector('em')?.textContent).toBe('then');
    expect(container.querySelector('code')?.textContent).toBe('light');
    expect(screen.getByRole('link', { name: 'IMGT' }).getAttribute('target')).toBe('_blank');
    expect(container.querySelector('.help-note')?.textContent).toBe('A note.');
    expect(container.querySelectorAll('ol li')).toHaveLength(2);
    expect(container.querySelectorAll('ul:not(.help-sections) li')).toHaveLength(2);
    expect(screen.getByText('[Missing]')).toBeTruthy();

    const image = screen.getByRole('img', { name: 'Screenshot' });
    Object.defineProperty(image, 'naturalWidth', { value: 900 });
    fireEvent.load(image);
    expect(image.getAttribute('width')).toBe('600');
  });

  it('searches the guide and highlights the words found', async () => {
    const user = userEvent.setup();
    const { container } = renderPanel();
    const field = screen.getByRole('searchbox');
    expect(document.activeElement).toBe(field);

    await user.type(field, 'sequence');

    expect(screen.getByText('1 section · 2 occurrences')).toBeTruthy();
    expect(screen.getByRole('option', { selected: true }).textContent).toBe('File formats  (2)');
    expect(container.querySelector('mark')?.textContent).toBe('Sequence');
    expect(container.querySelector('.help-code.highlight')).toBeTruthy();
    expect(container.querySelector('th.nowrap')?.textContent).toBe('Format');

    await user.clear(field);
    await user.type(field, 'unknownword');
    expect(screen.getByText('No section of the guide contains “unknownword”.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Clear' }));
    expect(field).toHaveProperty('value', '');
    await user.click(screen.getByRole('option', { name: 'File formats' }));
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('File formats');
  });

  it('focuses the search with Ctrl+F; Escape clears it, then closes the guide', async () => {
    const user = userEvent.setup();
    const { onClose } = renderPanel('formats');
    const field = screen.getByRole('searchbox');
    field.blur();

    await user.keyboard('{Control>}f{/Control}');
    expect(document.activeElement).toBe(field);
    await user.type(field, 'csv');
    await user.keyboard('{Escape}');
    expect(field).toHaveProperty('value', '');
    expect(onClose).not.toHaveBeenCalled();

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
    await user.click(screen.getByTitle('Close'));
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
