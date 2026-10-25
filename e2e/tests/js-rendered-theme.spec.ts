import { expect, Page, test } from '@playwright/test';
import { bindRealmFlow, createUser } from './admin';
import { fillCredentials, startFlow, submitAndExpectLoggedIn, waitForTurnstileToken } from './helpers';
import { messagesTo } from './mailpit';

/**
 * Keycloak's own pages, served as a theme that renders with JavaScript (React, Keycloakify) would:
 * the page arrives without the form, the theme adds Turnstile's scripts itself (so they run in
 * whatever order they load: here the injector first, its settings after it), and renders the form
 * a moment later. The injector has to wait for both.
 */
async function renderLikeAJavaScriptTheme(page: Page, formId: string) {
  await page.route(
    (url) => url.pathname.endsWith('/protocol/openid-connect/auth') || url.pathname.endsWith('/login-actions/reset-credentials'),
    async (route) => {
      if (route.request().method() !== 'GET') return route.continue();
      const response = await route.fetch();
      let html = await response.text();
      const form = html.match(new RegExp(`<form[^>]*id="${formId}"[\\s\\S]*?</form>`))?.[0];
      if (!form) return route.fulfill({ response, body: html });

      const scripts: Record<string, string> = {};
      html = html.replace(/<script src="([^"]*(config\.js|turnstile-injector\.js)[^"]*)"[^>]*><\/script>/g, (_tag, src: string, name: string) => {
        scripts[name] = src.replace(/&amp;/g, '&');
        return '';
      });
      if (!scripts['config.js'] || !scripts['turnstile-injector.js']) {
        throw new Error(`the page has no Turnstile scripts: ${Object.keys(scripts)}`);
      }

      html = html.replace(form, () => `<div id="js-theme-root"></div><template id="js-theme-form">${form}</template>`);
      html = html.replace('</body>', () => `<script>
        (function () {
          function add(src, then) { var s = document.createElement('script'); s.src = src; s.onload = then; document.head.appendChild(s); }
          add(${JSON.stringify(scripts['turnstile-injector.js'])}, function () { add(${JSON.stringify(scripts['config.js'])}); });
          window.renderForm = function () {
            var fresh = document.getElementById('js-theme-form').content.cloneNode(true);
            (document.getElementById(${JSON.stringify(formId)}) || document.getElementById('js-theme-root')).replaceWith(fresh);
          };
          setTimeout(window.renderForm, 1000);
        })();
      </script></body>`);
      return route.fulfill({ response, body: html });
    },
  );
}

test.describe('SCRIPT_INJECTION with a theme that renders the form with JavaScript', () => {
  test('puts the widget on a sign-in form rendered after the scripts ran, and signs in', async ({ page }) => {
    await renderLikeAJavaScriptTheme(page, 'kc-form-login');
    await startFlow(page, 'script-injection');

    await expect(page.locator('#kc-form-login .cf-turnstile')).toHaveCount(1);
    await expect(page.locator('#kc-login')).toBeDisabled();
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await expect(page.locator('#kc-login')).toBeEnabled();
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('puts the widget back when the form is rendered again, with a new token', async ({ page }) => {
    await renderLikeAJavaScriptTheme(page, 'kc-form-login');
    await startFlow(page, 'script-injection');
    await waitForTurnstileToken(page);

    await page.evaluate(() => (window as any).renderForm());
    await expect(page.locator('.cf-turnstile')).toHaveCount(1);
    await expect(page.locator('#kc-form-login .cf-turnstile')).toHaveCount(1);
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await expect(page.locator('#kc-login')).toBeEnabled();
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('puts the widget on a reset form rendered after the scripts ran, and sends the email', async ({ page }) => {
    const restore = await bindRealmFlow('resetCredentialsFlow', 'e2e-reset-script-injection');
    try {
      const user = await createUser();
      await renderLikeAJavaScriptTheme(page, 'kc-reset-password-form');
      await startFlow(page, 'script-injection');
      await page.locator('a[href*="reset-credentials"]').click();

      await expect(page.locator('#kc-reset-password-form .cf-turnstile')).toHaveCount(1);
      await page.locator('#username').fill(user.username);
      await waitForTurnstileToken(page);
      await page.locator('#kc-reset-password-form [type="submit"]').click();
      await expect.poll(async () => (await messagesTo(user.email)).length).toBe(1);
    } finally {
      await restore();
    }
  });
});
