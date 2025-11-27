<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <#-- Set debug logging flag for turnstile-injector.js -->
                <script>
                    window.TURNSTILE_DEBUG_LOGGING = ${enableDebugLogging?c};
                </script>

                <#-- Cloudflare Turnstile Widget -->
                <div class="turnstile-container" style="width: 100%; display: flex; justify-content: center; align-items: center; margin-bottom: 20px; min-height: 100px;">
                    <div id="turnstile-widget" class="cf-turnstile" style="width: 100%; max-width: 400px;"
                         data-sitekey="${turnstileSiteKey}"
                         data-theme="${turnstileTheme}"
                         <#if turnstileMode == 'invisible'>
                         data-size="invisible"
                         data-callback="onTurnstileSuccess"
                         <#else>
                         data-size="flexible"
                         data-callback="onTurnstileSuccess"
                         data-error-callback="onTurnstileError"
                         data-expired-callback="onTurnstileExpired"
                         data-timeout-callback="onTurnstileTimeout"
                         </#if>
                         <#if turnstileMode == 'non-interactive'>
                         data-appearance="interaction-only"
                         </#if>>
                    </div>
                </div>

                <#-- Hidden form for submission -->
                <form id="kc-turnstile-form" action="${url.loginAction}" method="post">
                    <input type="hidden" id="turnstile-response" name="cf-turnstile-response" />
                    <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                        <button type="submit"
                                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                name="login"
                                id="kc-login"
                                <#if turnstileMode != 'invisible'>disabled</#if>>
                            ${msg("doLogIn")}
                        </button>
                    </div>
                </form>

                <#-- Load Cloudflare Turnstile Script -->
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>

                <#-- Form submission handler -->
                <script>
                    // Helper to find submit button with fallbacks
                    function findSubmitButton() {
                        var form = document.querySelector('form#kc-form-login, form#kc-register-form, form#kc-turnstile-form');
                        if (!form) return null;
                        return form.querySelector('input[type="submit"]') ||
                               form.querySelector('button[type="submit"]') ||
                               form.querySelector('button[name="login"]') ||
                               form.querySelector('.btn-primary') ||
                               form.querySelector('button.pf-c-button');
                    }

                    function setSubmitButtonState(disabled) {
                        var submitBtn = findSubmitButton();
                        if (submitBtn) {
                            submitBtn.disabled = disabled;
                            <#if enableDebugLogging!false>
                            console.log('[Turnstile] Submit button ' + (disabled ? 'disabled' : 'enabled'));
                            </#if>
                        }
                    }

                    <#if turnstileMode == 'invisible'>
                    // Invisible mode: callback function for auto-submit
                    function onTurnstileSuccess(token) {
                        <#if enableDebugLogging!false>
                        console.log('[Turnstile] Verification successful (invisible mode)');
                        </#if>
                        document.getElementById('turnstile-response').value = token;
                        document.getElementById('kc-turnstile-form').submit();
                    }

                    // Trigger invisible challenge when login button is clicked
                    document.addEventListener('DOMContentLoaded', function() {
                        var loginButton = document.getElementById('kc-login');
                        loginButton.addEventListener('click', function(e) {
                            e.preventDefault();
                            <#if enableDebugLogging!false>
                            console.log('[Turnstile] Triggering invisible challenge...');
                            </#if>
                            // The invisible widget will automatically trigger and call onTurnstileSuccess
                            turnstile.execute(document.getElementById('turnstile-widget'));
                        });
                    });
                    <#else>
                    // Managed and Non-interactive modes: callbacks to enable/disable submit button
                    function onTurnstileSuccess(token) {
                        <#if enableDebugLogging!false>
                        console.log('[Turnstile] Verification successful');
                        </#if>
                        // Copy token to hidden form field
                        document.getElementById('turnstile-response').value = token;
                        // Enable submit button
                        setSubmitButtonState(false);
                    }

                    function onTurnstileError(errorCode) {
                        <#if enableDebugLogging!false>
                        console.warn('[Turnstile] Error:', errorCode);
                        </#if>
                        setSubmitButtonState(true);
                    }

                    function onTurnstileExpired() {
                        <#if enableDebugLogging!false>
                        console.log('[Turnstile] Token expired');
                        </#if>
                        setSubmitButtonState(true);
                    }

                    function onTurnstileTimeout() {
                        <#if enableDebugLogging!false>
                        console.warn('[Turnstile] Challenge timed out');
                        </#if>
                        setSubmitButtonState(true);
                    }
                    </#if>
                </script>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
