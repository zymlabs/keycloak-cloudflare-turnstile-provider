import { expect, test } from '@playwright/test';
import { adminApi, createUser, keycloakMajor, updateRealm } from './admin';
import { Page } from '@playwright/test';
import { fillCredentials, startFlow, submitAndExpectLoggedIn, USER, waitForTurnstileToken } from './helpers';

const BLOCKED = 'Security verification failed. Please try again.';

/** Takes the solved token off the page and enables Sign In: what a script skipping the check would post. */
async function dropToken(page: Page) {
  await page.evaluate(() => {
    document.querySelectorAll('input[name="cf-turnstile-response"]').forEach((input) => input.remove());
    (document.querySelector('#kc-login') as HTMLButtonElement).disabled = false;
  });
}

/** Posts what Keycloak's passkey form posts when the browser reports an error, to the sign-in page's action. */
async function postPasskeyError(page: Page) {
  await page.evaluate(() => {
    const form = document.createElement('form');
    form.method = 'post';
    form.action = (document.querySelector('#kc-form-login') as HTMLFormElement).action;
    const error = document.createElement('input');
    error.name = 'error';
    error.value = 'NotAllowedError';
    form.appendChild(error);
    document.body.appendChild(form);
    form.submit();
  });
}

async function failedSignIns(username: string): Promise<number> {
  const [user] = await (await adminApi(`/users?username=${encodeURIComponent(username)}&exact=true`)).json();
  return (await (await adminApi(`/attack-detection/brute-force/users/${user.id}`)).json()).numFailures;
}

/**
 * With the widget inline, the sign-in page is Keycloak's own username/password form (its step
 * builds the page and checks the password), so what that form does keeps working.
 */
test.describe('inline sign-in page is Keycloak\'s own form', () => {
  let restore: (() => Promise<void>) | undefined;

  test.afterEach(async () => {
    await restore?.();
    restore = undefined;
  });

  test('fills in the username from login_hint', async ({ page }) => {
    await startFlow(page, 'script-injection', 'auth', { login_hint: USER.username });

    await expect(page.locator('#username')).toHaveValue(USER.username);
    await expect(page.locator('#kc-form-login .cf-turnstile')).toBeVisible();
    await page.locator('#password').fill(USER.password);
    await waitForTurnstileToken(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('asks only for the password when an earlier step identified the user', async ({ page }) => {
    // Keycloak's username form first, then the Turnstile step
    await startFlow(page, 'username-first');
    await page.locator('#username').fill(USER.username);
    await page.locator('#kc-login').click();

    await expect(page.locator('#kc-form-login .cf-turnstile')).toBeVisible();
    await expect(page.locator('#username')).toHaveCount(0);
    await page.locator('#password').fill(USER.password);
    await waitForTurnstileToken(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('the bundled theme asks only for the password when an earlier step identified the user', async ({ page }) => {
    await startFlow(page, 'custom-theme-username-first');
    await page.locator('#username').fill(USER.username);
    await page.locator('#kc-login').click();

    await expect(page.locator('#kc-form-login .cf-turnstile')).toBeVisible();
    await expect(page.locator('#username')).toHaveCount(0);
    await page.locator('#password').fill(USER.password);
    await waitForTurnstileToken(page);
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });

  test('refuses a password posted without a token', async ({ page }) => {
    await startFlow(page, 'script-injection');
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await dropToken(page);
    await page.locator('#kc-login').click();

    await expect(page.getByText(BLOCKED)).toBeVisible();
  });

  test('refused and passkey posts don\'t count toward locking the account', async ({ page }) => {
    restore = await updateRealm({ bruteForceProtected: true });
    const user = await createUser();

    // One real wrong password counts (and names the account for this sign-in)
    await startFlow(page, 'script-injection');
    await fillCredentials(page, user.username, 'wrong-password');
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();
    await expect(page.getByText('Invalid username or password.')).toBeVisible();
    await expect.poll(() => failedSignIns(user.username)).toBe(1);

    // A passkey error (not checked by Turnstile) and a post without a token (refused) don't
    await postPasskeyError(page);
    await expect(page.locator('#kc-form-login')).toBeVisible();
    await fillCredentials(page, user.username, 'wrong-password');
    await waitForTurnstileToken(page);
    await dropToken(page);
    await page.locator('#kc-login').click();
    await expect(page.getByText(BLOCKED)).toBeVisible();
    expect(await failedSignIns(user.username)).toBe(1);
  });

  test('leaves the widget off a page a later step shows in the same response', async ({ page }) => {
    // Keycloak's deny-access step runs right after the sign-in and shows its error page
    await startFlow(page, 'then-deny');
    await fillCredentials(page);
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();

    await expect(page.locator('#kc-form-login')).toHaveCount(0);
    await expect(page.locator('#kc-error-message, .alert-error, .pf-v5-c-alert').first()).toBeVisible();
    await expect(page.locator('script[src*="turnstile-injector"]')).toHaveCount(0);
    await expect(page.locator('.cf-turnstile')).toHaveCount(0);
  });

  test('signs in with a passkey, which carries no Turnstile token', async ({ page }) => {
    test.skip((await keycloakMajor()) < 26, 'Passkeys on the username/password form arrived in Keycloak 26');
    // Keycloak applies the passwordless policy only together with its relying party name
    restore = await updateRealm({ webAuthnPolicyPasswordlessRpEntityName: 'keycloak', webAuthnPolicyPasswordlessPasskeysEnabled: true });
    const user = await createUser(['webauthn-register-passwordless']);

    // A virtual authenticator that answers at once, as if the user touched it
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('WebAuthn.enable');
    await cdp.send('WebAuthn.addVirtualAuthenticator', {
      options: { protocol: 'ctap2', transport: 'internal', hasResidentKey: true, hasUserVerification: true,
        isUserVerified: true, automaticPresenceSimulation: true },
    });
    page.on('dialog', (dialog) => dialog.accept()); // the passkey's label

    // Sign in with the password, then register a passkey (Keycloak's required action)
    await startFlow(page, 'script-injection');
    await fillCredentials(page, user.username, user.password);
    await waitForTurnstileToken(page);
    await page.locator('#kc-login').click();
    await submitAndExpectLoggedIn(page, page.locator('#registerWebAuthn'));

    // Next sign-in, signed out (the browser keeps its authenticator): the passkey alone.
    // First let the redirect to the app (which nothing serves) finish failing.
    await expect.poll(() => page.url()).toMatch(/^chrome-error:/);
    await page.context().clearCookies();
    const signedIn = page.waitForRequest((req) => req.url().startsWith('http://e2e.test/') && new URL(req.url()).searchParams.has('code'));
    // Keycloak's page offers the passkey in the browser's autofill, which the virtual authenticator
    // accepts as soon as the page loads (so the page may already be gone); else its passkey button
    await startFlow(page, 'script-injection').catch(() => {});
    await page.locator('#authenticateWebAuthnButton').click({ timeout: 5_000 }).catch(() => {});
    await signedIn;
  });
});
