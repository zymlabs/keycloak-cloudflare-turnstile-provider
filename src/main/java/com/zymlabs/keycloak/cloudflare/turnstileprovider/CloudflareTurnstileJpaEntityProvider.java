package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

import java.util.Collections;
import java.util.List;

/**
 * JPA Entity Provider for Cloudflare Turnstile entities.
 *
 * Registers the CloudflareTurnstileCheckEntity with Keycloak's JPA infrastructure.
 */
public class CloudflareTurnstileJpaEntityProvider implements JpaEntityProvider {

    @Override
    public List<Class<?>> getEntities() {
        return Collections.singletonList(CloudflareTurnstileCheckEntity.class);
    }

    @Override
    public String getChangelogLocation() {
        return "META-INF/cloudflare-turnstile-changelog.xml";
    }

    @Override
    public String getFactoryId() {
        return CloudflareTurnstileJpaEntityProviderFactory.ID;
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
