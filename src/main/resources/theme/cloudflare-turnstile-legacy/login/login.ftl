<#--
  Cloudflare Turnstile Login Template - Legacy Variant

  Based on: Keycloak 24.0.0 keycloak (v1)/login/login.ftl
  Last updated: 2024-11-22
  PatternFly: 3/4

  Changes from base template:
  - Added Turnstile widget integration for CUSTOM_THEME mode
  - Added turnstileHiddenFields support for SCRIPT_INJECTION mode
-->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <#if realm.password>
                <form id="kc-form-login" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <#if !usernameHidden??>
                        <div class="${properties.kcFormGroupClass!}">
                            <div class="${properties.kcLabelWrapperClass!}">
                                <label for="username" class="${properties.kcLabelClass!}"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                            </div>
                            <div class="${properties.kcInputWrapperClass!}">
                                <input tabindex="2" id="username" class="${properties.kcInputClass!}" name="username" value="${(login.username!'')}"  type="text" autofocus autocomplete="username"
                                       aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                                />

                                <#if messagesPerField.existsError('username','password')>
                                    <span id="input-error-username" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                    </span>
                                </#if>
                            </div>
                        </div>
                    </#if>

                    <div class="${properties.kcFormGroupClass!}">
                        <div class="${properties.kcLabelWrapperClass!}">
                            <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>
                        </div>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input tabindex="3" id="password" class="${properties.kcInputClass!}" name="password" type="password" autocomplete="current-password"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            />

                            <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                                <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </div>

                    <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                        <div id="kc-form-options">
                            <#if realm.rememberMe && !usernameHidden??>
                                <div class="checkbox">
                                    <label>
                                        <#if login.rememberMe??>
                                            <input tabindex="5" id="rememberMe" name="rememberMe" type="checkbox" checked> ${msg("rememberMe")}
                                        <#else>
                                            <input tabindex="5" id="rememberMe" name="rememberMe" type="checkbox"> ${msg("rememberMe")}
                                        </#if>
                                    </label>
                                </div>
                            </#if>
                        </div>
                        <div class="${properties.kcFormOptionsWrapperClass!}">
                            <#if realm.resetPasswordAllowed>
                                <span><a tabindex="6" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a></span>
                            </#if>
                        </div>
                    </div>

                      <#-- Cloudflare Turnstile Widget - Custom Theme with Multiple Implementation Options -->
                      <#-- Support for Script Injection Mode: Output hidden fields for JavaScript injector -->
                      <#if turnstileHiddenFields??>
                          ${turnstileHiddenFields?no_esc}
                      </#if>

                      <#-- Support for Custom Theme Mode: Native widget rendering -->
                      <#if turnstileRequired?? && turnstileRequired && !(turnstileSkipped!false)>
                          <div class="${properties.kcFormGroupClass!}">
                              <div class="${properties.kcInputWrapperClass!}">
                                  <div class="cf-turnstile"
                                       data-sitekey="${turnstileSiteKey}"
                                       data-theme="${turnstileTheme!'auto'}"
                                       <#if turnstileMode == 'invisible'>
                                       data-size="invisible"
                                       <#elseif turnstileMode == 'non-interactive'>
                                       data-size="flexible"
                                       data-appearance="interaction-only"
                                       data-callback="onTurnstileSuccess"
                                       data-error-callback="onTurnstileError"
                                       data-expired-callback="onTurnstileExpired"
                                       data-timeout-callback="onTurnstileTimeout"
                                       <#else>
                                       data-size="flexible"
                                       data-callback="onTurnstileSuccess"
                                       data-error-callback="onTurnstileError"
                                       data-expired-callback="onTurnstileExpired"
                                       data-timeout-callback="onTurnstileTimeout"
                                       </#if>
                                       style="width: 100%"
                                  ></div>
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

                      <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                          <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                          <input tabindex="7" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                      </div>
                </form>
            </#if>
            </div>
        </div>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a tabindex="8"
                                                 href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    <#elseif section = "socialProviders" >
        <#if realm.password && social.providers?? && social.providers?has_content>
            <div id="kc-social-providers" class="${properties.kcFormSocialAccountSectionClass!}">
                <hr/>
                <h2>${msg("identity-provider-login-label")}</h2>

                <ul class="${properties.kcFormSocialAccountListClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountListGridClass!}</#if>">
                    <#list social.providers as p>
                        <li>
                            <a id="social-${p.alias}" class="${properties.kcFormSocialAccountListButtonClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountGridItem!}</#if>"
                                    type="button" href="${p.loginUrl}">
                                <#if p.iconClasses?has_content>
                                    <i class="${properties.kcCommonLogoIdP!} ${p.iconClasses!}" aria-hidden="true"></i>
                                    <span class="${properties.kcFormSocialAccountNameClass!} kc-social-icon-text">${p.displayName!}</span>
                                <#else>
                                    <span class="${properties.kcFormSocialAccountNameClass!}">${p.displayName!}</span>
                                </#if>
                            </a>
                        </li>
                    </#list>
                </ul>
            </div>
        </#if>
    </#if>

</@layout.registrationLayout>
