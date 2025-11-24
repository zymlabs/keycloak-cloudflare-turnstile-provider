# Turnstile Authentication Flow Examples

This document explains all the authentication and registration flow examples included in the `turnstile-demo` realm configuration.

## Overview

The demo realm includes **5 pre-configured flows** demonstrating all three implementation methods for both browser authentication and registration:

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

### 1. Browser Turnstile Script Injection (Default)

**Flow Name:** `browser-turnstile-script-injection`

**Description:** Default browser authentication with Turnstile using Script Injection method

**Configuration:**
- Authenticator Config: `turnstile-config-script-injection`
- Widget Mode: `managed`
- Widget Theme: `auto`
- Implementation Method: `SCRIPT_INJECTION`
- Fail Action: `ALLOW`
- Fail Mode: `FAIL_OPEN`

**Flow Structure:**
```
browser-turnstile-script-injection (ALTERNATIVE)
├── auth-cookie (ALTERNATIVE)
├── auth-spnego (DISABLED)
├── identity-provider-redirector (ALTERNATIVE)
└── turnstile-script-injection-and-forms (ALTERNATIVE)
    ├── cloudflare-turnstile-authenticator (REQUIRED)
    └── browser-turnstile-separate-page-conditional-otp (CONDITIONAL)
        ├── conditional-user-configured (REQUIRED)
        └── auth-otp-form (REQUIRED)
```

**How to Use:**
1. This is set as the default browserFlow in the realm
2. Users will see the Turnstile widget on a separate page before the login form
3. After completing Turnstile, users proceed to authentication
4. Works with any theme (no customization needed)

---

### 2. Browser Turnstile Separate Page

**Flow Name:** `browser-turnstile-separate-page`

**Description:** Browser authentication with Turnstile on a dedicated separate page

**Configuration:**
- Authenticator Config: `cloudflare-turnstile`
- Widget Mode: `managed`
- Widget Theme: `auto`
- Implementation: Separate Page (Authenticator)
- Fail Action: `ALLOW`
- Fail Mode: `FAIL_OPEN`

**Flow Structure:**
```
browser-turnstile-separate-page (ALTERNATIVE)
├── auth-cookie (ALTERNATIVE)
├── auth-spnego (DISABLED)
├── identity-provider-redirector (ALTERNATIVE)
└── turnstile-and-forms (ALTERNATIVE)
    ├── cloudflare-turnstile-authenticator (REQUIRED)
    └── browser-turnstile-separate-page-forms (REQUIRED)
        ├── auth-username-password-form (REQUIRED)
        └── browser-turnstile-separate-page-conditional-otp (CONDITIONAL)
            ├── conditional-user-configured (REQUIRED)
            └── auth-otp-form (REQUIRED)
```

**How to Use:**
1. Go to **Authentication** → **Flows** → **Bindings**
2. Set **Browser Flow** to `browser-turnstile-separate-page`
3. Users will see the Turnstile widget on a dedicated verification page
4. After completing Turnstile, users are redirected to the login form

---

### 3. Browser Turnstile Custom Theme

**Flow Name:** `browser-turnstile-custom-theme`

**Description:** Browser authentication using Custom Theme method

**Configuration:**
- Authenticator Config: `turnstile-config-custom-theme`
- Widget Mode: `managed`
- Widget Theme: `auto`
- Implementation Method: `CUSTOM_THEME`
- Fail Action: `ALLOW`
- Fail Mode: `FAIL_OPEN`

**Flow Structure:**
```
browser-turnstile-custom-theme (ALTERNATIVE)
├── auth-cookie (ALTERNATIVE)
├── auth-spnego (DISABLED)
├── identity-provider-redirector (ALTERNATIVE)
└── turnstile-custom-theme-forms (ALTERNATIVE)
    ├── cloudflare-turnstile-authenticator (REQUIRED)
    └── browser-turnstile-separate-page-conditional-otp (CONDITIONAL)
        ├── conditional-user-configured (REQUIRED)
        └── auth-otp-form (REQUIRED)
```

**How to Use:**
1. Go to **Realm Settings** → **Themes**
2. Set **Login Theme** to `cloudflare-turnstile` or your custom theme
3. Go to **Authentication** → **Flows** → **Bindings**
4. Set **Browser Flow** to `browser-turnstile-custom-theme`
5. Turnstile widget will appear inline on the login form

**Differences from Default:**
- Requires custom theme with Turnstile support
- Widget appears inline on login form (not separate page)
- Full control over widget placement and styling

---

## Registration Flows

### 1. Registration Turnstile Script Injection (Default)

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
    ├── cloudflare-turnstile-form-action (REQUIRED)
    ├── registration-user-creation (REQUIRED)
    └── registration-password-action (REQUIRED)
