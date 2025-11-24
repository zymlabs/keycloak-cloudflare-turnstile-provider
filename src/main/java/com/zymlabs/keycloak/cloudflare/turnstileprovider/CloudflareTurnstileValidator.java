package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;

import java.util.Map;

/**
 * Shared utility class for Turnstile validation logic used across different authenticator types.
 * Extracts common validation code to avoid duplication between login and registration implementations.
 */
public class CloudflareTurnstileValidator {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileValidator.class);

    /**
     * Validation result containing success status and optional error information.
     */
    public static class ValidationResult {
        private final boolean success;
        private final String errorCode;
        private final String errorMessage;
        private final CloudflareTurnstileService.TurnstileVerificationResult verificationResult;

        public ValidationResult(boolean success, String errorCode, String errorMessage,
                                CloudflareTurnstileService.TurnstileVerificationResult verificationResult) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.verificationResult = verificationResult;
        }

        public static ValidationResult success(CloudflareTurnstileService.TurnstileVerificationResult result) {
            return new ValidationResult(true, null, null, result);
        }

        public static ValidationResult failure(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public CloudflareTurnstileService.TurnstileVerificationResult getVerificationResult() {
            return verificationResult;
        }
    }

    /**
     * Checks if an IP address should skip Turnstile verification based on allowlist.
     *
     * @param ipAddress IP address to check
     * @param ipAllowlist Comma-separated list of IPs/CIDRs
     * @return true if IP is in allowlist and should skip verification
     */
    public static boolean isIpAllowed(String ipAddress, String ipAllowlist) {
        if (ipAllowlist == null || ipAllowlist.trim().isEmpty()) {
            return false;
        }

        try {
            boolean isAllowed = IpAddressUtils.isIpInList(ipAddress, ipAllowlist);
            if (isAllowed) {
                logger.infof("IP %s is in allowlist, skipping Turnstile verification", ipAddress);
            }
            return isAllowed;
        } catch (Exception e) {
            logger.warn("Error checking IP allowlist", e);
            return false;
        }
    }

    /**
     * Checks if an IP address should be immediately blocked based on blocklist.
     *
     * @param ipAddress IP address to check
     * @param ipBlocklist Comma-separated list of IPs/CIDRs
     * @return true if IP is in blocklist and should be blocked
     */
    public static boolean isIpBlocked(String ipAddress, String ipBlocklist) {
        if (ipBlocklist == null || ipBlocklist.trim().isEmpty()) {
            return false;
        }

        try {
            boolean isBlocked = IpAddressUtils.isIpInList(ipAddress, ipBlocklist);
            if (isBlocked) {
                logger.warnf("IP %s is in blocklist, blocking access", ipAddress);
            }
            return isBlocked;
        } catch (Exception e) {
            logger.warn("Error checking IP blocklist", e);
            return false;
        }
    }

    /**
     * Validates Turnstile response token with Cloudflare API.
     *
     * @param turnstileResponse The cf-turnstile-response token from the form
     * @param ipAddress Client IP address
     * @param config Authenticator configuration containing siteKey, secretKey, etc.
     * @param service CloudflareTurnstileService instance for verification
     * @return ValidationResult containing success status and verification details
     */
    public static ValidationResult validateTurnstile(
            String turnstileResponse,
            String ipAddress,
            Map<String, String> config,
            CloudflareTurnstileService service) {

        // Extract configuration
        String ipAllowlist = config.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST,
                "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7,fe80::/10");
        String ipBlocklist = config.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST, "");
        String failMode = config.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");

        // Check IP blocklist first
        if (isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("Blocking access for IP %s (in blocklist)", ipAddress);
            return ValidationResult.failure("ip_blocked", "turnstileIpBlocked");
        }

        // Check IP allowlist
        if (isIpAllowed(ipAddress, ipAllowlist)) {
            logger.infof("IP %s in allowlist, skipping Turnstile verification", ipAddress);
            // Create a synthetic success result
            CloudflareTurnstileService.TurnstileVerificationResult allowlistResult =
                    new CloudflareTurnstileService.TurnstileVerificationResult(
                            true, null, null, null, "{\"success\":true,\"allowlist\":true}");
            return ValidationResult.success(allowlistResult);
        }

        // Verify with Cloudflare
        try {
            CloudflareTurnstileService.TurnstileVerificationResult result = service.verify(turnstileResponse, ipAddress);

            if (result.isSuccess()) {
                logger.infof("Turnstile verification successful for IP %s", ipAddress);
                return ValidationResult.success(result);
            } else {
                logger.warnf("Turnstile verification failed for IP %s: %s", ipAddress, result.getErrorCodes());
                return ValidationResult.failure("verification_failed", "turnstileVerificationFailed");
            }
        } catch (Exception e) {
            logger.error("Error during Turnstile verification", e);

            // Apply fail mode
            if ("FAIL_OPEN".equals(failMode)) {
                logger.warn("Turnstile verification error, but FAIL_OPEN mode - allowing access");
                // Create synthetic success for fail-open mode
                CloudflareTurnstileService.TurnstileVerificationResult failOpenResult =
                        new CloudflareTurnstileService.TurnstileVerificationResult(
                                true, null, null, null, "{\"success\":true,\"fail_open\":true}");
                return ValidationResult.success(failOpenResult);
            } else {
                logger.error("Turnstile verification error, FAIL_CLOSED mode - blocking access");
                return ValidationResult.failure("verification_error", "turnstileVerificationError");
            }
        }
    }

    /**
     * Gets the client IP address from the authentication context.
     *
     * @param context Authentication flow context
     * @return Client IP address
     */
    public static String getClientIpAddress(AuthenticationFlowContext context) {
        return context.getConnection().getRemoteAddr();
    }

    /**
     * Gets the client IP address from the form context.
     *
     * @param context Form context
     * @return Client IP address
     */
    public static String getClientIpAddress(org.keycloak.authentication.FormContext context) {
        return context.getConnection().getRemoteAddr();
    }

    /**
     * Gets the client IP address from the validation context.
     *
     * @param context Validation context
     * @return Client IP address
     */
    public static String getClientIpAddress(org.keycloak.authentication.ValidationContext context) {
        return context.getConnection().getRemoteAddr();
    }

    /**
     * Gets configuration from authenticator config model.
     *
     * @param context Authentication flow context
     * @return Configuration map, or empty map if no config
     */
    public static Map<String, String> getConfig(AuthenticationFlowContext context) {
        AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
        if (configModel == null) {
            logger.warn("No authenticator configuration found");
            return Map.of();
        }
        return configModel.getConfig();
    }
}
