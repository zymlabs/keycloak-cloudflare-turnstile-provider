import { Browser, BrowserContext, expect, Page, Route, test } from '@playwright/test';
import { KEYCLOAK_URL } from '../playwright.config';
import { admin, fitViewport, once, shot, widgetSettled, writeManifest } from './support';

/**
 * Screenshots for the docs (docs/images, listed in docs/images/manifest.json, shown in
 * docs/screens.md). Run with `npm run docs-screens` against the compose stack; never part of the
 * normal e2e run.
 *
 * Everything is shown as a buyer sees it out of the box: Keycloak's default login theme and admin
 * console, in a realm with neutral demo names. Each run deletes and rebuilds the realm, and the
 * widget runs on Cloudflare's test site keys, so the shots come out the same every time. The few
 * values that differ per run anyway (event times and ids, the visitor's address) are replaced in
 * what the admin console is sent, see `steadyEvents`.
 */
test.describe.configure({ mode: 'default' });
test.setTimeout(120_000);
test.use({
  actionTimeout: 20_000,
  launchOptions: {
    // Skia otherwise picks image-scaling code for the CPU it runs on, so the background could come
    // out a shade different on another machine (CI runners vary); plain sRGB and no GPU likewise
    args: ['--disable-skia-runtime-opts', '--force-color-profile=srgb', '--disable-gpu'],
  },
});

const REALM = 'northwind';
const PASSWORD = 'Northwind-Passw0rd!';
const DESKTOP = { width: 1280, height: 800 };
const PHONE = { width: 390, height: 844 };
const ADMIN = { width: 1440, height: 900 };
// Nothing listens here: a finished sign-in only needs to be redirected to it
const REDIRECT_URI = 'https://app.northwind.test/callback';

// Cloudflare's documented test keys (https://developers.cloudflare.com/turnstile/troubleshooting/testing/)
const SITE_KEY = {
  pass: '1x00000000000000000000AA',
  block: '2x00000000000000000000AB',
  interactive: '3x00000000000000000000FF',
};
const SECRET_KEY = {
  pass: '1x0000000000000000000000000000000AA',
  fail: '2x0000000000000000000000000000000AA',
};

const JORDAN = {
  // A fixed id, so it reads the same in the admin console every run
  id: '6b0c1f8e-3d2a-4c5b-9e7f-1a2b3c4d5e6f',
  username: 'jordan.lee', email: 'jordan.lee@northwind.test', firstName: 'Jordan', lastName: 'Lee',
};

/** A step's configuration as an administrator would set it up, with `changes` on top. */
const stepConfig = (alias: string, changes: Record<string, string> = {}) => ({
  alias,
  config: {
    useRealmSettings: 'false',
    siteKey: SITE_KEY.pass,
    secretKey: SECRET_KEY.pass,
    implementationMethod: 'SCRIPT_INJECTION',
    widgetMode: 'always',
    widgetTheme: 'auto',
    recordVerifications: 'true',
    // An office network; not the stack's own addresses, which would be let through
    ipAllowlist: '198.51.100.0/24',
    allowlistBehavior: 'VERIFY_BUT_ALLOW',
    ipBlocklist: '',
    failAction: 'BLOCK',
    failMode: 'FAIL_CLOSED',
    connectTimeout: '5000',
    readTimeout: '5000',
    enableDebugLogging: 'false',
    ...changes,
  },
});

const step = (authenticator: string, priority: number, requirement = 'REQUIRED', authenticatorConfig?: string) => ({
  authenticator, authenticatorFlow: false, requirement, priority, userSetupAllowed: false,
  ...(authenticatorConfig ? { authenticatorConfig } : {}),
});
const subFlow = (flowAlias: string, priority: number, requirement = 'ALTERNATIVE', authenticator?: string) => ({
  flowAlias, authenticatorFlow: true, requirement, priority, userSetupAllowed: false,
  ...(authenticator ? { authenticator } : {}),
});
const flow = (alias: string, description: string, executions: any[], topLevel = true, providerId = 'basic-flow') => ({
  alias, description, providerId, topLevel, builtIn: false, authenticationExecutions: executions,
});