```

**How to Use:**
1. This is set as the default registrationFlow in the realm
2. Users will see the Turnstile widget on the registration form
3. Widget is injected via JavaScript into the registration form
4. Works with any theme (no customization needed)

---

### 2. Registration Turnstile Custom Theme

**Flow Name:** `registration-turnstile-custom-theme`

**Description:** Registration flow using Custom Theme method

**Configuration:**
- Form Action Config: `turnstile-form-config-custom-theme`
- Widget Mode: `managed`
- Widget Theme: `auto`
- Implementation Method: `CUSTOM_THEME`
- Fail Action: `ALLOW`
- Fail Mode: `FAIL_OPEN`

**Flow Structure:**
```
registration-turnstile-custom-theme (REQUIRED)
└── registration-form-custom-theme (REQUIRED)
    ├── cloudflare-turnstile-form-action (REQUIRED)
    ├── registration-user-creation (REQUIRED)
    └── registration-password-action (REQUIRED)
```

**How to Use:**
1. Go to **Realm Settings** → **Themes**
2. Set **Login Theme** to `cloudflare-turnstile` or your custom theme
3. Go to **Authentication** → **Flows** → **Bindings**
4. Set **Registration Flow** to `registration-turnstile-custom-theme`
5. Widget will appear inline on registration form via custom theme

---

## Configuration Details

### Authenticator Configurations

All authenticator configurations use Cloudflare's test keys:
- Site Key: `1x00000000000000000000AA`
- Secret Key: `1x0000000000000000000000000000000AA`

**⚠️ Important:** Replace these with your actual Cloudflare Turnstile keys for production use.

#### turnstile-config-script-injection (Authenticator - Browser Default)
```json
{
  "siteKey": "1x00000000000000000000AA",
  "secretKey": "1x0000000000000000000000000000000AA",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN",
  "implementationMethod": "SCRIPT_INJECTION",
  "enableDebugLogging": "true"
}
```

#### cloudflare-turnstile (Authenticator - Separate Page)
```json
{
  "siteKey": "1x00000000000000000000AA",
  "secretKey": "1x0000000000000000000000000000000AA",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN",
  "enableDebugLogging": "true"
}
```

#### turnstile-config-custom-theme (Authenticator - Custom Theme)
```json
{
  "siteKey": "1x00000000000000000000AA",
  "secretKey": "1x0000000000000000000000000000000AA",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN",
  "implementationMethod": "CUSTOM_THEME",
  "enableDebugLogging": "true"
}
```

#### turnstile-form-config-script-injection (Form Action - Registration Default)
```json
{
  "siteKey": "1x00000000000000000000AA",
  "secretKey": "1x0000000000000000000000000000000AA",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "implementationMethod": "SCRIPT_INJECTION",
  "recordVerifications": "true",
  "failAction": "BLOCK",
  "failMode": "FAIL_OPEN",
  "enableDebugLogging": "true"
}
```

#### turnstile-form-config-custom-theme (Form Action - Custom Theme)
```json
{
  "siteKey": "1x00000000000000000000AA",
  "secretKey": "1x0000000000000000000000000000000AA",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "implementationMethod": "CUSTOM_THEME",
  "recordVerifications": "true",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN",
  "enableDebugLogging": "true"
}
```

---

## Switching Between Flows

### To Change Browser Flow:

1. Navigate to **Authentication** → **Flows** in Keycloak admin console
2. In the **Bindings** tab at the top
3. Select desired flow from **Browser Flow** dropdown:
   - `browser-turnstile-script-injection` (Script Injection - default)
   - `browser-turnstile-separate-page` (Separate Page)
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

| Flow Type | Implementation | Component | Flow Name | Config Name | Widget Mode | Theme | Fail Action |
|-----------|---------------|-----------|-----------|-------------|-------------|-------|-------------|
| Browser/Login | Script Injection | Authenticator | `browser-turnstile-script-injection` | `turnstile-config-script-injection` | managed | auto | ALLOW |
| Browser/Login | Separate Page | Authenticator | `browser-turnstile-separate-page` | `cloudflare-turnstile` | managed | auto | ALLOW |
| Browser/Login | Custom Theme | Authenticator | `browser-turnstile-custom-theme` | `turnstile-config-custom-theme` | managed | auto | ALLOW |
| Registration | Script Injection | Form Action | `registration-turnstile-script-injection` | `turnstile-form-config-script-injection` | managed | auto | BLOCK |
| Registration | Custom Theme | Form Action | `registration-turnstile-custom-theme` | `turnstile-form-config-custom-theme` | managed | auto | ALLOW |

---

## Further Reading

- [Cloudflare Turnstile Documentation](https://developers.cloudflare.com/turnstile/)
- [Keycloak Authentication SPI](https://www.keycloak.org/docs/latest/server_development/#_auth_spi)
- [Keycloak Theme Development](https://www.keycloak.org/docs/latest/server_development/#_themes)
- Main project README.md for implementation details
