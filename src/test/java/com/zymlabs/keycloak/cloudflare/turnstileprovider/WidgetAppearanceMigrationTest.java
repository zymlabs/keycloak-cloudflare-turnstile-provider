package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import com.zymlabs.keycloak.cloudflare.turnstileprovider.WidgetAppearanceMigration.Plan;
import com.zymlabs.keycloak.cloudflare.turnstileprovider.WidgetAppearanceMigration.Row;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ui.extend.UiTabProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WidgetAppearanceMigrationTest {

    @Test
    void aFormerKeyRowBecomesTheCurrentKeyWithItsValueRenamed() {
        Plan plan = WidgetAppearanceMigration.plan(List.of(
                new Row("c1", "c1", "widgetMode", "non-interactive"),
                new Row("c2", "c2", "widgetMode", "managed"),
                new Row("c3", "c3", "widgetMode", "")));

        assertThat(plan.renames()).containsExactly(
                new Row("c1", "c1", "widgetAppearance", "interaction-only"),
                new Row("c2", "c2", "widgetAppearance", "always"),
                new Row("c3", "c3", "widgetAppearance", ""));
        assertThat(plan.deletes()).isEmpty();
    }

    @Test
    void whereTheCurrentKeyIsSetAlreadyTheFormerRowIsDeleted() {
        Row legacy = new Row("c1", "c1", "widgetMode", "non-interactive");
        Plan plan = WidgetAppearanceMigration.plan(List.of(legacy, new Row("c1", "c1", "widgetAppearance", "always")));

        assertThat(plan.renames()).isEmpty();
        assertThat(plan.deletes()).containsExactly(legacy);
    }

    @Test
    void ofSeveralFormerValuesTheFirstIsKept() {
        Row second = new Row("v2", "tab", "widgetMode", "managed");
        Plan plan = WidgetAppearanceMigration.plan(List.of(new Row("v1", "tab", "widgetMode", "non-interactive"), second));

        assertThat(plan.renames()).containsExactly(new Row("v1", "tab", "widgetAppearance", "interaction-only"));
        assertThat(plan.deletes()).containsExactly(second);
    }

    @Test
    void settingsUnderTheCurrentKeyAreLeftAlone() {
        Plan plan = WidgetAppearanceMigration.plan(List.of(new Row("c1", "c1", "widgetAppearance", "managed")));

        assertThat(plan.renames()).isEmpty();
        assertThat(plan.deletes()).isEmpty();
    }

    @Test
    void anImportedRealmHasItsTurnstileStepsAndRealmSettingsRenamed() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getId()).thenReturn("r1");
        AuthenticationFlowModel flow = new AuthenticationFlowModel();
        flow.setId("f1");
        when(realm.getAuthenticationFlowsStream()).thenAnswer(i -> Stream.of(flow));
        when(realm.getAuthenticationExecutionsStream("f1")).thenAnswer(i -> Stream.of(
                execution(CloudflareTurnstileAuthenticatorFactory.PROVIDER_ID, "turnstile"),
                execution(CloudflareTurnstileFormActionFactory.PROVIDER_ID, "registration"),
                execution("someone-elses-step", "other"),
                execution(null, null)));
        AuthenticatorConfigModel turnstile = config("turnstile", Map.of("widgetMode", "non-interactive", "siteKey", "k"));
        AuthenticatorConfigModel registration = config("registration", Map.of("widgetAppearance", "always"));
        AuthenticatorConfigModel other = config("other", Map.of("widgetMode", "theirs"));
        when(realm.getAuthenticatorConfigById("turnstile")).thenReturn(turnstile);
        when(realm.getAuthenticatorConfigById("registration")).thenReturn(registration);
        when(realm.getAuthenticatorConfigById("other")).thenReturn(other);
        ComponentModel tab = new ComponentModel();
        tab.setProviderId(CloudflareTurnstileRealmSettings.TAB_ID);
        tab.setConfig(new MultivaluedHashMap<>());
        tab.getConfig().putSingle("widgetMode", "managed");
        when(realm.getComponentsStream("r1", UiTabProvider.class.getName())).thenAnswer(i -> Stream.of(tab));

        WidgetAppearanceMigration.renameIn(realm);

        assertThat(turnstile.getConfig()).isEqualTo(Map.of("widgetAppearance", "interaction-only", "siteKey", "k"));
        verify(realm).updateAuthenticatorConfig(turnstile);
        verify(realm, never()).updateAuthenticatorConfig(registration);
        verify(realm, never()).updateAuthenticatorConfig(other);
        assertThat(other.getConfig()).containsEntry("widgetMode", "theirs");
        assertThat(tab.getConfig()).containsOnlyKeys("widgetAppearance");
        assertThat(tab.getConfig().getFirst("widgetAppearance")).isEqualTo("always");
        verify(realm).updateComponent(tab);
    }

    @Test
    void realmSettingsThatCantBeSavedDontFailTheImport() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getId()).thenReturn("r1");
        when(realm.getAuthenticationFlowsStream()).thenAnswer(i -> Stream.empty());
        ComponentModel tab = new ComponentModel();
        tab.setProviderId(CloudflareTurnstileRealmSettings.TAB_ID);
        tab.setConfig(new MultivaluedHashMap<>());
        tab.getConfig().putSingle("widgetMode", "managed");
        when(realm.getComponentsStream("r1", UiTabProvider.class.getName())).thenAnswer(i -> Stream.of(tab));
        doThrow(new IllegalStateException("invalid")).when(realm).updateComponent(any());

        WidgetAppearanceMigration.renameIn(realm);

        verify(realm).updateComponent(tab);
    }

    private static AuthenticationExecutionModel execution(String authenticator, String configId) {
        AuthenticationExecutionModel execution = new AuthenticationExecutionModel();
        execution.setAuthenticator(authenticator);
        execution.setAuthenticatorConfig(configId);
        return execution;
    }

    private static AuthenticatorConfigModel config(String id, Map<String, String> values) {
        AuthenticatorConfigModel config = new AuthenticatorConfigModel();
        config.setId(id);
        config.setAlias(id);
        config.setConfig(new HashMap<>(values));
        return config;
    }
}
