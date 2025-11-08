package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FormAction that overrides login.ftl with a bundled template containing Turnstile widget.
 * This is Option 3 for Login: Copied Template - native integration without custom theme.
 *
 * Template is bundled in JAR at theme-resources/templates/turnstile-login.ftl
 * Works without custom themes but requires template maintenance during Keycloak upgrades.
 */
public class CloudflareTurnstileLoginCopiedTemplateAction implements FormAction {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileLoginCopiedTemplateAction.class);
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";
    private static final String TEMPLATE_PATH = "/theme-resources/templates/turnstile-login.ftl";

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.warn("No authenticator configuration found for Login Copied Template");
            return;
        }

        Map<String, String> configMap = config.getConfig();
        String ipAddress = context.getConnection().getRemoteAddr();

        // Check IP blocklist
        String ipBlocklist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, will be blocked during validation", ipAddress);
            // Note: We can't block here in buildPage(), will block in validate()
        }

        // Check IP allowlist
        String ipAllowlist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST);
        if (CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist)) {
            logger.debugf("IP %s is in allowlist, will skip Turnstile verification", ipAddress);
            form.setAttribute("turnstileSkipped", true);
            return;
        }

        // Get configuration
        String siteKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME, "auto");

        // Set attributes for template
        form.setAttribute("turnstileRequired", true);
        form.setAttribute("turnstileSiteKey", siteKey);
        form.setAttribute("turnstileMode", widgetMode);
        form.setAttribute("turnstileTheme", widgetTheme);

        // Load and set custom template
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                logger.error("Failed to load Turnstile login template from " + TEMPLATE_PATH);
                return;
            }

            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            form.setAttribute("login.ftl", template);
            logger.debugf("Loaded Turnstile login template for IP %s", ipAddress);
        } catch (Exception e) {
            logger.error("Failed to read Turnstile login template", e);
        }
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();

        if (config == null) {
            logger.error("No authenticator configuration found for validation");
            context.error(Errors.INVALID_USER_CREDENTIALS);
            return;
        }

        Map<String, String> configMap = config.getConfig();
        String ipAddress = context.getConnection().getRemoteAddr();

        // Check IP blocklist first
        String ipBlocklist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, blocking login", ipAddress);
            List<FormMessage> errors = new ArrayList<>();
            errors.add(new FormMessage(null, "turnstileIpBlocked"));
            context.error(Errors.ACCESS_DENIED);
            formData.remove(TURNSTILE_RESPONSE_PARAM);
            context.validationError(formData, errors);
            return;
        }

        // Check IP allowlist
        String ipAllowlist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST);
        if (CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist)) {
            logger.debugf("IP %s is in allowlist, skipping Turnstile validation", ipAddress);
            context.success();
            return;
        }

        // Verify Turnstile token
        String turnstileResponse = formData.getFirst(TURNSTILE_RESPONSE_PARAM);
        String secretKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY);

        CloudflareTurnstileValidator.ValidationResult validationResult;

        try {
            int connectTimeout = Integer.parseInt(configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000"));
            int readTimeout = Integer.parseInt(configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "5000"));

            try (CloudflareTurnstileService service = new CloudflareTurnstileService(secretKey, connectTimeout, readTimeout)) {
                validationResult = CloudflareTurnstileValidator.validateTurnstile(
                        turnstileResponse, ipAddress, configMap, service);
            }
        } catch (Exception e) {
            logger.errorf(e, "Error verifying Turnstile token during login: %s", e.getMessage());
            List<FormMessage> errors = new ArrayList<>();
            errors.add(new FormMessage(null, "turnstileVerificationError"));
            context.error(Errors.INVALID_USER_CREDENTIALS);
            formData.remove(TURNSTILE_RESPONSE_PARAM);
            context.validationError(formData, errors);
            return;
        }

        if (!validationResult.isSuccess()) {
            logger.warnf("Turnstile validation failed for login: %s", validationResult.getErrorCode());
            List<FormMessage> errors = new ArrayList<>();
            errors.add(new FormMessage(null, validationResult.getErrorMessage()));
            context.error(Errors.INVALID_USER_CREDENTIALS);
            formData.remove(TURNSTILE_RESPONSE_PARAM);
            context.validationError(formData, errors);
            return;
        }

        // Success
        context.success();
    }

    @Override
    public void success(FormContext context) {
        // Store verification result if configured
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            return;
        }

        Map<String, String> configMap = config.getConfig();
        boolean recordVerifications = Boolean.parseBoolean(
                configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true"));

        if (!recordVerifications) {
            return;
        }

        try {
            MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
            String turnstileResponse = formData.getFirst(TURNSTILE_RESPONSE_PARAM);
            String ipAddress = context.getConnection().getRemoteAddr();

            // Get verification result
            String secretKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY);
            int connectTimeout = Integer.parseInt(configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000"));
            int readTimeout = Integer.parseInt(configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "5000"));

            CloudflareTurnstileService.TurnstileVerificationResult result;
            try (CloudflareTurnstileService service = new CloudflareTurnstileService(secretKey, connectTimeout, readTimeout)) {
                result = service.verify(turnstileResponse, ipAddress);
            }

            // Store in database
            AuthenticationSessionModel authSession = context.getAuthenticationSession();
            CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();
            entity.setRealmId(context.getRealm().getId());
            entity.setIpAddress(ipAddress);
            entity.setTimestamp(Instant.now());
            entity.setSuccess(result.isSuccess());
            entity.setErrorCodes(result.getErrorCodes() != null ? String.join(",", result.getErrorCodes()) : null);
            entity.setChallengeTs(result.getChallengeTs());
            entity.setHostname(result.getHostname());
            entity.setSessionId(authSession.getParentSession().getId());
            entity.setRawResponse(result.getRawResponse());

            // Try to get user information if available
            UserModel user = context.getUser();
            if (user != null) {
                entity.setUserId(user.getId());
                entity.setUsername(user.getUsername());
                entity.setEmail(user.getEmail());
            }

            EntityManager em = context.getSession().getProvider(JpaConnectionProvider.class).getEntityManager();
            em.persist(entity);

            logger.debugf("Stored Turnstile verification result for login (copied template) from IP %s", ipAddress);
        } catch (Exception e) {
            logger.warn("Failed to store Turnstile verification result for login (copied template)", e);
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // No resources to close
    }
}
