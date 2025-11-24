package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for creating CloudflareTurnstileAuthenticator instances.
 *
 * Defines the configuration UI and properties for the Turnstile authenticator.
 */
public class CloudflareTurnstileAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "cloudflare-turnstile-authenticator";
    private static final CloudflareTurnstileAuthenticator SINGLETON = new CloudflareTurnstileAuthenticator();

    @Override
    public String getDisplayType() {
        return "Cloudflare Turnstile";
    }

    @Override
    public String getReferenceCategory() {
        return "captcha";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Validates users with Cloudflare Turnstile CAPTCHA challenge. " +
                "Automatically works for both login and registration flows. " +
                "Displays a Turnstile widget before the form and verifies the response token. " +
                "Supports advanced features like IP allowlists/blocklists, fail modes, and conditional MFA.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY)
                .label("Site Key")
                .helpText("Your Cloudflare Turnstile site key (client-side key)")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY)
                .label("Secret Key")
                .helpText("Your Cloudflare Turnstile secret key (server-side key)")
                .type(ProviderConfigProperty.PASSWORD)
                .secret(true)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_IMPLEMENTATION_METHOD)
                .label("Implementation Method")
                .helpText("How to integrate Turnstile: SEPARATE_PAGE shows Turnstile on its own page (default), SCRIPT_INJECTION uses JavaScript to inject widget inline (works with any theme), CUSTOM_THEME renders widget inline using theme's template (requires theme support).")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("SEPARATE_PAGE", "SCRIPT_INJECTION", "CUSTOM_THEME")
                .defaultValue("SEPARATE_PAGE")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE)
                .label("Widget Mode")
                .helpText("Turnstile widget mode: managed (default), non-interactive, or invisible")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("managed", "non-interactive", "invisible")
                .defaultValue("managed")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME)
                .label("Widget Theme")
                .helpText("Visual theme for the Turnstile widget")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("light", "dark", "auto")
                .defaultValue("auto")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS)
                .label("Record Verifications")
                .helpText("Store verification results in the database for auditing and analytics")
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
                .label("Verification Failure Action")
                .helpText("Action to take when Turnstile verification completes but fails (returns success=false). BLOCK denies access, ALLOW permits access with warning, REQUIRE_MFA adds MFA requirement.")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("BLOCK", "ALLOW", "REQUIRE_MFA")
                .defaultValue("BLOCK")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE)
                .label("Error Handling Mode")
                .helpText("How to handle errors during verification (API timeouts, network errors, service outages). FAIL_CLOSED blocks access for maximum security, FAIL_OPEN allows access to prevent lockouts.")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("FAIL_CLOSED", "FAIL_OPEN")
                .defaultValue("FAIL_CLOSED")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT)
                .label("Connect Timeout (ms)")
                .helpText("Connection timeout in milliseconds for Cloudflare API calls")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5000")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT)
                .label("Read Timeout (ms)")
                .helpText("Read timeout in milliseconds for Cloudflare API calls")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5000")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_ENABLE_DEBUG_LOGGING)
                .label("Enable Debug Logging")
                .helpText("Enable JavaScript console logging for troubleshooting Turnstile widget behavior. Disable in production to reduce console noise.")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("false")
                .add()

                .build();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization required
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization required
    }

    @Override
    public void close() {
        // No cleanup required
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
