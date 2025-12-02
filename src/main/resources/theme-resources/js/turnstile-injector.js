/**
 * Cloudflare Turnstile Script Injection Mode
 *
 * This script automatically detects Turnstile configuration from hidden form fields
 * and injects the Turnstile widget before the submit button.
 *
 * Works with any Keycloak theme without requiring template modifications.
 */
(function() {
    'use strict';

    // Conditional logger that respects window.TURNSTILE_DEBUG_LOGGING flag
    var logger = {
        log: function() {
            if (window.TURNSTILE_DEBUG_LOGGING) {
                console.log.apply(console, arguments);
            }
        },
        warn: function() {
            if (window.TURNSTILE_DEBUG_LOGGING) {
                console.warn.apply(console, arguments);
            }
        },
        error: function() {
            // Always log errors regardless of debug setting
            console.error.apply(console, arguments);
        }
    };

    logger.log('[Turnstile] Script injector loaded');

    /**
     * Load the Cloudflare Turnstile API script
     * The API will automatically detect and render all .cf-turnstile elements
     */
    function loadTurnstileAPI() {
        // Check if script tag already exists
        if (document.querySelector('script[src*="challenges.cloudflare.com/turnstile"]')) {
            logger.log('[Turnstile] API script already loaded');
            return;
        }

        logger.log('[Turnstile] Loading API script');
        var script = document.createElement('script');
        script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js';
        script.async = true;
        script.defer = true;
        script.onload = function() {
            logger.log('[Turnstile] API script loaded - widgets will auto-render');
        };
        script.onerror = function() {
            logger.error('[Turnstile] Failed to load API script');
        };
        document.head.appendChild(script);
    }

    /**
     * Find the form that needs Turnstile widget
     */
    function findTargetForm() {
        // Look for forms with specific IDs or actions
        var selectors = [
            'form#kc-form-login',           // Login form
            'form#kc-register-form',        // Registration form
            'form[action*="login"]',        // Any login form
            'form[action*="registration"]', // Any registration form
            'form[name="login"]',
            'form[name="register"]'
        ];

        for (var i = 0; i < selectors.length; i++) {
            var form = document.querySelector(selectors[i]);
            if (form) {
                logger.log('[Turnstile] Found target form using selector: ' + selectors[i]);
                return form;
            }
        }

        // Fallback: look for any form with username/password or registration fields
        var forms = document.getElementsByTagName('form');
        for (var i = 0; i < forms.length; i++) {
            var form = forms[i];
            if (form.querySelector('input[name="username"]') ||
                form.querySelector('input[name="password"]') ||
                form.querySelector('input[name="firstName"]')) {
                logger.log('[Turnstile] Found target form via field detection');
                return form;
            }
        }

        logger.warn('[Turnstile] Could not find target form');
        return null;
    }

    /**
     * Find the submit button in the form
     */
    function findSubmitButton(form) {
        // Look for submit buttons
        var submitBtn = form.querySelector('input[type="submit"]') ||
                       form.querySelector('button[type="submit"]') ||
                       form.querySelector('button[name="login"]') ||
                       form.querySelector('.btn-primary') ||
                       form.querySelector('button.pf-c-button');

        if (submitBtn) {
            logger.log('[Turnstile] Found submit button:', submitBtn);
            return submitBtn;
        }

        logger.warn('[Turnstile] Could not find submit button');
        return null;
    }

    /**
     * Set up global callback functions for Turnstile widget
     * These enable/disable the submit button based on widget state
     */
    function setupTurnstileCallbacks(form) {
        // Helper to set submit button state
        function setSubmitButtonState(disabled) {
            var submitBtn = findSubmitButton(form);
            if (submitBtn) {
                submitBtn.disabled = disabled;
                logger.log('[Turnstile] Submit button ' + (disabled ? 'disabled' : 'enabled'));
            }
        }

        // Global callbacks for Turnstile widget
        window.onTurnstileSuccess = function(token) {
            logger.log('[Turnstile] Verification successful');
            setSubmitButtonState(false);
        };

        window.onTurnstileError = function(errorCode) {
            logger.warn('[Turnstile] Error:', errorCode);
            setSubmitButtonState(true);
        };

        window.onTurnstileExpired = function() {
            logger.log('[Turnstile] Token expired');
            setSubmitButtonState(true);
        };

        window.onTurnstileTimeout = function() {
            logger.warn('[Turnstile] Challenge timed out');
            setSubmitButtonState(true);
        };

        logger.log('[Turnstile] Callbacks registered');
    }

    /**
     * Extract Turnstile configuration from hidden fields
     */
    function getTurnstileConfig(form) {
        // Check for global config set by config.js
        if (window.TURNSTILE_CONFIG && window.TURNSTILE_CONFIG.enabled) {
            logger.log('[Turnstile] Using global config:', window.TURNSTILE_CONFIG);
            return {
                siteKey: window.TURNSTILE_CONFIG.siteKey,
                mode: window.TURNSTILE_CONFIG.mode || 'managed',
                theme: window.TURNSTILE_CONFIG.theme || 'auto'
            };
        }

        // Fallback: Check for hidden fields (for backwards compatibility)
        var siteKey = form.querySelector('input[name="turnstile-site-key"]');
        var mode = form.querySelector('input[name="turnstile-mode"]');
        var theme = form.querySelector('input[name="turnstile-theme"]');
        var enabled = form.querySelector('input[name="turnstile-enabled"]');

        if (!siteKey || !enabled || enabled.value !== 'true') {
            logger.log('[Turnstile] Not enabled for this form');
            return null;
        }

        var config = {
            siteKey: siteKey.value,
            mode: mode ? mode.value : 'managed',
            theme: theme ? theme.value : 'auto'
        };

        logger.log('[Turnstile] Config extracted from hidden fields:', config);
        return config;
    }

    /**
     * Check if an element is a form group (supports PatternFly 3, 4, and 5)
     */
    function isFormGroup(element) {
        return element.classList.contains('pf-v5-c-form__group') ||  // PatternFly 5 (Keycloak 24+)
               element.classList.contains('pf-c-form__group') ||     // PatternFly 4 (Keycloak 18-23)
               element.classList.contains('form-group');             // PatternFly 3 (Keycloak <18)
    }

    /**
     * Create and inject the Turnstile widget
     */
    function injectTurnstileWidget(form, submitBtn, config) {
        // Check if already injected
        if (form.querySelector('.cf-turnstile')) {
            logger.log('[Turnstile] Widget already exists in form');
            return;
        }

        logger.log('[Turnstile] Injecting widget for auto-rendering');

        // For non-invisible modes, setup callbacks and disable button BEFORE creating widget
        // This prevents a race condition where the Turnstile API (if cached) could complete
        // before our callbacks are registered
        if (config.mode !== 'invisible') {
            // Set up global callback functions FIRST (before widget can call them)
            setupTurnstileCallbacks(form);

            // Disable submit button until Turnstile completes
            if (submitBtn) {
                submitBtn.disabled = true;
                logger.log('[Turnstile] Submit button disabled until verification completes');
            }
        }

        // Determine widget size and appearance
        var size = config.mode === 'invisible' ? 'invisible' : 'flexible';
        var appearance = config.mode === 'non-interactive' ? 'interaction-only' : 'always';

        // Create widget div with cf-turnstile class and data attributes (no wrapper needed)
        // The Turnstile API will automatically detect and render this element
        var widgetDiv = document.createElement('div');
        widgetDiv.className = 'cf-turnstile';
        widgetDiv.style.width = '100%';
        widgetDiv.setAttribute('data-sitekey', config.siteKey);
        widgetDiv.setAttribute('data-theme', config.theme);
        widgetDiv.setAttribute('data-size', size);
        widgetDiv.setAttribute('data-appearance', appearance);

        // Add callback attributes to widget (callbacks are already registered above)
        if (config.mode !== 'invisible') {
            widgetDiv.setAttribute('data-callback', 'onTurnstileSuccess');
            widgetDiv.setAttribute('data-error-callback', 'onTurnstileError');
            widgetDiv.setAttribute('data-expired-callback', 'onTurnstileExpired');
            widgetDiv.setAttribute('data-timeout-callback', 'onTurnstileTimeout');
        }

        // Insert before the submit button's parent form group to avoid nesting
        // Works with PatternFly 3, 4, and 5 (Keycloak 18+)
        var insertionPoint = submitBtn.parentNode;
        if (isFormGroup(insertionPoint)) {
            // Submit button is in a form group - insert before it to avoid nesting
            insertionPoint.parentNode.insertBefore(widgetDiv, insertionPoint);
            logger.log('[Turnstile] Widget inserted before submit button form group');
        } else {
            // Fallback: insert before submit button itself
            insertionPoint.insertBefore(widgetDiv, submitBtn);
            logger.log('[Turnstile] Widget inserted before submit button');
        }

        logger.log('[Turnstile] Widget container injected, waiting for API to auto-render');
    }

    /**
     * Main initialization function
     */
    function initializeTurnstile() {
        // Allow re-initialization if widget is missing from DOM
        if (window.TURNSTILE_INITIALIZED) {
            var existingWidget = document.querySelector('.cf-turnstile');
            if (existingWidget) {
                logger.log('[Turnstile] Already initialized and widget exists, skipping');
                return;
            }
            logger.log('[Turnstile] Re-initializing (widget missing from DOM)');
        }
        window.TURNSTILE_INITIALIZED = true;

        logger.log('[Turnstile] Initializing script injection mode');

        var form = findTargetForm();
        if (!form) {
            logger.log('[Turnstile] No target form found, skipping');
            return;
        }

        var config = getTurnstileConfig(form);
        if (!config) {
            logger.log('[Turnstile] Turnstile not configured for this form, skipping');
            return;
        }

        var submitBtn = findSubmitButton(form);
        if (!submitBtn) {
            logger.warn('[Turnstile] No submit button found, injecting at end of form');
            // Fallback: append to end of form
            submitBtn = form.querySelector(':scope > *:last-child');
        }

        // Inject widget container - API will auto-render when it loads
        injectTurnstileWidget(form, submitBtn, config);

        // Load Turnstile API script (will auto-detect and render the widget)
        loadTurnstileAPI();
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeTurnstile);
    } else {
        initializeTurnstile();
    }

})();
