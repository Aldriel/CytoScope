/**
 * Regenerates the screenshots of the web user guide, in French and English, by driving the real
 * application in Microsoft Edge. Images are written to src/help/images/<lang>. Start the server
 * module (port 7072) and "npm run dev" first, then run "npm run screenshots". Another address can
 * be given with the BASE_URL environment variable.
 */
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5175/';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const IMAGES = resolve(ROOT, 'src/help/images');
/** Screenshots are taken at this scale and drawn smaller by the guide, so they stay sharp. */
const SCALE = 1.5;
const LOCALES = ['fr', 'en'];
/** Longest wait for a request to the server, in milliseconds. */
const TIMEOUT_MS = 30_000;

/** Visible tab. */
const TAB = '.tab-panel:not([hidden]) ';

/** Opens an example experiment from the "Tubes" tab, by a word of its button. */
async function openExample(page, word) {
    await page.click(`${TAB}.row button:has-text("${word}")`);
    await settle(page);
}

/** Steps of the guide: the tab shown, then what the user does before the screenshot. */
const STEPS = [
    {
        file: '01-tubes.png',
        tab: 1,
        action: (page) => openExample(page, 'Cadmium'),
    },
    {
        file: '02-compensation.png',
        tab: 2,
        action: () => Promise.resolve(),
    },
    {
        file: '03-gating.png',
        tab: 3,
        action: (page) => page.locator(`${TAB}.hierarchy .population`).nth(1).click(),
    },
    {
        file: '04-apoptosis.png',
        tab: 4,
        action: () => Promise.resolve(),
    },
    {
        file: '05-results.png',
        tab: 5,
        action: async (page) => {
            await settle(page);
            await page.locator(`${TAB}.result-pair`).first().scrollIntoViewIfNeeded();
        },
    },
    {
        file: '06-dose-response.png',
        tab: 5,
        action: (page) => page.locator(`${TAB}.result-pair`).last().scrollIntoViewIfNeeded(),
    },
    {
        file: '07-report.png',
        tab: 6,
        action: async (page) => {
            await page.fill('#report-analyst', 'Maxime Ethier');
            await page.fill('#report-notes', 'CdCl2, 24 h, annexin V-FITC / PI.');
        },
    },
    {
        file: '08-cell-cycle.png',
        tab: 4,
        action: async (page) => {
            await page.keyboard.press('Alt+Digit1');
            await openExample(page, 'U-87MG, c');
            await page.keyboard.press('Alt+Digit4');
        },
    },
];

/** Waits until no request is running. */
async function settle(page) {
    await page.waitForTimeout(150);
    await page.waitForSelector('.status-bar .spinner.idle', {
        state: 'attached',
        timeout: TIMEOUT_MS,
    });
}

async function runLocale(browser, locale) {
    const output = resolve(IMAGES, locale);
    await mkdir(output, { recursive: true });
    const page = await browser.newPage({
        viewport: { width: 1280, height: 800 },
        deviceScaleFactor: SCALE,
        locale: `${locale}-CA`,
    });
    await page.addInitScript((language) => {
        localStorage.setItem('cytoscope.locale', language);
        localStorage.setItem('cytoscope.fontSize', '13');
    }, locale);
    await page.goto(BASE_URL);
    await page.waitForSelector('.tab');
    console.log(locale + ':');

    for (const step of STEPS) {
        await page.keyboard.press('Alt+Digit' + step.tab);
        try {
            await step.action(page);
            await settle(page);
        } catch (e) {
            console.error(`  ${step.file}: ${e.message.split('\n')[0]}`);
        }
        await page.mouse.move(0, 0);
        await page.waitForTimeout(500);
        await page.screenshot({ path: resolve(output, step.file) });
        console.log(`  ${step.file}  —  ${await page.locator('.status-text').textContent()}`);
    }
    await page.close();
}

const browser = await chromium.launch({ channel: 'msedge' });
try {
    for (const locale of LOCALES) {
        await runLocale(browser, locale);
    }
    console.log('Screenshots written to ' + IMAGES);
} finally {
    await browser.close();
}
