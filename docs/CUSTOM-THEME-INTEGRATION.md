# Custom Theme Integration Guide

This guide shows how to integrate Cloudflare Turnstile verification into your existing custom Keycloak theme by copying code from the bundled reference themes.

## Overview

The Cloudflare Turnstile provider includes two **reference theme implementations** that demonstrate how to integrate Turnstile widgets into Keycloak login and registration forms:

- **`cloudflare-turnstile`** - Modern reference implementation (Keycloak 25-26+, PatternFly 5)
- **`cloudflare-turnstile-legacy`** - Legacy reference implementation (Keycloak 24.x, PatternFly 3/4)

**Important**: These bundled themes are **example implementations** that you can copy from. If you have an existing custom theme, you'll copy the relevant code snippets into your theme's templates rather than extending the bundled themes.

---

## Prerequisites

Before integrating Turnstile into your custom theme:

1. ✅ You have an existing custom Keycloak theme
2. ✅ The Cloudflare Turnstile provider JAR is installed in Keycloak
3. ✅ You know your Keycloak version and PatternFly version
4. ✅ You have access to your theme's `login.ftl` and/or `register.ftl` files

---

## Step 1: Choose Your Reference Implementation

Select the reference theme that matches your Keycloak version:

| Your Keycloak Version | Reference Theme to Copy From |
|----------------------|------------------------------|
| Keycloak 26+ | `cloudflare-turnstile` (modern) |
| Keycloak 25.x using keycloak.v2 | `cloudflare-turnstile` (modern) |
| Keycloak 25.x using classic keycloak | `cloudflare-turnstile-legacy` |
| Keycloak 24.x | `cloudflare-turnstile-legacy` |

**How to check your theme's parent**:
```bash
# Look at your-theme/login/theme.properties
cat /opt/keycloak/themes/your-theme/login/theme.properties | grep parent
```

---

## Step 2: Locate Reference Theme Code

The bundled reference themes are included in the provider JAR at:

```
zymlabs-cloudflare-turnstile-provider.jar
└── theme/
    ├── cloudflare-turnstile/           # Modern reference
    │   └── login/
    │       ├── login.ftl               # Login template with Turnstile
    │       └── register.ftl            # Registration template with Turnstile
    └── cloudflare-turnstile-legacy/    # Legacy reference
        └── login/
            ├── login.ftl
            └── register.ftl
```

**To extract and view the reference code**:

```bash
# Extract the JAR to view reference implementations
cd /opt/keycloak/providers
jar -xf zymlabs-cloudflare-turnstile-provider.jar theme/

# View modern reference login template
cat theme/cloudflare-turnstile/login/login.ftl

# View legacy reference login template
cat theme/cloudflare-turnstile-legacy/login/login.ftl

# View modern reference registration template
cat theme/cloudflare-turnstile/login/register.ftl
```

---

## Step 3: Copy Code Into Your Custom Theme

### Option A: Modern Theme (Keycloak 25-26+, PatternFly 5)

#### For Login Forms (`login.ftl`)

**Location in reference**: `theme/cloudflare-turnstile/login/login.ftl` lines 65-91

**Code to copy**:
```ftl
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
```

**Where to insert**:
- **After** password field
- **Before** the login button
- Inside the `<form>` element

**Example placement**:
```ftl
<form id="kc-form-login" action="${url.loginAction}" method="post">
    <!-- Username field -->
    <@field.input name="username" ... />

    <!-- Password field -->
    <@field.password name="password" ... />

    <!-- ✨ INSERT TURNSTILE CODE HERE ✨ -->
    <#if turnstileHiddenFields??>
        ${turnstileHiddenFields?no_esc}
    </#if>
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

    <!-- Login button -->
    <@buttons.loginButton />
</form>
```

#### For Registration Forms (`register.ftl`)

**Location in reference**: `theme/cloudflare-turnstile/login/register.ftl` lines 49-84

**Code to copy**:
```ftl
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
```

