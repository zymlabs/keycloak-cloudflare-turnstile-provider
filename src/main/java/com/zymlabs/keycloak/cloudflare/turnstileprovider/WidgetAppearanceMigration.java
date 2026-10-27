package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ui.extend.UiTabProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Renames the widget setting's former key ({@code widgetMode}) to {@code widgetAppearance}, its value
 * under its current name, where Keycloak stores Turnstile settings: flow step configs and the realm
 * settings component.
 *
 * Settings already in the database are renamed once, by {@link WidgetAppearanceMigrationChange} (a
 * changeset in cloudflare-turnstile-changelog.xml). Realms imported afterwards, from JSON exported
 * before the rename, are renamed as they are created. Settings an admin API client writes under the
 * former key are still read ({@link CloudflareTurnstileRealmSettings#effective}).
 */
public final class WidgetAppearanceMigration {

    private static final Logger logger = Logger.getLogger(WidgetAppearanceMigration.class);

    /** The flow steps whose configs hold the setting. */
    static final Set<String> STEP_PROVIDERS = Set.of(
            CloudflareTurnstileAuthenticatorFactory.PROVIDER_ID, CloudflareTurnstileFormActionFactory.PROVIDER_ID);

    private WidgetAppearanceMigration() {
    }

    /** Renames the setting in every realm created from now on, imported realms included. */
    public static void register(KeycloakSessionFactory factory) {
        factory.register(event -> {
            if (event instanceof RealmModel.RealmPostCreateEvent created) {
                renameIn(created.getCreatedRealm());
            }
        });
    }

    /** Renames the setting in a realm's Turnstile step configs and realm settings. */
    static void renameIn(RealmModel realm) {
        List<String> configIds = realm.getAuthenticationFlowsStream()
                .flatMap(flow -> realm.getAuthenticationExecutionsStream(flow.getId()))
                // Subflows have no authenticator, and Set.of refuses to look up null
                .filter(execution -> execution.getAuthenticator() != null && STEP_PROVIDERS.contains(execution.getAuthenticator()))
                .map(AuthenticationExecutionModel::getAuthenticatorConfig)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (String id : configIds) {
            AuthenticatorConfigModel config = realm.getAuthenticatorConfigById(id);
            if (config != null && config.getConfig() != null && config.getConfig().containsKey(WidgetAppearance.LEGACY_KEY)) {
                try {
                    config.setConfig(WidgetAppearance.withCurrentKey(config.getConfig()));
                    realm.updateAuthenticatorConfig(config);
                    logger.debugf("Renamed %s to %s in step config %s of realm %s",
                            WidgetAppearance.LEGACY_KEY, WidgetAppearance.KEY, config.getAlias(), realm.getName());
                } catch (RuntimeException e) {
                    // The former key is still read; an import shouldn't fail over the rename
                    logger.warnf("Couldn't rename %s to %s in step config %s of realm %s: %s",
                            WidgetAppearance.LEGACY_KEY, WidgetAppearance.KEY, config.getAlias(), realm.getName(), e.getMessage());
                }
            }
        }

        List<ComponentModel> tabs = realm.getComponentsStream(realm.getId(), UiTabProvider.class.getName())
                .filter(component -> CloudflareTurnstileRealmSettings.TAB_ID.equals(component.getProviderId()))
                .toList();
        for (ComponentModel tab : tabs) {
            if (rename(tab.getConfig())) {
                try {
                    realm.updateComponent(tab);
                    logger.debugf("Renamed %s to %s in the realm settings of realm %s",
                            WidgetAppearance.LEGACY_KEY, WidgetAppearance.KEY, realm.getName());
                } catch (RuntimeException e) {
                    // Saving validates the whole component; the former key is still read, and an import
                    // shouldn't fail over the rename
                    logger.warnf("Couldn't rename %s to %s in the Cloudflare Turnstile realm settings of realm %s: %s",
                            WidgetAppearance.LEGACY_KEY, WidgetAppearance.KEY, realm.getName(), e.getMessage());
                }
            }
        }
    }

    /** Renames the setting in a component config; whether it was there. */
    static boolean rename(MultivaluedHashMap<String, String> config) {
        if (config == null || !config.containsKey(WidgetAppearance.LEGACY_KEY)) {
            return false;
        }
        List<String> legacy = config.remove(WidgetAppearance.LEGACY_KEY);
        if (!config.containsKey(WidgetAppearance.KEY)) {
            (legacy == null ? List.<String>of() : legacy).stream().filter(Objects::nonNull).findFirst()
                    .ifPresent(value -> config.putSingle(WidgetAppearance.KEY, WidgetAppearance.currentValue(value)));
        }
        return true;
    }

    /** A stored setting: {@code owner} is the step config or component the row belongs to. */
    record Row(String id, String owner, String name, String value) {
    }

    /** Rows to give the current key and value (the value already renamed), and rows to delete. */
    record Plan(List<Row> renames, List<Row> deletes) {
    }

    /**
     * What to change among stored setting rows, current and former keys: an owner's former-key row
     * becomes its current-key row, unless it has one already; other former-key rows are deleted.
     */
    static Plan plan(Collection<Row> rows) {
        Map<String, List<Row>> byOwner = new LinkedHashMap<>();
        rows.forEach(row -> byOwner.computeIfAbsent(row.owner(), owner -> new ArrayList<>()).add(row));
        List<Row> renames = new ArrayList<>();
        List<Row> deletes = new ArrayList<>();
        byOwner.values().forEach(ownerRows -> {
            boolean hasCurrent = ownerRows.stream().anyMatch(row -> WidgetAppearance.KEY.equals(row.name()));
            List<Row> legacy = ownerRows.stream().filter(row -> WidgetAppearance.LEGACY_KEY.equals(row.name())).toList();
            for (int i = 0; i < legacy.size(); i++) {
                Row row = legacy.get(i);
                if (i == 0 && !hasCurrent) {
                    renames.add(new Row(row.id(), row.owner(), WidgetAppearance.KEY, WidgetAppearance.currentValue(row.value())));
                } else {
                    deletes.add(row);
                }
            }
        });
        return new Plan(renames, deletes);
    }
}
