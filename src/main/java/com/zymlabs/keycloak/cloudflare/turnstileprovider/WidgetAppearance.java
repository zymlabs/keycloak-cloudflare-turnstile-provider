package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.forms.login.LoginFormsProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * When the widget is shown: the {@code widgetAppearance} setting.
 *
 * Cloudflare's own widget mode (Managed, Non-Interactive or Invisible) belongs to the site key and is
 * chosen in the Cloudflare dashboard; a page can't change it. This setting only sets the widget's
 * {@code appearance}. It was once stored as {@code widgetMode}, with values named after Cloudflare's
 * modes ({@code managed}, {@code non-interactive}) that it never set. Stored settings are renamed the
 * first time Keycloak starts with this version, and in realms imported later
 * ({@link WidgetAppearanceMigration}); the former key and names are still read, and the former names
 * still given to themes as {@code turnstileMode}.
 */
public enum WidgetAppearance {

    /** Always shown (Cloudflare's {@code appearance: always}). Formerly {@code managed}. */
    ALWAYS("always", "managed"),

    /**
     * Hidden unless Cloudflare asks the visitor to click (Cloudflare's {@code appearance: interaction-only}).
     * Formerly {@code non-interactive}.
     */
    INTERACTION_ONLY("interaction-only", "non-interactive"),

    /**
     * For site keys in Cloudflare's Invisible mode: there is nothing to show, and the submit button
     * isn't held back until the token arrives. Rendered as interaction-only, so a site key in another
     * mode still shows its checkbox when Cloudflare asks for one.
     */
    INVISIBLE("invisible", "invisible");

    /** The setting's key. */
    public static final String KEY = "widgetAppearance";

    /** The setting's key before the rename, still read when {@link #KEY} is absent. */
    public static final String LEGACY_KEY = "widgetMode";

    private final String value;
    private final String legacyName;

    WidgetAppearance(String value, String legacyName) {
        this.value = value;
        this.legacyName = legacyName;
    }

    /** The setting's value. */
    public String value() {
        return value;
    }

    /** The value's name before the rename, which themes read as {@code turnstileMode}. */
    public String legacyName() {
        return legacyName;
    }

    /** Cloudflare's {@code appearance} render parameter. */
    public String cloudflareAppearance() {
        return this == ALWAYS ? "always" : "interaction-only";
    }

    /**
     * The setting from a config value, current or former name. Empty and unknown values are
     * {@link #ALWAYS}, as they always behaved.
     */
    public static WidgetAppearance fromConfig(String configValue) {
        if (configValue != null) {
            String trimmed = configValue.trim();
            for (WidgetAppearance appearance : values()) {
                if (appearance.value.equalsIgnoreCase(trimmed) || appearance.legacyName.equalsIgnoreCase(trimmed)) {
                    return appearance;
                }
            }
        }
        return ALWAYS;
    }

    /**
     * A config value under its current name: {@code managed} becomes {@code always},
     * {@code non-interactive} becomes {@code interaction-only}. Empty values stay empty, since an empty
     * step value means "use the realm's setting".
     */
    public static String currentValue(String configValue) {
        return configValue == null || configValue.isBlank() ? configValue : fromConfig(configValue).value();
    }

    /**
     * A copy of {@code config} with a {@link #LEGACY_KEY} entry moved to {@link #KEY}, its value
     * renamed. Where both keys are set, the current one wins and the former is dropped.
     */
    public static Map<String, String> withCurrentKey(Map<String, String> config) {
        if (config == null || !config.containsKey(LEGACY_KEY)) {
            return config;
        }
        Map<String, String> copy = new HashMap<>(config);
        String legacy = copy.remove(LEGACY_KEY);
        if (!copy.containsKey(KEY)) {
            copy.put(KEY, currentValue(legacy));
        }
        return copy;
    }

    /**
     * The page attributes templates read: {@code turnstileAppearance} (Cloudflare's appearance),
     * {@code turnstileInvisible}, and {@code turnstileMode} with the former names, for themes written
     * against it.
     */
    public void setAttributes(LoginFormsProvider form) {
        form.setAttribute("turnstileAppearance", cloudflareAppearance())
                .setAttribute("turnstileInvisible", this == INVISIBLE)
                .setAttribute("turnstileMode", legacyName);
    }
}
