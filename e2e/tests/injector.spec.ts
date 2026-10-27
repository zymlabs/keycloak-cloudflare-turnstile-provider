import { expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * The script-injection injector on its own, on pages served here, with a stand-in for Cloudflare's
 * API: forms rendered late or re-rendered by JavaScript (as React/Keycloakify themes do), settings
 * that load after the injector, and themes that manage the submit button themselves.
 */

const INJECTOR = readFileSync(join(__dirname, '../../src/main/resources/theme-resources/js/turnstile-injector.js'), 'utf8');
const ORIGIN = 'http://injector.test';
const CONFIG = `window.TURNSTILE_CONFIG = { enabled: true, siteKey: 'test-site-key', mode: MODE, theme: 'auto' };
document.dispatchEvent(new Event('cloudflare-turnstile:config'));`;

/** Cloudflare's API as the injector uses it: render() adds the hidden token input to the container. */
const TURNSTILE_STUB = `
window.turnstileStub = { rendered: [], removed: [] };
function widget(id) { return window.turnstileStub.rendered.find(function (w) { return w.id === id; }); }
window.turnstileStub.solve = function (id) {
  var w = widget(id); w.input.value = 'token-' + id; w.options.callback('token-' + id);
};
window.turnstileStub.expire = function (id) {
  var w = widget(id); w.input.value = ''; w.options['expired-callback']();
};
window.turnstile = {
  render: function (container, options) {
    var id = 'w' + (window.turnstileStub.rendered.length + 1);
    var input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'cf-turnstile-response';
    container.appendChild(input);
    window.turnstileStub.rendered.push({ id: id, options: options, input: input });
    return id;
  },
  remove: function (id) { window.turnstileStub.removed.push(id); }
};`;

const FORM = `<form id="kc-form-login" action="/login-actions/authenticate" method="post">
  <div class="pf-v5-c-form__group"><input id="username" name="username"></div>
  <div class="pf-v5-c-form__group" id="buttons"><input type="submit" id="kc-login" value="Sign in"></div>
</form>`;

interface Served {
  apiRequests: string[];
}

/** Serves a page (and the injector, settings and Turnstile stand-in it loads) at http://injector.test/. */
async function serve(page: Page, body: string, { mode = 'managed', head = '' } = {}): Promise<Served> {
  const served: Served = { apiRequests: [] };
  await page.route(`${ORIGIN}/**`, (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/injector.js') return route.fulfill({ contentType: 'application/javascript', body: INJECTOR });
    if (path === '/config.js') return route.fulfill({ contentType: 'application/javascript', body: CONFIG.replace('MODE', JSON.stringify(mode)) });
    return route.fulfill({
      contentType: 'text/html',
      body: `<!doctype html><html><head>${head}</head><body>${body}
<template id="form">${FORM}</template>
<script>
  /** What a JavaScript theme does: adds a script, or (re-)renders the form from scratch. */
  window.addScript = function (src) { var s = document.createElement('script'); s.src = src; document.head.appendChild(s); };
  window.renderForm = function () {
    var fresh = document.getElementById('form').content.cloneNode(true);
    var old = document.getElementById('kc-form-login');
    if (old) old.replaceWith(fresh); else document.getElementById('root').appendChild(fresh);
  };
</script></body></html>`,
    });
  });
  await page.route('https://challenges.cloudflare.com/**', (route) => {
    served.apiRequests.push(route.request().url());
    return route.fulfill({ contentType: 'application/javascript', body: TURNSTILE_STUB });
  });
  await page.goto(`${ORIGIN}/`);
  return served;
}

const SERVER_RENDERED_HEAD = '<script src="/config.js"></script><script src="/injector.js"></script>';
const solve = (page: Page, id: string) => page.evaluate((w) => (window as any).turnstileStub.solve(w), id);
const stub = (page: Page) => page.evaluate(() => {
  const s = (window as any).turnstileStub;
  return { rendered: s.rendered.map((w: any) => w.id), removed: s.removed };
});

test.describe('script-injection injector', () => {
  test('a server-rendered form gets one widget before its submit button, and posts the token', async ({ page }) => {
    const served = await serve(page, FORM, { head: SERVER_RENDERED_HEAD });

    const widget = page.locator('#kc-form-login > .cf-turnstile');
    await expect(widget).toHaveCount(1);
    expect(await widget.evaluate((w) => w.nextElementSibling?.id)).toBe('buttons');
    expect(served.apiRequests).toEqual(['https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit']);
    await expect(page.locator('#kc-login')).toBeDisabled();

    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1'], removed: [] });
    await solve(page, 'w1');
    await expect(page.locator('#kc-login')).toBeEnabled();
    // Turnstile's hidden input is inside the widget, inside the form: it is posted with it
    expect(await page.locator('#kc-form-login').evaluate((f: HTMLFormElement) => new FormData(f).get('cf-turnstile-response')))
      .toBe('token-w1');
  });

  test('waits for a form rendered later, and for settings that load after it', async ({ page }) => {
    await serve(page, '<div id="root"></div>');

    // A theme adding the scripts itself: they run in whatever order they arrive
    await page.evaluate(() => (window as any).addScript('/injector.js'));
    await page.waitForFunction(() => (window as any).__cloudflareTurnstileInjector);
    await page.evaluate(() => (window as any).renderForm());
    await page.waitForTimeout(200);
    await expect(page.locator('.cf-turnstile')).toHaveCount(0);

    await page.evaluate(() => (window as any).addScript('/config.js'));
    await expect(page.locator('#kc-form-login .cf-turnstile')).toHaveCount(1);
    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1'], removed: [] });
    await expect(page.locator('#kc-login')).toBeDisabled();
    await solve(page, 'w1');
    await expect(page.locator('#kc-login')).toBeEnabled();
  });

  test('puts the widget back, once, when a re-render replaces the form or drops the widget', async ({ page }) => {
    await serve(page, '<div id="root"></div>', { head: SERVER_RENDERED_HEAD });
    await page.evaluate(() => (window as any).renderForm());
    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1'], removed: [] });
    await solve(page, 'w1');
    await expect(page.locator('#kc-login')).toBeEnabled();

    // A new form (and submit button): a new widget, and the old one removed from Turnstile
    await page.evaluate(() => (window as any).renderForm());
    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1', 'w2'], removed: ['w1'] });
    await expect(page.locator('.cf-turnstile')).toHaveCount(1);
    await expect(page.locator('#kc-login')).toBeDisabled();
    await solve(page, 'w2');
    await expect(page.locator('#kc-login')).toBeEnabled();

    // Only the widget dropped, the form kept: back in the same form, and a new token needed
    await page.locator('.cf-turnstile').evaluate((w) => w.remove());
    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1', 'w2', 'w3'], removed: ['w1', 'w2'] });
    await expect(page.locator('#kc-form-login .cf-turnstile')).toHaveCount(1);
    await expect(page.locator('#kc-login')).toBeDisabled();
    await solve(page, 'w3');
    await expect(page.locator('#kc-login')).toBeEnabled();
    expect(await page.locator('#kc-form-login').evaluate((f: HTMLFormElement) => new FormData(f).getAll('cf-turnstile-response')))
      .toEqual(['token-w3']);
  });

  test('leaves the submit button to a theme that disables it itself, and never enables it without a token', async ({ page }) => {
    await serve(page, FORM.replace('id="kc-login"', 'id="kc-login" disabled'), { head: SERVER_RENDERED_HEAD });
    const button = page.locator('#kc-login');
    const theme = (disabled: boolean) => button.evaluate((b: HTMLInputElement, d) => { b.disabled = d; }, disabled);
    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1'], removed: [] });

    // The theme's own reason (e.g. the form isn't filled in): a token doesn't override it
    await solve(page, 'w1');
    await page.waitForTimeout(100);
    await expect(button).toBeDisabled();
    await theme(false);
    await expect(button).toBeEnabled();

    // Without a token, the theme enabling it isn't enough
    await page.evaluate(() => (window as any).turnstileStub.expire('w1'));
    await expect(button).toBeDisabled();
    await theme(true);
    await theme(false);
    await expect(button).toBeDisabled();
    await solve(page, 'w1');
    await expect(button).toBeEnabled();
  });

  test('leaves the submit button alone with invisible widgets', async ({ page }) => {
    await serve(page, FORM, { head: SERVER_RENDERED_HEAD, mode: 'invisible' });
    await expect.poll(() => stub(page)).toEqual({ rendered: ['w1'], removed: [] });
    await expect(page.locator('#kc-login')).toBeEnabled();
  });

  // Cloudflare's own mode comes from the site key: the setting only picks the appearance, and never
  // asks for an 'invisible' size, which Cloudflare rejects for site keys in other modes
  for (const [mode, appearance, holdsSubmit] of [
    ['always', 'always', true],
    ['interaction-only', 'interaction-only', true],
    ['invisible', 'interaction-only', false],
    ['managed', 'always', true],
    ['non-interactive', 'interaction-only', true],
  ] as const) {
    test(`widget setting "${mode}" renders with appearance ${appearance}`, async ({ page }) => {
      await serve(page, FORM, { head: SERVER_RENDERED_HEAD, mode });
      await expect.poll(() => stub(page)).toEqual({ rendered: ['w1'], removed: [] });

      const options = await page.evaluate(() => {
        const { size, appearance } = (window as any).turnstileStub.rendered[0].options;
        return { size, appearance };
      });
      expect(options).toEqual({ size: 'flexible', appearance });
      if (holdsSubmit) await expect(page.locator('#kc-login')).toBeDisabled();
      else await expect(page.locator('#kc-login')).toBeEnabled();
    });
  }

  test('stays out of a form whose theme renders its own widget', async ({ page }) => {
    const served = await serve(page, FORM.replace('<div class="pf-v5-c-form__group" id="buttons">',
      '<div class="cf-turnstile" data-sitekey="theme"></div><div class="pf-v5-c-form__group" id="buttons">'), { head: SERVER_RENDERED_HEAD });
    await page.waitForTimeout(300);
    await expect(page.locator('.cf-turnstile')).toHaveCount(1);
    await expect(page.locator('#kc-login')).toBeEnabled();
    expect(served.apiRequests).toEqual([]);
  });

  test('stops waiting when no form appears in time', async ({ page }) => {
    await page.clock.install();
    await serve(page, '<div id="root"></div>', { head: SERVER_RENDERED_HEAD });
    await page.clock.runFor(31_000);

    await page.evaluate(() => (window as any).renderForm());
    await page.clock.runFor(1_000);
    await expect(page.locator('#kc-form-login')).toHaveCount(1);
    await expect(page.locator('.cf-turnstile')).toHaveCount(0);
  });
});