**Where to insert**:
- **After** terms acceptance section
- **Before** submit button
- Inside the `<form>` element

**Example placement**:
```ftl
<form id="kc-register-form" action="${url.registrationAction}" method="post">
    <!-- User profile fields -->
    <@userProfileCommons.userProfileFormFields ... />

    <!-- Terms acceptance -->
    <@registerCommons.termsAcceptance/>

    <!-- ✨ INSERT TURNSTILE CODE HERE ✨ -->
    <#if turnstileHiddenFields??>
        ${turnstileHiddenFields?no_esc}
    </#if>
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

    <!-- Submit button -->
    <div id="kc-form-buttons">
        <input type="submit" value="${msg("doRegister")}"/>
    </div>
</form>
```

---

### Option B: Legacy Theme (Keycloak 24.x, PatternFly 3/4)

#### For Login Forms (`login.ftl`)

**Location in reference**: `theme/cloudflare-turnstile-legacy/login/login.ftl` lines 78-104

**Code to copy**:
```ftl
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
                 <#else>
                 data-size="flexible"
                 </#if>
                 style="width: 100%"
            ></div>
        </div>
    </div>
    <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
</#if>
```

**Where to insert**:
- **After** password field and "Remember Me" checkbox
- **Before** the login button
- Inside the `<form>` element

**Example placement**:
```ftl
<form id="kc-form-login" action="${url.loginAction}" method="post">
    <!-- Username field -->
    <div class="${properties.kcFormGroupClass!}">
        <label for="username">${msg("username")}</label>
        <input id="username" name="username" type="text" />
    </div>

    <!-- Password field -->
    <div class="${properties.kcFormGroupClass!}">
        <label for="password">${msg("password")}</label>
        <input id="password" name="password" type="password" />
    </div>

    <!-- Remember Me -->
    <div class="${properties.kcFormGroupClass!}">
        <div class="checkbox">
            <label>
                <input name="rememberMe" type="checkbox"> ${msg("rememberMe")}
            </label>
        </div>
    </div>

    <!-- ✨ INSERT TURNSTILE CODE HERE ✨ -->
    <#if turnstileHiddenFields??>
        ${turnstileHiddenFields?no_esc}
    </#if>
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
                     <#else>
                     data-size="flexible"
                     </#if>
                     style="width: 100%"
                ></div>
            </div>
        </div>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
    </#if>

    <!-- Login button -->
    <div id="kc-form-buttons">
        <input type="submit" value="${msg("doLogIn")}"/>
    </div>
</form>
```

#### For Registration Forms (`register.ftl`)

The legacy registration template follows the same pattern as login. Insert the code:
- **After** user profile fields and terms acceptance
- **Before** the submit button

---

## Step 4: Understand Template Variables

The Cloudflare Turnstile authenticator provides these variables to your templates:

### Configuration Variables

| Variable | Type | Description | Example Values |
|----------|------|-------------|----------------|
| `turnstileRequired` | Boolean | Whether Turnstile verification is required | `true`, `false` |
| `turnstileSkipped` | Boolean | Whether verification was skipped (allowlisted IP) | `true`, `false` |
| `turnstileSiteKey` | String | Your Cloudflare site key | `"1x00000000000000000000AA"` |
| `turnstileTheme` | String | Widget theme | `"light"`, `"dark"`, `"auto"` |
| `turnstileMode` | String | Widget interaction mode | `"managed"`, `"non-interactive"`, `"invisible"` |

### Script Injection Variable

| Variable | Type | Description |
|----------|------|-------------|
| `turnstileHiddenFields` | String (HTML) | Hidden field markers for SCRIPT_INJECTION mode |

**Important**: Always include the `turnstileHiddenFields` block even if using CUSTOM_THEME mode. This ensures your theme works with both CUSTOM_THEME and SCRIPT_INJECTION implementation methods.

### Widget Data Attributes

