package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ui.extend.UiTabProvider;
import org.keycloak.vault.VaultStringSecret;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Realm-wide Turnstile settings (the "Cloudflare Turnstile" tab in Realm settings), and the
 * effective settings of a flow step. A step either uses the realm's settings (its "Use Realm
 * Settings" switch, on for steps created in the admin console), or its own, with empty values
 * falling back to the realm's.
 */
public final class CloudflareTurnstileRealmSettings {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileRealmSettings.class);

    /** Component (and tab) id of the realm settings. */
    public static final String TAB_ID = "cloudflare-turnstile-settings";

    /**
     * Step setting: take the realm's settings. The admin console turns it on for new steps (the
     * console saves every field's default, so a step's own values would otherwise always win). Off,
     * or absent as in steps configured before realm settings existed, the step's own values apply.
     */
    public static final String CONFIG_USE_REALM_SETTINGS = "useRealmSettings";

    /** What a step decides even when it takes the realm's settings: how its widget is placed. */
    private static final Set<String> STEP_SETTINGS = Set.of(CloudflareTurnstileAuthenticator.CONFIG_IMPLEMENTATION_METHOD);

    private CloudflareTurnstileRealmSettings() {
    }

    /** The realm's Turnstile settings (first value of each key); empty when the tab was never saved. */
    public static Map<String, String> realm(RealmModel realm) {
        Map<String, String> values = new HashMap<>();
        if (realm == null) {
            return values;
        }
        realm.getComponentsStream(realm.getId(), UiTabProvider.class.getName())
                .filter(c -> TAB_ID.equals(c.getProviderId()))
                .findFirst()
                .map(ComponentModel::getConfig)
                .ifPresent(config -> config.forEach((key, list) -> first(list).ifPresent(v -> values.put(key, v))));
        return values;
    }

    /**
     * A step's effective settings: the realm's, overridden by the step's own non-empty values unless
     * the step uses the realm's settings. The secret key may be a vault reference
     * ({@code ${vault.key}}) in either place; it is resolved.
     */
    public static Map<String, String> effective(KeycloakSession session, RealmModel realm, AuthenticatorConfigModel stepConfig) {
        Map<String, String> values = realm(realm);
        if (stepConfig != null && stepConfig.getConfig() != null) {
            boolean realmSettings = Boolean.parseBoolean(stepConfig.getConfig().get(CONFIG_USE_REALM_SETTINGS));
            stepConfig.getConfig().forEach((key, value) -> {
                if (value != null && !value.isBlank() && (!realmSettings || STEP_SETTINGS.contains(key))) {
                    values.put(key, value);
                }
            });
        }
        String secret = values.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY);
        if (secret != null && session != null && session.vault() != null) {
            try (VaultStringSecret resolved = session.vault().getStringSecret(secret)) {
                values.put(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY, resolved.get().orElse(secret));
            }
        }
        return values;
    }

    /** Timeout used when a setting is missing or not a positive number of milliseconds. */
    static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    /** A timeout setting in milliseconds; the default (logged) when it is missing or invalid. */
    public static int timeoutMillis(Map<String, String> settings, String key) {
        String value = settings.get(key);
        if (value == null || value.isBlank()) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        Integer millis = positiveInt(value);
        if (millis == null) {
            logger.warnf("Cloudflare Turnstile setting %s is not a positive number of milliseconds (%s); using %d",
                    key, value, DEFAULT_TIMEOUT_MILLIS);
            return DEFAULT_TIMEOUT_MILLIS;
        }
        return millis;
    }

    static Integer positiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether the settings can verify anything: a site key and a secret key. */
    public static boolean usable(Map<String, String> settings) {
        return notBlank(settings.get(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY))
                && notBlank(settings.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Optional<String> first(List<String> values) {
        return values == null ? Optional.empty() : values.stream().filter(CloudflareTurnstileRealmSettings::notBlank).findFirst();
    }
}
