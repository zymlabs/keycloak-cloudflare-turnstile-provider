package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudflareTurnstileResourceProviderTest {

    @Test
    @DisplayName("config.js sets the widget settings, then tells an injector that ran before it")
    void configScriptAnnouncesItself() {
        String script = CloudflareTurnstileResourceProvider.configScript("1x00000000000000000000AA", "managed", "dark", "false");

        assertThat(script)
                .contains("window.TURNSTILE_CONFIG.enabled = true;")
                .contains("window.TURNSTILE_CONFIG.siteKey = \"1x00000000000000000000AA\";")
                .contains("window.TURNSTILE_CONFIG.theme = \"dark\";")
                .endsWith("document.dispatchEvent(new Event('cloudflare-turnstile:config'));\n");
    }

    @Test
    @DisplayName("config.js falls back to an always-visible widget and automatic theme")
    void configScriptDefaults() {
        String script = CloudflareTurnstileResourceProvider.configScript("key", " ", null, "true");

        assertThat(script)
                .contains("window.TURNSTILE_CONFIG.mode = \"always\";")
                .contains("window.TURNSTILE_CONFIG.theme = \"auto\";")
                .contains("window.TURNSTILE_DEBUG_LOGGING = true;");
    }

    @Test
    @DisplayName("config.js gives the injector the current name of a former widget mode")
    void configScriptRenamesFormerModes() {
        assertThat(CloudflareTurnstileResourceProvider.configScript("key", "non-interactive", "auto", "false"))
                .contains("window.TURNSTILE_CONFIG.mode = \"interaction-only\";");
        assertThat(CloudflareTurnstileResourceProvider.configScript("key", "managed", "auto", "false"))
                .contains("window.TURNSTILE_CONFIG.mode = \"always\";");
    }
}
