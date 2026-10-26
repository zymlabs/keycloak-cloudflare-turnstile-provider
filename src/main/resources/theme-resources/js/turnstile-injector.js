/**
 * Cloudflare Turnstile Script Injection Mode
 *
 * Puts the Turnstile widget on the sign-in, registration or reset-password form, before its submit
 * button, and keeps the submit button disabled until Turnstile has issued a token. Works with any
 * Keycloak theme without template changes:
 *
 * - Server-rendered (FreeMarker) themes: the form is there once the page has loaded.
 * - Themes that render the form with JavaScript (React, Keycloakify): this script may run before
 *   the form exists, and before the widget settings (config.js) have loaded. It waits for both, and
 *   puts the widget back if a re-render removes it.
 *
 * The widget is rendered explicitly (turnstile.render) into a container inside the form, so
 * Turnstile's hidden cf-turnstile-response input is posted with the form.
 */
(function() {
    'use strict';

    // One injector per page, even if a theme adds the script twice
    if (window.__cloudflareTurnstileInjector) {
        return;
    }
    window.__cloudflareTurnstileInjector = true;

    /** How long to wait for a form to put the widget on (at first, and after a re-render removed it). */
    var FORM_WAIT_MS = 30000;

    /** Fired by config.js once window.TURNSTILE_CONFIG is set: scripts a theme adds may run in any order. */
    var CONFIG_EVENT = 'cloudflare-turnstile:config';

    var API_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

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

    /** What the injector manages on this page: one widget, in one form, at a time. */
    var state = {
        form: null,          // the form the widget was put in
        container: null,     // the widget's container (div.cf-turnstile), created by this script
        widgetId: null,      // Turnstile's id of the rendered widget
        config: null,
        hasToken: false,     // whether the current widget has issued a token that hasn't expired
        submit: null,        // the form's submit button
        ownsDisabled: false, // whether the submit button is disabled for Turnstile (rather than by the theme)
        waitTimer: null,
        waitingForConfig: false,
        unmanaged: false,    // the widget was rendered by someone else: its token can't be followed
        stopped: false
    };

    var pageObserver = null;   // forms rendered or re-rendered by JavaScript
    var buttonObserver = null; // the theme disabling or enabling the submit button
    var apiCallbacks = [];

    /**
     * Find the form that needs Turnstile widget
     */
    function findTargetForm() {
        // Keycloak's own forms first, then forms that look like them
        var idSelectors = [
            'form#kc-form-login',           // Login form
            'form#kc-register-form',        // Registration form
            'form#kc-reset-password-form'   // Reset password ("Forgot your password?") form
        ];
        var genericSelectors = [
            'form[action*="login"]',        // Any login form
            'form[action*="registration"]', // Any registration form
            'form[name="login"]',
            'form[name="register"]'
        ];

        var i;
        var form;
        for (i = 0; i < idSelectors.length; i++) {
            form = document.querySelector(idSelectors[i]);
            if (form) {
                logger.log('[Turnstile] Found target form using selector: ' + idSelectors[i]);
                return form;
            }
        }
        for (i = 0; i < genericSelectors.length; i++) {
            form = firstShown(document.querySelectorAll(genericSelectors[i]));
            if (form) {
                logger.log('[Turnstile] Found target form using selector: ' + genericSelectors[i]);
                return form;
            }
        }

        // Fallback: look for any form with username/password or registration fields
        var forms = document.getElementsByTagName('form');
        for (i = 0; i < forms.length; i++) {
            form = forms[i];
            if (!form.hidden && (form.querySelector('input[name="username"]') ||
                form.querySelector('input[name="password"]') ||
                form.querySelector('input[name="firstName"]'))) {
                logger.log('[Turnstile] Found target form via field detection');
                return form;
            }
        }

        return null;
    }

    /** The first form that isn't hidden (not, say, the sign-in page's hidden passkey form). */
    function firstShown(forms) {
        for (var i = 0; i < forms.length; i++) {
            if (!forms[i].hidden) {
                return forms[i];
            }
        }
        return null;
    }

    /**
     * Find the submit button in the form
     */
    function findSubmitButton(form) {
        return form.querySelector('input[type="submit"]') ||
               form.querySelector('button[type="submit"]') ||
               form.querySelector('button[name="login"]') ||
               form.querySelector('.btn-primary') ||
               form.querySelector('button.pf-c-button');
    }

    /**
     * The widget appearance setting: always, interaction-only or invisible. The former names
     * (managed, non-interactive) are still accepted; anything else is 'always'.
     */
    function widgetAppearance(mode) {
        if (mode === 'interaction-only' || mode === 'non-interactive') {
            return 'interaction-only';
        }
        return mode === 'invisible' ? 'invisible' : 'always';
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
                mode: widgetAppearance(window.TURNSTILE_CONFIG.mode),
                theme: window.TURNSTILE_CONFIG.theme || 'auto'
            };
        }

        // Fallback: Check for hidden fields (for backwards compatibility)
        var siteKey = form.querySelector('input[name="turnstile-site-key"]');
        var mode = form.querySelector('input[name="turnstile-mode"]');
        var theme = form.querySelector('input[name="turnstile-theme"]');
        var enabled = form.querySelector('input[name="turnstile-enabled"]');

        if (!siteKey || !enabled || enabled.value !== 'true') {
            return null;
        }

        var config = {
            siteKey: siteKey.value,
            mode: widgetAppearance(mode ? mode.value : ''),
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

    // ---- Submit button ----------------------------------------------------------------------
    //
    // Disabled until there is a token (except with invisible widgets). A theme may disable and
    // enable the button too, e.g. until the form is filled in, re-applying its state on every
    // render: the injector only enables a button it disabled itself, and disables it again when the
    // theme enables it while there is no token.

    function managesSubmit() {
        return state.config !== null && state.config.mode !== 'invisible' && !state.unmanaged;
    }

    /** Gives the submit button back to the theme (enabled, if this script disabled it). */
    function releaseSubmit() {
        if (state.submit && state.ownsDisabled && state.submit.isConnected) {
            setDisabled(state.submit, false);
        }
        state.ownsDisabled = false;
    }

    function setDisabled(button, disabled) {
        button.disabled = disabled;
        if (buttonObserver) {
            buttonObserver.takeRecords(); // this script's own change, not the theme's
        }
        logger.log('[Turnstile] Submit button ' + (disabled ? 'disabled' : 'enabled'));
    }

    function applySubmitState() {
        var button = state.submit;
        if (!button || !managesSubmit()) {
            return;
        }
        if (!state.hasToken && !button.disabled) {
            setDisabled(button, true);
            state.ownsDisabled = true;
        } else if (state.hasToken && state.ownsDisabled) {
            state.ownsDisabled = false;
            if (button.disabled) {
                setDisabled(button, false);
            }
        }
    }

    /** Follows the form's current submit button (a re-render may replace it). */
    function trackSubmitButton() {
        var button = state.form ? findSubmitButton(state.form) : null;
        if (button !== state.submit) {
            releaseSubmit(); // the previous button is no longer this script's to manage
            state.submit = button;
            state.ownsDisabled = false;
            if (buttonObserver) {
                buttonObserver.disconnect();
                if (button) {
                    buttonObserver.observe(button, { attributes: true, attributeFilter: ['disabled'] });
                }
            }
        }
        applySubmitState();
    }

    /** The theme disabled or enabled the submit button. */
    function onThemeChangedButton() {
        var button = state.submit;
        if (!button) {
            return;
        }
        if (button.disabled) {
            state.ownsDisabled = false; // the theme's call: it enables the button again when it wants
        } else {
            applySubmitState();
        }
    }

    // ---- Turnstile API ----------------------------------------------------------------------

    function turnstileApi() {
        return window.turnstile && typeof window.turnstile.render === 'function' ? window.turnstile : null;
    }

    function flushApiCallbacks() {
        if (!turnstileApi()) {
            return;
        }
        var callbacks = apiCallbacks;
        apiCallbacks = [];
        for (var i = 0; i < callbacks.length; i++) {
            callbacks[i]();
        }
    }

    /** Calls back once the Turnstile API is there, loading it (for explicit rendering) if needed. */
    function whenTurnstileReady(callback) {
        if (turnstileApi()) {
            callback();
            return;
        }
        apiCallbacks.push(callback);
        if (apiCallbacks.length > 1) {
            return; // already waiting
        }

        var existing = document.querySelector('script[src*="challenges.cloudflare.com/turnstile"]');
        if (existing) {
            logger.log('[Turnstile] API script already on the page, waiting for it');
            existing.addEventListener('load', flushApiCallbacks);
            return;
        }

        logger.log('[Turnstile] Loading API script');
        var script = document.createElement('script');
        script.src = API_URL;
        script.async = true;
        script.defer = true;
        script.onload = function() {
            logger.log('[Turnstile] API script loaded');
            flushApiCallbacks();
        };
        script.onerror = function() {
            logger.error('[Turnstile] Failed to load API script');
        };
        document.head.appendChild(script);
    }

    // ---- Widget -----------------------------------------------------------------------------

    function createContainer(config) {
        var container = document.createElement('div');
        container.className = 'cf-turnstile';
        container.style.width = '100%';
        container.setAttribute('data-turnstile-injected', 'true');
        container.setAttribute('data-sitekey', config.siteKey);
        container.setAttribute('data-theme', config.theme);
        // Cloudflare's own mode comes from the site key; a size of 'invisible' throws for other modes
        container.setAttribute('data-size', 'flexible');
        container.setAttribute('data-appearance', config.mode === 'always' ? 'always' : 'interaction-only');
        return container;
    }

    function insertContainer(form, container) {
        var submitBtn = findSubmitButton(form);
        if (!submitBtn) {
            logger.warn('[Turnstile] No submit button found, injecting at end of form');
            form.appendChild(container);
            return;
        }

        // Insert before the submit button's parent form group to avoid nesting
        // Works with PatternFly 3, 4, and 5 (Keycloak 18+)
        var insertionPoint = submitBtn.parentNode;
        if (insertionPoint !== form && isFormGroup(insertionPoint) && insertionPoint.parentNode) {
            insertionPoint.parentNode.insertBefore(container, insertionPoint);
            logger.log('[Turnstile] Widget inserted before submit button form group');
        } else {
            insertionPoint.insertBefore(container, submitBtn);
            logger.log('[Turnstile] Widget inserted before submit button');
        }
    }

    function renderWidget(container) {
        if (container !== state.container || !container.isConnected) {
            return; // removed or replaced while the API loaded
        }
        if (container.querySelector('input[name="cf-turnstile-response"]')) {
            // Rendered by an API script the theme loaded without explicit rendering: without this
            // script's callbacks, the token can't be followed, so the button is left to the theme
            logger.log('[Turnstile] Widget already rendered');
            state.unmanaged = true;
            releaseSubmit();
            return;
        }

        var config = state.config;
        var isCurrent = function() {
            return container === state.container;
        };
        var lostToken = function() {
            if (isCurrent()) {
                state.hasToken = false;
                applySubmitState();
            }
        };

        state.widgetId = turnstileApi().render(container, {
            sitekey: config.siteKey,
            theme: config.theme,
            size: container.getAttribute('data-size'),
            appearance: container.getAttribute('data-appearance'),
            callback: function() {
                logger.log('[Turnstile] Verification successful');
                if (isCurrent()) {
                    state.hasToken = true;
                    applySubmitState();
                }
            },
            'error-callback': function(errorCode) {
                logger.warn('[Turnstile] Error:', errorCode);
                lostToken();
            },
            'expired-callback': function() {
                logger.log('[Turnstile] Token expired');
                lostToken();
            },
            'timeout-callback': function() {
                logger.warn('[Turnstile] Challenge timed out');
                lostToken();
            }
        });
        logger.log('[Turnstile] Widget rendered');
    }

    /** Forgets a widget a re-render took off the page, and lets Turnstile clean up after it. */
    function dropWidget() {
        var api = turnstileApi();
        if (api && state.widgetId !== null && state.widgetId !== undefined) {
            try {
                api.remove(state.widgetId);
            } catch (e) {
                logger.warn('[Turnstile] Could not remove widget:', e);
            }
        }
        state.container = null;
        state.widgetId = null;
        state.hasToken = false;
    }

    function inject(form, config) {
        logger.log('[Turnstile] Injecting widget');
        state.form = form;
        state.config = config;
        state.hasToken = false;
        var container = createContainer(config);
        state.container = container;

        // Disable the submit button before the widget can issue a token (the API may be cached)
        trackSubmitButton();
        insertContainer(form, container);

        whenTurnstileReady(function() {
            renderWidget(container);
        });
    }

    // ---- Watching the page ------------------------------------------------------------------

    function widgetInPlace() {
        return state.container !== null && state.container.isConnected && state.form.isConnected &&
            state.form.contains(state.container);
    }

    /** Puts the widget on the page's form, if it isn't there (yet, or any more). */
    function sync() {
        if (state.stopped) {
            return;
        }
        if (widgetInPlace()) {
            trackSubmitButton();
            return;
        }
        if (state.container) {
            logger.log('[Turnstile] Widget was removed (re-render), putting it back');
            dropWidget();
            armWaitTimer();
        }

        var form = findTargetForm();
        if (!form) {
            return; // not rendered yet
        }

        var config = getTurnstileConfig(form);
        if (!config) {
            if (!state.waitingForConfig) {
                state.waitingForConfig = true;
                logger.log('[Turnstile] Waiting for the Turnstile configuration');
            }
            return; // config.js hasn't run yet
        }

        if (form.querySelector('.cf-turnstile:not([data-turnstile-injected])')) {
            logger.log('[Turnstile] The theme renders its own widget, nothing to inject');
            stop();
            return;
        }

        clearWaitTimer();
        inject(form, config);
    }

    function armWaitTimer() {
        clearWaitTimer();
        state.waitTimer = setTimeout(function() {
            state.waitTimer = null;
            if (!widgetInPlace()) {
                logger.log('[Turnstile] No target form found, skipping');
                stop();
            }
        }, FORM_WAIT_MS);
    }

    function clearWaitTimer() {
        if (state.waitTimer !== null) {
            clearTimeout(state.waitTimer);
            state.waitTimer = null;
        }
    }

    function stop() {
        state.stopped = true;
        clearWaitTimer();
        releaseSubmit();
        if (pageObserver) {
            pageObserver.disconnect();
        }
        if (buttonObserver) {
            buttonObserver.disconnect();
        }
        document.removeEventListener(CONFIG_EVENT, sync);
    }

    /**
     * Main initialization function
     */
    function initializeTurnstile() {
        logger.log('[Turnstile] Initializing script injection mode');

        if (typeof MutationObserver === 'function') {
            buttonObserver = new MutationObserver(onThemeChangedButton);
            pageObserver = new MutationObserver(sync);
        }

        sync();
        if (state.stopped) {
            return;
        }
        if (!pageObserver) {
            if (!state.container) {
                logger.log('[Turnstile] No target form found, skipping');
            }
            return;
        }

        // Forms rendered (or re-rendered) by JavaScript, and settings that load after this script
        pageObserver.observe(document.documentElement, { childList: true, subtree: true });
        document.addEventListener(CONFIG_EVENT, sync);
        if (!state.container) {
            armWaitTimer();
        }
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeTurnstile);
    } else {
        initializeTurnstile();
    }

})();
