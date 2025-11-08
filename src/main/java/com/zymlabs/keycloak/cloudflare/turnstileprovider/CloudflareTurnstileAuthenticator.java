package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.time.Instant;
import java.util.Map;

/**
 * Authenticator that verifies Cloudflare Turnstile CAPTCHA challenges.
 *
 * This authenticator displays a Turnstile widget before the login form
 * and verifies the response token against Cloudflare's API.
 */
public class CloudflareTurnstileAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileAuthenticator.class);

    // Configuration keys
    public static final String CONFIG_SITE_KEY = "siteKey";
    public static final String CONFIG_SECRET_KEY = "secretKey";
    public static final String CONFIG_WIDGET_MODE = "widgetMode";
    public static final String CONFIG_WIDGET_THEME = "widgetTheme";
    public static final String CONFIG_RECORD_VERIFICATIONS = "recordVerifications";
    public static final String CONFIG_IP_ALLOWLIST = "ipAllowlist";
    public static final String CONFIG_IP_BLOCKLIST = "ipBlocklist";
    public static final String CONFIG_FAIL_ACTION = "failAction";
    public static final String CONFIG_FAIL_MODE = "failMode";
    public static final String CONFIG_CONNECT_TIMEOUT = "connectTimeout";
    public static final String CONFIG_READ_TIMEOUT = "readTimeout";

    // Form parameter
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.error("Cloudflare Turnstile authenticator is not configured");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        Map<String, String> configMap = config.getConfig();
        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);

        // Check IP blocklist first
        String ipBlocklist = configMap.get(CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, denying access", ipAddress);
            context.getEvent()
                    .detail("cloudflare_turnstile_result", "blocked_ip")
                    .detail("ip_address", ipAddress)
                    .error(Errors.ACCESS_DENIED);

            Response response = context.form()
                    .setError("turnstileIpBlocked")
                    .createErrorPage(Response.Status.FORBIDDEN);
            context.failure(AuthenticationFlowError.ACCESS_DENIED, response);
            return;
        }

        // Check IP allowlist
        String ipAllowlist = configMap.get(CONFIG_IP_ALLOWLIST);
        if (CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist)) {
            logger.debugf("IP %s is in allowlist, skipping Turnstile verification", ipAddress);
            context.success();
            return;
        }

        // Display Turnstile challenge
        String siteKey = configMap.get(CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CONFIG_WIDGET_THEME, "auto");

        Response challenge = context.form()
                .setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .createForm("turnstile-form.ftl");

        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String turnstileResponse = formData.getFirst(TURNSTILE_RESPONSE_PARAM);

        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        Map<String, String> configMap = config.getConfig();

        String ipAddress = context.getConnection().getRemoteAddr();
        String secretKey = configMap.get(CONFIG_SECRET_KEY);
        String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED");

        // Verify the Turnstile token
        CloudflareTurnstileService.TurnstileVerificationResult result;

        try {
            int connectTimeout = Integer.parseInt(configMap.getOrDefault(CONFIG_CONNECT_TIMEOUT, "5000"));
            int readTimeout = Integer.parseInt(configMap.getOrDefault(CONFIG_READ_TIMEOUT, "5000"));

            try (CloudflareTurnstileService service = new CloudflareTurnstileService(secretKey, connectTimeout, readTimeout)) {
                result = service.verify(turnstileResponse, ipAddress);
            }
        } catch (Exception e) {
            logger.errorf(e, "Error verifying Turnstile token: %s", e.getMessage());

            // Handle according to fail mode
            if ("FAIL_OPEN".equals(failMode)) {
                logger.warn("Turnstile verification failed, but FAIL_OPEN mode is enabled - allowing access");
                context.getEvent()
                        .detail("cloudflare_turnstile_result", "verification_error_fail_open")
                        .detail("error_message", e.getMessage());
                context.success();
                return;
            } else {
                context.getEvent()
                        .detail("cloudflare_turnstile_result", "verification_error")
                        .detail("error_message", e.getMessage())
                        .error(Errors.INVALID_USER_CREDENTIALS);

                Response response = context.form()
                        .setError("turnstileVerificationError")
                        .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR);
                context.failure(AuthenticationFlowError.INTERNAL_ERROR, response);
                return;
            }
        }

        // Store verification result if enabled
        boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
        if (recordVerifications) {
            storeVerificationResult(context, result, ipAddress);
        }

        // Log event details
        context.getEvent()
                .detail("cloudflare_turnstile_success", String.valueOf(result.isSuccess()))
                .detail("cloudflare_turnstile_hostname", result.getHostname())
                .detail("ip_address", ipAddress);

        if (result.getErrorCodes() != null) {
            context.getEvent().detail("cloudflare_turnstile_errors", result.getErrorCodes());
        }

        // Handle verification result
        if (result.isSuccess()) {
            logger.debugf("Turnstile verification successful for IP: %s", ipAddress);
            context.success();
        } else {
            handleVerificationFailure(context, result, configMap);
        }
    }

    private void handleVerificationFailure(AuthenticationFlowContext context,
                                          CloudflareTurnstileService.TurnstileVerificationResult result,
                                          Map<String, String> configMap) {
        String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
        String ipAddress = context.getConnection().getRemoteAddr();

        logger.warnf("Turnstile verification failed for IP: %s, errors: %s, action: %s",
                ipAddress, result.getErrorCodes(), failAction);

        switch (failAction) {
            case "ALLOW":
                logger.info("ALLOW action configured - allowing access despite failed verification");
                context.getEvent().detail("cloudflare_turnstile_action", "allowed");
                context.success();
                break;

            case "REQUIRE_MFA":
                logger.info("REQUIRE_MFA action configured - triggering MFA requirement");
                context.getEvent().detail("cloudflare_turnstile_action", "mfa_required");
                // Set auth note for potential MFA enforcer
                context.getAuthenticationSession().setAuthNote("turnstile_failed", "true");
                context.success();
                break;

            case "BLOCK":
            default:
                logger.info("BLOCK action configured - denying access");
                context.getEvent()
                        .detail("cloudflare_turnstile_action", "blocked")
                        .error(Errors.INVALID_USER_CREDENTIALS);

                Response response = context.form()
                        .setError("turnstileVerificationFailed")
                        .createErrorPage(Response.Status.FORBIDDEN);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS, response);
                break;
        }
    }

    private void storeVerificationResult(AuthenticationFlowContext context,
                                         CloudflareTurnstileService.TurnstileVerificationResult result,
                                         String ipAddress) {
        try {
            EntityManager em = context.getSession().getProvider(JpaConnectionProvider.class).getEntityManager();

            CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();

            // User info (may be null for pre-auth)
            UserModel user = context.getUser();
            if (user != null) {
                entity.setUserId(user.getId());
                entity.setUsername(user.getUsername());
                entity.setEmail(user.getEmail());
            }

            // Realm info
            entity.setRealmId(context.getRealm().getId());

            // Request info
            entity.setIpAddress(ipAddress);
            entity.setTimestamp(Instant.now());

            // Verification result
            entity.setSuccess(result.isSuccess());
            entity.setErrorCodes(result.getErrorCodes());
            entity.setChallengeTs(result.getChallengeTs());
            entity.setHostname(result.getHostname());
            entity.setRawResponse(result.getRawResponse());

            // Event correlation
            if (context.getEvent() != null) {
                entity.setEventId(context.getEvent().getEvent().getId());
            }
            entity.setSessionId(context.getAuthenticationSession().getParentSession().getId());

            em.persist(entity);
            logger.debugf("Stored Turnstile verification result: %s", entity);

        } catch (Exception e) {
            // Don't fail authentication if storage fails
            logger.errorf(e, "Failed to store Turnstile verification result: %s", e.getMessage());
        }
    }

    @Override
    public boolean requiresUser() {
        // Pre-auth mode - no user required
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // Always enabled if configured at flow level
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