The Turnstile widget supports these Cloudflare data attributes:

| Attribute | Description | Possible Values |
|-----------|-------------|-----------------|
| `data-sitekey` | Your site key (required) | From config |
| `data-theme` | Visual theme | `light`, `dark`, `auto` |
| `data-size` | Widget size | `flexible`, `invisible` |
| `data-appearance` | Interaction mode | `interaction-only`, `always` |

---

## Step 5: Configure Authenticator

After adding the code to your templates:

1. **Select your custom theme**:
   - Admin Console → Realm Settings → Themes
   - Login Theme: Select your custom theme
   - Click **Save**

2. **Configure Implementation Method**:
   - Authentication → Flows → Select your flow
   - Find Turnstile authenticator
   - Click gear icon (⚙️) to configure
   - Set **Implementation Method** to `CUSTOM_THEME`
   - Configure site key, secret key, widget settings
   - Click **Save**

3. **Test the integration**:
   - Open login page in incognito/private window
   - Verify Turnstile widget appears
   - Test form submission
   - Check error handling

---

## Step 6: Verify Integration

### Visual Check

✅ **Correct integration**:
- Widget appears between password field and login button
- Widget is centered and properly sized
- No layout issues or double margins
- Error messages display correctly below widget

❌ **Common issues**:
- Widget missing → Check `turnstileRequired` variable and implementation method
- Layout broken → Verify CSS class structure matches your theme
- Double widgets → Ensure you only have one Turnstile code block
- Styling conflicts → Check for custom CSS overrides

### Functional Check

Test these scenarios:

1. **Successful verification**:
   - Complete Turnstile challenge
   - Submit form
   - Should proceed to login

2. **Failed verification**:
   - Block JavaScript or use invalid token
   - Submit form
   - Should show error message

3. **Allowlisted IP**:
   - Configure IP allowlist with your IP
   - Widget still appears (UX consistency)
   - Verification succeeds automatically

4. **Blocklisted IP**:
   - Configure IP blocklist with test IP
   - Access should be denied immediately

---

## Implementation Methods Comparison

Your custom theme code supports **two implementation methods**:

### CUSTOM_THEME (Native Integration)

**What it does**:
- Uses the FreeMarker code you copied into your templates
- Renders widget natively as part of the form
- Best visual integration

**When to use**:
- You have control over theme templates
- You want native, seamless integration
- You maintain the theme files

**Requires**:
- Copying code into `login.ftl` and `register.ftl`
- Selecting your custom theme in realm settings

### SCRIPT_INJECTION (JavaScript Injection)

**What it does**:
- JavaScript automatically injects widget into existing forms
- No template modification required
- Works with any theme

**When to use**:
- You can't modify theme templates
- You need to support multiple themes
- Quick deployment without theme changes

**Requires**:
- Only the `turnstileHiddenFields` block in templates
- Or no template changes at all (injector finds form automatically)

**Note**: By including both code blocks (hidden fields + native widget), your theme supports both methods seamlessly.

---

## Customization Options

### Widget Styling

You can customize widget appearance with CSS:

```css
/* Center the widget */
.cf-turnstile {
    margin: 0 auto;
    max-width: 300px;
}

/* Add spacing */
div:has(> .cf-turnstile) {
    margin-top: 1rem;
    margin-bottom: 1rem;
}

/* Dark theme adjustments */
@media (prefers-color-scheme: dark) {
    .cf-turnstile {
        /* Custom dark mode styles */
    }
}
```

### Widget Placement

Default placement (after password, before button) works for most forms. Alternative placements:

**Top of form** (before username):
```ftl
<form>
    <!-- Turnstile here -->
    <!-- Username -->
    <!-- Password -->
    <!-- Button -->
</form>
```

**After terms acceptance** (registration forms):
```ftl
<form>
    <!-- User fields -->
    <!-- Terms checkbox -->
    <!-- Turnstile here -->
    <!-- Button -->
</form>
```

### Conditional Rendering

