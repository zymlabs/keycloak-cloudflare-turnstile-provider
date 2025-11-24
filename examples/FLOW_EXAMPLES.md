# Turnstile Authentication Flow Examples

This document explains all the authentication and registration flow examples included in the `turnstile-demo` realm configuration.

## Overview

The demo realm includes **7 pre-configured flows** demonstrating all three implementation methods for both browser authentication and registration:

### Implementation Methods

The plugin provides **3 implementation approaches** combining 2 component types:

#### Component Types

1. **Authenticator** (`cloudflare-turnstile-authenticator`)
   - Shows Turnstile on a **separate page** before the form
   - Works for both login and registration flows
   - Requires extra page load/redirect
   - Maximum compatibility with all themes

2. **Form Action** (`cloudflare-turnstile-form-action`)
   - Integrates Turnstile **inline** within forms
   - Primarily for registration flows
   - Supports 2 rendering methods: Script Injection, Custom Theme

#### Implementation Modes

1. **Separate Page** (Authenticator) - Dedicated verification page
   - Shows Turnstile before login/registration form
   - Works with any theme
   - Extra redirect for users
   - Best for: Maximum compatibility, simplicity

2. **Script Injection** (Form Action) - JavaScript DOM manipulation
   - Injects widget via JavaScript into existing forms
   - Requires custom theme with turnstile-injector.js
   - Requires JavaScript enabled
   - Best for: Registration flows, modern browsers

3. **Custom Theme** (Form Action/Theme) - Theme-dependent rendering
   - Requires custom theme with Turnstile support
   - Works for both login and registration
   - Full control over widget placement
   - Best for: Custom branded pages

#### Architecture Notes

**For Login/Browser Flows:**
- All browser flows in this demo use **Authenticator** (Separate Page mode)
- This shows Turnstile on a dedicated page before the login form
- For inline Turnstile on login forms, use **Custom Theme** mode (included as `cloudflare-turnstile` theme)

**For Registration Flows:**
- Use **Form Action** for inline integration (Script Injection, Custom Theme)
- Or use **Authenticator** for separate page (like login flows)
- This demo includes Form Action examples for both rendering methods

## Browser Authentication Flows

### 1. Browser with Turnstile (Separate Page - Default)

**Flow Name:** `browser with turnstile`

**Description:** Default browser authentication with Turnstile using Separate Page/Authenticator method

**Configuration:**
- Authenticator Config: `cloudflare-turnstile`
- Widget Mode: `managed`
- Widget Theme: `auto`
- Implementation: Separate Page (Authenticator)

**Flow Structure:**
```
browser with turnstile (ALTERNATIVE)
├── auth-cookie (ALTERNATIVE)
├── auth-spnego (DISABLED)
├── identity-provider-redirector (ALTERNATIVE)
└── turnstile-and-forms (ALTERNATIVE)
    ├── cloudflare-turnstile-authenticator (REQUIRED)
    └── browser with turnstile forms (REQUIRED)
        ├── auth-username-password-form (REQUIRED)
        └── browser with turnstile Browser - Conditional OTP (CONDITIONAL)
            ├── conditional-user-configured (REQUIRED)
            └── auth-otp-form (REQUIRED)
```

**How to Use:**
1. This is set as the default browserFlow in the realm
2. Users will see the Turnstile widget on a separate page before the login form
3. After completing Turnstile, users are redirected to the login form
4. Works with any theme (no customization needed)

---

### 2. Browser Turnstile (Alternative Configuration)

**Flow Name:** `browser-turnstile-custom-theme`

**Description:** Browser authentication using Separate Page with `invisible` widget mode

**Configuration:**
- Authenticator Config: `turnstile-config-custom-theme`
- Widget Mode: `invisible` (runs in background)
- Widget Theme: `dark`
- Implementation: Separate Page (Authenticator)

**How to Use:**
1. Go to **Authentication** → **Flows**
2. Set **Browser Flow** to `browser-turnstile-custom-theme`
3. Turnstile runs invisibly on separate page before login form

