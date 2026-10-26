package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.forms.login.LoginFormsProvider;

/**
 * When the widget is shown: the {@code widgetMode} setting.
 *
 * Cloudflare's own widget mode (Managed, Non-Interactive or Invisible) belongs to the site key and is
 * chosen in the Cloudflare dashboard; a page can't change it. This setting only sets the widget's
 * {@code appearance}. Its values were once named after Cloudflare's modes ({@code managed},
 * {@code non-interactive}), which they never set; those names are still read, and still given to
 * themes as {@code turnstileMode}.
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
