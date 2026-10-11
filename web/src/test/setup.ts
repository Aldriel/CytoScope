/**
 * Prepares jsdom for the unit tests: the browser functions it lacks are replaced by mocks, and
 * every rendered component is removed after its test.
 */
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
});

URL.createObjectURL = vi.fn(() => 'blob:report');
URL.revokeObjectURL = vi.fn();

// jsdom has no pointer events: they are mouse events with a pointer identifier.
if (typeof window.PointerEvent === 'undefined') {
  class PointerEvent extends MouseEvent {
    readonly pointerId: number;
    constructor(type: string, init: PointerEventInit = {}) {
      super(type, init);
      this.pointerId = init.pointerId ?? 1;
    }
  }
  window.PointerEvent = PointerEvent as typeof window.PointerEvent;
}

// jsdom draws nothing on canvases; the tests that need one give a fake context.
HTMLCanvasElement.prototype.getContext = vi.fn(() => null) as never;
