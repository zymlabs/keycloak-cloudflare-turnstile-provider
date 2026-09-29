import { expect } from '@playwright/test';
import { MAILPIT_URL } from '../playwright.config';

interface MessageSummary {
  ID: string;
}

/** Messages Mailpit caught for an address (each test user has its own). */
export async function messagesTo(address: string): Promise<MessageSummary[]> {
  const res = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`);
  if (!res.ok) throw new Error(`mailpit search failed: ${res.status}`);
  return (await res.json()).messages ?? [];
}

/** Waits for the first message to an address and returns its link to Keycloak's action-token endpoint. */
export async function waitForActionLink(address: string): Promise<string> {
  let messages: MessageSummary[] = [];
  await expect.poll(async () => (messages = await messagesTo(address)).length, {
    message: `waiting for an email to ${address}`,
  }).toBeGreaterThan(0);
  const text: string = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`)).json()).Text;
  const link = text.match(/https?:\/\/\S+action-token\S+/)?.[0];
  if (!link) throw new Error(`no action-token link in email to ${address}`);
  return link;
}