/** A browser flow like Keycloak's own, with `forms` as its sign-in form sub-flow. */
const browserFlow = (alias: string, description: string, forms: any[]) => [
  flow(alias, description, [
    step('auth-cookie', 10, 'ALTERNATIVE'),
    step('identity-provider-redirector', 25, 'ALTERNATIVE'),
    subFlow(`${alias} forms`, 30),
  ]),
  flow(`${alias} forms`, 'Username, password and Cloudflare Turnstile', forms, false),
];

/** Sign-in flows by scenario; each has a client (see `signIn`) bound to it. */
const SCENARIOS: Record<string, { flows: any[]; config?: ReturnType<typeof stepConfig>; theme?: string }> = {
  // The main flow: the widget on Keycloak's own sign-in form, with conditional OTP as in Keycloak's browser flow
  inline: {
    flows: [
      ...browserFlow('browser with Turnstile', 'Keycloak\'s browser flow, with Cloudflare Turnstile on the sign-in form', [
        step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile'),
        subFlow('browser with Turnstile conditional OTP', 20, 'CONDITIONAL'),
      ]),
      flow('browser with Turnstile conditional OTP', 'One-time code for users who set one up', [
        step('conditional-user-configured', 10),
        step('auth-otp-form', 20),
      ], false),
    ],
  },
  page: {
    flows: browserFlow('browser with Turnstile page', 'A Cloudflare Turnstile page before the sign-in form', [
      step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile page'),
      step('auth-username-password-form', 20),
    ]),
    config: stepConfig('turnstile page', { implementationMethod: 'SEPARATE_PAGE' }),
  },
  theme: {
    flows: browserFlow('browser with Turnstile theme', 'The widget from the bundled login theme', [
      step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile theme'),
    ]),
    config: stepConfig('turnstile theme', { implementationMethod: 'CUSTOM_THEME' }),
    theme: 'cloudflare-turnstile',
  },
  interactive: {
    flows: browserFlow('browser with Turnstile interactive', 'Cloudflare asks every visitor to click', [
      step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile interactive'),
    ]),
    config: stepConfig('turnstile interactive', { siteKey: SITE_KEY.interactive }),
  },
  rejected: {
    flows: browserFlow('browser with Turnstile rejecting', 'Cloudflare rejects every token', [
      step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile rejecting'),
    ]),
    config: stepConfig('turnstile rejecting', { secretKey: SECRET_KEY.fail }),
  },
  'widget-fails': {
    flows: browserFlow('browser with Turnstile failing', 'The widget fails every visitor', [
      step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile failing'),
    ]),
    config: stepConfig('turnstile failing', { siteKey: SITE_KEY.block }),
  },
  blocked: {
    flows: browserFlow('browser with Turnstile blocklist', 'Every address is on the blocklist', [
      step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile blocklist'),
    ]),
    config: stepConfig('turnstile blocklist', { ipBlocklist: '0.0.0.0/0,::/0' }),
  },
};

const REGISTRATION = {
  inline: 'registration with Turnstile',
  page: 'registration with Turnstile page',
};
const RESET = {
  inline: 'reset credentials with Turnstile',
  page: 'reset credentials with Turnstile page',
};

