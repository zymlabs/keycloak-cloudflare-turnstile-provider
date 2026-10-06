package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CloudflareTurnstileThemeResourceProviderTest {

    private final CloudflareTurnstileThemeResourceProvider provider = new CloudflareTurnstileThemeResourceProvider();

    @Test
    @DisplayName("Messages it has are loaded, with English for other languages")
    void loadsItsMessages() throws Exception {
        assertThat(provider.getMessages("messages", Locale.ENGLISH)).isNotEmpty();
        assertThat(provider.getMessages("messages", Locale.FRENCH)).isEqualTo(provider.getMessages("messages", Locale.ENGLISH));
    }

    @Test
    @DisplayName("A bundle it doesn't have is empty, never null (Keycloak merges provider messages without a null check)")
    void missingBundleIsEmptyNotNull() throws Exception {
        // e.g. the admin console's localized validation errors load "admin-messages"
        Properties adminMessages = provider.getMessages("admin-messages", Locale.ENGLISH);

        assertThat(adminMessages).isNotNull().isEmpty();
        assertThat(provider.getMessages("admin-messages", Locale.GERMAN)).isNotNull().isEmpty();
    }
}
