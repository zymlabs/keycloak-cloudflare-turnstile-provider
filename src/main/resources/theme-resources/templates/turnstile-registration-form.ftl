<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('cf-turnstile-response'); section>
    <#if section = "header">
        ${msg("turnstileRegistrationHeader")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-turnstile-registration-form" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label class="${properties.kcLabelClass!}">${msg("turnstileRegistrationInstructions")}</label>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!}" style="display: flex; justify-content: center; align-items: center; min-height: 100px;">
                        <div class="cf-turnstile"
                             data-sitekey="${turnstileSiteKey}"
                             data-theme="${turnstileTheme}"
                             data-size="${(turnstileMode == 'invisible')?then('invisible', 'normal')}"
                             data-appearance="${(turnstileMode == 'non-interactive')?then('interaction-only', 'always')}"
                             <#if turnstileMode != 'invisible'>
                             data-callback="onTurnstileSuccess"
                             data-error-callback="onTurnstileError"
                             data-expired-callback="onTurnstileExpired"
                             data-timeout-callback="onTurnstileTimeout"
                             </#if>>
                        </div>
                    </div>

                    <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                        <button type="submit"
                                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                name="login" id="kc-login" value="true"
                                <#if turnstileMode != 'invisible'>disabled</#if>>
                            ${msg("turnstileRegistrationSubmit")}
                        </button>
                    </div>
                </form>
            </div>
        </div>

        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>

        <script>
            // Helper to find submit button with fallbacks
            function findSubmitButton() {
                var form = document.querySelector('form#kc-form-login, form#kc-register-form, form#kc-turnstile-form, form#kc-turnstile-registration-form');
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
                }
            }

            <#if turnstileMode == 'invisible'>
            // Auto-submit for invisible mode after widget loads
            window.addEventListener('load', function() {
                // Wait for Turnstile to be ready
                var checkTurnstile = setInterval(function() {
                    if (typeof turnstile !== 'undefined') {
                        clearInterval(checkTurnstile);

                        // Render invisible widget with callback
                        turnstile.render('.cf-turnstile', {
                            callback: function(token) {
                                // Auto-submit form when challenge completes
                                document.getElementById('kc-turnstile-registration-form').submit();
                            }
                        });
                    }
                }, 100);
            });
            <#else>
            // Managed and Non-interactive modes: callbacks to enable/disable submit button
            function onTurnstileSuccess(token) {
                setSubmitButtonState(false);
            }

            function onTurnstileError(errorCode) {
                setSubmitButtonState(true);
            }

            function onTurnstileExpired() {
                setSubmitButtonState(true);
            }

            function onTurnstileTimeout() {
                setSubmitButtonState(true);
            }
            </#if>
        </script>
    </#if>
</@layout.registrationLayout>