Show widget only for specific realms:

```ftl
<#if realm.name == 'production'>
    <#if turnstileRequired?? && turnstileRequired && !(turnstileSkipped!false)>
        <!-- Turnstile widget -->
    </#if>
</#if>
```

---

## Troubleshooting

### Widget Not Appearing

**Problem**: Turnstile widget doesn't display

**Solutions**:

1. **Check template variables**:
   ```ftl
   <!-- Add debug output temporarily -->
   turnstileRequired: ${turnstileRequired!'not set'}<br>
   turnstileSkipped: ${turnstileSkipped!'not set'}<br>
   turnstileSiteKey: ${turnstileSiteKey!'not set'}<br>
   ```

2. **Verify implementation method**:
   - Authenticator config should be set to `CUSTOM_THEME`

3. **Check theme selection**:
   - Realm Settings → Themes → Login Theme
   - Should be your custom theme, not the reference theme

4. **Inspect FreeMarker errors**:
   ```bash
   # Check Keycloak logs for template errors
   tail -f /opt/keycloak/data/log/keycloak.log | grep -i freemarker
   ```

5. **Test with SCRIPT_INJECTION**:
   - Temporarily switch to SCRIPT_INJECTION mode
   - If widget appears, issue is with template code

### Styling Issues

**Problem**: Widget looks misaligned or has layout problems

**Solutions**:

1. **Verify CSS class structure**:
   - Modern themes use `kcFormGroupClass` and `kcInputWrapperClass`
   - Legacy themes have simpler structure
   - Match the structure in reference theme for your version

2. **Check for conflicting CSS**:
   ```bash
   # Look for custom styles affecting Turnstile
   grep -r "cf-turnstile" /opt/keycloak/themes/your-theme/
   ```

3. **Inspect with browser DevTools**:
   - F12 → Elements tab
   - Inspect `.cf-turnstile` div
   - Check computed styles and layout

4. **Try minimal styling**:
   ```ftl
   <!-- Simplified version for testing -->
   <div style="text-align: center; margin: 20px 0;">
       <div class="cf-turnstile" data-sitekey="${turnstileSiteKey}"></div>
   </div>
   <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
   ```

### Error Messages Not Showing

**Problem**: Turnstile errors don't display

**For modern themes (PatternFly 5)**:
```ftl
<#if messagesPerField.existsError('cf-turnstile-response')>
    <div class="${properties.kcFormHelperTextClass!}" aria-live="polite">
        <div class="${properties.kcInputHelperTextClass!}">
            <div class="${properties.kcInputHelperTextItemClass!} ${properties.kcError!}">
                <span class="${properties.kcInputErrorMessageClass!}">
                    ${kcSanitize(messagesPerField.get('cf-turnstile-response'))?no_esc}
                </span>
            </div>
        </div>
    </div>
</#if>
```

**For legacy themes (PatternFly 3/4)**:
```ftl
<#if messagesPerField.existsError('cf-turnstile-response')>
    <span class="${properties.kcInputErrorMessageClass!}">
        ${kcSanitize(messagesPerField.get('cf-turnstile-response'))?no_esc}
    </span>
</#if>
```

### Widget Appears Twice

**Problem**: Two Turnstile widgets on the same page

**Causes**:
1. Code block inserted multiple times in template
2. Both CUSTOM_THEME and SCRIPT_INJECTION active simultaneously

**Solutions**:
1. Search template for duplicate code:
   ```bash
   grep -n "cf-turnstile" /opt/keycloak/themes/your-theme/login/login.ftl
   ```

2. Ensure only one implementation method configured

3. Clear browser cache and test in incognito mode

---

## Migration from Bundled Themes

If you were previously using the bundled reference themes (`cloudflare-turnstile` or `cloudflare-turnstile-legacy`) directly and want to move to your own custom theme:

### Migration Steps

1. **Extract your current configuration**:
   - Note your current theme selection
   - Document authenticator configuration
   - Export any custom properties or CSS

