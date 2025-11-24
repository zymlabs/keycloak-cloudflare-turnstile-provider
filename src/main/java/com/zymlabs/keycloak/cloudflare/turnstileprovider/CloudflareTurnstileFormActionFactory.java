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
 * Unified Factory for CloudflareTurnstileFormAction.
 *
 * Provides a single FormAction that:
 * - Auto-detects whether it's running in a login or registration flow
 * - Supports three implementation methods via configuration:
 *   - Script Injection: JavaScript DOM manipulation (works with any theme)
 *   - Copied Template: Bundled template with embedded widget
 *   - Custom Theme: Relies on theme's template to render widget
 *
 * This replaces the previous 6 separate Form Action implementations
 * (3 for login × 3 for registration).
 */
public class CloudflareTurnstileFormActionFactory implements FormActionFactory {

    public static final String PROVIDER_ID = "cloudflare-turnstile-form-action";

    @Override
    public String getDisplayType() {
        return "Cloudflare Turnstile";
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
        return "Cloudflare Turnstile CAPTCHA verification for REGISTRATION flows only. " +
               "For login flows, use the 'Cloudflare Turnstile Authenticator' instead. " +
               "Supports Script Injection (JavaScript) or Custom Theme implementation methods.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CloudflareTurnstileFormAction.CONFIG_IMPLEMENTATION_METHOD)
                .label("Implementation Method")
                .helpText("How to render the Turnstile widget: " +
                         "SCRIPT_INJECTION (JavaScript DOM, works with any theme), " +
                         "CUSTOM_THEME (requires theme customization)")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("SCRIPT_INJECTION", "CUSTOM_THEME")
                .defaultValue("SCRIPT_INJECTION")
                .add()

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
                .name(CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR)
                .label("Allowlist Behavior")
                .helpText("Behavior for allowlisted IPs: VERIFY_BUT_ALLOW (default, audit mode) - make API call and log result, but always allow access; SKIP_VERIFICATION (faster, saves API quota) - don't make Cloudflare API call.")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("SKIP_VERIFICATION", "VERIFY_BUT_ALLOW")
                .defaultValue("VERIFY_BUT_ALLOW")
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
        return new CloudflareTurnstileFormAction();
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
