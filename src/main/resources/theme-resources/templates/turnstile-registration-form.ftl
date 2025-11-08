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

                        <div class="${properties.kcInputWrapperClass!}">
                            <div class="cf-turnstile"
                                 data-sitekey="${turnstileSiteKey}"
                                 data-theme="${turnstileTheme}"
                                 data-size="${(turnstileMode == 'invisible')?then('invisible', 'normal')}"
                                 data-appearance="${(turnstileMode == 'non-interactive')?then('interaction-only', 'always')}">
                            </div>
                        </div>
                    </div>

                    <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                        <button type="submit"
                                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                name="login" id="kc-login" value="true">
                            ${msg("turnstileRegistrationSubmit")}
                        </button>
                    </div>
                </form>
            </div>
        </div>

        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>

        <#if turnstileMode == 'invisible'>
        <script>
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
        </script>
        </#if>
    </#if>
</@layout.registrationLayout>
