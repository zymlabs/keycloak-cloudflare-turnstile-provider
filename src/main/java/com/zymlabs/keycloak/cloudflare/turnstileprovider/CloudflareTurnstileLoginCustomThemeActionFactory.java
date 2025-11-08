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
 * Factory for CloudflareTurnstileLoginCustomThemeAction.
 * This is Option 4 for Login: Custom Theme - uses 'turnstile' theme with login.ftl template.
 */
public class CloudflareTurnstileLoginCustomThemeActionFactory implements FormActionFactory {

    public static final String PROVIDER_ID = "cloudflare-turnstile-login-custom-theme-action";

    @Override
    public String getDisplayType() {
        return "Cloudflare Turnstile - Login (Custom Theme)";
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
        return "Renders Cloudflare Turnstile widget using the 'turnstile' custom theme. " +
                "REQUIRES: Select 'turnstile' as the Login Theme in Realm Settings → Themes. " +
                "The widget appears natively embedded in the login form via theme template. " +
                "Server-side validation ensures security. " +
                "Best for single-realm deployments with custom theme management.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY)
                .label("Site Key")
                .helpText("Cloudflare Turnstile site key (public key)")
                .type(ProviderConfigProperty.STRING_TYPE)
                .required(true)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY)
                .label("Secret Key")
                .helpText("Cloudflare Turnstile secret key (private key)")
                .type(ProviderConfigProperty.PASSWORD)
                .required(true)
                .secret(true)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE)
                .label("Widget Mode")
                .helpText("Turnstile widget interaction mode")
                .type(ProviderConfigProperty.LIST_TYPE)
                .defaultValue("managed")
                .options("managed", "non-interactive", "invisible")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME)
                .label("Widget Theme")
                .helpText("Turnstile widget visual theme")
                .type(ProviderConfigProperty.LIST_TYPE)
                .defaultValue("auto")
                .options("light", "dark", "auto")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS)
                .label("Record Verifications")
                .helpText("Store verification results in database for auditing")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("true")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST)
                .label("IP Allowlist")
                .helpText("IPs or CIDR ranges to skip verification (comma-separated)")
                .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST)
                .label("IP Blocklist")
                .helpText("IPs or CIDR ranges to block (comma-separated)")
                .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION)
                .label("Fail Action")
                .helpText("Action when verification fails")
                .type(ProviderConfigProperty.LIST_TYPE)
                .defaultValue("BLOCK")
                .options("BLOCK", "ALLOW", "REQUIRE_MFA")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE)
                .label("Fail Mode")
                .helpText("Behavior when API is unreachable")
                .type(ProviderConfigProperty.LIST_TYPE)
                .defaultValue("FAIL_CLOSED")
                .options("FAIL_CLOSED", "FAIL_OPEN")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT)
                .label("Connect Timeout (ms)")
                .helpText("Connection timeout for API calls in milliseconds")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5000")
                .add()

                .property()
                .name(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT)
                .label("Read Timeout (ms)")
                .helpText("Read timeout for API calls in milliseconds")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5000")
                .add()

                .build();
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return new CloudflareTurnstileLoginCustomThemeAction();
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