**Differences from Default:**
- Widget mode is `invisible` (runs in background, no user interaction)
- Uses `dark` theme
- Fail action is `ALLOW` (user-friendly for testing)

---

### 4. Browser with Inline Turnstile (Custom Theme)

**Description:** For inline Turnstile on the login form itself (not a separate page), use the included `cloudflare-turnstile` custom theme

**How to Use:**
1. Go to **Realm Settings** → **Themes**
2. Set **Login Theme** to `cloudflare-turnstile`
3. Use any of the above browser flows
4. Turnstile widget will appear inline on the login form before the submit button

**Note:** This approach uses theme templates, not FormAction, because Keycloak login forms don't support FormActions

---

## Registration Flows

### 4. Registration Turnstile Script Injection (Default)

**Flow Name:** `registration-turnstile-script-injection`

**Description:** Default registration flow with Turnstile using Script Injection method

**Configuration:**
- Form Action Config: `turnstile-form-config-script-injection`
- Widget Mode: `managed`
- Widget Theme: `auto`
- Implementation Method: `SCRIPT_INJECTION`

**Flow Structure:**
```
registration-turnstile-script-injection (REQUIRED)
└── registration-form-script-injection (REQUIRED)
    ├── registration-page-form (REQUIRED)
    ├── cloudflare-turnstile-form-action (REQUIRED)
    ├── registration-profile-action (REQUIRED)
    ├── registration-password-action (REQUIRED)
    └── registration-recaptcha-action (DISABLED)
```

**How to Use:**
1. This is set as the default registrationFlow in the realm
2. Users will see the Turnstile widget on the registration form
3. Widget is injected via JavaScript below the submit button

---

### 5. Registration Turnstile Custom Theme

**Flow Name:** `registration-turnstile-custom-theme`

**Description:** Registration flow using Custom Theme method

**Configuration:**
- Form Action Config: `turnstile-form-config-custom-theme`
- Widget Mode: `invisible`
- Widget Theme: `dark`
- Implementation Method: `CUSTOM_THEME`

**How to Use:**
1. Develop a custom Keycloak theme with Turnstile registration support
2. Go to **Authentication** → **Flows**
3. Set **Registration Flow** to `registration-turnstile-custom-theme`
4. Set realm theme to your custom theme

---

## Configuration Details

### Authenticator Configurations

All authenticator configurations use Cloudflare's test keys:
- Site Key: `1x00000000000000000000AA`
- Secret Key: `1x0000000000000000000000000000000AA`

**⚠️ Important:** Replace these with your actual Cloudflare Turnstile keys for production use.

#### cloudflare-turnstile (Default - Authenticator)
```json
{
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN"
}
```

#### turnstile-config-custom-theme (Authenticator)
```json
{
  "widgetMode": "invisible",
  "widgetTheme": "dark",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN"
}
```

#### turnstile-form-config-script-injection (Form Action)
```json
{
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "implementationMethod": "SCRIPT_INJECTION",
  "failAction": "BLOCK",
  "failMode": "FAIL_OPEN"
}
```

#### turnstile-form-config-custom-theme (Form Action)
```json
{
  "widgetMode": "invisible",
  "widgetTheme": "dark",
  "implementationMethod": "CUSTOM_THEME",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN"
}
```

---

## Switching Between Flows

### To Change Browser Flow:

1. Navigate to **Authentication** → **Flows** in Keycloak admin console
2. In the **Bindings** tab at the top
3. Select desired flow from **Browser Flow** dropdown:
   - `browser with turnstile` (Script Injection - default)
   - `browser-turnstile-custom-theme` (Custom Theme)
4. Click **Save**

### To Change Registration Flow:

1. Navigate to **Authentication** → **Flows** in Keycloak admin console
2. In the **Bindings** tab at the top
3. Select desired flow from **Registration Flow** dropdown:
   - `registration-turnstile-script-injection` (Script Injection - default)
   - `registration-turnstile-custom-theme` (Custom Theme)
4. Click **Save**

---

## Testing the Flows

### Test Credentials

