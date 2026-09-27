import { expect, test } from '@playwright/test';
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
