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
     * Create and inject the Turnstile widget
     */
    function injectTurnstileWidget(form, submitBtn, config) {
        // Check if already injected
        if (form.querySelector('.cf-turnstile')) {
            logger.log('[Turnstile] Widget already exists in form');
            return;
        }

        logger.log('[Turnstile] Injecting widget for auto-rendering');

        // Determine widget size and appearance
        var size = config.mode === 'invisible' ? 'invisible' : 'normal';
        var appearance = config.mode === 'non-interactive' ? 'interaction-only' : 'always';

        // Create widget container
        var container = document.createElement('div');
        container.className = 'form-group pf-c-form__group';
        container.style.margin = '20px 0';

        // Create widget div with cf-turnstile class and data attributes
        // The Turnstile API will automatically detect and render this element
        var widgetDiv = document.createElement('div');
        widgetDiv.className = 'cf-turnstile';
        widgetDiv.setAttribute('data-sitekey', config.siteKey);
        widgetDiv.setAttribute('data-theme', config.theme);
        widgetDiv.setAttribute('data-size', size);
        widgetDiv.setAttribute('data-appearance', appearance);

        container.appendChild(widgetDiv);

        // Insert before submit button (or its parent container)
        var insertionPoint = submitBtn.parentNode;
        if (insertionPoint.classList.contains('form-group') ||
            insertionPoint.classList.contains('pf-c-form__group')) {
            // Insert before the submit button's container
            insertionPoint.parentNode.insertBefore(container, insertionPoint);
        } else {
            // Insert before the submit button itself
            insertionPoint.insertBefore(container, submitBtn);
        }

        logger.log('[Turnstile] Widget container injected, waiting for API to auto-render');
    }

    /**
     * Main initialization function
     */
    function initializeTurnstile() {
        // Prevent duplicate initialization
        if (window.TURNSTILE_INITIALIZED) {
            logger.log('[Turnstile] Already initialized, skipping duplicate initialization');
            return;
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
