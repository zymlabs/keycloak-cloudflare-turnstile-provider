import { expect, test } from '@playwright/test';
import { adminApi } from './admin';

const STEPS = ['cloudflare-turnstile-authenticator', 'cloudflare-turnstile-form-action'];

// The e2e realm's JSON still stores the widget setting as it was before the rename (widgetMode, with the
// former value names), so importing it shows the plugin renaming realms as they are imported.
test.describe('widget setting stored before the rename', () => {
  test('is renamed in imported step configs', async () => {
    const flows: { alias: string; topLevel: boolean }[] = await (await adminApi('/authentication/flows')).json();
    const configIds = new Set<string>();
    for (const flow of flows.filter((f) => f.topLevel)) {
      const executions: { providerId?: string; authenticationConfig?: string }[] =
        await (await adminApi(`/authentication/flows/${encodeURIComponent(flow.alias)}/executions`)).json();
      executions
        .filter((e) => e.providerId && STEPS.includes(e.providerId) && e.authenticationConfig)
        .forEach((e) => configIds.add(e.authenticationConfig!));
    }
    expect(configIds.size).toBeGreaterThan(0);

    for (const id of configIds) {
      const { alias, config } = await (await adminApi(`/authentication/config/${id}`)).json();
      expect(config, alias).not.toHaveProperty('widgetMode');
      expect(config, alias).toHaveProperty('widgetAppearance', 'always');
    }
  });

  test('is renamed in the imported realm settings', async () => {
    const components: { providerId: string; config: Record<string, string[]> }[] =
      await (await adminApi('/components?type=org.keycloak.services.ui.extend.UiTabProvider')).json();
    const tab = components.find((c) => c.providerId === 'cloudflare-turnstile-settings');

    expect(tab?.config).not.toHaveProperty('widgetMode');
    expect(tab?.config).toHaveProperty('widgetAppearance', ['always']);
  });
});
