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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FormAction that injects Turnstile widget into login form via JavaScript.
 * This is Option 2 for Login: Script Injection - works with any theme, no template modifications needed.
 *
 * Uses DOM manipulation to dynamically insert the Turnstile widget before the submit button.
 * Server-side validation ensures security even if JavaScript is disabled/bypassed.
 */
public class CloudflareTurnstileLoginScriptInjectionAction implements FormAction {

    private static final Logger logger = Logger.getLogger(CloudflareTurnstileLoginScriptInjectionAction.class);
    private static final String TURNSTILE_RESPONSE_PARAM = "cf-turnstile-response";

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.warn("No authenticator configuration found for Login Script Injection");
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
            // Set attribute so JavaScript knows to skip rendering
            form.setAttribute("turnstileSkipped", true);
            return;
        }

        // Get configuration
        String siteKey = configMap.get(CloudflareTurnstileAuthenticator.CONFIG_SITE_KEY);
        String widgetMode = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_MODE, "managed");
        String widgetTheme = configMap.getOrDefault(CloudflareTurnstileAuthenticator.CONFIG_WIDGET_THEME, "auto");

        // Set attributes for potential template use (if theme supports it)
        form.setAttribute("turnstileRequired", true);
        form.setAttribute("turnstileSiteKey", siteKey);
        form.setAttribute("turnstileMode", widgetMode);
        form.setAttribute("turnstileTheme", widgetTheme);

        // Inject Turnstile script
        form.addScript("https://challenges.cloudflare.com/turnstile/v0/api.js");

        // Inject JavaScript to dynamically add widget to form
        String injectionScript = buildInjectionScript(siteKey, widgetMode, widgetTheme);
        try {
            // Use data URI to inject inline script
            String encodedScript = "data:text/javascript;charset=utf-8," + URLEncoder.encode(injectionScript, "UTF-8");
            form.addScript(encodedScript);
        } catch (UnsupportedEncodingException e) {
            logger.error("Failed to encode Turnstile injection script for login", e);
        }
    }

    private String buildInjectionScript(String siteKey, String widgetMode, String widgetTheme) {
        // Build robust JavaScript that:
        // 1. Waits for DOM and Turnstile API to load
        // 2. Finds the login form
        // 3. Inserts widget before submit button
        // 4. Handles errors gracefully

        String size = "invisible".equals(widgetMode) ? "invisible" : "normal";
        String appearance = "non-interactive".equals(widgetMode) ? "interaction-only" : "always";

        return String.format(
            "(function() {" +
            "  function initTurnstile() {" +
            "    try {" +
            "      var form = document.querySelector('form#kc-form-login') || " +
            "                 document.querySelector('form[action*=\\\"login\\\"]') || " +
            "                 document.querySelector('form');" +
            "      if (!form) {" +
            "        console.warn('Turnstile: Could not find login form');" +
            "        return;" +
            "      }" +
            "      var submitBtn = form.querySelector('input[type=\\\"submit\\\"][name=\\\"login\\\"]') || " +
            "                      form.querySelector('input[type=\\\"submit\\\"]') || " +
            "                      form.querySelector('button[type=\\\"submit\\\"]');" +
            "      if (!submitBtn) {" +
            "        console.warn('Turnstile: Could not find submit button');" +
            "        return;" +
            "      }" +
            "      var existingWidget = form.querySelector('.cf-turnstile');" +
            "      if (existingWidget) {" +
            "        console.log('Turnstile: Widget already exists');" +
            "        return;" +
            "      }" +
            "      var container = document.createElement('div');" +
            "      container.className = 'form-group';" +
            "      container.style.margin = '20px 0';" +
            "      var widget = document.createElement('div');" +
            "      widget.className = 'cf-turnstile';" +
            "      widget.setAttribute('data-sitekey', '%s');" +
            "      widget.setAttribute('data-theme', '%s');" +
            "      widget.setAttribute('data-size', '%s');" +
            "      widget.setAttribute('data-appearance', '%s');" +
            "      container.appendChild(widget);" +
            "      submitBtn.parentNode.insertBefore(container, submitBtn);" +
            "      console.log('Turnstile: Widget injected successfully into login form');" +
            "      if (typeof turnstile !== 'undefined') {" +
            "        turnstile.render(widget);" +
            "      }" +
            "    } catch (e) {" +
            "      console.error('Turnstile: Error injecting widget into login form:', e);" +
            "    }" +
            "  }" +
            "  if (document.readyState === 'loading') {" +
            "    document.addEventListener('DOMContentLoaded', function() {" +
            "      setTimeout(initTurnstile, 100);" +
            "    });" +
            "  } else {" +
            "    setTimeout(initTurnstile, 100);" +
            "  }" +
            "})();",
            siteKey, widgetTheme, size, appearance
        );
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

            logger.debugf("Stored Turnstile verification result for login (script injection) from IP %s", ipAddress);
        } catch (Exception e) {
            logger.warn("Failed to store Turnstile verification result for login (script injection)", e);
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
