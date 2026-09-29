package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.FlowStatus;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordFormFactory;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialChooseUser;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.DefaultActionTokenKey;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.resources.LoginActionsService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unified Cloudflare Turnstile authenticator for login, registration and reset-password flows.
 *
 * This authenticator automatically detects the flow type and displays the appropriate
 * Turnstile widget before (or, inline, on) the login, registration or reset-password form. It verifies the response
 * token against Cloudflare's API with support for advanced features like fail modes
 * and conditional actions.
 *
 * With the widget inline on the sign-in page, this step stands in for Keycloak's
 * username/password form and delegates to it (page, passkeys, password check), adding only the
 * widget and the Turnstile check. It extends Keycloak's base class for such forms, which marks it
 * as a form that keeps to an account set earlier in the flow (as Keycloak's own does, inline). The
 * base class's form helpers ({@code challenge}, {@code validateUserAndPassword}) are deliberately
 * unused: pages must come from Keycloak's step, with the widget, and refusals mustn't count as
 * failed sign-ins.
 */
public class CloudflareTurnstileAuthenticator extends AbstractUsernameFormAuthenticator {

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

    // Error handling modes and verification failure actions
    private static final String FAIL_OPEN = "FAIL_OPEN";
    private static final String FAIL_CLOSED = "FAIL_CLOSED";
    private static final String FAIL_ACTION_BLOCK = "BLOCK";
    private static final String FAIL_ACTION_ALLOW = "ALLOW";
    private static final String FAIL_ACTION_REQUIRE_MFA = "REQUIRE_MFA";

    // Allowlist behavior modes
    private static final String ALLOWLIST_SKIP_VERIFICATION = "SKIP_VERIFICATION";
    private static final String ALLOWLIST_VERIFY_BUT_ALLOW = "VERIFY_BUT_ALLOW";

    // Form parameter
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";

    /** Authentication-session note set when a check failed and REQUIRE_MFA let the sign-in continue. */
    public static final String FAILED_NOTE = "turnstile_failed";

    /** Event error when Cloudflare rejected the token: a failed check, kept apart from wrong passwords. */
    public static final String EVENT_ERROR_VERIFICATION_FAILED = "turnstile_verification_failed";

    /** Event error when Cloudflare couldn't be asked and the error handling mode blocks. */
    public static final String EVENT_ERROR_VERIFICATION_ERROR = "turnstile_verification_error";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // The realm's Turnstile settings, overridden by this step's own configuration
        Map<String, String> configMap = CloudflareTurnstileRealmSettings.effective(context.getSession(), context.getRealm(),
                context.getAuthenticatorConfig());
        if (!CloudflareTurnstileRealmSettings.usable(configMap)) {
            logger.error("Cloudflare Turnstile has no site key and secret key: set them on the realm's Cloudflare Turnstile "
                    + "settings tab or on this step");
            Response response = context.form()
                    .setError("turnstileConfigError")
                    .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR);
            refuse(context, response);
            return;
        }

        String ipAddress = CloudflareTurnstileValidator.getClientIpAddress(context);
        boolean isRegistrationFlow = isRegistrationFlow(context);

        // Check IP blocklist first
        String ipBlocklist = configMap.get(CONFIG_IP_BLOCKLIST);
        if (CloudflareTurnstileValidator.isIpBlocked(ipAddress, ipBlocklist)) {
            logger.warnf("IP %s is in blocklist, denying access", ipAddress);
            CloudflareTurnstileHelper.logIpBlockedEvent(context.getEvent(), ipAddress);

            // Add comprehensive audit context to event
            String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, FAIL_CLOSED);
            String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, FAIL_ACTION_BLOCK);
            String allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
            String implementationMethod = implementationMethod(context, configMap);
            CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(),
                failMode, failAction, allowlistBehavior, implementationMethod,
                false, true, false, false, "Blocked - IP blocklisted");

            // Store blocklist denial to database
            boolean recordVerifications = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true"));
            if (recordVerifications) {
                CloudflareTurnstileService.TurnstileVerificationResult blockedResult =
                    new CloudflareTurnstileService.TurnstileVerificationResult(false, "ip_blocked", null, null, "ip_blocklisted");
                storeVerificationResultWithAudit(context, blockedResult, ipAddress,
                    configMap, false, true, false, false, "Blocked - IP blocklisted");
            }

            context.getEvent().error(Errors.ACCESS_DENIED);

            Response response = context.form()
                    .setError("turnstileIpBlocked")
                    .createErrorPage(Response.Status.FORBIDDEN);
            refuse(context, response);
            return;
        }

        // Check IP allowlist (note: allowlisted IPs still see widget for UX, validation handled in action())
        String ipAllowlist = configMap.get(CONFIG_IP_ALLOWLIST);
        boolean ipAllowed = CloudflareTurnstileValidator.isIpAllowed(ipAddress, ipAllowlist);
        if (ipAllowed) {
            logger.debugf("IP %s is in allowlist, will render widget but handle verification based on allowlistBehavior config", ipAddress);
            // Don't return here - continue to render the widget
        }

        // A reset link from the user's mailbox (a signed, single-use action token) already proves who
        // they are, and Keycloak's reset steps skip their pages for it: there is nothing left to protect.
        // Inline, Keycloak's own "choose user" step makes the same decision.
        if (METHOD_SEPARATE_PAGE.equals(implementationMethod(context, configMap)) && isResetCredentialsFlow(context)
                && context.getAuthenticationSession().getAuthNote(DefaultActionTokenKey.ACTION_TOKEN_USER_ID) != null) {
            logger.debug("Reset link already identified the user, skipping the Turnstile page");
            context.success();
            return;
        }

        // Display Turnstile challenge
        String siteKey = configMap.get(CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CONFIG_WIDGET_THEME, "auto");
        boolean enableDebugLogging = Boolean.parseBoolean(configMap.getOrDefault(CONFIG_ENABLE_DEBUG_LOGGING, "false"));
        String implementationMethod = implementationMethod(context, configMap);

        logger.debugf("Rendering Turnstile using %s method for %s flow (IP: %s)",
                     implementationMethod, flowType(context), ipAddress);

        // Render based on implementation method
        switch (implementationMethod) {
            case METHOD_SCRIPT_INJECTION:
            case METHOD_CUSTOM_THEME:
                displayInline(context, configMap, implementationMethod);
                break;
            case METHOD_SEPARATE_PAGE:
            default:
                displaySeparatePage(context, siteKey, widgetMode, widgetTheme, enableDebugLogging, isRegistrationFlow);
                break;
        }
    }

    /** Adds the script-injection scripts (widget settings and injector) to a page. */
    private static void addInjectionScripts(LoginFormsProvider form, AuthenticationFlowContext context, Map<String, String> configMap) {
        String realmName = context.getRealm().getName();
        String providerId = CloudflareTurnstileResourceProviderFactory.PROVIDER_ID;
        form.addScript(CloudflareTurnstileHelper.buildConfigJsUrl(context.getUriInfo(), realmName, providerId,
                configMap.get(CONFIG_SITE_KEY), configMap.getOrDefault(CONFIG_WIDGET_MODE, "managed"),
                configMap.getOrDefault(CONFIG_WIDGET_THEME, "auto"),
                Boolean.parseBoolean(configMap.getOrDefault(CONFIG_ENABLE_DEBUG_LOGGING, "false"))));
        form.addScript(CloudflareTurnstileHelper.buildInjectorJsUrl(context.getUriInfo(), realmName, providerId));
    }

    /**
     * Display Turnstile on a separate page (default method).
     * Shows only the Turnstile widget, then proceeds to the sign-in, registration or reset-password form.
     */
    private void displaySeparatePage(AuthenticationFlowContext context, String siteKey, String widgetMode,
                                     String widgetTheme, boolean enableDebugLogging, boolean isRegistrationFlow) {
        String template = isRegistrationFlow ? "turnstile-registration-form.ftl" : "turnstile-form.ftl";

        Response challenge = context.form()
                .setAttribute("turnstileSiteKey", siteKey)
                .setAttribute("turnstileMode", widgetMode)
                .setAttribute("turnstileTheme", widgetTheme)
                .setAttribute("isRegistrationFlow", isRegistrationFlow)
                .setAttribute("isResetFlow", isResetCredentialsFlow(context))
                .setAttribute("enableDebugLogging", enableDebugLogging)
                .setAttribute("turnstileRequired", true)
                .setAttribute("turnstileSkipped", false)
                .createForm(template);

        context.challenge(challenge);
        logger.debugf("Displayed Turnstile on separate page using template: %s", template);
    }

    /**
     * Display Turnstile inline: Keycloak's own page, built by Keycloak's own step (the
     * username/password form, or on the reset page the "choose user" step), with the widget added.
     */
    private void displayInline(AuthenticationFlowContext context, Map<String, String> configMap, String implementationMethod) {
        if (isResetCredentialsFlow(context)) {
            keycloakStep(context, ResetCredentialChooseUser.PROVIDER_ID)
                    .authenticate(withWidgetOnItsPage(context, form -> addInjectionScripts(form, context, configMap)));
        } else {
            keycloakStep(context, UsernamePasswordFormFactory.PROVIDER_ID)
                    .authenticate(withWidgetOnItsPage(context, signInWidget(context, configMap, implementationMethod)));
        }
        logger.debugf("Inline widget (%s) for %s flow", implementationMethod, flowType(context));
    }

    /**
     * The widget on the sign-in page: with script injection, the scripts; with the custom theme, the
     * attributes the theme's template reads (script injection sets them too, for themes that check).
     */
    private static Consumer<LoginFormsProvider> signInWidget(AuthenticationFlowContext context, Map<String, String> configMap,
                                                            String implementationMethod) {
        boolean scriptInjection = METHOD_SCRIPT_INJECTION.equals(implementationMethod);
        return form -> {
            form.setAttribute("turnstileRequired", true)
                    .setAttribute("turnstileSkipped", false)
                    .setAttribute("turnstileSiteKey", configMap.get(CONFIG_SITE_KEY))
                    .setAttribute("turnstileMode", configMap.getOrDefault(CONFIG_WIDGET_MODE, "managed"))
                    .setAttribute("turnstileTheme", configMap.getOrDefault(CONFIG_WIDGET_THEME, "auto"))
                    .setAttribute("enableDebugLogging", Boolean.parseBoolean(configMap.getOrDefault(CONFIG_ENABLE_DEBUG_LOGGING, "false")));
            if (scriptInjection) {
                form.setAttribute("turnstileUseScriptInjection", true);
                addInjectionScripts(form, context, configMap);
            }
        };
    }

    /** Whether this runs in the registration flow. */
    static boolean isRegistrationFlow(AuthenticationFlowContext context) {
        return LoginActionsService.REGISTRATION_PATH.equals(context.getFlowPath());
    }

    /** Whether this runs in the reset-password ("Forgot your password?") flow. */
    static boolean isResetCredentialsFlow(AuthenticationFlowContext context) {
        return LoginActionsService.RESET_CREDENTIALS_PATH.equals(context.getFlowPath());
    }

    /** The flow, as recorded in events: {@code login}, {@code registration} or {@code reset-credentials}. */
    static String flowType(AuthenticationFlowContext context) {
        if (isRegistrationFlow(context)) {
            return "registration";
        }
        return isResetCredentialsFlow(context) ? "reset-credentials" : "login";
    }

    /**
     * How the widget is shown in this flow. Inline widgets need a page to put the widget on:
     * <ul>
     *   <li>Registration: inline widgets come from the registration form action, so this step shows
     *       the separate page.</li>
     *   <li>Reset password: Keycloak's reset page has no custom-theme variant, so the custom-theme
     *       method uses script injection there.</li>
     * </ul>
     */
    static String implementationMethod(AuthenticationFlowContext context, Map<String, String> configMap) {
        String method = configMap.getOrDefault(CONFIG_IMPLEMENTATION_METHOD, METHOD_SEPARATE_PAGE);
        if (isRegistrationFlow(context)) {
            return METHOD_SEPARATE_PAGE;
        }
        return METHOD_CUSTOM_THEME.equals(method) && isResetCredentialsFlow(context) ? METHOD_SCRIPT_INJECTION : method;
    }

    /**
     * One of Keycloak's own steps, which this step stands in for with the widget on its page: the
     * username/password form (its page, login_hint and remembered username, passkeys, the password
     * check), or on the reset page the "choose user" step (which finds the account, or quietly doesn't,
     * so usernames can't be probed). Keycloak's provider is used, so each version's behaviour applies
     * and a replacement registered under the same id is honoured.
     */
    private static Authenticator keycloakStep(AuthenticationFlowContext context, String providerId) {
        AuthenticatorFactory factory = (AuthenticatorFactory) context.getSession().getKeycloakSessionFactory()
                .getProviderFactory(Authenticator.class, providerId);
        if (factory == null) {
            throw new IllegalStateException("Keycloak's authenticator " + providerId + " is not available");
        }
        return factory.create(context.getSession());
    }

    /**
     * The flow context, for a Keycloak step that shows a page of its own: the widget is added to the
     * page it builds, and only to that page. The request has one form provider, so a widget added to
     * it up front would also reach any page later steps render in the same response (such as the
     * sign-in page Keycloak shows once a reset email is sent, or a one-time-code page).
     */
    static AuthenticationFlowContext withWidgetOnItsPage(AuthenticationFlowContext context, Consumer<LoginFormsProvider> widget) {
        boolean[] added = {false};
        return (AuthenticationFlowContext) Proxy.newProxyInstance(AuthenticationFlowContext.class.getClassLoader(),
                new Class<?>[] {AuthenticationFlowContext.class}, (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(context, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (!added[0] && result instanceof LoginFormsProvider form && "form".equals(method.getName())) {
                        added[0] = true;
                        widget.accept(form);
                    }
                    return result;
                });
    }

    /**
     * Continues the flow after an attempt was let through. With the widget on Keycloak's reset page,
     * this step is that page's "choose user" step, which then takes the submitted username. On the
     * sign-in page, Keycloak's form already continued the flow (see {@link #submittedToKeycloakForm}).
     */
    private void finish(AuthenticationFlowContext context, Map<String, String> configMap, boolean inlineMode, boolean resetFlow) {
        if (inlineMode && resetFlow) {
            keycloakStep(context, ResetCredentialChooseUser.PROVIDER_ID)
                    .action(withWidgetOnItsPage(context, form -> addInjectionScripts(form, context, configMap)));
        } else if (!inlineMode) {
            context.success();
        }
    }

    /**
     * With the widget inline on the sign-in page, this step is also the username/password form: before
     * an attempt counts as let through, Keycloak's form takes the submission (checks the password and
     * continues the flow, or shows its error with the widget).
     */
    private boolean submittedToKeycloakForm(AuthenticationFlowContext context, Map<String, String> configMap,
                                            boolean inlineMode, boolean resetFlow) {
        if (!inlineMode || resetFlow) {
            return true;
        }
        keycloakStep(context, UsernamePasswordFormFactory.PROVIDER_ID)
                .action(withWidgetOnItsPage(context, signInWidget(context, configMap, implementationMethod(context, configMap))));
        return context.getStatus() == FlowStatus.SUCCESS;
    }

    /**
     * A passkey (or the browser's passkey error) from the sign-in page's passkey form, which posts
     * without the Turnstile token: WebAuthn fields, and no username or password at all (Keycloak
     * treats only an empty password as none). It can't test a password.
     */
    static boolean isPasskeySubmission(MultivaluedMap<String, String> formData) {
        return (!isEmpty(formData.getFirst("authenticatorData")) || !isEmpty(formData.getFirst("error")))
                && isEmpty(formData.getFirst("username")) && isEmpty(formData.getFirst("password"));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Refuses an attempt with a page, without counting it toward Keycloak's brute-force lockout of
     * the account named so far: a Turnstile refusal is no wrong password, and counting it would let
     * anyone lock accounts without solving a check.
     */
    private static void refuse(AuthenticationFlowContext context, Response page) {
        context.forceChallenge(page);
    }

    /**
     * The flow context for a submission Turnstile didn't check (a passkey): whatever Keycloak's step
     * refuses is shown without counting toward brute-force lockout, so unchecked requests can't lock
     * accounts. A passkey can't be guessed, so nothing is lost.
     */
    static AuthenticationFlowContext withoutLockoutCounting(AuthenticationFlowContext context) {
        return (AuthenticationFlowContext) Proxy.newProxyInstance(AuthenticationFlowContext.class.getClassLoader(),
                new Class<?>[] {AuthenticationFlowContext.class}, (proxy, method, args) -> {
                    boolean refusal = "failure".equals(method.getName()) || "failureChallenge".equals(method.getName());
                    if (refusal && args != null && args.length >= 2 && args[1] instanceof Response page) {
                        context.forceChallenge(page);
                        return null;
                    }
                    try {
                        return method.invoke(context, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** One submission: the step's effective settings and the request's circumstances, resolved once. */
    private record Attempt(AuthenticationFlowContext context, Map<String, String> settings, String ipAddress,
                           String implementationMethod, boolean inline, boolean reset, boolean registration,
                           String failMode, String failAction, String allowlistBehavior, boolean record) {

        static Attempt of(AuthenticationFlowContext context) {
            Map<String, String> settings = CloudflareTurnstileRealmSettings.effective(context.getSession(), context.getRealm(),
                    context.getAuthenticatorConfig());
            String method = CloudflareTurnstileAuthenticator.implementationMethod(context, settings);
            return new Attempt(context, settings, CloudflareTurnstileValidator.getClientIpAddress(context), method,
                    METHOD_SCRIPT_INJECTION.equals(method) || METHOD_CUSTOM_THEME.equals(method),
                    isResetCredentialsFlow(context), isRegistrationFlow(context),
                    settings.getOrDefault(CONFIG_FAIL_MODE, FAIL_CLOSED), settings.getOrDefault(CONFIG_FAIL_ACTION, FAIL_ACTION_BLOCK),
                    settings.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW),
                    Boolean.parseBoolean(settings.getOrDefault(CONFIG_RECORD_VERIFICATIONS, "true")));
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        Attempt attempt = Attempt.of(context);

        if (attempt.inline() && !attempt.reset() && isPasskeySubmission(formData)) {
            logger.debug("Passkey submission: handled by Keycloak's form, without a Turnstile check");
            keycloakStep(context, UsernamePasswordFormFactory.PROVIDER_ID).action(withWidgetOnItsPage(withoutLockoutCounting(context),
                    signInWidget(context, attempt.settings(), attempt.implementationMethod())));
            return;
        }

        boolean ipAllowed = CloudflareTurnstileValidator.isIpAllowed(attempt.ipAddress(), attempt.settings().get(CONFIG_IP_ALLOWLIST));
        if (ipAllowed && ALLOWLIST_SKIP_VERIFICATION.equals(attempt.allowlistBehavior())) {
            logger.debugf("IP %s is allowlisted with SKIP_VERIFICATION behavior - bypassing Cloudflare API call", attempt.ipAddress());
            CloudflareTurnstileHelper.logSkipVerificationEvent(context.getEvent(), attempt.ipAddress());
            allow(attempt, new CloudflareTurnstileService.TurnstileVerificationResult(true, null, null, null, "verification_skipped"),
                    true, true, "Allowlisted - SKIP_VERIFICATION");
            return;
        }

        CloudflareTurnstileService.TurnstileVerificationResult result;
        try {
            result = verify(attempt, formData.getFirst(TURNSTILE_RESPONSE_PARAM));
        } catch (Exception e) {
            handleVerificationError(attempt, e);
            return;
        }
        CloudflareTurnstileHelper.logVerificationResult(context.getEvent(), result, attempt.ipAddress(), flowType(context));

        if (ipAllowed && ALLOWLIST_VERIFY_BUT_ALLOW.equals(attempt.allowlistBehavior())) {
            logger.debugf("IP %s is allowlisted with VERIFY_BUT_ALLOW behavior - allowing access regardless of verification result (success=%s)",
                         attempt.ipAddress(), result.isSuccess());
            context.getEvent().detail("cloudflare_turnstile_action", "ip_allowlisted_verify_but_allow");
            allow(attempt, result, true, false, "Allowlisted - VERIFY_BUT_ALLOW");
        } else if (result.isSuccess()) {
            logger.debugf("Turnstile verification successful for IP: %s", attempt.ipAddress());
            allow(attempt, result, false, false, "Success");
        } else {
            handleVerificationFailure(attempt, result);
        }
    }

    /** Asks Cloudflare about the token (the timeouts never throw: bad values fall back to the default). */
    private static CloudflareTurnstileService.TurnstileVerificationResult verify(Attempt attempt, String token) throws Exception {
        int connectTimeout = CloudflareTurnstileHelper.getConnectTimeout(attempt.settings());
        int readTimeout = CloudflareTurnstileHelper.getReadTimeout(attempt.settings());
        try (CloudflareTurnstileService service = new CloudflareTurnstileService(attempt.settings().get(CONFIG_SECRET_KEY),
                connectTimeout, readTimeout)) {
            return service.verify(token, attempt.ipAddress());
        }
    }

    /** Cloudflare couldn't be asked: the error handling mode decides. */
    private void handleVerificationError(Attempt attempt, Exception e) {
        logger.errorf(e, "Error verifying Turnstile token: %s", e.getMessage());
        boolean failOpen = FAIL_OPEN.equals(attempt.failMode());
        CloudflareTurnstileHelper.logVerificationError(attempt.context().getEvent(), e.getMessage(), failOpen, attempt.registration());
        CloudflareTurnstileService.TurnstileVerificationResult errorResult =
                new CloudflareTurnstileService.TurnstileVerificationResult(false, "verification-error", null, null, e.getMessage());
        if (failOpen) {
            logger.warn("Turnstile verification failed, but FAIL_OPEN mode is enabled - allowing access");
            allow(attempt, errorResult, false, false, "Error - FAIL_OPEN");
        } else {
            block(attempt, errorResult, attempt.failAction(), "Error - FAIL_CLOSED",
                    EVENT_ERROR_VERIFICATION_ERROR, "turnstileVerificationError", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /** Cloudflare rejected the token: the verification failure action decides. */
    private void handleVerificationFailure(Attempt attempt, CloudflareTurnstileService.TurnstileVerificationResult result) {
        AuthenticationFlowContext context = attempt.context();
        String failAction = attempt.failAction();
        if (FAIL_ACTION_REQUIRE_MFA.equals(failAction) && attempt.reset()) {
            // Nothing can ask for a second factor before the reset email goes out
            logger.info("REQUIRE_MFA can't apply before a reset email is sent - blocking instead");
            failAction = FAIL_ACTION_BLOCK;
        }

        logger.warnf("Turnstile verification failed for IP: %s, errors: %s, action: %s, flow: %s",
                attempt.ipAddress(), result.getErrorCodes(), failAction, flowType(context));

        switch (failAction) {
            case FAIL_ACTION_ALLOW:
                logger.info("ALLOW action configured - allowing access despite failed verification");
                CloudflareTurnstileHelper.logFailAction(context.getEvent(), "allowed");
                allow(attempt, result, false, false, "Failed - ALLOW");
                break;
            case FAIL_ACTION_REQUIRE_MFA:
                logger.info("REQUIRE_MFA action configured - triggering MFA requirement");
                CloudflareTurnstileHelper.logFailAction(context.getEvent(), "mfa_required");
                if (allow(attempt, result, false, false, "Failed - REQUIRE_MFA")) {
                    // For a later step (e.g. a condition) that asks for a second factor
                    context.getAuthenticationSession().setAuthNote(FAILED_NOTE, "true");
                }
                break;
            case FAIL_ACTION_BLOCK:
            default:
                logger.info("BLOCK action configured - denying access");
                CloudflareTurnstileHelper.logFailAction(context.getEvent(), "blocked");
                block(attempt, result, failAction, "Failed - BLOCK",
                        EVENT_ERROR_VERIFICATION_FAILED, "turnstileVerificationFailed", Response.Status.FORBIDDEN);
                break;
        }
    }

    /**
     * Lets an attempt through. Inline on the sign-in page, Keycloak's form takes the submission first
     * (only an accepted one counts as let through); then the outcome is audited and the flow goes on.
     *
     * @return whether the attempt went through
     */
    private boolean allow(Attempt attempt, CloudflareTurnstileService.TurnstileVerificationResult result,
                          boolean ipAllowlisted, boolean verificationSkipped, String reason) {
        AuthenticationFlowContext context = attempt.context();
        if (!submittedToKeycloakForm(context, attempt.settings(), attempt.inline(), attempt.reset())) {
            return false;
        }
        CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(), attempt.failMode(), attempt.failAction(),
                attempt.allowlistBehavior(), attempt.implementationMethod(), ipAllowlisted, false, verificationSkipped, true, reason);
        if (attempt.record()) {
            storeVerificationResultWithAudit(context, result, attempt.ipAddress(), attempt.settings(),
                    ipAllowlisted, false, verificationSkipped, true, reason);
        }
        finish(context, attempt.settings(), attempt.inline(), attempt.reset());
        return true;
    }

    /** Refuses an attempt with an error page, recorded under the given event error. */
    private void block(Attempt attempt, CloudflareTurnstileService.TurnstileVerificationResult result, String failAction,
                       String reason, String eventError, String message, Response.Status status) {
        AuthenticationFlowContext context = attempt.context();
        // The audit context goes on the event before error() sends it
        CloudflareTurnstileHelper.addAuditContextToEvent(context.getEvent(), attempt.failMode(), failAction,
                attempt.allowlistBehavior(), attempt.implementationMethod(), false, false, false, false, reason);
        context.getEvent().error(eventError);
        if (attempt.record()) {
            storeVerificationResultWithAudit(context, result, attempt.ipAddress(), attempt.settings(),
                    false, false, false, false, reason);
        }
        refuse(context, context.form().setError(message).createErrorPage(status));
    }

    /**
     * Stores verification result with comprehensive audit data.
     *
     * @param context the authentication context
     * @param result the verification result
     * @param ipAddress the client IP address
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
        String flowType = flowType(context).toUpperCase(java.util.Locale.ROOT).replace('-', '_');

        // Extract configuration context
        String failMode = configMap.getOrDefault(CONFIG_FAIL_MODE, FAIL_CLOSED);
        String failAction = configMap.getOrDefault(CONFIG_FAIL_ACTION, FAIL_ACTION_BLOCK);
        String allowlistBehavior = configMap.getOrDefault(CONFIG_ALLOWLIST_BEHAVIOR, ALLOWLIST_VERIFY_BUT_ALLOW);
        String implementationMethod = implementationMethod(context, configMap);

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
