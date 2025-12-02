package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

import java.time.Instant;
import java.util.Map;

/**
 * Unified Cloudflare Turnstile authenticator for both login and registration flows.
 *
 * This authenticator automatically detects the flow type and displays the appropriate
 * Turnstile widget before the login or registration form. It verifies the response
 * token against Cloudflare's API with support for advanced features like fail modes
 * and conditional actions.
 *
 * Extends AbstractUsernameFormAuthenticator to reuse Keycloak's battle-tested
 * credential validation logic, including brute force protection, password validation,
 * and authentication session management.
 */
public class CloudflareTurnstileAuthenticator extends org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator {

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
    public static final String CONFIG_ENABLE_DEBUG_LOGGING = "enableDebugLogging";
    public static final String CONFIG_IMPLEMENTATION_METHOD = "implementationMethod";
    public static final String CONFIG_ALLOWLIST_BEHAVIOR = "allowlistBehavior";

    // Implementation methods
    private static final String METHOD_SEPARATE_PAGE = "SEPARATE_PAGE";
    private static final String METHOD_SCRIPT_INJECTION = "SCRIPT_INJECTION";
    private static final String METHOD_CUSTOM_THEME = "CUSTOM_THEME";

    // Allowlist behavior modes
    private static final String ALLOWLIST_SKIP_VERIFICATION = "SKIP_VERIFICATION";
    private static final String ALLOWLIST_VERIFY_BUT_ALLOW = "VERIFY_BUT_ALLOW";

