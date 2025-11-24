<#--
  Cloudflare Turnstile Login Template - Modern Variant

  Based on: Keycloak 26.0.0 keycloak.v2/login/login.ftl
  Last updated: 2024-11-22
  PatternFly: 5

  Changes from base template:
  - Added Turnstile widget integration for CUSTOM_THEME mode (lines 69-84)
  - Added turnstileHiddenFields support for SCRIPT_INJECTION mode (lines 64-66)
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "social-providers.ftl" as identityProviders>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <#if realm.password>
                <form id="kc-form-login" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate="novalidate">
                    <#if !usernameHidden??>
                        <#-- Determine label based on realm configuration -->
                        <#assign label>
                            <#if !realm.loginWithEmailAllowed>
                                ${msg("username")}
                            <#elseif !realm.registrationEmailAsUsername>
                                ${msg("usernameOrEmail")}
                            <#else>
                                ${msg("email")}
                            </#if>
                        </#assign>

                        <#-- Username field using field.ftl macro -->
                        <@field.input
                            name="username"
                            label=label
                            value=login.username!''
                            autofocus=true
                            autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'username')}"
                            fieldName="username"
                        />

                        <#-- Password field using field.ftl macro -->
                        <@field.password
                            name="password"
                            label=msg("password")
                            forgotPassword=realm.resetPasswordAllowed
                            autocomplete="current-password"
                            fieldName="password"
                        >
                            <#-- Remember Me checkbox nested inside password field -->
                            <#if realm.rememberMe && !usernameHidden??>
                                <@field.checkbox
                                    name="rememberMe"
                                    label=msg("rememberMe")
                                    value=login.rememberMe??
                                />
                            </#if>
                        </@field.password>
                    </#if>

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
                                     <#if turnstileMode == 'invisible'>
                                     data-size="invisible"
                                     <#else>
                                     data-size="flexible"
                                     </#if>
                                     <#if turnstileMode == 'non-interactive'>
                                     data-appearance="interaction-only"
                                     </#if>
                                     style="width: 100%"
                                ></div>
                            </div>
                        </div>
                        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
                    </#if>

                    <#-- Hidden credential ID field -->
                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>

                    <#-- Login button using buttons.ftl macro -->
                    <@buttons.loginButton />
                </form>
            </#if>
          </div>
        </div>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container" class="pf-v5-c-login__main-footer-band">
                <div id="kc-registration" class="pf-v5-c-login__main-footer-band-item">
                    <span>${msg("noAccount")} <a tabindex="8"
                                                 href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    <#elseif section = "socialProviders" >
        <#-- Social providers using identityProviders.ftl macro -->
        <#if realm.password && social.providers?? && social.providers?has_content>
            <@identityProviders.show social=social/>
        </#if>
    </#if>

</@layout.registrationLayout>
