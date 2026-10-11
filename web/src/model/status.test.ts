import { describe, expect, it } from 'vitest';
import { messagesOf } from '../i18n';
import { ApiError } from './api';
import { failure, fromError, info, READY } from './status';

const en = messagesOf('en');
const fr = messagesOf('fr');

describe('status', () => {
  it('writes informative and failure messages in the current language', () => {
    expect(READY.error).toBe(false);
    expect(info((m) => m.status.analyzing).render(fr)).toBe(fr.status.analyzing);
    const noTubes = failure((m) => m.error.noTubes);
    expect(noTubes.error).toBe(true);
    expect(noTubes.render(en)).toBe('Error: load tubes first.');
  });

  it('explains API errors by their kind, with the detail', () => {
    expect(fromError(new ApiError('No $TEXT segment', 400, 'PARSING')).render(en)).toBe(
      'Error: invalid FCS file (No $TEXT segment)',
    );
    expect(
      ['INVALID_INPUT', 'NOT_FOUND', 'EXPIRED', 'TOO_LARGE', 'BUSY', 'UNEXPECTED'].map((kind) =>
        fromError(new ApiError('detail', 400, kind as never)).render(en),
      ),
    ).toEqual([
      'Error: invalid input (detail)',
      'Error: item not found (detail)',
      'Error: session expired (detail)',
      'Error: file too large (50 MB at most) (detail)',
      'Error: the server is very busy; try again in a few minutes (detail)',
      'Error: unexpected error (detail)',
    ]);
  });

  it('tells that the server does not answer', () => {
    expect(fromError(new ApiError('Server unreachable', 0, null)).render(en)).toBe(
      'Error: the server does not answer; try again in a moment',
    );
  });

  it('treats other failures as unexpected', () => {
    expect(fromError(new ApiError('HTTP 502', 502, null)).render(en)).toBe(
      'Error: unexpected error (HTTP 502)',
    );
    expect(fromError(new Error('boom')).render(en)).toBe('Error: unexpected error (boom)');
    expect(fromError('odd').render(en)).toBe('Error: unexpected error (odd)');
    expect(fromError(new Error('')).render(en)).toBe('Error: unexpected error');
  });
});
