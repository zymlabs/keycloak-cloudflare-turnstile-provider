package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.forms.login.LoginFormsProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WidgetAppearanceTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @DisplayName("reads current and former setting values")
    @CsvSource({
            "always, ALWAYS",
            "interaction-only, INTERACTION_ONLY",
            "invisible, INVISIBLE",
            "managed, ALWAYS",
            "non-interactive, INTERACTION_ONLY",
            "' Interaction-Only ', INTERACTION_ONLY",
            "'', ALWAYS",
            "compact, ALWAYS",
    })
    void fromConfig(String configValue, WidgetAppearance expected) {
        assertThat(WidgetAppearance.fromConfig(configValue)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an unset value is always visible, as the former default (managed) was")
    void unsetIsAlways() {
        assertThat(WidgetAppearance.fromConfig(null)).isEqualTo(WidgetAppearance.ALWAYS);
    }

    @ParameterizedTest(name = "\"{0}\" is stored as \"{1}\"")
    @DisplayName("renames former values, keeping empty ones empty")
    @CsvSource({
            "managed, always",
            "non-interactive, interaction-only",
            "Non-Interactive, interaction-only",
            "invisible, invisible",
            "interaction-only, interaction-only",
            "compact, always",
            "'', ''",
    })
    void currentValue(String configValue, String expected) {
        assertThat(WidgetAppearance.currentValue(configValue)).isEqualTo(expected);
    }

    @Test
    @DisplayName("moves the former key to the current one, and the current one wins where both are set")
    void withCurrentKey() {
        assertThat(WidgetAppearance.withCurrentKey(Map.of("widgetMode", "non-interactive", "widgetTheme", "dark")))
                .isEqualTo(Map.of("widgetAppearance", "interaction-only", "widgetTheme", "dark"));
        assertThat(WidgetAppearance.withCurrentKey(Map.of("widgetMode", "non-interactive", "widgetAppearance", "invisible")))
                .isEqualTo(Map.of("widgetAppearance", "invisible"));
        Map<String, String> current = Map.of("widgetAppearance", "always");
        assertThat(WidgetAppearance.withCurrentKey(current)).isSameAs(current);
    }

    @Test
    @DisplayName("asks Cloudflare for a widget hidden until a click is needed, except when always visible")
    void cloudflareAppearance() {
        assertThat(WidgetAppearance.ALWAYS.cloudflareAppearance()).isEqualTo("always");
        assertThat(WidgetAppearance.INTERACTION_ONLY.cloudflareAppearance()).isEqualTo("interaction-only");
        assertThat(WidgetAppearance.INVISIBLE.cloudflareAppearance()).isEqualTo("interaction-only");
    }

    @Test
    @DisplayName("gives themes the appearance, and turnstileMode with the former names")
    void setAttributes() {
        LoginFormsProvider form = mock(LoginFormsProvider.class);
        when(form.setAttribute(anyString(), any())).thenReturn(form);

        WidgetAppearance.INTERACTION_ONLY.setAttributes(form);

        verify(form).setAttribute("turnstileAppearance", "interaction-only");
        verify(form).setAttribute("turnstileInvisible", false);
        verify(form).setAttribute("turnstileMode", "non-interactive");
    }
}
