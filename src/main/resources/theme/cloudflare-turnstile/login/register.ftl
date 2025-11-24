<#--
  Cloudflare Turnstile Registration Template - Modern Variant

  Based on: Keycloak 26.0.0 keycloak.v2/login/register.ftl
  Last updated: 2024-11-22
  PatternFly: 5

  Changes from base template:
  - Added Turnstile widget integration for CUSTOM_THEME mode (lines 76-106)
  - Added turnstileHiddenFields support for SCRIPT_INJECTION mode (lines 71-73)
-->
<#import "template.ftl" as layout>
<#import "user-profile-commons.ftl" as userProfileCommons>
<#import "register-commons.ftl" as registerCommons>
<#import "field.ftl" as field>
<@layout.registrationLayout displayMessage=messagesPerField.exists('global') displayRequiredFields=true; section>
    <#if section = "header">
        ${msg("registerTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" class="${properties.kcFormClass!}" action="${url.registrationAction}" method="post" novalidate="novalidate">

            <@userProfileCommons.userProfileFormFields; callback, attribute>
                <#if callback = "afterField">
                <#-- render password fields just under the username or email (if used as username) -->
                    <#if passwordRequired?? && (attribute.name == 'username' || (attribute.name == 'email' && realm.registrationEmailAsUsername))>
                        <#-- Password field using field.ftl macro -->
                        <@field.password
                            name="password"
                            label=msg("password")
                            required=true
                            autocomplete="new-password"
                            fieldName="password"
                        />

                        <#-- Password confirmation field using field.ftl macro -->
                        <@field.password
                            name="password-confirm"
                            label=msg("passwordConfirm")
                            required=true
                            autocomplete="new-password"
                            fieldName="password-confirm"
                        />
                    </#if>
                </#if>
            </@userProfileCommons.userProfileFormFields>

            <@registerCommons.termsAcceptance/>

            <#-- Cloudflare Turnstile Widget - Custom Theme with Multiple Implementation Options -->
            <#-- Support for Script Injection Mode: Output hidden fields for JavaScript injector -->
            <#if turnstileHiddenFields??>
                ${turnstileHiddenFields?no_esc}
            </#if>

            <#-- Support for Custom Theme Mode: Native widget rendering -->
            <#if turnstileRequired?? && turnstileRequired && !(turnstileSkipped!false)>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcInputWrapperClass!}" style="display: flex; justify-content: center;">
                        <div class="cf-turnstile"
                             data-sitekey="${turnstileSiteKey}"
                             data-theme="${turnstileTheme!'auto'}"
                             data-size="${(turnstileMode == 'invisible')?then('invisible', 'flexible')}"
                             <#if turnstileMode == 'non-interactive'>
                             data-appearance="interaction-only"
                             </#if>
                             style="width: 100%"
                        ></div>
                    </div>
                    <#-- Error message display using PatternFly v5 structure -->
                    <#if messagesPerField.existsError('cf-turnstile-response')>
                        <div class="${properties.kcFormHelperTextClass!}" aria-live="polite">
                            <div class="${properties.kcInputHelperTextClass!}">
                                <div class="${properties.kcInputHelperTextItemClass!} ${properties.kcError!}">
                                    <span class="${properties.kcInputErrorMessageClass!}">
                                        <span class="${properties.kcInputErrorIconClass!}" aria-hidden="true"></span>
                                        ${kcSanitize(messagesPerField.get('cf-turnstile-response'))?no_esc}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </#if>
                </div>
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
            </#if>

            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
            </div>

            <div class="${properties.kcFormGroupClass!} pf-v5-c-login__main-footer-band">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                        <span><a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></span>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
