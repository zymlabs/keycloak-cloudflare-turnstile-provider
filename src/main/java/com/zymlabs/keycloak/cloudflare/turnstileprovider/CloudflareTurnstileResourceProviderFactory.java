package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Factory for Cloudflare Turnstile Resource Provider.
 *
 * Registers the JAX-RS resource endpoint at:
 * /realms/{realm}/cloudflare-turnstile/...
 *
 * This provider serves JavaScript files needed for Script Injection mode.
 */
public class CloudflareTurnstileResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String PROVIDER_ID = "cloudflare-turnstile";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new CloudflareTurnstileResourceProvider();
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
