<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <#-- Cloudflare Turnstile Widget -->
                <div class="turnstile-container" style="margin-bottom: 20px;">
                    <div class="cf-turnstile"
                         data-sitekey="${turnstileSiteKey}"
                         data-theme="${turnstileTheme}"
                         data-size="${(turnstileMode == 'invisible')?then('invisible', 'normal')}"
                         data-appearance="${(turnstileMode == 'non-interactive')?then('interaction-only', 'always')}">
                    </div>
                </div>

                <#-- Hidden form for submission -->
                <form id="kc-turnstile-form" action="${url.loginAction}" method="post">
                    <input type="hidden" id="turnstile-response" name="cf-turnstile-response" />
                    <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                        <button type="submit"
                                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                                name="login"
                                id="kc-login">
                            ${msg("doLogIn")}
                        </button>
                    </div>
                </form>

                <#-- Load Cloudflare Turnstile Script -->
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>

                <#-- Form submission handler -->
                <script>
                    document.getElementById('kc-turnstile-form').addEventListener('submit', function(e) {
                        // Get the Turnstile response token
                        var response = document.querySelector('[name="cf-turnstile-response"]');
                        if (!response || !response.value) {
                            // For invisible mode, the token might not be set yet
                            var widget = document.querySelector('.cf-turnstile');
                            if (widget) {
                                var token = widget.querySelector('input[name="cf-turnstile-response"]');
                                if (token && token.value) {
                                    document.getElementById('turnstile-response').value = token.value;
                                }
                            }
                        }
                    });

                    // For invisible mode, auto-submit after verification
                    <#if turnstileMode == 'invisible'>
                    window.onTurnstileLoad = function() {
                        turnstile.ready(function() {
                            document.getElementById('kc-login').addEventListener('click', function(e) {
                                e.preventDefault();
                                turnstile.execute();
                            });
                        });
                    };

                    function onTurnstileSuccess(token) {
                        document.getElementById('turnstile-response').value = token;
                        document.getElementById('kc-turnstile-form').submit();
                    }
                    </#if>
                </script>

                <#-- Error display -->
                <#if message?has_content && (message.type = 'error')>
                    <div class="${properties.kcAlertClass!} ${properties.kcAlertErrorClass!} pf-m-danger">
                        <div class="pf-c-alert__icon">
                            <span class="${properties.kcFeedbackErrorIcon!}"></span>
                        </div>
                        <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
