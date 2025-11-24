package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.theme.ThemeResourceProvider;
import org.keycloak.theme.ThemeResourceProviderFactory;

/**
 * Factory for Cloudflare Turnstile Theme Resource Provider.
 */
public class CloudflareTurnstileThemeResourceProviderFactory implements ThemeResourceProviderFactory {

    public static final String PROVIDER_ID = "cloudflare-turnstile-resources";

    @Override
    public ThemeResourceProvider create(KeycloakSession session) {
        return new CloudflareTurnstileThemeResourceProvider();
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
