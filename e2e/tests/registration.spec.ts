import { expect, test } from '@playwright/test';
import { bindRealmFlow } from './admin';
import { submitAndExpectLoggedIn, startFlow, waitForTurnstileToken } from './helpers';

test.describe('registration form action', () => {
  test('requires Turnstile before registering a new user', async ({ page }) => {
    const username = `e2e-reg-${Date.now()}`;
    await startFlow(page, 'script-injection', 'registrations');

    const form = page.locator('#kc-register-form');
    const submit = form.locator('[type="submit"]');
    await expect(form.locator('.cf-turnstile')).toBeVisible();

    await form.locator('#email').fill(`${username}@example.com`);
    await form.locator('#firstName').fill('Reg');
    await form.locator('#lastName').fill('User');
    await form.locator('#username').fill(username);
    await form.locator('#password').fill('Str0ng-e2e-password!');
    await form.locator('#password-confirm').fill('Str0ng-e2e-password!');

    await waitForTurnstileToken(page);
    await expect(submit).toBeEnabled();
    await submitAndExpectLoggedIn(page, submit);
  });
});

test.describe('SEPARATE_PAGE registration', () => {
  test('shows the challenge page before the registration form', async ({ page }) => {
    const username = `e2e-reg-${Date.now()}`;
    const restore = await bindRealmFlow('registrationFlow', 'e2e-registration-separate-page');
    try {
      await startFlow(page, 'separate-page', 'registrations');

      // The authenticator's own page, worded for registration
      await expect(page.locator('#kc-turnstile-registration-form')).toBeVisible();
      await expect(page.locator('#kc-login')).toContainText('Continue to Registration');
      await expect(page.locator('#firstName')).toHaveCount(0);
      await waitForTurnstileToken(page);
      await page.locator('#kc-login').click();

      const form = page.locator('#kc-register-form');
      await form.locator('#email').fill(`${username}@example.com`);
      await form.locator('#firstName').fill('Reg');
      await form.locator('#lastName').fill('User');
      await form.locator('#username').fill(username);
      await form.locator('#password').fill('Str0ng-e2e-password!');
      await form.locator('#password-confirm').fill('Str0ng-e2e-password!');
      await submitAndExpectLoggedIn(page, form.locator('[type="submit"]'));
    } finally {
      await restore();
    }
  });
});
