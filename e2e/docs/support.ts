import { expect, Page } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { adminToken } from '../global-setup';
import { KEYCLOAK_URL } from '../playwright.config';

/** Helpers for the documentation screenshots (`npm run docs-screens`). */

const REPO = join(__dirname, '..', '..');
const IMAGES = join(REPO, 'docs', 'images');
mkdirSync(IMAGES, { recursive: true });

export type Section = 'Sign-in' | 'Registration' | 'Password reset' | 'Refusals' | 'Admin console';
const SECTIONS: Section[] = ['Sign-in', 'Registration', 'Password reset', 'Refusals', 'Admin console'];

interface Entry {
  section: Section;
  title: string;
  device: 'desktop' | 'phone';
  file: string;
  width: number;
  height: number;
  caption: string;
}

/**
 * Playwright starts a new worker after a failed test, which reloads this module: the run's shots
 * and "the realm is built" are kept in files named after the test runner's process, so a failed shot
 * neither loses the others from the manifest nor rebuilds the realm halfway through.
 */
const RUN = join(__dirname, '..', 'test-results', `docs-screens-${process.ppid}`);
mkdirSync(RUN, { recursive: true });
const ENTRIES = join(RUN, 'entries.json');
const entries: Entry[] = existsSync(ENTRIES) ? JSON.parse(readFileSync(ENTRIES, 'utf8')) : [];

/** Runs `build` once per test run (see above). */
export async function once(name: string, build: () => Promise<void>) {
  const marker = join(RUN, `${name}.done`);
  if (existsSync(marker)) return;
  await build();
  writeFileSync(marker, '');
}

/**
 * The admin console scrolls inside its panels and dialogs, which a screenshot doesn't capture:
 * makes the window tall enough for every panel to show all of its content (the side menu aside),
 * and returns a function that puts the window back.
 */
export async function fitViewport(page: Page): Promise<() => Promise<void>> {
  const original = page.viewportSize()!;
  // Measure once the page has finished laying out
  await page.waitForTimeout(500);
  for (let i = 0; i < 4; i++) {
    const extra = await page.evaluate(() => Math.max(0, ...[...document.querySelectorAll('body *')].map((e) => {
      if (e.closest('nav, [class*="c-page__sidebar"]')) return 0;
      const overflow = getComputedStyle(e).overflowY;
      return (overflow === 'auto' || overflow === 'scroll') && e.scrollHeight > e.clientHeight + 1 ? e.scrollHeight - e.clientHeight : 0;
    })));
    if (!extra) break;
    const now = page.viewportSize()!;
    await page.setViewportSize({ width: now.width, height: Math.min(now.height + extra, 4000) });
    await page.waitForTimeout(300);
  }
  return () => page.setViewportSize(original);
}

/** Saves a screenshot of the page's viewport as docs/images/<folder>/<name>.png and records it for the manifest. */
export async function shot(page: Page, folder: string, name: string, entry: { section: Section; title: string; caption: string }) {
  const path = join(IMAGES, folder, `${name}.png`);
  mkdirSync(dirname(path), { recursive: true });
  // Let fonts, icons and transitions settle so shots are stable from run to run
  await page.evaluate(() => document.fonts.ready);
  // No hover highlight or focus ring wherever the last click left them
  await page.mouse.move(0, 0);
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForTimeout(500);
  await page.screenshot({ path, animations: 'disabled', caret: 'hide' });
  const png = readFileSync(path);
  const file = relative(REPO, path).split('\\').join('/');
  const device = (page.viewportSize()?.width ?? 1280) <= 500 ? 'phone' : 'desktop';
  const record: Entry = { ...entry, device, file, width: png.readUInt32BE(16), height: png.readUInt32BE(20) };
  const at = entries.findIndex((e) => e.file === file);
  if (at >= 0) entries[at] = record;
  else entries.push(record);
  writeFileSync(ENTRIES, JSON.stringify(entries));
}

/**
 * Writes docs/images/manifest.json (every screenshot, by section, in the order taken) and the
 * gallery, docs/screens.md. A run of only some tests replaces the shots it retook and keeps the rest.
 */
