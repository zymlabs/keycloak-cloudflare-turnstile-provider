import { expect, Locator, Page } from '@playwright/test';

export const REALM = 'turnstile-e2e';
export const USER = { username: 'e2e-user', password: 'e2e-password' };

// Nothing listens here: tests only assert that Keycloak redirects to it with an authorization code
const REDIRECT_URI = 'http://e2e.test/callback';

type Endpoint = 'auth' | 'registrations';

/** Starts an authorization-code flow for a scenario client (each client is bound to its own browser flow). */
export async function startFlow(page: Page, client: string, endpoint: Endpoint = 'auth') {
  const params = new URLSearchParams({
    client_id: `e2e-${client}`,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    scope: 'openid',
  });
  await page.goto(`/realms/${REALM}/protocol/openid-connect/${endpoint}?${params}`);
}

/** Waits for the Turnstile test key to issue a token (it always passes, but loads asynchronously). */
export async function waitForTurnstileToken(page: Page) {
  // Turnstile renders a hidden input with the token next to the widget iframe
  const token = page.locator('input[name="cf-turnstile-response"]').first();
  await expect(token).toHaveValue(/.+/, { timeout: 30_000 });
}

export async function fillCredentials(page: Page, username = USER.username, password = USER.password) {
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
}

/**
 * Submits and waits for Keycloak's redirect back to the client with an authorization code.
 * Watches the request rather than the page: page.route() can't stub navigations that follow a
 * server redirect, so the callback itself fails to load, which is irrelevant here.
 */
export async function submitAndExpectLoggedIn(page: Page, submit: Locator) {
  const callback = page.waitForRequest(
    (req) => req.url().startsWith(REDIRECT_URI) && new URL(req.url()).searchParams.has('code'),
  );
  await submit.click();
  await callback;
}
