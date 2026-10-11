import { useState } from 'react';

interface Props {
  value: string;
  /** Called with the typed text when the field loses the focus or Enter is pressed. */
  onCommit: (value: string) => void;
  label: string;
  id?: string;
  title?: string;
  placeholder?: string;
  className?: string;
  /** Right-aligned, for numbers. */
  numeric?: boolean;
}

/**
 * Text field whose change is committed only once typed, so that each key stroke does not send a
 * request to the server. Escape gives the previous value back.
 */
export function TextField({
  value,
  onCommit,
  label,
  id,
  title,
  placeholder,
  className,
  numeric,
}: Props) {
  const [text, setText] = useState<string | null>(null);

  const commit = () => {
    if (text !== null && text !== value) {
      onCommit(text);
    }
    setText(null);
  };

  return (
    <input
      id={id}
      className={[className, numeric ? 'numeric' : ''].join(' ').trim() || undefined}
      value={text ?? value}
      aria-label={label}
      title={title}
      placeholder={placeholder}
      inputMode={numeric ? 'decimal' : undefined}
      onChange={(e) => setText(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          commit();
        } else if (e.key === 'Escape') {
          setText(null);
        }
      }}
    />
  );
}

/**
 * Reads a number typed by the user; a comma is accepted as decimal separator.
 *
 * @returns the number, null for an empty text, or undefined if the text is not a number
 */
export function parseNumber(text: string): number | null | undefined {
  const trimmed = text.trim();
  if (trimmed === '') {
    return null;
  }
  const value = Number(trimmed.replace(',', '.'));
  return Number.isFinite(value) ? value : undefined;
}
