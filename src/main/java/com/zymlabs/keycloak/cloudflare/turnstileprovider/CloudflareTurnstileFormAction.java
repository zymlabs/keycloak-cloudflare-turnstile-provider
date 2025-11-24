package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.MultivaluedMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified Cloudflare Turnstile Form Action for both login and registration flows.
 *
 * Auto-detects flow type (login vs registration) and supports three implementation methods:
 * - Script Injection: JavaScript DOM manipulation (works with any theme)
 * - Copied Template: Bundled template with embedded widget
 * - Custom Theme: Relies on theme's template to render widget
 *
 * Provides server-side validation regardless of implementation method.
 */
public class CloudflareTurnstileFormAction implements FormAction {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileFormAction.class);
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";

    // Configuration keys
    public static final String CONFIG_IMPLEMENTATION_METHOD = "implementationMethod";

    // Implementation methods
    private static final String METHOD_SCRIPT_INJECTION = "SCRIPT_INJECTION";
    private static final String METHOD_CUSTOM_THEME = "CUSTOM_THEME";

    // Allowlist behavior modes
    private static final String ALLOWLIST_SKIP_VERIFICATION = "SKIP_VERIFICATION";
    private static final String ALLOWLIST_VERIFY_BUT_ALLOW = "VERIFY_BUT_ALLOW";

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        logger.info("***** CloudflareTurnstileFormAction.buildPage() CALLED *****");
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.warn("No authenticator configuration found for Turnstile Form Action");
            return;
        }

        // Guard: Only execute in registration flows
        boolean isRegistrationFlow = isRegistrationFlow(context);
        if (!isRegistrationFlow) {
            logger.warn("CloudflareTurnstileFormAction.buildPage() called in non-registration flow. " +
                       "FormActions should only be used in registration flows. " +
                       "For login flows, use CloudflareTurnstileAuthenticator instead. Skipping execution.");
            return;
        }

        // Guard: Verify no existing user (prevents back-navigation issues)
        if (context.getUser() != null) {
            logger.warn("CloudflareTurnstileFormAction.buildPage() called with existing user context. " +
                       "This indicates browser back-navigation or misconfigured flow. Skipping execution.");
            return;
        }

        logger.debug("Registration flow check passed, proceeding with widget rendering");

        Map<String, String> configMap = config.getConfig();
        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);
        logger.debugf("Client IP address: %s", ipAddress);

        // Check IP blocklist
        String ipBlocklist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, will be blocked during validation", ipAddress);
            // Cannot block in buildPage(), will block in validate()
        }

        // Check IP allowlist (note: allowlisted IPs still see widget for UX, validation behavior controlled by allowlistBehavior config)
        String ipAllowlist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST);
        logger.debugf("IP allowlist config: %s", ipAllowlist);
        boolean ipAllowed = CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist);
        logger.debugf("Is IP in allowlist? %s", ipAllowed);

        if (ipAllowed) {
            logger.debugf("IP %s is allowlisted, will render widget but handle verification based on allowlistBehavior config", ipAddress);
            // Don't return here - continue to render the widget for consistent UX
        }

        logger.debug("Proceeding to render Turnstile widget");

        // Get configuration
        String siteKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME, "auto");
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SCRIPT_INJECTION);

        logger.debugf("Configuration: siteKey=%s, mode=%s, theme=%s, method=%s",
                    siteKey != null ? siteKey.substring(0, Math.min(10, siteKey.length())) + "..." : "null",
                    widgetMode, widgetTheme, implementationMethod);

        // Set common attributes
        form.setAttribute("turnstileRequired", true);
        form.setAttribute("turnstileSiteKey", siteKey);
        form.setAttribute("turnstileMode", widgetMode);
        form.setAttribute("turnstileTheme", widgetTheme);
        form.setAttribute("turnstileSkipped", false);

        logger.debugf("Rendering Turnstile widget using %s method for registration flow", implementationMethod);

        // Render based on implementation method
        switch (implementationMethod) {
            case METHOD_SCRIPT_INJECTION:
                logger.debug("Calling buildPageScriptInjection()");
                buildPageScriptInjection(form, context, siteKey, widgetMode, widgetTheme, isRegistrationFlow);
                break;
            case METHOD_CUSTOM_THEME:
                logger.debug("Calling buildPageCustomTheme()");
                buildPageCustomTheme(form);
                break;
            default:
                logger.debugf("Unknown implementation method: %s, defaulting to Script Injection", implementationMethod);
                buildPageScriptInjection(form, context, siteKey, widgetMode, widgetTheme, isRegistrationFlow);
        }

        logger.debug("buildPage() completed successfully");
    }

    private void buildPageScriptInjection(LoginFormsProvider form, FormContext context, String siteKey,
                                          String widgetMode, String widgetTheme, boolean isRegistrationFlow) {
        // Script injection approach: Load configuration via dynamic script, then load the injector
        // The injector will read window.TURNSTILE_CONFIG and inject the widget via DOM manipulation

        String realmName = context.getRealm().getName();
        String providerId = CloudflareTurnstileResourceProviderFactory.PROVIDER_ID;

        // Step 1: Add config script with query parameters
        // This sets window.TURNSTILE_CONFIG for the injector to read
        String configPath = String.format("/realms/%s/%s/config.js?siteKey=%s&mode=%s&theme=%s",
                realmName, providerId,
                CloudflareTurnstileHelper.urlEncode(siteKey), CloudflareTurnstileHelper.urlEncode(widgetMode), CloudflareTurnstileHelper.urlEncode(widgetTheme));
        form.addScript(configPath);

        // Step 2: Add the injector script that will read the config and inject the widget
        String injectorPath = String.format("/realms/%s/%s/resources/js/turnstile-injector.js",
                realmName, providerId);
        form.addScript(injectorPath);

        logger.debugf("Script injection mode configured for %s flow: siteKey=%s, mode=%s, theme=%s",
                     isRegistrationFlow ? "registration" : "login", siteKey, widgetMode, widgetTheme);
    }

    private void buildPageCustomTheme(LoginFormsProvider form) {
        // For custom theme method, only attributes are needed
        // The theme must implement the widget rendering
        logger.debug("Using Custom Theme method - theme must support Turnstile attributes");
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();

        if (config == null) {
            logger.error("No authenticator configuration found for validation");
            setValidationError(context, formData, "turnstileConfigError", true);
            return;
        }

        // Guard: Only validate in registration flows
        boolean isRegistrationFlow = isRegistrationFlow(context);
        if (!isRegistrationFlow) {
            logger.warn("CloudflareTurnstileFormAction.validate() called in non-registration flow. Skipping validation.");
            context.success();  // Don't block, just pass through
            return;
        }

        Map<String, String> configMap = config.getConfig();
        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);

        // Check IP blocklist
        String ipBlocklist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, blocking %s", ipAddress,
                        isRegistrationFlow ? "registration" : "login");

            // Log IP block event
            CloudflareTurnstileHelper.logIpBlockedEvent(context.getEvent(), ipAddress);

            setValidationError(context, formData, "turnstileIpBlocked", isRegistrationFlow);
            return;
        }

        // Check IP allowlist and handle based on allowlistBehavior configuration
        String ipAllowlist = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_IP_ALLOWLIST);
        boolean ipAllowed = CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist);
        String allowlistBehavior = configMap.getOrDefault(
            CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);

        if (ipAllowed && ALLOWLIST_SKIP_VERIFICATION.equals(allowlistBehavior)) {
            // SKIP_VERIFICATION mode: Don't make API call, allow registration immediately
            logger.debugf("IP %s is allowlisted with SKIP_VERIFICATION behavior - bypassing Cloudflare API call", ipAddress);
            CloudflareTurnstileHelper.logSkipVerificationEvent(context.getEvent(), ipAddress);

            // Add comprehensive audit context to event
            String failMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
            String failAction = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION, "BLOCK");
            String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SCRIPT_INJECTION);
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                true, false, true, true, "Allowlisted - SKIP_VERIFICATION");

            // Store audit record for SKIP_VERIFICATION (create dummy successful result)
            boolean recordVerifications = Boolean.parseBoolean(
                configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                CloudflareTurnstileService.TurnstileVerificationResult dummyResult =
                    new CloudflareTurnstileService.TurnstileVerificationResult(true, null, null, null, "verification_skipped");
                storeVerificationResultWithAudit(context, dummyResult, ipAddress, isRegistrationFlow,
                    configMap, true, false, true, true, "Allowlisted - SKIP_VERIFICATION");
            }

            context.success();
            return;
        }

        // Verify Turnstile token
        String turnstileResponse = formData.getFirst(TURNSTILE_RESPONSE_PARAM);
        String secretKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SECRET_KEY);

        CloudflareTurnstileValidator.ValidationResult validationResult;

        try {
            int connectTimeout = Integer.parseInt(configMap.getOrDefault(
                CloudflareTurnstileAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000"));
            int readTimeout = Integer.parseInt(configMap.getOrDefault(
                CloudflareTurnstileAuthenticator.CONFIG_READ_TIMEOUT, "5000"));

            try (CloudflareTurnstileService service = new CloudflareTurnstileService(secretKey, connectTimeout, readTimeout)) {
                validationResult = CloudflareTurnstileValidator.validateTurnstile(
                        turnstileResponse, ipAddress, configMap, service);
            }
        } catch (Exception e) {
            logger.errorf(e, "Error verifying Turnstile token: %s", e.getMessage());
            setValidationError(context, formData, "turnstileVerificationError", isRegistrationFlow);
            return;
        }

        // Handle VERIFY_BUT_ALLOW mode for allowlisted IPs
        if (ipAllowed && ALLOWLIST_VERIFY_BUT_ALLOW.equals(allowlistBehavior)) {
            logger.debugf("IP %s is allowlisted with VERIFY_BUT_ALLOW behavior - allowing registration regardless of verification result (success=%s)",
                         ipAddress, validationResult.isSuccess());

            // Log event details (for auditing, even though we're allowing access)
            CloudflareTurnstileService.TurnstileVerificationResult result = validationResult.getVerificationResult();
            CloudflareTurnstileHelper.logVerifyButAllowEvent(context.getEvent(), result, ipAddress, true);

            // Add comprehensive audit context to event
            String failMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
            String failAction = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION, "BLOCK");
            String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SCRIPT_INJECTION);
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                true, false, false, true, "Allowlisted - VERIFY_BUT_ALLOW");

            // Store verification result with VERIFY_BUT_ALLOW audit data
            boolean recordVerifications = Boolean.parseBoolean(
                configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                    configMap, true, false, false, true, "Allowlisted - VERIFY_BUT_ALLOW");
            }

            context.success();
            return;
        }

        // Get verification result for logging and storage
        CloudflareTurnstileService.TurnstileVerificationResult result = validationResult.getVerificationResult();

        if (!validationResult.isSuccess()) {
            logger.warnf("Turnstile validation failed for %s: %s",
                        isRegistrationFlow ? "registration" : "login", validationResult.getErrorCode());

            // Log failure event details
            CloudflareTurnstileHelper.logVerificationResult(context.getEvent(), result, ipAddress, true);

            // Add comprehensive audit context to event
            String failMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
            String failAction = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION, "BLOCK");
            String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SCRIPT_INJECTION);
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                false, false, false, false, "Failed - BLOCK");

            // Store failed verification with audit data
            boolean recordVerifications = Boolean.parseBoolean(
                configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                    configMap, false, false, false, false, "Failed - BLOCK");
            }

            setValidationError(context, formData, validationResult.getErrorMessage(), isRegistrationFlow);
            return;
        }

        // Log success event details
        CloudflareTurnstileHelper.logVerificationResult(context.getEvent(), result, ipAddress, true);

        // Add comprehensive audit context to event
        String failMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
        String failAction = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION, "BLOCK");
        String implementationMethod = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SCRIPT_INJECTION);
        CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
            failMode, failAction, allowlistBehavior, implementationMethod,
            false, false, false, true, "Success");

        // Store successful verification with audit data
        boolean recordVerifications = Boolean.parseBoolean(
            configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_RECORD_VERIFICATIONS, "true"));
        if (recordVerifications) {
            storeVerificationResultWithAudit(context, result, ipAddress, isRegistrationFlow,
                configMap, false, false, false, true, "Success");
        }

        // Success
        context.success();
    }

    private void setValidationError(ValidationContext context, MultivaluedMap<String, String> formData,
                                    String errorMessage, boolean isRegistrationFlow) {
        List<FormMessage> errors = new ArrayList<>();
        errors.add(new FormMessage(null, errorMessage));

        String errorCode = isRegistrationFlow
            ? (errorMessage.contains("Blocked") ? Errors.REGISTRATION_DISABLED : Errors.INVALID_REGISTRATION)
            : (errorMessage.contains("Blocked") ? Errors.ACCESS_DENIED : Errors.INVALID_USER_CREDENTIALS);

        context.error(errorCode);
        formData.remove(TURNSTILE_RESPONSE_PARAM);
        context.validationError(formData, errors);
    }

    @Override
    public void success(FormContext context) {
        // Storage is now handled directly in validate() with comprehensive audit data
        // No additional action needed in success callback
    }

    /**
     * Stores verification result with comprehensive audit data.
     *
     * @param context the validation context
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
    private void storeVerificationResultWithAudit(ValidationContext context,
                                                  CloudflareTurnstileService.TurnstileVerificationResult result,
                                                  String ipAddress,
                                                  boolean isRegistrationFlow,
                                                  Map<String, String> configMap,
                                                  boolean ipAllowlisted,
                                                  boolean ipBlocklisted,
                                                  boolean verificationSkipped,
                                                  boolean authenticationAllowed,
                                                  String actionReason) {
        String eventId = context.getEvent() != null ? context.getEvent().getEvent().getId() : null;
        String sessionId = context.getAuthenticationSession().getParentSession().getId();
        String flowType = isRegistrationFlow ? "REGISTRATION" : "LOGIN";

        // Extract configuration context
        String failMode = configMap.getOrDefault(
            CloudflareTurnstileAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
        String failAction = configMap.getOrDefault(
            CloudflareTurnstileAuthenticator.CONFIG_FAIL_ACTION, "BLOCK");
        String allowlistBehavior = configMap.getOrDefault(
            CloudflareTurnstileAuthenticator.CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
        String implementationMethod = configMap.getOrDefault(
            CONFIG_IMPLEMENTATION_METHOD, METHOD_SCRIPT_INJECTION);

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

    /**
     * Detects if the current flow is a registration flow.
     *
     * This method compares the execution's parent flow ID with the realm's configured
     * registration flow ID to reliably detect registration flows.
     *
     * @param context the form context
     * @return true if this is a registration flow, false otherwise
     */
    private boolean isRegistrationFlow(FormContext context) {
        logger.debug("isRegistrationFlow() called");

        try {
            logger.debug("Attempting to get execution model from context");
            AuthenticationExecutionModel execution = context.getExecution();
            if (execution == null) {
                logger.debug("Execution model is NULL - returning false");
                return false;
            }
            logger.debugf("Execution model retrieved: %s", execution.getId());

            logger.debug("Attempting to get parent flow ID");
            String parentFlowId = execution.getParentFlow();
            if (parentFlowId == null) {
                logger.debug("Parent flow ID is NULL - returning false");
                return false;
            }
            logger.debugf("Parent flow ID: %s", parentFlowId);

            logger.debug("Attempting to get realm and registration flow");
            RealmModel realm = context.getRealm();
            logger.debugf("Realm: %s", realm.getName());

            AuthenticationFlowModel registrationFlow = realm.getRegistrationFlow();
            if (registrationFlow == null) {
                logger.debug("Registration flow is NULL in realm - returning false");
                return false;
            }
            logger.debugf("Registration flow ID: %s, Alias: %s",
                        registrationFlow.getId(), registrationFlow.getAlias());

            // Check if parent flow matches registration flow directly
            boolean isRegistration = parentFlowId.equals(registrationFlow.getId());
            logger.debugf("Direct comparison: parentFlowId=%s == registrationFlowId=%s ? %s",
                         parentFlowId, registrationFlow.getId(), isRegistration);

            // If not a direct match, check if parent flow is a subflow of the registration flow
            if (!isRegistration) {
                logger.debug("Not a direct match - checking if parent is a subflow of registration");

                AuthenticationFlowModel parentFlow = realm.getAuthenticationFlowById(parentFlowId);
                if (parentFlow != null) {
                    logger.debugf("Parent flow: ID=%s, Alias=%s, TopLevel=%s",
                                parentFlow.getId(), parentFlow.getAlias(), parentFlow.isTopLevel());

                    // Check if the parent flow is a subflow (not top-level) and has "registration" in its alias
                    // This handles the case where FormAction is in a subflow of the registration flow
                    if (!parentFlow.isTopLevel()) {
                        logger.debug("Parent is a subflow - checking registration flow executions");

                        // Check if this subflow is referenced by the registration flow
                        List<AuthenticationExecutionModel> registrationExecutions =
                            realm.getAuthenticationExecutionsStream(registrationFlow.getId())
                                .collect(java.util.stream.Collectors.toList());

                        logger.debugf("Registration flow has %d executions", registrationExecutions.size());

                        for (AuthenticationExecutionModel regExecution : registrationExecutions) {
                            if (regExecution.isAuthenticatorFlow()) {
                                String subflowId = regExecution.getFlowId();
                                logger.debugf("Registration execution flow ID: %s", subflowId);

                                if (parentFlowId.equals(subflowId)) {
                                    logger.debug("Found parent flow as a subflow execution in registration flow!");
                                    isRegistration = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        logger.debugf("Parent flow '%s' is top-level but not the registration flow",
                                    parentFlow.getAlias());
                    }
                } else {
                    logger.debugf("Could not retrieve flow model for parent ID: %s", parentFlowId);
                }
            }

            logger.debugf("FINAL RESULT: isRegistration = %s", isRegistration);
            return isRegistration;

        } catch (Exception e) {
            logger.errorf(e, "EXCEPTION in isRegistrationFlow: %s", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean requiresUser() {
        return false; // Works for both pre-user (registration) and post-user (login) scenarios
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true; // Always enabled if configured at flow level
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