const registrationFlows = [
  flow(REGISTRATION.inline, 'Keycloak\'s registration flow, with Cloudflare Turnstile on the form', [
    subFlow(`${REGISTRATION.inline} form`, 10, 'REQUIRED', 'registration-page-form'),
  ]),
  flow(`${REGISTRATION.inline} form`, 'Registration form', [
    step('registration-user-creation', 20),
    step('registration-password-action', 50),
    step('cloudflare-turnstile-form-action', 60, 'REQUIRED', 'turnstile registration'),
  ], false, 'form-flow'),
  flow(REGISTRATION.page, 'A Cloudflare Turnstile page before the registration form', [
    step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile page'),
    subFlow(`${REGISTRATION.page} form`, 20, 'REQUIRED', 'registration-page-form'),
  ]),
  flow(`${REGISTRATION.page} form`, 'Registration form', [
    step('registration-user-creation', 20),
    step('registration-password-action', 50),
  ], false, 'form-flow'),
];

const resetFlows = [
  // Inline, the Turnstile step takes the place of Choose User (and reuses it)
  flow(RESET.inline, 'Keycloak\'s reset flow, with Cloudflare Turnstile on the reset page', [
    step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile'),
    step('reset-credential-email', 20),
    step('reset-password', 30),
  ]),
  flow(RESET.page, 'A Cloudflare Turnstile page before the reset page', [
    step('cloudflare-turnstile-authenticator', 10, 'REQUIRED', 'turnstile page'),
    step('reset-credentials-choose-user', 20),
    step('reset-credential-email', 30),
    step('reset-password', 40),
  ]),
];

let browser: Browser;

test.beforeAll(async ({ browser: b }) => {
  browser = b;
  await once('northwind', buildNorthwind);
});

test.afterAll(() => writeManifest());

async function buildNorthwind() {
  await admin('DELETE', `/${REALM}`).catch(() => undefined);
  const configs = [
    stepConfig('turnstile'),
    stepConfig('turnstile registration'),
    ...Object.values(SCENARIOS).flatMap((s) => (s.config ? [s.config] : [])),
  ];
  await admin('POST', '', {
    realm: REALM,
    displayName: 'Northwind',
    enabled: true,
    registrationAllowed: true,
    loginWithEmailAllowed: true,
    resetPasswordAllowed: true,
    eventsEnabled: true,
    smtpServer: { host: 'mailpit', port: '1025', from: 'no-reply@northwind.test', fromDisplayName: 'Northwind' },
    // Keycloak's default policy plus Cloudflare's frame; with no script-src, the widget's script is already allowed
    browserSecurityHeaders: {
      contentSecurityPolicy: 'frame-src \'self\' https://challenges.cloudflare.com; frame-ancestors \'self\'; object-src \'none\';',
      xFrameOptions: 'SAMEORIGIN',
    },
    authenticationFlows: [...Object.values(SCENARIOS).flatMap((s) => s.flows), ...registrationFlows, ...resetFlows],
    authenticatorConfig: configs,
    browserFlow: 'browser with Turnstile',
    registrationFlow: REGISTRATION.inline,
    resetCredentialsFlow: RESET.inline,
    clients: Object.keys(SCENARIOS).map((scenario) => ({
      clientId: scenario === 'inline' ? 'portal' : `portal-${scenario}`,
      name: 'Northwind portal',
      enabled: true,
      publicClient: true,
      standardFlowEnabled: true,
      redirectUris: [REDIRECT_URI],
      ...(SCENARIOS[scenario].theme ? { attributes: { login_theme: SCENARIOS[scenario].theme } } : {}),
    })),
    users: [{
      ...JORDAN, enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: PASSWORD, temporary: false }],
    }],
    // The realm-wide settings (Realm settings > Cloudflare Turnstile)
    components: {
      'org.keycloak.services.ui.extend.UiTabProvider': [{
        name: 'cloudflare-turnstile-settings',
        providerId: 'cloudflare-turnstile-settings',
        subComponents: {},
        config: {
          siteKey: [SITE_KEY.pass], secretKey: [SECRET_KEY.pass], widgetMode: ['always'], widgetTheme: ['auto'],
          // An office network; not the stack's own addresses, which would be let through
          recordVerifications: ['true'], ipAllowlist: ['198.51.100.0/24'], allowlistBehavior: ['VERIFY_BUT_ALLOW'],
          ipBlocklist: [''], failAction: ['BLOCK'], failMode: ['FAIL_CLOSED'],
        },
      }],
    },
  });
  // Bind each scenario's client to its flow (flow ids are known only after import)
  const flows: { id: string; alias: string }[] = await admin('GET', `/${REALM}/authentication/flows`);
  const clients: any[] = await admin('GET', `/${REALM}/clients`);
  for (const [scenario, { flows: [top] }] of Object.entries(SCENARIOS)) {
    if (scenario === 'inline') continue;
    const client = clients.find((c) => c.clientId === `portal-${scenario}`);
    client.authenticationFlowBindingOverrides = { browser: flows.find((f) => f.alias === top.alias)!.id };
    await admin('PUT', `/${REALM}/clients/${client.id}`, client);
  }
}

