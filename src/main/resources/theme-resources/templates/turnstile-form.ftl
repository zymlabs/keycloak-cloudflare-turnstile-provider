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
                                id="kc-login">
                            ${msg("doLogIn")}
                        </button>
                    </div>
                </form>

                <#-- Load Cloudflare Turnstile Script -->
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>

                <#-- Form submission handler -->
                <script>
                    <#if turnstileMode == 'invisible'>
                    // Invisible mode: callback function for auto-submit
                    function onTurnstileSuccess(token) {
                        <#if enableDebugLogging!false>
                        console.log('Turnstile verification successful (invisible mode)');
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
                            console.log('Triggering invisible Turnstile challenge...');
                            </#if>
                            // The invisible widget will automatically trigger and call onTurnstileSuccess
                            turnstile.execute(document.getElementById('turnstile-widget'));
                        });
                    });
                    <#else>
                    // Managed and Non-interactive modes: copy token on form submit
                    document.addEventListener('DOMContentLoaded', function() {
                        document.getElementById('kc-turnstile-form').addEventListener('submit', function(e) {
                            // Get the Turnstile response token from the widget
                            var widgetContainer = document.querySelector('.cf-turnstile');
                            if (widgetContainer) {
                                var tokenInput = widgetContainer.querySelector('input[name="cf-turnstile-response"]');
                                if (tokenInput && tokenInput.value) {
                                    <#if enableDebugLogging!false>
                                    console.log('Turnstile token found, copying to form');
                                    </#if>
                                    document.getElementById('turnstile-response').value = tokenInput.value;
                                } else {
                                    <#if enableDebugLogging!false>
                                    console.warn('Turnstile token not found - verification may fail');
                                    </#if>
                                }
                            }
                        });
                    });
                    </#if>
                </script>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
