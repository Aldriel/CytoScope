import { act, fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MenuBar, type Menu } from './MenuBar';

let actions: Record<string, ReturnType<typeof vi.fn>>;

beforeEach(() => {
  actions = { open: vi.fn(), save: vi.fn(), small: vi.fn(), large: vi.fn(), about: vi.fn() };
});

function renderBar() {
  const menus: Menu[] = [
    {
      label: 'File',
      items: [
        { label: 'Open', shortcut: 'Ctrl+O', action: actions.open },
        { label: 'Save', disabled: true, action: actions.save },
        'separator',
        {
          label: 'Size',
          items: [
            { label: 'Small', checked: true, action: actions.small },
            { label: 'Large', checked: false, action: actions.large },
          ],
        },
      ],
    },
    { label: 'Help', items: [{ label: 'About', action: actions.about }] },
  ];
  return render(
    <div>
      <MenuBar menus={menus} />
      <p>outside</p>
    </div>,
  );
}

const title = (name: string) => screen.getByRole('menuitem', { name });
const openMenus = () => screen.queryAllByRole('menu').length;

/** Runs the focus moves scheduled for the next frame. */
const nextFrame = () => act(() => new Promise((resolve) => requestAnimationFrame(resolve)));

describe('MenuBar', () => {
  it('opens a menu with a click, runs an item and closes', () => {
    renderBar();

    fireEvent.mouseDown(title('File'));
    expect(title('File').getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText('Ctrl+O')).toBeTruthy();
    expect(screen.getByRole('menuitem', { name: 'Save' })).toHaveProperty('disabled', true);
    fireEvent.click(screen.getByRole('menuitem', { name: /Open/ }));

    expect(actions.open).toHaveBeenCalled();
    expect(openMenus()).toBe(0);
  });

  it('closes on a second click, a click elsewhere, Escape or when the window loses focus', () => {
    renderBar();

    fireEvent.mouseDown(title('File'));
    fireEvent.mouseDown(title('File'));
    expect(openMenus()).toBe(0);

    fireEvent.mouseDown(title('File'));
    fireEvent.mouseDown(screen.getByText('outside'));
    expect(openMenus()).toBe(0);

    fireEvent.mouseDown(title('File'));
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(openMenus()).toBe(0);

    fireEvent.mouseDown(title('File'));
    fireEvent.keyDown(window, { key: 'x' });
    expect(openMenus()).toBe(1);
    fireEvent.blur(window);
    expect(openMenus()).toBe(0);
  });

  it('switches menus with the mouse and the arrow keys', () => {
    renderBar();

    fireEvent.mouseEnter(title('Help'));
    expect(openMenus()).toBe(0);
    fireEvent.mouseDown(title('File'));
    fireEvent.mouseEnter(title('Help'));
    expect(title('Help').getAttribute('aria-expanded')).toBe('true');

    fireEvent.keyDown(window, { key: 'ArrowRight' });
    expect(title('File').getAttribute('aria-expanded')).toBe('true');
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    expect(title('Help').getAttribute('aria-expanded')).toBe('true');
  });

  it('is used with the keyboard alone', async () => {
    renderBar();

    fireEvent.keyDown(title('File'), { key: 'x' });
    expect(openMenus()).toBe(0);
    fireEvent.keyDown(title('File'), { key: 'Enter' });
    await nextFrame();
    const open = screen.getByRole('menuitem', { name: /Open/ });
    expect(document.activeElement).toBe(open);

    fireEvent.keyDown(open, { key: 'ArrowDown' });
    const size = screen.getByRole('menuitem', { name: /Size/ });
    expect(document.activeElement).toBe(size);
    fireEvent.keyDown(size, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(open);
    fireEvent.keyDown(open, { key: 'ArrowUp' });
    expect(document.activeElement).toBe(size);
    fireEvent.keyDown(size, { key: 'Tab' });

    fireEvent.keyDown(size, { key: 'x' });
    expect(size.getAttribute('aria-expanded')).toBe('false');
    fireEvent.keyDown(size, { key: 'ArrowRight' });
    await nextFrame();
    const small = screen.getByRole('menuitemradio', { name: /Small/ });
    expect(small.getAttribute('aria-checked')).toBe('true');
    expect(document.activeElement).toBe(small);
    fireEvent.click(screen.getByRole('menuitemradio', { name: /Large/ }));

    expect(actions.large).toHaveBeenCalled();
    expect(openMenus()).toBe(0);
  });

  it('opens a sub-menu when hovered or clicked, and closes it when left', () => {
    renderBar();
    fireEvent.mouseDown(title('File'));
    const size = screen.getByRole('menuitem', { name: /Size/ });

    fireEvent.mouseEnter(size.parentElement!);
    expect(openMenus()).toBe(2);
    fireEvent.mouseEnter(screen.getByRole('menuitem', { name: /Open/ }));
    fireEvent.mouseLeave(size.parentElement!);
    expect(openMenus()).toBe(1);

    fireEvent.click(size);
    expect(openMenus()).toBe(2);
  });
});
