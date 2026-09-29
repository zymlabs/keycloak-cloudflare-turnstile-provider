import { expect, Page, test } from '@playwright/test';
import { adminApi, bindRealmFlow, createUser } from './admin';
import { messagesTo, waitForActionLink } from './mailpit';
import { startFlow, submitAndExpectLoggedIn, waitForTurnstileToken } from './helpers';

/**
 * Turnstile in the reset-credentials ("Forgot your password?") flow. The realm has one reset
 * flow for all clients, so each test binds its own and restores the previous one afterwards.
 */
let restore: (() => Promise<void>) | undefined;

test.afterEach(async () => {
  await restore?.();
  restore = undefined;
});

const NEW_PASSWORD = 'n3w-e2e-password!';
const EMAIL_SENT = 'You should receive an email shortly with further instructions.';

/** Fills in a new password on Keycloak's update-password page and returns its submit button. */
async function fillNewPassword(page: Page) {
  const form = page.locator('#kc-passwd-update-form');
  await form.locator('#password-new').fill(NEW_PASSWORD);
  await form.locator('#password-confirm').fill(NEW_PASSWORD);
  return form.locator('[type="submit"]').first();
}

test.describe('SEPARATE_PAGE reset', () => {
  test('shows the challenge page before the reset page, and the emailed link skips it', async ({ page, browser }) => {
    const user = await createUser();
    restore = await bindRealmFlow('resetCredentialsFlow', 'e2e-reset-separate-page');

    await startFlow(page, 'separate-page');
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();
    await page.locator('a[href*="reset-credentials"]').click();

    // The challenge page, worded for the reset
    await expect(page.locator('#kc-turnstile-form')).toBeVisible();
    await expect(page.locator('#kc-page-title')).toContainText('Forgot Your Password?');
    await expect(page.locator('#kc-login')).toContainText('Continue');
    await expect(page.locator('#username')).toHaveCount(0);
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();

    // Then Keycloak's own reset page
    const form = page.locator('#kc-reset-password-form');
    await form.locator('#username').fill(user.email);
    await form.locator('[type="submit"]').click();
    await expect(page.getByText(EMAIL_SENT)).toBeVisible();

    // The link, opened in another browser (say, on a phone), already proves who it is: no challenge page
    const phone = await browser.newContext();
    try {
      const phonePage = await phone.newPage();
      await phonePage.goto(await waitForActionLink(user.email));
      await expect(phonePage.locator('#password-new')).toBeVisible();
      await expect(phonePage.locator('#kc-turnstile-form')).toHaveCount(0);
      await (await fillNewPassword(phonePage)).click();
      // Away from the browser that asked for the reset, Keycloak ends with a page of its own
      // (older themes show the message twice: as the page and as its alert)
      await expect(phonePage.getByText('Your account has been updated.').first()).toBeVisible();
    } finally {
      await phone.close();
    }
  });
});

test.describe('SCRIPT_INJECTION reset', () => {
  test('puts the widget on Keycloak\'s reset page', async ({ page }) => {
    const user = await createUser();
    restore = await bindRealmFlow('resetCredentialsFlow', 'e2e-reset-script-injection');

    await startFlow(page, 'script-injection');
    await page.locator('a[href*="reset-credentials"]').click();

    const form = page.locator('#kc-reset-password-form');
    const submit = form.locator('[type="submit"]');
    await expect(form.locator('.cf-turnstile')).toBeVisible();
    await expect(submit).toBeDisabled();

    // Keycloak's own checks still apply, and the page they show again keeps the widget
    await waitForTurnstileToken(page);
    await submit.click();
    await expect(page.getByText('Please specify username.')).toBeVisible();
    await expect(form.locator('.cf-turnstile')).toBeVisible();

    await form.locator('#username').fill(user.username);
    await waitForTurnstileToken(page);
    await submit.click();
    await expect(page.getByText(EMAIL_SENT)).toBeVisible();

    const events: any[] = await (await adminApi('/events?type=SEND_RESET_PASSWORD&max=10')).json();
    expect(events.find((e) => e.details?.username === user.username)?.details).toMatchObject({
      cloudflare_turnstile_success: 'true',
      cloudflare_turnstile_flow_type: 'reset-credentials',
      cloudflare_turnstile_implementation_method: 'SCRIPT_INJECTION',
    });

    // In the browser that asked for the reset, the link continues the sign-in to the application
    await page.goto(await waitForActionLink(user.email));
    await submitAndExpectLoggedIn(page, await fillNewPassword(page));
  });

  test('keeps the widget off the sign-in page Keycloak shows after the request', async ({ page, browser }) => {
    const user = await createUser();
    restore = await bindRealmFlow('resetCredentialsFlow', 'e2e-reset-script-injection');

    // A client whose sign-in starts with the challenge page
    await startFlow(page, 'separate-page');
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();
    await page.locator('a[href*="reset-credentials"]').click();

    const form = page.locator('#kc-reset-password-form');
    await form.locator('#username').fill(user.username);
    await waitForTurnstileToken(page);
    await form.locator('[type="submit"]').click();

    // Keycloak renders the client's sign-in in the same response: its own challenge page, one widget
    await expect(page.getByText(EMAIL_SENT)).toBeVisible();
    await expect(page.locator('#kc-turnstile-form')).toBeVisible();
    await expect(page.locator('.cf-turnstile')).toHaveCount(1);
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();
    await page.locator('#username').fill(user.username);
    await page.locator('#password').fill(user.password);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));

    // The emailed link, in another browser: Keycloak's Choose User skips its page, so no widget either
    const phone = await browser.newContext();
    try {
      const phonePage = await phone.newPage();
      await phonePage.goto(await waitForActionLink(user.email));
      await expect(phonePage.locator('#password-new')).toBeVisible();
      await expect(phonePage.locator('.cf-turnstile')).toHaveCount(0);
      await (await fillNewPassword(phonePage)).click();
      await expect(phonePage.getByText('Your account has been updated.').first()).toBeVisible();
    } finally {
      await phone.close();
    }
  });

  test('sends no email when Cloudflare rejects the token', async ({ page }) => {
    const user = await createUser();
    restore = await bindRealmFlow('resetCredentialsFlow', 'e2e-reset-server-reject');

    await startFlow(page, 'script-injection');
    await page.locator('a[href*="reset-credentials"]').click();

    const form = page.locator('#kc-reset-password-form');
    await form.locator('#username').fill(user.username);
    await waitForTurnstileToken(page);
    await form.locator('[type="submit"]').click();

    await expect(page.getByText('Security verification failed. Please try again.')).toBeVisible();
    // Keycloak sends the email before it answers, so none has been sent
    expect(await messagesTo(user.email)).toHaveLength(0);
  });
});
