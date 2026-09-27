import { expect, test } from '@playwright/test';
import { adminToken } from '../global-setup';
import { KEYCLOAK_URL } from '../playwright.config';
import { submitAndExpectLoggedIn, fillCredentials, REALM, startFlow, waitForTurnstileToken } from './helpers';

test.describe('SCRIPT_INJECTION login', () => {
  test('injects the widget into the login form and logs in once verified', async ({ page }) => {
    await startFlow(page, 'script-injection');

    await expect(page.locator('#kc-form-login .cf-turnstile')).toBeVisible();
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await expect(page.locator('#kc-login')).toBeEnabled();

    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('keeps the submit button disabled when Turnstile cannot load', async ({ page }) => {
    await page.route('https://challenges.cloudflare.com/**', (route) => route.abort());
    await startFlow(page, 'script-injection');

    await fillCredentials(page);
    await expect(page.locator('#kc-login')).toBeDisabled();
    // Give a late-loading widget every chance to (wrongly) enable the button
    await page.waitForTimeout(3_000);
    await expect(page.locator('#kc-login')).toBeDisabled();
  });

  test('re-renders the widget after a wrong password', async ({ page }) => {
    await startFlow(page, 'script-injection');

    await fillCredentials(page, 'e2e-user', 'wrong-password');
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();

    await expect(page.getByText('Invalid username or password.')).toBeVisible();
    await expect(page.locator('#kc-form-login .cf-turnstile')).toBeVisible();

    // A fresh token is required for the retry
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('records Turnstile details on the login event', async ({ page }) => {
    await startFlow(page, 'script-injection');
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));

    const token = await adminToken();
    const events: any[] = await (
      await fetch(`${KEYCLOAK_URL}/admin/realms/${REALM}/events?type=LOGIN&client=e2e-script-injection&max=1`, {
        headers: { Authorization: `Bearer ${token}` },
      })
    ).json();

    expect(events[0]?.details).toMatchObject({
      cloudflare_turnstile_success: 'true',
      cloudflare_turnstile_flow_type: 'login',
      cloudflare_turnstile_implementation_method: 'SCRIPT_INJECTION',
      cloudflare_turnstile_authentication_allowed: 'true',
    });
  });
});

test.describe('SEPARATE_PAGE login', () => {
  test('shows the challenge page before the username/password form', async ({ page }) => {
    await startFlow(page, 'separate-page');

    await expect(page.locator('#kc-turnstile-form')).toBeVisible();
    await expect(page.locator('#username')).toHaveCount(0);
    await expect(page.locator('#kc-login')).toBeDisabled();

    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();

    await fillCredentials(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });
});

test.describe('CUSTOM_THEME login', () => {
  test('renders the widget from the bundled theme and logs in', async ({ page }) => {
    await startFlow(page, 'custom-theme');

    await expect(page.locator('#kc-form-login .cf-turnstile')).toBeVisible();
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });
});

test.describe('server-side enforcement', () => {
  test('blocks login when Cloudflare rejects the token (failAction BLOCK)', async ({ page }) => {
    // Site key passes in the browser, but the secret key always fails siteverify
    await startFlow(page, 'server-reject');

    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();

    await expect(page.getByText('Security verification failed. Please try again.')).toBeVisible();
    // The redirect_uri appears in Keycloak's own URLs, so check where the page is, not what the URL contains
    expect(new URL(page.url()).host).not.toBe('e2e.test');
  });

  test('blocks blocklisted IPs before showing the widget', async ({ page }) => {
    await startFlow(page, 'ip-blocked');

    await expect(page.getByText('Access denied. Your IP address is blocked.')).toBeVisible();
    await expect(page.locator('.cf-turnstile')).toHaveCount(0);
  });
});
