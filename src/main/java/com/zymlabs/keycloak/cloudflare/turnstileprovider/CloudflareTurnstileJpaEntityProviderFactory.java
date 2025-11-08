package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.Config;

/**
 * Factory for creating CloudflareTurnstileJpaEntityProvider instances.
 *
 * This factory registers the Cloudflare Turnstile entities with Keycloak's
 * JPA infrastructure during startup.
 */
public class CloudflareTurnstileJpaEntityProviderFactory implements JpaEntityProviderFactory {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileJpaEntityProviderFactory.class);

    public static final String ID = "cloudflare-turnstile-entity-provider";

    @Override
    public JpaEntityProvider create(KeycloakSession session) {
        return new CloudflareTurnstileJpaEntityProvider();
    }

    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing CloudflareTurnstileJpaEntityProviderFactory");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        logger.info("CloudflareTurnstileJpaEntityProviderFactory post-initialization complete");
    }

    @Override
    public void close() {
        logger.info("Closing CloudflareTurnstileJpaEntityProviderFactory");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int order() {
        return 100;
    }
}
