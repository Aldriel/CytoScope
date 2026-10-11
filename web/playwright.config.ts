import { defineConfig } from '@playwright/test';

/**
 * Functional tests: a real browser uses the application like a visitor, with the REST API. They
 * test the deployed site by default; set E2E_BASE_URL to test another copy, e.g.
 * http://localhost:5175/ with the server module and "npm run dev" started ("npm run
 * test:e2e:local").
 */
export default defineConfig({
  testDir: 'e2e',
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 20_000 },
  fullyParallel: true,
  workers: process.env.CI ? 2 : 3,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'https://cytoscope.maximeethier.com/',
    // Microsoft Edge is already installed on Windows; CI uses the browser of Playwright.
    channel: process.env.CI ? undefined : 'msedge',
    viewport: { width: 1280, height: 860 },
    acceptDownloads: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'en', use: { locale: 'en-CA' } },
    { name: 'fr', use: { locale: 'fr-CA' } },
  ],
});