    // Form parameter
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.error("Cloudflare Turnstile authenticator is not configured");
            Response response = context.form()
                    .setError("turnstileConfigError")
                    .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR, response);
            return;
        }

        Map<String, String> configMap = config.getConfig();
        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);
        boolean isRegistrationFlow = isRegistrationFlow(context);

        // Check IP blocklist first
        String ipBlocklist = configMap.get(CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, denying access", ipAddress);
            CloudflareTurnstileHelper.logIpBlockedEvent(context.getEvent(), ipAddress);

            // Add comprehensive audit context to event
            String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED");
            String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
            String allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
            String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                false, true, false, false, "Blocked - IP blocklisted");

            // Store blocklist denial to database
            boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                CloudflareTurnstileService.TurnstileVerificationResult blockedResult =
                    new CloudflareTurnstileService.TurnstileVerificationResult(false, "ip_blocked", null, null, "ip_blocklisted");
                storeVerificationResultWithAudit(context, blockedResult, ipAddress, isRegistrationFlow,
                    configMap, false, true, false, false, "Blocked - IP blocklisted");
            }

            context.getEvent().error(Errors.ACCESS_DENIED);

            Response response = context.form()
                    .setError("turnstileIpBlocked")
                    .createErrorPage(Response.Status.FORBIDDEN);
            context.failure(AuthenticationFlowError.ACCESS_DENIED, response);
            return;
        }

        // Check IP allowlist (note: allowlisted IPs still see widget for UX, validation handled in action())
        String ipAllowlist = configMap.get(CONFIG_IP_ALLOWLIST);
        boolean ipAllowed = CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist);
        if (ipAllowed) {
            logger.debugf("IP %s is in allowlist, will render widget but handle verification based on allowlistBehavior config", ipAddress);
            // Don't return here - continue to render the widget
        }

        // Display Turnstile challenge
        String siteKey = configMap.get(CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CONFIG_WIDGET_THEME, "auto");
        boolean enableDebugLogging = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_ENABLE_DEBUG_LOGGING, "false"));
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);

        logger.debugf("Rendering Turnstile using %s method for %s flow (IP: %s)",
                     implementationMethod, isRegistrationFlow ? "registration" : "login", ipAddress);

        // Render based on implementation method
        switch (implementationMethod) {
            case METHOD_SCRIPT_INJECTION:
                displayScriptInjection(context, configMap, siteKey, widgetMode, widgetTheme, enableDebugLogging, isRegistrationFlow);
                break;
            case METHOD_CUSTOM_THEME:
                displayCustomTheme(context, siteKey, widgetMode, widgetTheme, enableDebugLogging, isRegistrationFlow);
                break;
            case METHOD_SEPARATE_PAGE:
            default:
                displaySeparatePage(context, siteKey, widgetMode, widgetTheme, enableDebugLogging, isRegistrationFlow);
                break;
        }
    }

    /**
     * Override challenge() to ensure Turnstile configuration is re-added when form is re-displayed
     * (e.g., after wrong credentials, brute force errors, etc.).
     *
     * This fixes the issue where Turnstile widget disappears on retry in SCRIPT_INJECTION mode.
     */
    @Override
    protected Response challenge(AuthenticationFlowContext context, String error, String field) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            return super.challenge(context, error, field);
        }

        Map<String, String> configMap = config.getConfig();
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);
        String siteKey = configMap.get(CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CONFIG_WIDGET_THEME, "auto");
        boolean enableDebugLogging = Boolean.parseBoolean(
                configMap.getOrDefault(CONFIG_ENABLE_DEBUG_LOGGING, "false"));

        // Create form with error message
        org.keycloak.forms.login.LoginFormsProvider form = context.form().setExecution(context.getExecution().getId());
        if (error != null) {
            if (field != null) {
                form.addError(new FormMessage(field, error));
            } else {
                form.setError(error);
            }
        }

        // Add Turnstile configuration based on implementation method
        form.setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .setAttribute("enableDebugLogging", enableDebugLogging);

        // Handle each implementation method appropriately
        if (METHOD_SEPARATE_PAGE.equals(implementationMethod)) {
            // For SEPARATE_PAGE: display Turnstile-only form (not login form)
            // This handles edge cases like brute force errors on Turnstile page
            return form.createForm("turnstile-form.ftl");

        } else if (METHOD_SCRIPT_INJECTION.equals(implementationMethod)) {
            // For SCRIPT_INJECTION: re-add scripts and display login form with Turnstile
            String realmName = context.getRealm().getName();
            String providerId = CloudflareTurnstileResourceProviderFactory.PROVIDER_ID;

            String configPath = CloudflareTurnstileHelper.buildConfigJsUrl(
                    context.getUriInfo(), realmName, providerId, siteKey, widgetMode, widgetTheme, enableDebugLogging);
            String injectorPath = CloudflareTurnstileHelper.buildInjectorJsUrl(
                    context.getUriInfo(), realmName, providerId);

            form.addScript(configPath);
            form.addScript(injectorPath);
            form.setAttribute("turnstileRequired", true);
            form.setAttribute("turnstileUseScriptInjection", true);
            form.setAttribute("turnstileSkipped", false);

            return form.createLoginUsernamePassword();

        } else if (METHOD_CUSTOM_THEME.equals(implementationMethod)) {
            // For CUSTOM_THEME: set attributes for theme to use
            form.setAttribute("turnstileRequired", true);
            form.setAttribute("turnstileSkipped", false);
            return form.createLoginUsernamePassword();
        }

        // Fallback to parent implementation
        return super.challenge(context, error, field);
    }

    /**
     * Display Turnstile on a separate page (default method).
     * Shows only the Turnstile widget, then proceeds to login/registration form.
     */
    private void displaySeparatePage(AuthenticationFlowContext context, String siteKey, String widgetMode,
                                     String widgetTheme, boolean enableDebugLogging, boolean isRegistrationFlow) {
        String template = isRegistrationFlow ? "turnstile-registration-form.ftl" : "turnstile-form.ftl";

        Response challenge = context.form()
                .setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .setAttribute("isRegistrationFlow", isRegistrationFlow)
                .setAttribute("enableDebugLogging", enableDebugLogging)
                .setAttribute("turnstileRequired", true)
                .setAttribute("turnstileSkipped", false)
                .createForm(template);

        context.challenge(challenge);
        logger.debugf("Displayed Turnstile on separate page using template: %s", template);
    }

    /**
     * Display Turnstile inline using script injection method.
     * Adds JavaScript files that inject the widget into the login/registration form.
     */
    private void displayScriptInjection(AuthenticationFlowContext context, Map<String, String> configMap,
                                       String siteKey, String widgetMode, String widgetTheme,
                                       boolean enableDebugLogging, boolean isRegistrationFlow) {
        String realmName = context.getRealm().getName();
        String providerId = CloudflareTurnstileResourceProviderFactory.PROVIDER_ID;

        // Build URLs using helper methods (handles context path automatically via UriInfo)
        String configPath = CloudflareTurnstileHelper.buildConfigJsUrl(
                context.getUriInfo(), realmName, providerId, siteKey, widgetMode, widgetTheme, enableDebugLogging);
        String injectorPath = CloudflareTurnstileHelper.buildInjectorJsUrl(
                context.getUriInfo(), realmName, providerId);

        var formProvider = context.form()
                .setAttribute("turnstileRequired", true)
                .setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .setAttribute("turnstileUseScriptInjection", true)
                .setAttribute("turnstileSkipped", false)
                .setAttribute("enableDebugLogging", enableDebugLogging);

        formProvider.addScript(configPath);
        formProvider.addScript(injectorPath);

        // For login flows, use theme's standard login form with scripts injected
        // For registration flows, use custom template (FormAction handles registration differently)
        Response challenge;
        if (isRegistrationFlow) {
            // Registration uses custom template
            challenge = formProvider.createForm("register-turnstile.ftl");
        } else {
            // Login uses theme's native login.ftl with scripts injected
            challenge = formProvider.createLoginUsernamePassword();
        }

        context.challenge(challenge);
        logger.debugf("Script injection mode configured for %s flow: siteKey=%s, mode=%s, theme=%s",
                     isRegistrationFlow ? "registration" : "login", siteKey, widgetMode, widgetTheme);
    }

    /**
     * Display Turnstile inline using custom theme method.
     * Sets attributes for the theme's template to render the widget.
     */
    private void displayCustomTheme(AuthenticationFlowContext context, String siteKey, String widgetMode,
                                    String widgetTheme, boolean enableDebugLogging, boolean isRegistrationFlow) {
        var formProvider = context.form()
                .setAttribute("turnstileRequired", true)
                .setAttribute("turnstileSkipped", false)
                .setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .setAttribute("enableDebugLogging", enableDebugLogging);

        // For login flows, use theme's standard login form with Turnstile attributes
        // For registration flows, use custom template (FormAction handles registration differently)
        Response challenge;
        if (isRegistrationFlow) {
            // Registration uses custom template
            challenge = formProvider.createForm("register-turnstile.ftl");
        } else {
            // Login uses theme's native login.ftl (theme must support Turnstile attributes)
            challenge = formProvider.createLoginUsernamePassword();
        }

        context.challenge(challenge);
        logger.debugf("Custom theme mode enabled for %s flow",
                     isRegistrationFlow ? "registration" : "login");
    }

    /**
     * Detects if the current authentication flow is a registration flow.
     */
    private boolean isRegistrationFlow(AuthenticationFlowContext context) {
        if (context.getAuthenticationSession() == null) {
            return false;
        }
        String flowPath = context.getAuthenticationSession().getAuthNote("AUTHENTICATION_FLOW_PATH");
        return flowPath != null && flowPath.contains("registration");
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String turnstileResponse = formData.getFirst(TURNSTILE_RESPONSE_PARAM);

        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        Map<String, String> configMap = config.getConfig();

        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);

        // Check IP allowlist and handle based on allowlistBehavior configuration
        String ipAllowlist = configMap.get(CONFIG_IP_ALLOWLIST);
        boolean ipAllowed = CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist);
        String allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);

        // Detect flow type early since it's needed for audit logging
        boolean isRegistrationFlow = isRegistrationFlow(context);

        if (ipAllowed && ALLOWLIST_SKIP_VERIFICATION.equals(allowlistBehavior)) {
            // SKIP_VERIFICATION mode: Don't make API call, allow access immediately
            logger.debugf("IP %s is allowlisted with SKIP_VERIFICATION behavior - bypassing Cloudflare API call", ipAddress);
            CloudflareTurnstileHelper.logSkipVerificationEvent(context.getEvent(), ipAddress);

            // Add comprehensive audit context to event
            String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED");
            String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
            String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                true, false, true, true, "Allowlisted - SKIP_VERIFICATION");

            // Store audit record for SKIP_VERIFICATION (create dummy successful result)
            boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                CloudflareTurnstileService.TurnstileVerificationResult dummyResult =
                    new CloudflareTurnstileService.TurnstileVerificationResult(true, null, null, null, "verification_skipped");
                storeVerificationResultWithAudit(context, dummyResult, ipAddress, isRegistrationFlow,
                    configMap, true, false, true, true, "Allowlisted - SKIP_VERIFICATION");
            }

            context.success();
            return;
        }

        String secretKey = configMap.get(CONFIG_SECRET_KEY);
        String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED");
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);
        boolean isInlineMode = METHOD_SCRIPT_INJECTION.equals(implementationMethod) || METHOD_CUSTOM_THEME.equals(implementationMethod);

        // Verify the Turnstile token using service
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
                CloudflareTurnstileHelper.logVerificationError(context.getEvent(), e.getMessage(), true, isRegistrationFlow);

                // Add comprehensive audit context to event
                String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
                CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                    failMode, failAction, allowlistBehavior, implementationMethod,
                    false, false, false, true, "Error - FAIL_OPEN");

                // Store error result with FAIL_OPEN audit data
                boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
                if (recordVerifications) {
                    CloudflareTurnstileService.TurnstileVerificationResult errorResult =
                        new CloudflareTurnstileService.TurnstileVerificationResult(false, "verification-error", null, null, e.getMessage());
                    storeVerificationResultWithAudit(context, errorResult, ipAddress, isRegistrationFlow,
                        configMap, false, false, false, true, "Error - FAIL_OPEN");
                }

                context.success();
                return;
            } else {
                String errorType = isRegistrationFlow ? Errors.INVALID_REGISTRATION : Errors.INVALID_USER_CREDENTIALS;
                CloudflareTurnstileHelper.logVerificationError(context.getEvent(), e.getMessage(), false, isRegistrationFlow);

                // Add comprehensive audit context to event BEFORE calling error()
                String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
                CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                    failMode, failAction, allowlistBehavior, implementationMethod,
                    false, false, false, false, "Error - FAIL_CLOSED");

                context.getEvent().error(errorType);

                // Store error result with FAIL_CLOSED audit data
                boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
                if (recordVerifications) {
                    CloudflareTurnstileService.TurnstileVerificationResult errorResult =
                        new CloudflareTurnstileService.TurnstileVerificationResult(false, "verification-error", null, null, e.getMessage());
                    storeVerificationResultWithAudit(context, errorResult, ipAddress, isRegistrationFlow,
                        configMap, false, false, false, false, "Error - FAIL_CLOSED");
                }

                Response response = context.form()
                        .setError("turnstileVerificationError")
                        .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR);
                context.failure(AuthenticationFlowError.INTERNAL_ERROR, response);
                return;
            }
        }

        // Log event details
        CloudflareTurnstileHelper.logVerificationResult(context.getEvent(), result, ipAddress, isRegistrationFlow);

        // Handle VERIFY_BUT_ALLOW mode for allowlisted IPs
        if (ipAllowed && ALLOWLIST_VERIFY_BUT_ALLOW.equals(allowlistBehavior)) {
            logger.debugf("IP %s is allowlisted with VERIFY_BUT_ALLOW behavior - allowing access regardless of verification result (success=%s)",
                         ipAddress, result.isSuccess());
            context.getEvent().detail("cloudflare_turnstile_action", "ip_allowlisted_verify_but_allow");

            // Add comprehensive audit context to event
            String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                true, false, false, true, "Allowlisted - VERIFY_BUT_ALLOW");

            // For inline modes (login), still validate credentials
            if (isInlineMode && !isRegistrationFlow) {
                if (!validateUserAndPassword(context, formData)) {
                    return; // Parent method handles all error responses
                }
            }

            // Store verification result with VERIFY_BUT_ALLOW audit data
            boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                    configMap, true, false, false, true, "Allowlisted - VERIFY_BUT_ALLOW");
            }

            context.success();
            return;
        }

        // Handle verification result
        if (result.isSuccess()) {
            logger.debugf("Turnstile verification successful for IP: %s", ipAddress);

            // For inline modes (login), use parent's credential validation
            // This replaces our custom validateCredentials() with Keycloak's battle-tested logic
            if (isInlineMode && !isRegistrationFlow) {
                // Use AbstractUsernameFormAuthenticator's built-in validation
                // Includes: brute force check, user lookup, password validation, session notes
                if (!validateUserAndPassword(context, formData)) {
                    return; // Parent method handles all error responses
                }
            }

            // Add comprehensive audit context to event
            String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                false, false, false, true, "Success");

            // Store successful verification with audit data
            boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                    configMap, false, false, false, true, "Success");
            }

            // Success - either Turnstile-only (separate page/registration) or Turnstile + credentials (inline)
            context.success();
        } else {
            handleVerificationFailure(context, result, configMap, isRegistrationFlow);
        }
    }

    private void handleVerificationFailure(AuthenticationFlowContext context,
                                          CloudflareTurnstileService.TurnstileVerificationResult result,
                                          Map<String, String> configMap,
                                          boolean isRegistrationFlow) {
        String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);
        boolean isInlineMode = METHOD_SCRIPT_INJECTION.equals(implementationMethod) ||
                               METHOD_CUSTOM_THEME.equals(implementationMethod);
        boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));

        logger.warnf("Turnstile verification failed for IP: %s, errors: %s, action: %s, flow: %s",
                ipAddress, result.getErrorCodes(), failAction, isRegistrationFlow ? "registration" : "login");

        switch (failAction) {
            case "ALLOW":
                logger.info("ALLOW action configured - allowing access despite failed verification");
                CloudflareTurnstileHelper.logFailAction(context.getEvent(), "allowed");

                // Add comprehensive audit context to event
                String allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
                CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                    configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED"), failAction, allowlistBehavior, implementationMethod,
                    false, false, false, true, "Failed - ALLOW");

                // For inline login modes, still need to validate credentials even though Turnstile failed
                // This ensures the user is set in context before proceeding to subsequent authenticators (e.g., OTP)
                if (isInlineMode && !isRegistrationFlow) {
                    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
                    if (!validateUserAndPassword(context, formData)) {
                        return; // Parent method handles error response
                    }
                }

                // Store failed verification with ALLOW action audit data
                if (recordVerifications) {
                    storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                        configMap, false, false, false, true, "Failed - ALLOW");
                }

                context.success();
                break;

            case "REQUIRE_MFA":
                logger.info("REQUIRE_MFA action configured - triggering MFA requirement");
                CloudflareTurnstileHelper.logFailAction(context.getEvent(), "mfa_required");

                // Add comprehensive audit context to event
                allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
                CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                    configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED"), failAction, allowlistBehavior, implementationMethod,
                    false, false, false, true, "Failed - REQUIRE_MFA");

                // For inline login modes, still need to validate credentials
                // This ensures the user is set in context before proceeding to MFA
                if (isInlineMode && !isRegistrationFlow) {
                    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
                    if (!validateUserAndPassword(context, formData)) {
                        return; // Parent method handles error response
                    }
                }

                // Store failed verification with REQUIRE_MFA action audit data
                if (recordVerifications) {
                    storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                        configMap, false, false, false, true, "Failed - REQUIRE_MFA");
                }

                // Set auth note for potential MFA enforcer
                context.getAuthenticationSession().setAuthNote("turnstile_failed", "true");
                context.success();
                break;

            case "BLOCK":
            default:
                logger.info("BLOCK action configured - denying access");
                String errorType = isRegistrationFlow ? Errors.INVALID_REGISTRATION : Errors.INVALID_USER_CREDENTIALS;
                CloudflareTurnstileHelper.logFailAction(context.getEvent(), "blocked");

                // Add comprehensive audit context to event BEFORE calling error()
                allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
                CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                    configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED"), failAction, allowlistBehavior, implementationMethod,
                    false, false, false, false, "Failed - BLOCK");

                context.getEvent().error(errorType);

                // Store failed verification with BLOCK action audit data
                if (recordVerifications) {
                    storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                        configMap, false, false, false, false, "Failed - BLOCK");
                }

                Response response = context.form()
                        .setError("turnstileVerificationFailed")
                        .createErrorPage(Response.Status.FORBIDDEN);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS, response);
                break;
        }
    }

    /**
     * Stores verification result with comprehensive audit data.
     *
     * @param context the authentication context
     * @param result the verification result
     * @param ipAddress the client IP address
     * @param isRegistrationFlow whether this is a registration flow
     * @param configMap the authenticator configuration
     * @param ipAllowlisted whether IP was on the allowlist
     * @param ipBlocklisted whether IP was on the blocklist
     * @param verificationSkipped whether verification was skipped
     * @param authenticationAllowed final authentication outcome
     * @param actionReason reason for the final action taken
     */
    private void storeVerificationResultWithAudit(AuthenticationFlowContext context,
                                                  CloudflareTurnstileService.TurnstileVerificationResult result,
                                                  String ipAddress,
                                                  boolean isRegistrationFlow,
                                                  Map<String, String> configMap,
                                                  boolean ipAllowlisted,
                                                  boolean ipBlocklisted,
                                                  boolean verificationSkipped,
                                                  boolean authenticationAllowed,
                                                  String actionReason) {
        // Note: event_id may be null since this is called before context.success()/error() finalizes the event
        // The event ID is generated during finalization. Use session_id for correlation if needed.
        String eventId = context.getEvent() != null ? context.getEvent().getEvent().getId() : null;
        String sessionId = context.getAuthenticationSession().getParentSession().getId();
        String flowType = isRegistrationFlow ? "REGISTRATION" : "LOGIN";

        // Extract configuration context
        String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_CLOSED");
        String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, "BLOCK");
        String allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);

        CloudflareTurnstileHelper.storeVerificationResult(
                context.getSession(),
                result,
                ipAddress,
                context.getRealm(),
                context.getUser(),
                sessionId,
                eventId,
                flowType,
                failMode,
                failAction,
                allowlistBehavior,
                implementationMethod,
                ipAllowlisted,
                ipBlocklisted,
                verificationSkipped,
                authenticationAllowed,
                actionReason
        );
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
