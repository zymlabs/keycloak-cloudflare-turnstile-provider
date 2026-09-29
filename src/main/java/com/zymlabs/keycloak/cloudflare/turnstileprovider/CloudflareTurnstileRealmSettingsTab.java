package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.ui.extend.UiTabProvider;
import org.keycloak.services.ui.extend.UiTabProviderFactory;

import java.util.List;
import java.util.Map;

/**
 * The "Cloudflare Turnstile" tab in Realm settings: realm-wide keys and policies, for flow steps
 * that use the realm's settings (and for empty values of those that don't). Saved as a realm
 * component, so realm JSON can carry it too. The tab needs Keycloak's declarative-ui feature;
 * without it, set the component through realm JSON or the admin REST API.
 */
public class CloudflareTurnstileRealmSettingsTab implements UiTabProvider, UiTabProviderFactory<ComponentModel> {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileRealmSettingsTab.class);

    @Override
    public String getId() {
        return CloudflareTurnstileRealmSettings.TAB_ID;
    }

    @Override
    public String getHelpText() {
        return "Cloudflare Turnstile: site key, secret key and policies for this realm. Flow steps use these when their "
                + "Use Realm Settings switch is on, and for their empty values otherwise.";
    }

    @Override
    public String getPath() {
        return "/:realm/realm-settings/:tab?";
    }

    @Override
    public Map<String, String> getParams() {
        return Map.of("tab", getId());
    }

    /** The flow step's settings, except those that only make sense on a step. */
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return new CloudflareTurnstileAuthenticatorFactory().getConfigProperties().stream()
                .filter(p -> !CloudflareTurnstileAuthenticator.CONFIG_IMPLEMENTATION_METHOD.equals(p.getName())
                        && !CloudflareTurnstileRealmSettings.CONFIG_USE_REALM_SETTINGS.equals(p.getName()))
                .toList();
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
        for (String key : List.of(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT)) {
            String value = model.get(key);
            if (value != null && !value.isBlank() && CloudflareTurnstileRealmSettings.positiveInt(value) == null) {
                throw new ComponentValidationException("Cloudflare Turnstile " + key + " must be a positive number of milliseconds");
            }
        }
        String secret = model.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY);
        if (secret != null && !secret.isBlank() && !secret.trim().startsWith("${vault.")) {
            logger.warnf("Realm %s stores its Turnstile secret key in the database; prefer a vault reference such as ${vault.turnstile-secret}",
                    realm == null ? "?" : realm.getName());
        }
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        if (!Profile.isFeatureEnabled(Profile.Feature.DECLARATIVE_UI)) {
            logger.info("The Cloudflare Turnstile realm settings tab needs Keycloak's declarative-ui feature (--features=declarative-ui); "
                    + "without it, set realm-wide Turnstile settings through realm JSON or the admin REST API.");
        }
    }

    @Override
    public void close() {
    }
}
