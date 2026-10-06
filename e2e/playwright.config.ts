import { defineConfig, devices } from '@playwright/test';

export const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? 'http://localhost:8080';
export const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025';

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
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