The demo realm includes a test user:
- Username: `testuser`
- Password: `password`

### Test Procedures

#### Testing Browser Flow:
1. Logout if currently logged in
2. Navigate to: `http://localhost:8080/realms/turnstile-demo/account`
3. You should see the Turnstile widget before the login form
4. Complete the Turnstile challenge
5. Login with test credentials

#### Testing Registration Flow:
1. Navigate to: `http://localhost:8080/realms/turnstile-demo/account`
2. Click **Register** link
3. You should see the Turnstile widget on the registration form
4. Fill out registration form and complete Turnstile challenge
5. Submit registration

---

## Troubleshooting

### Widget Not Appearing

**Script Injection Method:**
- Check browser console for JavaScript errors
- Verify CSP headers allow `https://challenges.cloudflare.com`
- Check that form selector matches your theme's HTML structure

**Custom Theme Method:**
- Verify your custom theme implements Turnstile widget rendering
- Check theme attributes are being passed correctly
- Review theme FreeMarker templates for errors

### Verification Failing

- Verify Site Key and Secret Key are correct
- Check IP allowlist/blocklist configuration
- Review Keycloak logs for Cloudflare API errors
- Verify network connectivity to `challenges.cloudflare.com`

### Flow Not Activating

- Ensure flow is bound in **Authentication** → **Flows** → **Bindings**
- Check that all required authenticators are enabled
- Verify no conflicting flows are interfering
- Clear browser cache and test in incognito mode

---

## Advanced Customization

### Creating Your Own Flow

1. Go to **Authentication** → **Flows**
2. Click **New** to create a new flow
3. Choose **Basic flow** for browser, or **Basic flow** → **Form flow** for registration
4. Add executions:
   - For browser: Add `cloudflare-turnstile-authenticator`
   - For registration: Add `cloudflare-turnstile-form-action`
5. Configure the authenticator/form action with desired settings
6. Set execution requirements appropriately
7. Bind the flow in **Bindings** tab

### Mixing Implementation Methods

You can create hybrid flows mixing different methods:
- Use Script Injection for login, Custom Theme for registration
- Use different widget modes for different user segments
- Apply different fail actions based on risk assessment

---

## Production Checklist

Before deploying to production:

- [ ] Replace test Site Key and Secret Key with production keys
- [ ] Set appropriate `failAction` (`BLOCK` recommended for production)
- [ ] Set `failMode` to `FAIL_CLOSED` for critical applications
- [ ] Configure IP allowlist/blocklist as needed
- [ ] Enable `recordVerifications` for audit trails
- [ ] Test all flows thoroughly
- [ ] Review CSP headers for Script Injection method
- [ ] Validate custom theme implementation for Custom Theme method
- [ ] Monitor verification success rates
- [ ] Set up alerts for verification failures

---

## Quick Reference

| Flow Type | Implementation | Component | Flow Name | Config Name | Widget Mode | Theme |
|-----------|---------------|-----------|-----------|-------------|-------------|-------|
| Browser/Login | Separate Page | Authenticator | `browser with turnstile` | `cloudflare-turnstile` | managed | auto |
| Browser/Login | Separate Page | Authenticator | `browser-turnstile-custom-theme` | `turnstile-config-custom-theme` | invisible | dark |
| Browser/Login | Inline (Custom Theme) | Theme | Use `cloudflare-turnstile` theme | N/A | Configured in theme | auto |
| Registration | Script Injection | Form Action | `registration-turnstile-script-injection` | `turnstile-form-config-script-injection` | managed | auto |
| Registration | Custom Theme | Form Action | `registration-turnstile-custom-theme` | `turnstile-form-config-custom-theme` | invisible | dark |

---

## Further Reading

- [Cloudflare Turnstile Documentation](https://developers.cloudflare.com/turnstile/)
- [Keycloak Authentication SPI](https://www.keycloak.org/docs/latest/server_development/#_auth_spi)
- [Keycloak Theme Development](https://www.keycloak.org/docs/latest/server_development/#_themes)
- Main project README.md for implementation details
