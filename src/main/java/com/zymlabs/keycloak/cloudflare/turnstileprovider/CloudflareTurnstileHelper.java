package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.Map;

/**
 * Helper class containing shared logic for Cloudflare Turnstile authentication.
 * Extracts common functionality used by both CloudflareTurnstileAuthenticator
 * and CloudflareTurnstileFormAction to eliminate code duplication.
 */
public class CloudflareTurnstileHelper {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileHelper.class);

    // ===== EVENT LOGGING METHODS =====

    /**
     * Logs an IP blocked event.
     *
     * @param event the event builder
     * @param ipAddress the blocked IP address
     */
    public static void logIpBlockedEvent(EventBuilder event, String ipAddress) {
        if (event == null) {
            return;
        }
        event.detail("cloudflare_turnstile_result", "blocked_ip")
             .detail("ip_address", ipAddress);
    }

    /**
     * Logs a skip verification event (IP allowlist with SKIP_VERIFICATION behavior).
     *
     * @param event the event builder
     * @param ipAddress the allowlisted IP address
     */
    public static void logSkipVerificationEvent(EventBuilder event, String ipAddress) {
        if (event == null) {
            return;
        }
        event.detail("cloudflare_turnstile_result", "ip_allowlisted_skip_verification")
             .detail("ip_address", ipAddress);
    }

    /**
     * Logs a verify-but-allow event (IP allowlist with VERIFY_BUT_ALLOW behavior).
     * Logs full verification result details for auditing purposes.
     *
     * @param event the event builder
     * @param result the verification result (may be null)
     * @param ipAddress the IP address
     * @param isRegistration whether this is a registration flow
     */
    public static void logVerifyButAllowEvent(EventBuilder event,
                                              CloudflareTurnstileService.TurnstileVerificationResult result,
                                              String ipAddress,
                                              boolean isRegistration) {
        if (event == null) {
            return;
        }

        if (result != null) {
            event.detail("cloudflare_turnstile_success", String.valueOf(result.isSuccess()))
                 .detail("cloudflare_turnstile_hostname", result.getHostname())
                 .detail("cloudflare_turnstile_flow_type", isRegistration ? "registration" : "login")
                 .detail("cloudflare_turnstile_action", "ip_allowlisted_verify_but_allow")
                 .detail("ip_address", ipAddress);

            if (result.getErrorCodes() != null && !result.getErrorCodes().isEmpty()) {
                event.detail("cloudflare_turnstile_errors", result.getErrorCodes());
            }
        } else {
            // Fallback if result is null
            event.detail("cloudflare_turnstile_action", "ip_allowlisted_verify_but_allow")
                 .detail("ip_address", ipAddress);
        }
    }

    /**
     * Logs verification result details (success or failure).
     *
     * @param event the event builder
     * @param result the verification result
     * @param ipAddress the IP address
     * @param isRegistration whether this is a registration flow
     */
    public static void logVerificationResult(EventBuilder event,
                                             CloudflareTurnstileService.TurnstileVerificationResult result,
                                             String ipAddress,
                                             boolean isRegistration) {
        if (event == null || result == null) {
            return;
        }

        event.detail("cloudflare_turnstile_success", String.valueOf(result.isSuccess()))
             .detail("cloudflare_turnstile_hostname", result.getHostname())
             .detail("cloudflare_turnstile_flow_type", isRegistration ? "registration" : "login")
             .detail("ip_address", ipAddress);

        if (result.getErrorCodes() != null && !result.getErrorCodes().isEmpty()) {
            event.detail("cloudflare_turnstile_errors", result.getErrorCodes());
        }
    }

    /**
     * Logs a verification error event.
     *
     * @param event the event builder
     * @param errorMessage the error message
     * @param failOpen whether fail-open mode is enabled
     * @param isRegistration whether this is a registration flow
     */
    public static void logVerificationError(EventBuilder event,
                                            String errorMessage,
                                            boolean failOpen,
                                            boolean isRegistration) {
        if (event == null) {
            return;
        }

        if (failOpen) {
            event.detail("cloudflare_turnstile_result", "verification_error_fail_open")
                 .detail("error_message", errorMessage);
        } else {
            event.detail("cloudflare_turnstile_result", "verification_error")
                 .detail("error_message", errorMessage);
        }
    }

    /**
     * Logs a fail action event (ALLOW, REQUIRE_MFA, or BLOCK).
     *
     * @param event the event builder
     * @param action the fail action (lowercase: "allowed", "mfa_required", "blocked")
     */
    public static void logFailAction(EventBuilder event, String action) {
        if (event == null) {
            return;
        }
        event.detail("cloudflare_turnstile_action", action);
    }

    /**
     * Adds comprehensive audit context to the event for consistency with database audit trail.
     * This should be called after the specific event logging methods to add configuration
     * and outcome details.
     *
     * @param event the event builder
     * @param failMode the configured fail mode (FAIL_OPEN, FAIL_CLOSED)
     * @param failAction the configured fail action (ALLOW, BLOCK, REQUIRE_MFA)
     * @param allowlistBehavior the IP allowlist behavior (SKIP_VERIFICATION, VERIFY_BUT_ALLOW)
     * @param implementationMethod the implementation method (SEPARATE_PAGE, SCRIPT_INJECTION, CUSTOM_THEME)
     * @param ipAllowlisted whether IP was on the allowlist
     * @param ipBlocklisted whether IP was on the blocklist
     * @param verificationSkipped whether verification was skipped
     * @param authenticationAllowed final authentication outcome
     * @param actionReason reason for the final action taken
     */
    public static void addAuditContextToEvent(EventBuilder event,
                                              String failMode,
                                              String failAction,
                                              String allowlistBehavior,
                                              String implementationMethod,
                                              boolean ipAllowlisted,
                                              boolean ipBlocklisted,
                                              boolean verificationSkipped,
                                              boolean authenticationAllowed,
                                              String actionReason) {
        if (event == null) {
            return;
        }

        // Add configuration context
        if (failMode != null) {
            event.detail("cloudflare_turnstile_fail_mode", failMode);
        }
        if (failAction != null) {
            event.detail("cloudflare_turnstile_fail_action", failAction);
        }
        if (allowlistBehavior != null) {
            event.detail("cloudflare_turnstile_allowlist_behavior", allowlistBehavior);
        }
        if (implementationMethod != null) {
            event.detail("cloudflare_turnstile_implementation_method", implementationMethod);
        }

        // Add IP processing status
        event.detail("cloudflare_turnstile_ip_allowlisted", String.valueOf(ipAllowlisted));
        event.detail("cloudflare_turnstile_ip_blocklisted", String.valueOf(ipBlocklisted));
        event.detail("cloudflare_turnstile_verification_skipped", String.valueOf(verificationSkipped));

        // Add final outcome
        event.detail("cloudflare_turnstile_authentication_allowed", String.valueOf(authenticationAllowed));
        if (actionReason != null) {
            event.detail("cloudflare_turnstile_action_reason", actionReason);
        }
    }

    // ===== DATABASE STORAGE METHODS =====

    /**
     * Creates a CloudflareTurnstileCheckEntity populated with verification result data.
     *
     * @param result the verification result
     * @param ipAddress the IP address
     * @param realm the realm
     * @param user the user (nullable for pre-authentication scenarios)
     * @param sessionId the session ID (nullable)
     * @param eventId the event ID (nullable)
     * @param flowType the flow type (LOGIN, REGISTRATION, etc.)
     * @param failMode the configured fail mode (FAIL_OPEN, FAIL_CLOSED)
     * @param failAction the configured fail action (ALLOW, BLOCK, REQUIRE_MFA)
     * @param allowlistBehavior the IP allowlist behavior (SKIP_VERIFICATION, VERIFY_BUT_ALLOW)
     * @param implementationMethod the implementation method (SEPARATE_PAGE, SCRIPT_INJECTION, CUSTOM_THEME)
     * @param ipAllowlisted whether IP was on the allowlist
     * @param ipBlocklisted whether IP was on the blocklist
     * @param verificationSkipped whether verification was skipped
     * @param authenticationAllowed final authentication outcome
     * @param actionReason reason for the final action taken
     * @return populated entity ready to persist
     */
    public static CloudflareTurnstileCheckEntity createVerificationEntity(
            CloudflareTurnstileService.TurnstileVerificationResult result,
            String ipAddress,
            RealmModel realm,
            UserModel user,
            String sessionId,
            String eventId,
            String flowType,
            String failMode,
            String failAction,
            String allowlistBehavior,
            String implementationMethod,
            boolean ipAllowlisted,
            boolean ipBlocklisted,
            boolean verificationSkipped,
            boolean authenticationAllowed,
            String actionReason) {

        CloudflareTurnstileCheckEntity entity = new CloudflareTurnstileCheckEntity();

        // User info (may be null for registration or pre-auth flows)
        if (user != null) {
            entity.setUserId(user.getId());
            entity.setUsername(user.getUsername());
            entity.setEmail(user.getEmail());
        }

        // Realm and request info
        entity.setRealmId(realm.getId());
        entity.setIpAddress(ipAddress);
        entity.setTimestamp(Instant.now());

        // Verification result
        entity.setSuccess(result.isSuccess());
        entity.setErrorCodes(result.getErrorCodes());
        entity.setChallengeTs(result.getChallengeTs());
        entity.setHostname(result.getHostname());
        entity.setRawResponse(result.getRawResponse());

        // Session and event correlation
        if (sessionId != null) {
            entity.setSessionId(sessionId);
        }
        if (eventId != null) {
            entity.setEventId(eventId);
        }
        if (flowType != null) {
            entity.setFlowType(flowType);
        }

        // Configuration context
        entity.setFailMode(failMode);
        entity.setFailAction(failAction);
        entity.setAllowlistBehavior(allowlistBehavior);
        entity.setImplementationMethod(implementationMethod);

        // IP processing status
        entity.setIpAllowlisted(ipAllowlisted);
        entity.setIpBlocklisted(ipBlocklisted);
        entity.setVerificationSkipped(verificationSkipped);

        // Final outcome
        entity.setAuthenticationAllowed(authenticationAllowed);
        entity.setActionReason(actionReason);

        return entity;
    }

    /**
     * Stores a verification result in the database.
     * Handles exceptions gracefully without failing the authentication/registration flow.
     *
     * @param session the Keycloak session
     * @param result the verification result
     * @param ipAddress the IP address
     * @param realm the realm
     * @param user the user (nullable)
     * @param sessionId the session ID (nullable)
     * @param eventId the event ID (nullable)
     * @param flowType the flow type (LOGIN, REGISTRATION, etc.)
     * @param failMode the configured fail mode (FAIL_OPEN, FAIL_CLOSED)
     * @param failAction the configured fail action (ALLOW, BLOCK, REQUIRE_MFA)
     * @param allowlistBehavior the IP allowlist behavior (SKIP_VERIFICATION, VERIFY_BUT_ALLOW)
     * @param implementationMethod the implementation method (SEPARATE_PAGE, SCRIPT_INJECTION, CUSTOM_THEME)
     * @param ipAllowlisted whether IP was on the allowlist
     * @param ipBlocklisted whether IP was on the blocklist
     * @param verificationSkipped whether verification was skipped
     * @param authenticationAllowed final authentication outcome
     * @param actionReason reason for the final action taken
     */
    public static void storeVerificationResult(KeycloakSession session,
                                               CloudflareTurnstileService.TurnstileVerificationResult result,
                                               String ipAddress,
                                               RealmModel realm,
                                               UserModel user,
                                               String sessionId,
                                               String eventId,
                                               String flowType,
                                               String failMode,
                                               String failAction,
                                               String allowlistBehavior,
                                               String implementationMethod,
                                               boolean ipAllowlisted,
                                               boolean ipBlocklisted,
                                               boolean verificationSkipped,
                                               boolean authenticationAllowed,
                                               String actionReason) {
        try {
            CloudflareTurnstileCheckEntity entity = createVerificationEntity(
                    result, ipAddress, realm, user, sessionId, eventId, flowType,
                    failMode, failAction, allowlistBehavior, implementationMethod,
                    ipAllowlisted, ipBlocklisted, verificationSkipped,
                    authenticationAllowed, actionReason);

            EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
            em.persist(entity);

            logger.debugf("Stored Turnstile verification result for IP %s (flow: %s, allowed: %s, reason: %s)",
                    ipAddress, flowType, authenticationAllowed, actionReason);
        } catch (Exception e) {
            logger.warn("Failed to store Turnstile verification result", e);
            // Don't fail the authentication/registration if storage fails
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * URL-encodes a string value for use in query parameters.
     *
     * @param value the value to encode
     * @return URL-encoded value, or empty string if value is null
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.warnf("Failed to URL encode value: %s", value);
            return value;
        }
    }

    /**
     * Extracts connect timeout from configuration with default fallback.
     *
     * @param config the configuration map
     * @return connect timeout in milliseconds (default: 5000)
     */
    public static int getConnectTimeout(Map<String, String> config) {
        return Integer.parseInt(config.getOrDefault(
                CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000"));
    }

    /**
     * Extracts read timeout from configuration with default fallback.
     *
     * @param config the configuration map
     * @return read timeout in milliseconds (default: 5000)
     */
    public static int getReadTimeout(Map<String, String> config) {
        return Integer.parseInt(config.getOrDefault(
                CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "5000"));
    }
}
