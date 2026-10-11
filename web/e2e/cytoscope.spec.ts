/**
 * Functional tests: scenarios of a visitor, run in a real browser against the REST API, in
 * English and in French (one Playwright project per language).
 */
import { fileURLToPath } from 'node:url';
import { expect, test } from './app';

/** An FCS file of the cadmium example, as a visitor would have on disk. */
const FCS_FILE = fileURLToPath(
  new URL(
    '../../core/src/main/resources/com/bioinfo/cytoscope/examples/cadmium/Cd_0uM_1.fcs',
    import.meta.url,
  ),
);

test.beforeEach(async ({ app }) => {
  await app.open();
});

test('compares the apoptosis of the U-87MG treatments with Dunnett’s test', async ({ app }) => {
  await app.openExample('u87-apoptosis');
  const banner = app.panel().locator('.banner');
  await expect(banner).toContainText('CC BY 4.0');
  await expect(banner.getByRole('link', { name: app.m.tubes.dataset })).toBeVisible();
  const results = await app.show('results');
  await app.settle();
  const comparison = results.getByRole('table', { name: app.m.results.comparison });
  await expect(comparison.locator('tbody tr')).toHaveCount(5);
  const doxycycline = comparison
    .locator('tbody tr')
    .filter({ has: app.page.getByRole('cell', { name: 'DOXY', exact: true }) });
  await expect(doxycycline.locator('.stars')).toHaveText('***');
  await expect(results.locator('svg.bar-chart')).toBeVisible();
});

test('fits the dose-response curve of the cadmium series', async ({ app }) => {
  await app.openExample('cadmium');
  await expect(app.panel().locator('.banner.simulated')).toBeVisible();
  const results = await app.show('results');
  await app.settle();
  const fit = results.getByRole('table', { name: app.m.results.doseResponse });
  await expect(fit.locator('tr').nth(1)).toContainText(app.language === 'fr' ? '8,' : '8.');
  await expect(results.locator('svg.dose-curve path.fit')).toBeVisible();
});

test('reads the cell cycle of the U-87MG cells', async ({ app }) => {
  await app.openExample('u87-cell-cycle');
  const analysis = await app.show('analysis');
  await app.settle();
  await expect(app.plot(app.m.analysis.cellCycle).locator('path.histogram')).toBeVisible();
  await expect(analysis.locator('.gate.interval')).toHaveCount(4);
  const results = await app.show('results');
  await app.settle();
  await expect(results.getByRole('columnheader', { name: 'G2/M' })).toBeVisible();
});

test('draws a gate on the plot, then deletes it', async ({ app }) => {
  await app.openExample('u87-apoptosis');
  const gating = await app.show('gating');
  await app.settle();
  const plot = gating.getByRole('application');
  await expect(plot.locator('image.density')).toBeVisible();
  const before = await gating.getByRole('treeitem').count();
  await gating.getByRole('button', { name: app.m.gating.tool.rectangle }).click();
  const box = (await plot.boundingBox())!;
  await app.page.mouse.move(box.x + box.width * 0.4, box.y + box.height * 0.3);
  await app.page.mouse.down();
  await app.page.mouse.move(box.x + box.width * 0.7, box.y + box.height * 0.6, { steps: 5 });
  await app.page.mouse.up();
  await app.settle();
  await expect(gating.getByRole('treeitem')).toHaveCount(before + 1);

  await gating.getByRole('treeitem').last().getByRole('button').first().click();
  await gating.getByRole('button', { name: app.m.gating.remove }).click();
  await app.settle();
  await expect(gating.getByRole('treeitem')).toHaveCount(before);
});

test('loads an FCS file of the visitor and downloads the report', async ({ app }) => {
  const tubes = await app.show('tubes');
  await app.page.getByTestId('file-input').setInputFiles(FCS_FILE);
  await app.settle();
  await expect(app.rows('table.tubes')).toHaveCount(1);
  await expect(tubes).toContainText('Cd_0uM_1');

  await app.show('report');
  await app.panel().getByLabel(app.m.report.title).fill('CytoScope e2e');
  const download = app.page.waitForEvent('download');
  await app.panel().getByRole('button', { name: app.m.report.download }).click();
  expect((await download).suggestedFilename()).toBe('CytoScope e2e.pdf');
});

test('explains why a file cannot be read', async ({ app }) => {
  test.info().annotations.push({ type: 'expects-error' });
  await app.page.getByTestId('file-input').setInputFiles({
    name: 'broken.fcs',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from('not an FCS file'),
  });
  await app.settle();
  await expect(app.status()).toContainText(app.m.error.parsing);
});

test('opens the guide at the section of the tab', async ({ app }) => {
  await app.show('compensation');
  await app.page.keyboard.press('F1');
  await expect(app.page.locator('.help-panel h1').first()).toContainText(app.m.tab.compensation);
});
