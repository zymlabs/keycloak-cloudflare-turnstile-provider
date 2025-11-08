package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;

/**
 * Cloudflare Turnstile authenticator for registration flows.
 * Presents a standalone Turnstile verification page before the registration form.
 * This is Option 1: Separate Page - most stable, no theme dependencies.
 */
public class CloudflareTurnstileRegistrationAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileRegistrationAuthenticator.class);
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.error("No authenticator configuration found");
            context.attempted();
            return;
        }

        Map<String, String> configMap = config.getConfig();
        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);

        // Check IP blocklist first
        String ipBlocklist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, denying registration access", ipAddress);
            context.getEvent()
                    .detail("cloudflare_turnstile_result", "blocked_ip")
                    .detail("ip_address", ipAddress)
                    .error(Errors.ACCESS_DENIED);

            Response response = context.form()
                    .setError("turnstileIpBlocked")
                    .createErrorPage(Response.Status.FORBIDDEN);
            context.failure(org.keycloak.authentication.AuthenticationFlowError.ACCESS_DENIED, response);
            return;
        }

        // Check IP allowlist
        String ipAllowlist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST);
        if (CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist)) {
            logger.debugf("IP %s is in allowlist, skipping Turnstile verification for registration", ipAddress);
            context.success();
            return;
        }

        // Display Turnstile challenge
        String siteKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME, "auto");

        Response challenge = context.form()
                .setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .setAttribute("registrationMode", true)
                .createForm("turnstile-registration-form.ftl");

        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String turnstileResponse = formData.getFirst(TURNSTILE_RESPONSE_PARAM);

        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        Map<String, String> configMap = config.getConfig();

        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);
        String secretKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY);

        // Verify the Turnstile token using shared validator
        CloudflareTurnstileValidator.ValidationResult validationResult;

        try {
            int connectTimeout = Integer.parseInt(configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000"));
            int readTimeout = Integer.parseInt(configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "5000"));

            try (CloudflareTurnstileService service = new CloudflareTurnstileService(secretKey, connectTimeout, readTimeout)) {
                validationResult = CloudflareTurnstileValidator.validateTurnstile(
                        turnstileResponse, ipAddress, configMap, service);
            }
        } catch (Exception e) {
            logger.errorf(e, "Error verifying Turnstile token for registration: %s", e.getMessage());
            handleVerificationError(context, "verification_error", e.getMessage());
            return;
        }

        if (!validationResult.isSuccess()) {
            handleVerificationFailure(context, validationResult.getErrorCode(), validationResult.getErrorMessage());
            return;
        }

        // Success - store verification result if configured
        CloudflareTurnstileService.TurnstileVerificationResult result = validationResult.getVerificationResult();
        boolean recordVerifications = Boolean.parseBoolean(
                configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true"));

        if (recordVerifications) {
            storeVerificationResult(context, result, ipAddress);
        }

        context.getEvent()
                .detail("cloudflare_turnstile_result", "success")
                .detail("ip_address", ipAddress);

        context.success();
    }

    private void handleVerificationFailure(AuthenticationFlowContext context, String errorCode, String errorMessage) {
        logger.warnf("Turnstile verification failed for registration: %s", errorCode);

        context.getEvent()
                .detail("cloudflare_turnstile_result", "failed")
                .detail("error_code", errorCode)
                .error(Errors.INVALID_REGISTRATION);

        Response challenge = context.form()
                .setError(errorMessage)
                .createForm("turnstile-registration-form.ftl");

        context.failureChallenge(org.keycloak.authentication.AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
    }

    private void handleVerificationError(AuthenticationFlowContext context, String errorType, String errorMessage) {
        context.getEvent()
                .detail("cloudflare_turnstile_result", errorType)
                .detail("error_message", errorMessage)
                .error(Errors.INVALID_REGISTRATION);

        Response challenge = context.form()
                .setError("turnstileVerificationError")
                .createForm("turnstile-registration-form.ftl");

        context.failureChallenge(org.keycloak.authentication.AuthenticationFlowError.INTERNAL_ERROR, challenge);
    }

    private void storeVerificationResult(AuthenticationFlowContext context,
                                        CloudflareTurnstileService.TurnstileVerificationResult result,
                                        String ipAddress) {
        try {
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

            EntityManager em = context.getSession().getProvider(JpaConnectionProvider.class).getEntityManager();
            em.persist(entity);

            logger.debugf("Stored Turnstile verification result for registration from IP %s", ipAddress);
        } catch (Exception e) {
            logger.warn("Failed to store Turnstile verification result for registration", e);
        }
    }

    @Override
    public boolean requiresUser() {
        return false; // Pre-registration verification, no user exists yet
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