async function context(viewport = DESKTOP): Promise<BrowserContext> {
  return browser.newContext({ viewport, colorScheme: 'light', locale: 'en-US', timezoneId: 'UTC' });
}

/** Opens the sign-in page of a scenario's client. */
async function signIn(page: Page, scenario: keyof typeof SCENARIOS = 'inline', endpoint: 'auth' | 'registrations' = 'auth') {
  const params = new URLSearchParams({
    client_id: scenario === 'inline' ? 'portal' : `portal-${scenario}`,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    scope: 'openid',
  });
  await page.goto(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/${endpoint}?${params}`);
}

async function fillCredentials(page: Page, password = PASSWORD) {
  await page.locator('#username').fill(JORDAN.username);
  await page.locator('#password').fill(password);
}

/** Submits and waits for the redirect back to the app (which isn't there: only the request matters). */
async function submitAndExpectSignedIn(page: Page) {
  const callback = page.waitForRequest((req) => req.url().startsWith(REDIRECT_URI) && new URL(req.url()).searchParams.has('code'));
  await page.locator('#kc-login').click();
  await callback;
}

// ------------------------------------------------------------------ Sign-in

test('sign-in: widget on the sign-in form', async () => {
  const ctx = await context();
  const page = await ctx.newPage();
  await signIn(page);
  await fillCredentials(page);
  await widgetSettled(page);
  await shot(page, 'sign-in', 'inline-widget', {
    section: 'Sign-in', title: 'Widget on the sign-in form',
    caption: 'Script injection: the widget on Keycloak\'s own sign-in form. Sign In is enabled once Turnstile has a token.',
  });
  await ctx.close();
});

test('sign-in: separate page, bundled theme, interactive challenge and phone', async () => {
  let ctx = await context();
  let page = await ctx.newPage();
  await signIn(page, 'page');
  await expect(page.locator('#kc-turnstile-form')).toBeVisible();
  await widgetSettled(page);
  await shot(page, 'sign-in', 'separate-page', {
    section: 'Sign-in', title: 'Separate verification page',
    caption: 'Separate page: Turnstile on a page of its own, before Keycloak\'s sign-in form.',
  });
  await ctx.close();

  ctx = await context();
  page = await ctx.newPage();
  await signIn(page, 'theme');
  await fillCredentials(page);
  await widgetSettled(page);
  await shot(page, 'sign-in', 'custom-theme', {
    section: 'Sign-in', title: 'Bundled login theme',
    caption: 'Custom theme: the cloudflare-turnstile login theme renders the widget itself, without script injection.',
  });
  await ctx.close();

  ctx = await context();
  page = await ctx.newPage();
  await signIn(page, 'interactive');
  await fillCredentials(page);
  await widgetSettled(page, false);
  await shot(page, 'sign-in', 'interactive-challenge', {
    section: 'Sign-in', title: 'Interactive challenge',
    caption: 'When Cloudflare wants a click, the widget asks for one; Sign In stays disabled until the visitor passes.',
  });
  await ctx.close();

  ctx = await context(PHONE);
  page = await ctx.newPage();
  await signIn(page);
  await fillCredentials(page);
  await widgetSettled(page);
  await shot(page, 'sign-in', 'phone', {
    section: 'Sign-in', title: 'On a phone', caption: 'The sign-in form with the widget on a phone.',
  });
  await ctx.close();
});

// ------------------------------------------------------------------ Registration

test('registration: widget on the form and separate page', async () => {
  let ctx = await context();
  let page = await ctx.newPage();
  await signIn(page, 'inline', 'registrations');
  const form = page.locator('#kc-register-form');
  await form.locator('#username').fill('riley.chen');
  await form.locator('#password').fill(PASSWORD);
  await form.locator('#password-confirm').fill(PASSWORD);
  await form.locator('#email').fill('riley.chen@northwind.test');
  await form.locator('#firstName').fill('Riley');
  await form.locator('#lastName').fill('Chen');
  await widgetSettled(page);
  await page.setViewportSize({ width: DESKTOP.width, height: await page.evaluate(() => document.documentElement.scrollHeight) });
  await shot(page, 'registration', 'inline-widget', {
    section: 'Registration', title: 'Widget on the registration form',
    caption: 'The registration form action puts the widget on Keycloak\'s registration form.',
  });
  await ctx.close();

  await admin('PUT', `/${REALM}`, { registrationFlow: REGISTRATION.page });
  try {
    ctx = await context();
    page = await ctx.newPage();
    await signIn(page, 'inline', 'registrations');
    await expect(page.locator('#kc-turnstile-registration-form')).toBeVisible();
    await widgetSettled(page);
    await shot(page, 'registration', 'separate-page', {
      section: 'Registration', title: 'Separate verification page',
      caption: 'Separate page: Turnstile before the registration form, which then has no widget.',
    });
    await ctx.close();
  } finally {
    await admin('PUT', `/${REALM}`, { registrationFlow: REGISTRATION.inline });
  }
});

// ------------------------------------------------------------------ Password reset

test('password reset: widget on the reset page and separate page', async () => {
  let ctx = await context();
  let page = await ctx.newPage();
  await signIn(page);
  await page.locator('a[href*="reset-credentials"]').click();
  await page.locator('#kc-reset-password-form #username').fill(JORDAN.email);
  await widgetSettled(page);
  await shot(page, 'password-reset', 'inline-widget', {
    section: 'Password reset', title: 'Widget on the reset page',
    caption: 'Script injection: the widget on Keycloak\'s "Forgot your password?" page, before any email is sent.',
  });
  await ctx.close();

  await admin('PUT', `/${REALM}`, { resetCredentialsFlow: RESET.page });
  try {
    ctx = await context();
    page = await ctx.newPage();
    await signIn(page);
    await page.locator('a[href*="reset-credentials"]').click();
    await expect(page.locator('#kc-turnstile-form')).toBeVisible();
    await widgetSettled(page);
    await shot(page, 'password-reset', 'separate-page', {
      section: 'Password reset', title: 'Separate verification page',
      caption: 'Separate page: Turnstile, worded for the reset, before Keycloak\'s reset page.',
    });
    await ctx.close();
  } finally {
    await admin('PUT', `/${REALM}`, { resetCredentialsFlow: RESET.inline });
  }
});

// ------------------------------------------------------------------ Refusals

test('refusals: failed verification, failed widget, blocked address', async () => {
  let ctx = await context();
  let page = await ctx.newPage();
  await signIn(page, 'rejected');
  await fillCredentials(page);
  await widgetSettled(page);
  await page.locator('#kc-login').click();
  await expect(page.getByText('Security verification failed. Please try again.')).toBeVisible();
  await shot(page, 'refusals', 'verification-failed', {
    section: 'Refusals', title: 'Verification failed',
    caption: 'Cloudflare rejected the token and the Verification Failure Action is BLOCK: the sign-in ends on Keycloak\'s error page.',
  });
  await ctx.close();

  ctx = await context();
  page = await ctx.newPage();
  await signIn(page, 'widget-fails');
  await fillCredentials(page);
  await widgetSettled(page, false);
  await expect(page.locator('#kc-login')).toBeDisabled();
  await shot(page, 'refusals', 'challenge-failed', {
    section: 'Refusals', title: 'Challenge failed in the browser',
    caption: 'The widget itself failed the visitor: there is no token, so Sign In stays disabled.',
  });
  await ctx.close();

  ctx = await context();
  page = await ctx.newPage();
  await signIn(page, 'blocked');
  await expect(page.getByText('Access denied. Your IP address is blocked.')).toBeVisible();
  await shot(page, 'refusals', 'ip-blocked', {
    section: 'Refusals', title: 'Blocked address',
    caption: 'An address on the IP blocklist is refused before the widget is even shown.',
  });
  await ctx.close();
});

// ------------------------------------------------------------------ Admin console

/** Signs in to the admin console as the master administrator, at a page of the Northwind realm. */
async function adminConsole(page: Page, path: string) {
  await page.goto(`${KEYCLOAK_URL}/admin/master/console/#/${REALM}/${path}`);
  await page.locator('#username').fill(process.env.KEYCLOAK_ADMIN ?? 'admin');
  await page.locator('#password').fill(process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin');
  await page.locator('#kc-login').click();
  await hideTemporaryAdminBanner(page);
}

/** The stack's bootstrap admin gets a "temporary admin" warning that a real installation doesn't show. */
async function hideTemporaryAdminBanner(page: Page) {
  await page.addStyleTag({ content: '.pf-v5-c-banner, .pf-v6-c-banner { display: none !important; }' });
}

async function settled(page: Page) {
  await page.waitForLoadState('networkidle');
  await expect(page.locator('.pf-v5-c-spinner, .pf-v6-c-spinner')).toHaveCount(0, { timeout: 15_000 });
}

async function adminContext(): Promise<BrowserContext> {
  const ctx = await context(ADMIN);
  // Nothing on these pages should depend on today's date; a fixed clock makes sure of it
  await ctx.clock.setFixedTime(new Date(EVENT_TIME));
  return ctx;
}

test('admin console: flow, step configuration and realm settings', async () => {
  const ctx = await adminContext();
  const page = await ctx.newPage();
  await adminConsole(page, 'authentication');
  await page.getByRole('link', { name: 'browser with Turnstile', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: 'OTP Form' })).toBeVisible();
  await settled(page);
  let restore = await fitViewport(page);
  await shot(page, 'admin-console', 'browser-flow', {
    section: 'Admin console', title: 'Browser flow with Turnstile',
    caption: 'A copy of Keycloak\'s browser flow, with the Cloudflare Turnstile step in place of Username Password Form.',
  });
  await restore();

  // Adding the step: the forms sub-flow's + > Add step (Add execution on Keycloak 24 and 25)
  const forms = page.getByRole('row').filter({ hasText: 'browser with Turnstile forms' }).first();
  await forms.getByRole('button', { name: /add/i }).first().click();
  await page.getByRole('menuitem', { name: /add (step|execution)/i }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox').first().fill('turnstile');
  await dialog.getByRole('textbox').first().press('Enter');
  await expect(dialog.getByText('Cloudflare Turnstile', { exact: true }).first()).toBeVisible();
  await settled(page);
  await shot(page, 'admin-console', 'add-step', {
    section: 'Admin console', title: 'Adding the step',
    caption: 'Cloudflare Turnstile in the admin console\'s list of steps, once the extension is installed.',
  });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);

  const row = page.getByRole('row').filter({ hasText: 'Cloudflare Turnstile' }).first();
  await row.getByRole('button', { name: /settings/i }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await settled(page);
  restore = await fitViewport(page);
  await shot(page, 'admin-console', 'authenticator-configuration', {
    section: 'Admin console', title: 'Step configuration',
    caption: 'The Cloudflare Turnstile step\'s settings: keys, implementation method, widget, IP lists and failure handling.',
  });
  await restore();
  await page.keyboard.press('Escape');

  await page.goto(`${KEYCLOAK_URL}/admin/master/console/#/${REALM}/realm-settings`);
  await hideTemporaryAdminBanner(page);
  // The tab is titled with its id unless the admin console has a translation for it
  await page.getByRole('tab', { name: /cloudflare turnstile|cloudflare-turnstile-settings/i }).click();
  await settled(page);
  restore = await fitViewport(page);
  await shot(page, 'admin-console', 'realm-settings', {
    section: 'Admin console', title: 'Realm-wide settings',
    caption: 'Realm settings > Cloudflare Turnstile: keys and policies for every step that uses the realm settings.',
  });
  await restore();
  await ctx.close();
});

// What the admin console is sent about events, made the same every run (see `steadyEvents`)
const EVENT_TIME = '2026-03-12T09:41:00Z';
const VISITOR_IP = '203.0.113.24';

/**
 * Event times, ids and the visitor's address differ every run (the address is the Docker
 * network's gateway, which differs per machine): replaces them in the admin console's event list
 * with fixed values, a minute apart, so the events page reads the same every run.
 */
async function steadyEvents(page: Page) {
  await page.route(`**/admin/realms/${REALM}/events?**`, async (route: Route) => {
    const response = await route.fetch();
    const events: any[] = await response.json();
    const steady = events.map((e, i) => {
      const n = String(i + 1).padStart(12, '0');
      const details = { ...e.details };
      if ('ip_address' in details) details.ip_address = VISITOR_IP;
      if ('code_id' in details) details.code_id = `c0de0000-0000-4000-8000-${n}`;
      return {
        ...e,
        ...(e.id ? { id: `e0e00000-0000-4000-8000-${n}` } : {}),
        time: new Date(EVENT_TIME).getTime() - i * 60_000,
        ipAddress: VISITOR_IP,
        ...(e.sessionId ? { sessionId: `5e550000-0000-4000-8000-${n}` } : {}),
        details,
      };
    });
    await route.fulfill({ response, json: steady });
  });
}

test('admin console: events', async () => {
  // Exactly two sign-ins in the list: one refused by Turnstile, then one let through
  await admin('DELETE', `/${REALM}/events`);
  let ctx = await context();
  let page = await ctx.newPage();
  await signIn(page, 'rejected');
  await fillCredentials(page);
  await widgetSettled(page);
  await page.locator('#kc-login').click();
  await expect(page.getByText('Security verification failed. Please try again.')).toBeVisible();
  await ctx.close();
  ctx = await context();
  page = await ctx.newPage();
  await signIn(page);
  await fillCredentials(page);
  await widgetSettled(page);
  await submitAndExpectSignedIn(page);
  await ctx.close();

  ctx = await adminContext();
  page = await ctx.newPage();
  await steadyEvents(page);
  await adminConsole(page, 'events');
  await settled(page);
  const toggle = async (type: string) => {
    const row = page.getByRole('row').filter({ has: page.getByText(type, { exact: true }) }).first();
    await row.getByRole('button').first().click();
    await settled(page);
  };
  await toggle('LOGIN');
  let restore = await fitViewport(page);
  await shot(page, 'admin-console', 'event-details', {
    section: 'Admin console', title: 'Sign-in event',
    caption: 'Events > User events: a sign-in, with every cloudflare_turnstile_* detail of the check.',
  });
  await restore();
  await toggle('LOGIN');
  await toggle('LOGIN_ERROR');
  restore = await fitViewport(page);
  await shot(page, 'admin-console', 'event-refused', {
    section: 'Admin console', title: 'Refused sign-in event',
    caption: 'A sign-in Turnstile refused: error turnstile_verification_failed, with Cloudflare\'s error codes.',
  });
  await restore();
  await ctx.close();
});
