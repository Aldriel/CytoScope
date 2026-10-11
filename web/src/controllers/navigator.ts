/** Page of the U-87MG dataset whose files are the real examples. */
export const DATASET_URL = 'https://doi.org/10.17632/d4r5h95356.1';

/** Tabs of the application, in display order. */
export const TAB_IDS = [
  'tubes',
  'compensation',
  'gating',
  'analysis',
  'results',
  'report',
] as const;
export type TabId = (typeof TAB_IDS)[number];

/** Section of the user guide describing each tab. */
export const HELP_SECTIONS: Record<TabId, string> = {
  tubes: 'tubes',
  compensation: 'compensation',
  gating: 'gating',
  analysis: 'analyses',
  results: 'results',
  report: 'report',
};

/** Has the browser save a file in the downloads of the user. */
export function saveFile(content: Blob, fileName: string): void {
  const url = URL.createObjectURL(content);
  download(url, fileName);
  // The download has started; the address is released once the browser has read it.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Has the browser download the file at an address. */
export function download(url: string, fileName = ''): void {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
}
