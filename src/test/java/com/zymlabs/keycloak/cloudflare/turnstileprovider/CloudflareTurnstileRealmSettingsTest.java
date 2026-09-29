package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ui.extend.UiTabProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudflareTurnstileRealmSettingsTest {

    private final RealmModel realm = mock(RealmModel.class);
    private final MultivaluedHashMap<String, String> tab = new MultivaluedHashMap<>();

    @BeforeEach
    void setUp() {
        when(realm.getId()).thenReturn("r1");
        ComponentModel component = new ComponentModel();
        component.setProviderId(CloudflareTurnstileRealmSettings.TAB_ID);
        component.setConfig(tab);
        when(realm.getComponentsStream("r1", UiTabProvider.class.getName())).thenAnswer(i -> Stream.of(component));
    }

    @Test
    void aStepOverridesTheRealmOnlyWhereItSetsAValue() {
        tab.putSingle("siteKey", "realm-site");
        tab.putSingle("secretKey", "realm-secret");
        tab.putSingle("failMode", "FAIL_OPEN");
        AuthenticatorConfigModel step = new AuthenticatorConfigModel();
        step.setConfig(new HashMap<>(Map.of("siteKey", "step-site", "failMode", "", "widgetMode", "invisible")));

        Map<String, String> effective = CloudflareTurnstileRealmSettings.effective(null, realm, step);

        assertThat(effective).containsEntry("siteKey", "step-site")
                .containsEntry("secretKey", "realm-secret")
                .containsEntry("failMode", "FAIL_OPEN")
                .containsEntry("widgetMode", "invisible");
        assertThat(CloudflareTurnstileRealmSettings.usable(effective)).isTrue();
    }

    @Test
    void aStepUsingTheRealmsSettingsKeepsOnlyItsImplementationMethod() {
        tab.putSingle("siteKey", "realm-site");
        tab.putSingle("secretKey", "realm-secret");
        tab.putSingle("failAction", "BLOCK");
        // What the admin console saves for a new step: the switch on, and every field's default
        AuthenticatorConfigModel step = new AuthenticatorConfigModel();
        step.setConfig(new HashMap<>(Map.of("useRealmSettings", "true", "implementationMethod", "SCRIPT_INJECTION",
                "failAction", "ALLOW", "siteKey", "step-site", "ipAllowlist", "10.0.0.0/8")));

        Map<String, String> effective = CloudflareTurnstileRealmSettings.effective(null, realm, step);

        assertThat(effective).containsEntry("siteKey", "realm-site")
                .containsEntry("secretKey", "realm-secret")
                .containsEntry("failAction", "BLOCK")
                .containsEntry("implementationMethod", "SCRIPT_INJECTION")
                .doesNotContainKey("ipAllowlist");
    }

    @Test
    void theTabOffersOnlyRealmWideSettings() {
        assertThat(new CloudflareTurnstileRealmSettingsTab().getConfigProperties())
                .extracting(p -> p.getName())
                .contains("siteKey", "secretKey", "failAction")
                .doesNotContain("implementationMethod", "useRealmSettings");
    }

    @Test
    void badTimeoutsFallBackToTheDefaultAndAreRefusedOnTheTab() {
        assertThat(CloudflareTurnstileRealmSettings.timeoutMillis(Map.of("readTimeout", "2500"), "readTimeout")).isEqualTo(2500);
        assertThat(CloudflareTurnstileRealmSettings.timeoutMillis(Map.of("readTimeout", "5s"), "readTimeout")).isEqualTo(5000);
        assertThat(CloudflareTurnstileRealmSettings.timeoutMillis(Map.of("readTimeout", "-1"), "readTimeout")).isEqualTo(5000);
        assertThat(CloudflareTurnstileRealmSettings.timeoutMillis(Map.of(), "readTimeout")).isEqualTo(5000);

        ComponentModel model = new ComponentModel();
        model.setConfig(new MultivaluedHashMap<>());
        model.getConfig().putSingle("connectTimeout", "soon");
        assertThatThrownBy(() -> new CloudflareTurnstileRealmSettingsTab().validateConfiguration(null, realm, model))
                .isInstanceOf(ComponentValidationException.class);
    }

    @Test
    void withoutTheRealmTabAStepStandsAlone() {
        AuthenticatorConfigModel step = new AuthenticatorConfigModel();
        step.setConfig(new HashMap<>(Map.of("siteKey", "s", "secretKey", "k")));
        when(realm.getComponentsStream("r1", UiTabProvider.class.getName())).thenAnswer(i -> Stream.empty());

        assertThat(CloudflareTurnstileRealmSettings.effective(null, realm, step)).containsEntry("siteKey", "s").containsEntry("secretKey", "k");
        assertThat(CloudflareTurnstileRealmSettings.usable(CloudflareTurnstileRealmSettings.effective(null, realm, null))).isFalse();
    }
}
