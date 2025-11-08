package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for CloudflareTurnstileCopiedTemplateAction.
 * Provides FormAction that renders Turnstile using a copied template - native form integration.
 */
public class CloudflareTurnstileCopiedTemplateActionFactory implements FormActionFactory {

    public static final String PROVIDER_ID = "cloudflare-turnstile-copied-template-action";
    private static final CloudflareTurnstileCopiedTemplateAction SINGLETON = new CloudflareTurnstileCopiedTemplateAction();

    @Override
    public String getDisplayType() {
        return "Cloudflare Turnstile (Copied Template)";
    }

    @Override
    public String getReferenceCategory() {
        return "turnstile";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Renders Cloudflare Turnstile widget inline on registration form using a copied template (turnstile-register.ftl). Provides native integration like built-in reCAPTCHA. Template must be kept in sync with Keycloak updates.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY)
                .label("Site Key")
                .helpText("Your Cloudflare Turnstile site key")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY)
                .label("Secret Key")
                .helpText("Your Cloudflare Turnstile secret key")
                .type(ProviderConfigProperty.PASSWORD)
                .secret(true)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE)
                .label("Widget Mode")
                .helpText("Turnstile widget mode: managed (interactive when needed), non-interactive (minimal friction), invisible (background)")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("managed", "non-interactive", "invisible")
                .defaultValue("managed")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME)
                .label("Widget Theme")
                .helpText("Visual theme for the Turnstile widget")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("auto", "light", "dark")
                .defaultValue("auto")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS)
                .label("Record Verifications")
                .helpText("Store verification results in database for auditing and analytics")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("true")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST)
                .label("IP Allowlist")
                .helpText("Comma-separated list of IPs/CIDRs to skip Turnstile verification. Takes precedence over blocklist. Supports IPv4 and IPv6. Default includes standard private/internal ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, ::1/128, fc00::/7, fe80::/10).")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7,fe80::/10")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST)
                .label("IP Blocklist")
                .helpText("Comma-separated list of IPs/CIDRs to immediately block. Allowlist takes precedence if IP appears in both lists. Supports IPv4 (203.0.113.0/24) and IPv6 (2001:db9::/32). Example: 203.0.113.0/24,198.51.100.1")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION)
                .label("Fail Action")
                .helpText("Action to take when Turnstile verification fails")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("BLOCK", "ALLOW", "REQUIRE_MFA")
                .defaultValue("BLOCK")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE)
                .label("Fail Mode")
                .helpText("Behavior when Cloudflare API is unreachable: FAIL_CLOSED (block access) or FAIL_OPEN (allow access)")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("FAIL_CLOSED", "FAIL_OPEN")
                .defaultValue("FAIL_CLOSED")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT)
                .label("Connect Timeout (ms)")
                .helpText("Connection timeout for Cloudflare API in milliseconds")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5000")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT)
                .label("Read Timeout (ms)")
                .helpText("Read timeout for Cloudflare API in milliseconds")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5000")
                .add()

                .build();
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // No resources to close
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
