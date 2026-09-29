package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.forms.login.LoginFormsProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudflareTurnstileAuthenticatorTest {

    private static final List<String> METHODS = List.of("SEPARATE_PAGE", "SCRIPT_INJECTION", "CUSTOM_THEME");

    /** A step running in the flow Keycloak names by this path (as in its login-actions URLs). */
    private static AuthenticationFlowContext inFlow(String flowPath) {
        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        when(context.getFlowPath()).thenReturn(flowPath);
        return context;
    }

    private static String methodIn(String flowPath, String configured) {
        return CloudflareTurnstileAuthenticator.implementationMethod(inFlow(flowPath),
                Map.of(CloudflareTurnstileAuthenticator.CONFIG_IMPLEMENTATION_METHOD, configured));
    }

    @Test
    void knowsTheFlowFromKeycloaksFlowPath() {
        assertThat(CloudflareTurnstileAuthenticator.flowType(inFlow("authenticate"))).isEqualTo("login");
        assertThat(CloudflareTurnstileAuthenticator.flowType(inFlow("registration"))).isEqualTo("registration");
        assertThat(CloudflareTurnstileAuthenticator.flowType(inFlow("reset-credentials"))).isEqualTo("reset-credentials");
        // Other flows that sign someone in (e.g. after an identity provider) count as sign-ins
        assertThat(CloudflareTurnstileAuthenticator.flowType(inFlow("first-broker-login"))).isEqualTo("login");
    }

    @Test
    void signInUsesTheConfiguredMethod() {
        METHODS.forEach(method -> assertThat(methodIn("authenticate", method)).isEqualTo(method));
        assertThat(CloudflareTurnstileAuthenticator.implementationMethod(inFlow("authenticate"), Map.of()))
                .isEqualTo("SEPARATE_PAGE");
    }

    @Test
    void registrationUsesTheSeparatePageBecauseInlineWidgetsComeFromTheFormAction() {
        METHODS.forEach(method -> assertThat(methodIn("registration", method)).isEqualTo("SEPARATE_PAGE"));
    }

    @Test
    void theResetPageGetsItsInlineWidgetByScriptInjection() {
        assertThat(methodIn("reset-credentials", "SEPARATE_PAGE")).isEqualTo("SEPARATE_PAGE");
        assertThat(methodIn("reset-credentials", "SCRIPT_INJECTION")).isEqualTo("SCRIPT_INJECTION");
        // Themes can't render the widget on Keycloak's reset page, so the custom-theme method injects it
        assertThat(methodIn("reset-credentials", "CUSTOM_THEME")).isEqualTo("SCRIPT_INJECTION");
    }

    @Test
    void onlyPasskeySubmissionsSkipTheCheck() {
        // Keycloak's passkey form posts WebAuthn fields: no username, no password, no token
        MultivaluedHashMap<String, String> passkey = new MultivaluedHashMap<>();
        passkey.putSingle("authenticatorData", "AAAA");
        passkey.putSingle("credentialId", "id");
        assertThat(CloudflareTurnstileAuthenticator.isPasskeySubmission(passkey)).isTrue();
        MultivaluedHashMap<String, String> passkeyError = new MultivaluedHashMap<>();
        passkeyError.putSingle("error", "NotAllowedError");
        assertThat(CloudflareTurnstileAuthenticator.isPasskeySubmission(passkeyError)).isTrue();

        // Anything that could test a password needs the check
        MultivaluedHashMap<String, String> withPassword = new MultivaluedHashMap<>(passkey);
        withPassword.putSingle("password", "guess");
        assertThat(CloudflareTurnstileAuthenticator.isPasskeySubmission(withPassword)).isFalse();
        MultivaluedHashMap<String, String> blankPassword = new MultivaluedHashMap<>(passkey);
        blankPassword.putSingle("password", "   "); // Keycloak checks a blank password; only an empty one is none
        assertThat(CloudflareTurnstileAuthenticator.isPasskeySubmission(blankPassword)).isFalse();
        MultivaluedHashMap<String, String> withUsername = new MultivaluedHashMap<>(passkey);
        withUsername.putSingle("username", "alice");
        assertThat(CloudflareTurnstileAuthenticator.isPasskeySubmission(withUsername)).isFalse();
        // An empty form isn't a passkey either
        assertThat(CloudflareTurnstileAuthenticator.isPasskeySubmission(new MultivaluedHashMap<>())).isFalse();
    }

    @Test
    void theWidgetGoesOnlyOnThePageTheStepBuilds() {
        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        LoginFormsProvider form = mock(LoginFormsProvider.class);
        when(context.form()).thenReturn(form);
        List<LoginFormsProvider> decorated = new ArrayList<>();

        AuthenticationFlowContext wrapped = CloudflareTurnstileAuthenticator.withWidgetOnItsPage(context, decorated::add);
        wrapped.success();
        assertThat(decorated).isEmpty(); // no page, no widget
        wrapped.form();
        wrapped.form();
        assertThat(decorated).containsExactly(form); // once
        verify(context).success();
    }

    @Test
    void refusalsOfUncheckedSubmissionsDontCountTowardLockout() {
        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        Response page = mock(Response.class);

        AuthenticationFlowContext wrapped = CloudflareTurnstileAuthenticator.withoutLockoutCounting(context);
        wrapped.failureChallenge(AuthenticationFlowError.INVALID_USER, page);
        wrapped.failure(AuthenticationFlowError.INVALID_CREDENTIALS, page);

        verify(context, times(2)).forceChallenge(page);
        verify(context, never()).failureChallenge(any(), any());
        verify(context, never()).failure(any(), any());
    }

    @Test
    void wrappedContextsRethrowWhatTheContextThrows() {
        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        when(context.getUser()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> CloudflareTurnstileAuthenticator.withoutLockoutCounting(context).getUser())
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThatThrownBy(() -> CloudflareTurnstileAuthenticator.withWidgetOnItsPage(context, f -> { }).getUser())
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }
}
