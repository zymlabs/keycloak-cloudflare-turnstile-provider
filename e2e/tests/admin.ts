import { adminToken } from '../global-setup';
import { KEYCLOAK_URL } from '../playwright.config';
import { REALM } from './helpers';

/** Calls the e2e realm's admin API (path relative to /admin/realms/{realm}). */
export async function adminApi(path: string, init: RequestInit = {}): Promise<Response> {
  const res = await fetch(`${KEYCLOAK_URL}/admin/realms/${REALM}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${await adminToken()}`, 'Content-Type': 'application/json', ...init.headers },
  });
  if (!res.ok) throw new Error(`${init.method ?? 'GET'} ${path} failed: ${res.status} ${await res.text()}`);
  return res;
}

export interface TestUser {
  username: string;
  email: string;
  password: string;
}

/** A user of its own, so tests that change a password leave the shared e2e user alone. */
export async function createUser(requiredActions: string[] = []): Promise<TestUser> {
  const username = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const user = { username, email: `${username}@example.com`, password: 'e2e-password' };
  await adminApi('/users', {
    method: 'POST',
    body: JSON.stringify({
      username,
      email: user.email,
      emailVerified: true,
      enabled: true,
      firstName: 'E2E',
      lastName: 'User',
      credentials: [{ type: 'password', value: user.password, temporary: false }],
      requiredActions,
    }),
  });
  return user;
}

/**
 * Binds one of the realm's flows (clients can override only the browser and direct-grant flows),
 * and returns a function that restores the previous binding.
 */
export async function bindRealmFlow(
  binding: 'resetCredentialsFlow' | 'registrationFlow',
  alias: string,
): Promise<() => Promise<void>> {
  const previous = (await (await adminApi('')).json())[binding];
  await adminApi('', { method: 'PUT', body: JSON.stringify({ [binding]: alias }) });
  return async () => {
    await adminApi('', { method: 'PUT', body: JSON.stringify({ [binding]: previous }) });
  };
}

/** The running Keycloak's major version. */
export async function keycloakMajor(): Promise<number> {
  const info = await (await fetch(`${KEYCLOAK_URL}/admin/serverinfo`, {
    headers: { Authorization: `Bearer ${await adminToken()}` },
  })).json();
  return parseInt(info.systemInfo.version, 10);
}

/** Changes realm settings and returns a function that puts the previous values back. */
export async function updateRealm(changes: Record<string, unknown>): Promise<() => Promise<void>> {
  const current = await (await adminApi('')).json();
  const previous = Object.fromEntries(Object.keys(changes).map((key) => [key, current[key] ?? null]));
  await adminApi('', { method: 'PUT', body: JSON.stringify(changes) });
  return async () => {
    await adminApi('', { method: 'PUT', body: JSON.stringify(previous) });
  };
}
