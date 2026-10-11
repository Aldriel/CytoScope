import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Modal } from './Modal';
import { parseNumber, TextField } from './TextField';

describe('TextField', () => {
  function renderField(numeric = false) {
    const onCommit = vi.fn();
    render(<TextField value="5" label="Dose" numeric={numeric} onCommit={onCommit} />);
    return { field: screen.getByLabelText('Dose') as HTMLInputElement, onCommit };
  }

  it('commits the typed text on Enter or when the focus leaves', () => {
    const { field, onCommit } = renderField();

    fireEvent.change(field, { target: { value: '7' } });
    expect(onCommit).not.toHaveBeenCalled();
    fireEvent.keyDown(field, { key: 'Enter' });
    expect(onCommit).toHaveBeenLastCalledWith('7');

    fireEvent.change(field, { target: { value: '8' } });
    fireEvent.blur(field);
    expect(onCommit).toHaveBeenLastCalledWith('8');
    expect(onCommit).toHaveBeenCalledTimes(2);
  });

  it('gives the value back on Escape and ignores an unchanged text', () => {
    const { field, onCommit } = renderField(true);

    fireEvent.change(field, { target: { value: '9' } });
    fireEvent.keyDown(field, { key: 'Escape' });
    expect(field.value).toBe('5');
    fireEvent.blur(field);
    fireEvent.keyDown(field, { key: 'a' });
    expect(onCommit).not.toHaveBeenCalled();
    expect(field.className).toBe('numeric');
    expect(field.inputMode).toBe('decimal');
  });
});

describe('parseNumber', () => {
  it('reads numbers with a point or a comma', () => {
    expect(parseNumber(' 2,5 ')).toBe(2.5);
    expect(parseNumber('1e3')).toBe(1000);
    expect(parseNumber('')).toBeNull();
    expect(parseNumber('abc')).toBeUndefined();
  });
});

describe('Modal', () => {
  it('focuses its first button and closes on Escape or a click outside', () => {
    const onClose = vi.fn();
    render(
      <Modal title="Title" header="Header" icon="!" onClose={onClose} buttons={<button>OK</button>}>
        Content
      </Modal>,
    );

    expect(document.activeElement?.textContent).toBe('OK');
    expect(screen.getByText('!')).toBeTruthy();
    fireEvent.mouseDown(screen.getByText('Content'));
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.mouseDown(document.querySelector('.modal-backdrop')!);
    fireEvent.keyDown(window, { key: 'Enter' });
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