2. **Copy template code**:
   - Follow Step 3 above to copy Turnstile code
   - Copy into your custom theme's login.ftl and register.ftl

3. **Test before switching**:
   - Keep bundled theme active initially
   - Test your custom theme in a dev/test realm first

4. **Switch theme**:
   - Realm Settings → Themes
   - Change Login Theme to your custom theme
   - Click Save

5. **Verify functionality**:
   - Test login flow
   - Test registration flow
   - Check error handling
   - Verify styling

6. **Monitor logs**:
   ```bash
   tail -f /opt/keycloak/data/log/keycloak.log
   ```

---

## Best Practices

### ✅ Do

- **Always include both code blocks** (hidden fields + native widget) for maximum compatibility
- **Test in incognito/private window** to avoid caching issues
- **Use the reference theme for your Keycloak version** (modern vs legacy)
- **Keep the exact variable names** (`turnstileRequired`, `turnstileSiteKey`, etc.)
- **Include error handling** for better UX
- **Document your customizations** for future maintenance

### ❌ Don't

- **Don't extend bundled themes** via `parent=cloudflare-turnstile` - copy the code instead
- **Don't modify variable names** - authenticator expects exact names
- **Don't skip the `turnstileHiddenFields` block** - needed for SCRIPT_INJECTION fallback
- **Don't hardcode site keys** - use `${turnstileSiteKey}` variable
- **Don't forget to load Cloudflare's script** - `<script src="https://challenges.cloudflare.com/turnstile/v0/api.js">`

---

## Complete Example: Custom Theme Integration

Here's a complete example of a custom theme with Turnstile integration:

### Directory Structure
```
your-custom-theme/
└── login/
    ├── theme.properties
    ├── login.ftl          # Modified with Turnstile
    └── register.ftl       # Modified with Turnstile
```

### theme.properties
```properties
# Your existing custom theme
parent=keycloak.v2
# ... your other properties ...
```

### login.ftl (excerpt)
```ftl
<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <form id="kc-form-login" action="${url.loginAction}" method="post">
            <!-- Your existing form fields -->
            <div class="form-group">
                <label for="username">${msg("username")}</label>
                <input id="username" name="username" type="text" />
            </div>

            <div class="form-group">
                <label for="password">${msg("password")}</label>
                <input id="password" name="password" type="password" />
            </div>

            <!-- ✨ Turnstile Integration ✨ -->
            <#-- Script Injection Mode Support -->
            <#if turnstileHiddenFields??>
                ${turnstileHiddenFields?no_esc}
            </#if>

            <#-- Custom Theme Mode Support -->
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

            <!-- Submit button -->
            <button type="submit">${msg("doLogIn")}</button>
        </form>
    </#if>
</@layout.registrationLayout>
```

---

## Support and Resources

- **Reference Themes**: Extract from `zymlabs-cloudflare-turnstile-provider.jar` → `theme/` directory
- **Theme Selection Guide**: [THEME-SELECTION.md](THEME-SELECTION.md)
- **Setup Documentation**: [SETUP.md](SETUP.md)
- **Troubleshooting Guide**: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- **Cloudflare Docs**: [Turnstile Documentation](https://developers.cloudflare.com/turnstile/)
- **Keycloak Theme Docs**: [Server Developer Guide - Themes](https://www.keycloak.org/docs/latest/server_development/#_themes)

---

## Summary

1. **Choose reference theme** based on Keycloak version (modern vs legacy)
2. **Extract reference code** from provider JAR
3. **Copy Turnstile code blocks** into your custom theme's `login.ftl` and `register.ftl`
4. **Configure authenticator** with CUSTOM_THEME method
5. **Select your custom theme** in realm settings
6. **Test thoroughly** in multiple scenarios

**Remember**: The bundled themes are **reference implementations** to copy from, not to extend. Copy the relevant code snippets into your existing custom theme for best results.
