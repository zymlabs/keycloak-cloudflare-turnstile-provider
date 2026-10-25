import { defineConfig, devices } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

// Read the same ../.env docker compose uses, so tests target the ports it published
// (explicit environment variables still win).
const envFile = join(__dirname, '..', '.env');
if (existsSync(envFile)) {
  for (const line of readFileSync(envFile, 'utf8').split('\n')) {
    const match = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (match && process.env[match[1]] === undefined) process.env[match[1]] = match[2];
  }
}

export const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? `http://localhost:${process.env.KC_HTTP_PORT ?? '8080'}`;
export const MAILPIT_URL = process.env.MAILPIT_URL ?? `http://localhost:${process.env.MAILPIT_UI_PORT ?? '8025'}`;

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  // Tests share one realm and Keycloak instance; keep them serial to avoid session interference
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  // The widget talks to the real challenges.cloudflare.com, so allow a retry for network hiccups in CI
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: KEYCLOAK_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // Screenshots for the docs (`npm run docs-screens`): only listed when asked for, so the normal run never includes them
    ...(process.env.DOCS_SCREENS ? [{
      name: 'docs-screens',
      testDir: './docs',
      use: { ...devices['Desktop Chrome'] },
    }] : []),
  ],
});
