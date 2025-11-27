<#import "template.ftl" as layout>
<#import "user-profile-commons.ftl" as userProfileCommons>
<#import "register-commons.ftl" as registerCommons>
<@layout.registrationLayout displayMessage=messagesPerField.exists('global') displayRequiredFields=true; section>
    <#if section = "header">
        ${msg("registerTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" class="${properties.kcFormClass!}" action="${url.registrationAction}" method="post">

            <@userProfileCommons.userProfileFormFields; callback, attribute>
                <#if callback = "afterField">
                <#-- render password fields just under the username or email (if used as username) -->
                    <#if passwordRequired?? && (attribute.name == 'username' || (attribute.name == 'email' && realm.registrationEmailAsUsername))>
                        <div class="${properties.kcFormGroupClass!}">
                            <div class="${properties.kcLabelWrapperClass!}">
                                <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label> *
                            </div>
                            <div class="${properties.kcInputWrapperClass!}">
                                <div class="${properties.kcInputGroup!}">
                                    <input type="password" id="password" class="${properties.kcInputClass!}" name="password"
                                           autocomplete="new-password"
                                           aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                                    />
                                    <button class="${properties.kcFormPasswordVisibilityButtonClass!}" type="button" aria-label="${msg('showPassword')}"
                                            aria-controls="password"  data-password-toggle
                                            data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}" data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                            data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                                        <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                                    </button>
                                </div>

                                <#if messagesPerField.existsError('password')>
                                    <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
		                                ${kcSanitize(messagesPerField.get('password'))?no_esc}
		                            </span>
                                </#if>
                            </div>
                        </div>

                        <div class="${properties.kcFormGroupClass!}">
                            <div class="${properties.kcLabelWrapperClass!}">
                                <label for="password-confirm"
                                       class="${properties.kcLabelClass!}">${msg("passwordConfirm")}</label> *
                            </div>
                            <div class="${properties.kcInputWrapperClass!}">
                                <div class="${properties.kcInputGroup!}">
                                    <input type="password" id="password-confirm" class="${properties.kcInputClass!}"
                                           name="password-confirm"
                                           aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                                    />
                                    <button class="${properties.kcFormPasswordVisibilityButtonClass!}" type="button" aria-label="${msg('showPassword')}"
                                            aria-controls="password-confirm"  data-password-toggle
                                            data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}" data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                            data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                                        <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                                    </button>
                                </div>

                                <#if messagesPerField.existsError('password-confirm')>
                                    <span id="input-error-password-confirm" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
		                                ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
		                            </span>
                                </#if>
                            </div>
                        </div>
                    </#if>
                </#if>
            </@userProfileCommons.userProfileFormFields>

            <@registerCommons.termsAcceptance/>

            <#-- Cloudflare Turnstile Widget -->
            <#if turnstileRequired?? && turnstileRequired>
                <div class="form-group">
                    <div class="${properties.kcInputWrapperClass!}">
                        <div class="cf-turnstile"
                             data-sitekey="${turnstileSiteKey}"
                             data-theme="${turnstileTheme!'auto'}"
                             data-size="${(turnstileMode == 'invisible')?then('invisible', 'normal')}"
                             data-appearance="${(turnstileMode == 'non-interactive')?then('interaction-only', 'always')}"
                             <#if turnstileMode != 'invisible'>
                             data-callback="onTurnstileSuccess"
                             data-error-callback="onTurnstileError"
                             data-expired-callback="onTurnstileExpired"
                             data-timeout-callback="onTurnstileTimeout"
                             </#if>>
                        </div>
                        <#if messagesPerField.existsError('cf-turnstile-response')>
                            <span id="input-error-turnstile" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('cf-turnstile-response'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </div>
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
                <#if turnstileMode != 'invisible'>
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
                        }
                    }

                    // Disable submit button initially
                    document.addEventListener('DOMContentLoaded', function() {
                        setSubmitButtonState(true);
                    });

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
                </script>
                </#if>
            </#if>

            <#-- Legacy reCAPTCHA support (for backwards compatibility) -->
            <#if recaptchaRequired?? && !turnstileRequired??>
                <div class="form-group">
                    <div class="${properties.kcInputWrapperClass!}">
                        <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
                    </div>
                </div>
            </#if>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                        <span><a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></span>
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
                </div>
            </div>
        </form>
        <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    </#if>
</@layout.registrationLayout>