export function writeManifest() {
  const path = join(IMAGES, 'manifest.json');
  const kept: Entry[] = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : [];
  const retaken = new Map(entries.map((e) => [e.file, e]));
  const merged = [
    ...kept.map((e) => retaken.get(e.file) ?? e),
    ...entries.filter((e) => !kept.some((k) => k.file === e.file)),
  ].filter((e) => existsSync(join(REPO, e.file)));
  const ordered = SECTIONS.flatMap((s) => merged.filter((e) => e.section === s));
  writeFileSync(path, JSON.stringify(ordered, null, 2) + '\n');
  writeFileSync(join(REPO, 'docs', 'screens.md'), gallery(ordered));
}

const anchor = (heading: string) => heading.toLowerCase().replace(/[^a-z0-9 -]/g, '').replace(/ /g, '-');

/** docs/screens.md: every screenshot, by section, two to a row (phones four to a row). */
function gallery(manifest: Entry[]): string {
  const image = (e: Entry, width: number) => {
    const src = e.file.replace(/^docs\//, '');
    return `<td valign="top" width="${width}"><a href="${src}"><img src="${src}" width="${width}" alt="${e.caption.replace(/"/g, '&quot;')}"></a><br><b>${e.title}</b><br>${e.caption}</td>`;
  };
  const rows = (shots: Entry[], perRow: number, width: number) => {
    const out: string[] = [];
    for (let i = 0; i < shots.length; i += perRow) out.push(`<tr>${shots.slice(i, i + perRow).map((e) => image(e, width)).join('')}</tr>`);
    return out;
  };
  const sections = SECTIONS.filter((s) => manifest.some((e) => e.section === s));
  const lines = [
    '# Screens',
    '',
    'Every screen the extension adds or changes, in Keycloak\'s default theme: the Turnstile widget on',
    'the sign-in, registration and reset-password pages, what a refused visitor sees, and the admin',
    'console. Select an image for the full size.',
    '',
    'Generated by `npm run docs-screens` in `e2e/` (see [CONTRIBUTING.md](../CONTRIBUTING.md)): don\'t edit by hand.',
    '',
    ...sections.map((s) => `- [${s}](#${anchor(s)})`),
    '',
  ];
  for (const section of sections) {
    const shots = manifest.filter((e) => e.section === section);
    const wide = shots.filter((e) => e.device !== 'phone');
    const phones = shots.filter((e) => e.device === 'phone');
    lines.push(`## ${section}`, '', '<table>', ...rows(wide, 2, 400), ...rows(phones, 4, 190), '</table>', '');
  }
  return lines.join('\n');
}

/** Admin REST calls (master admin), signing in again when the short-lived token runs out. */
let token: string | undefined;
export async function admin<T = any>(method: string, path: string, body?: unknown): Promise<T> {
  token ??= await adminToken();
  const send = () => fetch(`${KEYCLOAK_URL}/admin/realms${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let res = await send();
  if (res.status === 401) {
    token = await adminToken();
    res = await send();
  }
  if (res.status >= 400) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`);
  const text = await res.text();
  return (text.startsWith('{') || text.startsWith('[') ? JSON.parse(text) : text) as T;
}

/**
 * Waits until the Turnstile widget shows its outcome. The widget is Cloudflare's iframe, in a
 * closed shadow root that Playwright can't look into: with the test site keys it always ends in the
 * same state, but it loads asynchronously and animates its way there, so this waits for a token
 * (when one is expected) and then until the widget looks the same three times in a row.
 */
export async function widgetSettled(page: Page, expectToken = true) {
  const widget = page.locator('.cf-turnstile').first();
  await expect(widget).toBeVisible();
  if (expectToken) {
    await expect(page.locator('input[name="cf-turnstile-response"]').first()).toHaveValue(/.+/, { timeout: 30_000 });
  }
  // The iframe is in a closed shadow root, but Playwright still sees it as a frame
  await expect.poll(() => page.frames().some((f) => f.url().startsWith('https://challenges.cloudflare.com/')), {
    message: 'the Turnstile iframe', timeout: 30_000,
  }).toBe(true);
  let previous: Buffer | undefined;
  let same = 0;
  const deadline = Date.now() + 30_000;
  while (same < 3) {
    if (Date.now() > deadline) throw new Error('the Turnstile widget kept changing');
    await page.waitForTimeout(500);
    const now = await widget.screenshot({ animations: 'disabled', caret: 'hide' });
    same = previous && now.equals(previous) && (await widget.boundingBox())!.height > 30 ? same + 1 : 0;
    previous = now;
  }
}
