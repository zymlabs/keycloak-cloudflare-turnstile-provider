# Setup Guide

Step-by-step guide for installing and configuring the Cloudflare Turnstile Keycloak extension.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Getting Cloudflare Turnstile Keys](#getting-cloudflare-turnstile-keys)
3. [Installing the Extension](#installing-the-extension)
4. [Configure Content Security Policy](#4-configure-content-security-policy)
5. [Configure Theme (Optional - Custom Theme Mode Only)](#5-configure-theme-optional---custom-theme-mode-only)
6. [Creating an Authentication Flow](#creating-an-authentication-flow)
7. [Configuring the Authenticator](#configuring-the-authenticator)
8. [Testing](#testing)
9. [Activating the Flow](#activating-the-flow)
10. [Adding Turnstile to Registration](#adding-turnstile-to-registration)
11. [Next Steps](#next-steps)

## Prerequisites

### Software Requirements

- ✅ Keycloak 24.0.0 or later
- ✅ Java 17 or later (for building from source)
- ✅ PostgreSQL, MySQL, or another supported database

### Cloudflare Requirements

- ✅ Cloudflare account (free tier is sufficient)
- ✅ Turnstile site configured

### Network Requirements

- ✅ Keycloak server can reach `https://challenges.cloudflare.com`
- ✅ Client browsers can reach `https://challenges.cloudflare.com`
- ✅ HTTPS enabled on your Keycloak instance (Turnstile requires HTTPS)
- ✅ Content Security Policy (CSP) configured to allow Cloudflare domains (see [Configure CSP](#4-configure-content-security-policy) below)

## Getting Cloudflare Turnstile Keys

### Step 1: Create Cloudflare Account

If you don't have one:

1. Go to https://dash.cloudflare.com/sign-up
2. Create a free account
3. Verify your email

### Step 2: Access Turnstile Dashboard

1. Log in to https://dash.cloudflare.com
2. Click on **Turnstile** in the left sidebar
3. Click **Add Site**

### Step 3: Create Turnstile Site

**Site Name**: Enter a descriptive name (e.g., "Keycloak Production")

**Domain**: Enter your Keycloak domain
- For production: `auth.yourdomain.com`
- For testing: `localhost` or your test domain
- Can use wildcards: `*.yourdomain.com`

**Widget Mode**: Select the default mode
- **Managed** (recommended): Automatic for low-risk, interactive for suspicious
- **Non-interactive**: Minimal user interaction
- **Invisible**: No visible widget (advanced)

**Pre-Clearance**: Optional, keep default settings

Click **Create**.

### Step 4: Copy Keys

After creation, you'll see two keys:

**Site Key** (Public):
```
0x4AAAAAAABCDEFGHIJKLMNOPexample
```
- This is public and visible in HTML
- Safe to commit to version control
- Used client-side to render widget

**Secret Key** (Private):
```
0x4AAAAAAABCDEFGHIJKLMNOPsecretexample
```
- ⚠️ Keep this secret!
- Never commit to version control
- Used server-side to verify tokens
- Stored encrypted in Keycloak database

**Save both keys** - you'll need them later.

## Installing the Extension

### Option 1: Download Pre-built JAR (Recommended)

1. Download the latest release from [GitHub Releases](https://github.com/zymlabs/keycloak-cloudflare-turnstile/releases)

```bash
wget https://github.com/zymlabs/keycloak-cloudflare-turnstile/releases/download/v1.0.0/zymlabs-cloudflare-turnstile-provider-1.0.0.jar
```

### Option 2: Build from Source

1. Clone the repository:
```bash
git clone https://github.com/zymlabs/keycloak-cloudflare-turnstile.git
cd keycloak-cloudflare-turnstile
```

2. Build with Maven:
```bash
mvn clean package
```

3. JAR will be in `target/zymlabs-cloudflare-turnstile-provider.jar`

### Deploy to Keycloak

#### Keycloak Quarkus (v17+)

1. Copy JAR to providers directory:
```bash
cp zymlabs-cloudflare-turnstile-provider-*.jar /opt/keycloak/providers/
```

2. Rebuild Keycloak (required for Quarkus):
```bash
/opt/keycloak/bin/kc.sh build
```

3. Restart Keycloak:
```bash
/opt/keycloak/bin/kc.sh start
# or for development:
/opt/keycloak/bin/kc.sh start-dev
```

#### Docker

Add volume mount in your docker-compose.yml or docker run command:

```yaml
services:
  keycloak:
    image: bitnamilegacy/keycloak:24.0.5
    volumes:
      - ./zymlabs-cloudflare-turnstile-provider.jar:/opt/bitnami/keycloak/providers/zymlabs-cloudflare-turnstile-provider.jar:rw
```

Then restart the container:
```bash
docker-compose restart keycloak
```

### Verify Installation

1. Check Keycloak logs for:
```
INFO  [com.zymlabs.keycloak.cloudflare.turnstileprovider.CloudflareTurnstileJpaEntityProviderFactory]
Initializing CloudflareTurnstileJpaEntityProviderFactory
```

2. Check database for new table:
```sql
SELECT * FROM information_schema.tables
WHERE table_name = 'cloudflare_turnstile_check';
```

3. In Keycloak Admin Console, go to Authentication → Flows
4. Try to create a new execution - "Cloudflare Turnstile" should appear in the dropdown

## 4. Configure Content Security Policy

⚠️ **IMPORTANT**: Cloudflare Turnstile requires CSP configuration to load the widget from Cloudflare's servers.

### Why CSP is Required

Turnstile loads JavaScript and iframe content from `challenges.cloudflare.com`. Without proper CSP configuration, browsers will block these resources and the widget won't appear.

### Required CSP Directives

**Minimum (from Cloudflare documentation)**:
```
script-src https://challenges.cloudflare.com
frame-src https://challenges.cloudflare.com
```

**Recommended for Keycloak** (includes `'self'` and `connect-src`):
```
script-src 'self' https://challenges.cloudflare.com
frame-src 'self' https://challenges.cloudflare.com
connect-src 'self' https://challenges.cloudflare.com
```

### Configuration Methods

Choose the method that best fits your deployment:

#### Method 1: Keycloak Environment Variables

Add CSP configuration via environment variables before starting Keycloak:

```bash
# Set CSP environment variables
export KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC="'self' https://challenges.cloudflare.com"
export KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC="'self' https://challenges.cloudflare.com"
export KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC="'self' https://challenges.cloudflare.com"

# Start Keycloak
/opt/keycloak/bin/kc.sh start
```

#### Method 2: Docker / Docker Compose

Add environment variables to your `docker-compose.yml`:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:24.0.0
    environment:
      KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC: "'self' https://challenges.cloudflare.com"
      KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC: "'self' https://challenges.cloudflare.com"
      KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC: "'self' https://challenges.cloudflare.com"
    volumes:
      - ./zymlabs-cloudflare-turnstile-provider.jar:/opt/keycloak/providers/zymlabs-cloudflare-turnstile-provider.jar
    command:
      - start-dev
```

Or with `docker run`:

```bash
docker run -d \
  -e KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC="'self' https://challenges.cloudflare.com" \
  -e KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC="'self' https://challenges.cloudflare.com" \
  -e KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC="'self' https://challenges.cloudflare.com" \
  -v ./zymlabs-cloudflare-turnstile-provider.jar:/opt/keycloak/providers/zymlabs-cloudflare-turnstile-provider.jar \
  quay.io/keycloak/keycloak:24.0.0
```

#### Method 3: Reverse Proxy Headers

If Keycloak is behind a reverse proxy, configure CSP headers there:

**nginx** (`/etc/nginx/nginx.conf` or site config):

```nginx
server {
    listen 443 ssl;
    server_name auth.yourdomain.com;

    location / {
        proxy_pass http://keycloak:8080;

        # Add CSP header
        add_header Content-Security-Policy "script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;" always;

        # Other proxy headers...
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

**Apache** (`httpd.conf` or `.htaccess`):

```apache
<VirtualHost *:443>
    ServerName auth.yourdomain.com

    # Add CSP header
    Header always set Content-Security-Policy "script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;"

    ProxyPass / http://keycloak:8080/
    ProxyPassReverse / http://keycloak:8080/
</VirtualHost>
```

**Traefik** (labels in `docker-compose.yml`):

```yaml
services:
  keycloak:
    labels:
      - "traefik.http.middlewares.csp.headers.contentsecuritypolicy=script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;"
      - "traefik.http.routers.keycloak.middlewares=csp"
```

#### Method 4: Kubernetes

**Via ConfigMap**:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-env
data:
  KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC: "'self' https://challenges.cloudflare.com"
  KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC: "'self' https://challenges.cloudflare.com"
  KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC: "'self' https://challenges.cloudflare.com"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
spec:
  template:
    spec:
      containers:
      - name: keycloak
        image: quay.io/keycloak/keycloak:24.0.0
        envFrom:
        - configMapRef:
            name: keycloak-env
```

**Via Ingress** (nginx-ingress):

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: keycloak
  annotations:
    nginx.ingress.kubernetes.io/configuration-snippet: |
      add_header Content-Security-Policy "script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;" always;
spec:
  rules:
  - host: auth.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: keycloak
            port:
              number: 8080
```

### Verify CSP Configuration

After configuring CSP, verify it's working:

**1. Check HTTP Headers**:

```bash
curl -I https://your-keycloak.com/realms/your-realm/protocol/openid-connect/auth | grep -i content-security
```

Should show:
```
content-security-policy: script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; ...
```

**2. Check Browser Console**:

1. Open your Keycloak login page
2. Press F12 to open DevTools
3. Go to Console tab
4. Look for CSP errors

**Should NOT see**:
```
Refused to load script from 'https://challenges.cloudflare.com/...' because it violates CSP
Refused to frame 'https://challenges.cloudflare.com/...' because it violates CSP
```

**3. Check Widget Loads**:

1. Navigate to login page
2. Turnstile widget should appear
3. No console errors related to Cloudflare

If you see CSP errors, review your configuration method and ensure CSP headers are being set correctly.

### Troubleshooting CSP Issues

If CSP is not working:

1. **Environment variables not taking effect**:
   - Restart Keycloak completely
   - Verify env vars with: `printenv | grep KC_SPI_CONTENT_SECURITY_POLICY`

2. **Docker CSP not working**:
   - Check environment variables are passed: `docker exec keycloak printenv | grep KC_SPI`
   - Rebuild container: `docker-compose up -d --force-recreate`

3. **Reverse proxy CSP conflicts**:
   - CSP headers can conflict if set in multiple places
   - Check if Keycloak is setting CSP AND proxy is setting CSP
   - Choose one location (preferably reverse proxy for better control)

4. **Widget still not loading**:
   - Verify HTTPS is enabled (Turnstile requires HTTPS)
   - Check browser console for specific error messages
   - See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#widget-issues) for more help

## 5. Configure Theme (Optional - Custom Theme Mode Only)

**Note**: This step is **only required** if you plan to use the **Custom Theme** implementation method. If you're using Separate Page, Script Injection, or Copied Template methods, skip this section.

### Theme Variants

This provider includes two theme variants for compatibility across different Keycloak versions:

| Theme Variant | Parent Theme | Keycloak Version | PatternFly | When to Use |
|---------------|--------------|------------------|------------|-------------|
| **cloudflare-turnstile** | keycloak.v2 | 25-26+ | 5 | ✅ Modern Keycloak installations |
| **cloudflare-turnstile-legacy** | keycloak | 24.x | 3/4 | ⚠️ Legacy support for KC 24.x |

### Selecting a Theme Variant

#### For Keycloak 26+ (Recommended)

1. Log in to Keycloak Admin Console
2. Navigate to **Realm Settings**
3. Click the **Themes** tab
4. Under **Login Theme** dropdown, select: **cloudflare-turnstile**
5. Click **Save**

#### For Keycloak 24.x

1. Log in to Keycloak Admin Console
2. Navigate to **Realm Settings**
3. Click the **Themes** tab
4. Under **Login Theme** dropdown, select: **cloudflare-turnstile-legacy**
5. Click **Save**

#### For Keycloak 25.x (Transition Version)

- **If using keycloak.v2 theme**: Select **cloudflare-turnstile**
- **If using classic keycloak theme**: Select **cloudflare-turnstile-legacy**

### Verifying Theme Selection

After saving, the theme will be active immediately for new sessions:

1. Open a private/incognito browser window
2. Navigate to your realm's login page
3. Verify the theme appears correct
4. Check browser DevTools console for any errors

### When NOT to Configure Theme

Skip theme configuration if you're using:
- **Separate Page** implementation - Uses standalone Turnstile page (theme-independent)
- **Script Injection** implementation - JavaScript injects widget into any theme
- **Copied Template** implementation - Uses bundled templates from JAR

These methods work with **any Keycloak theme** and don't require custom theme selection.

### Detailed Theme Selection Guide

For detailed information about theme variants, compatibility, and troubleshooting, see:
- **[docs/THEME-SELECTION.md](THEME-SELECTION.md)** - Comprehensive theme selection guide

## Creating an Authentication Flow

### Step 1: Access Authentication Settings

1. Log in to Keycloak Admin Console
2. Select your realm (or create a new one)
3. Navigate to **Authentication** → **Flows**

### Step 2: Copy Browser Flow

1. Find the "Browser" flow in the list
2. Click the **⋮** (three dots) menu
3. Select **Duplicate**
4. Name it: `Browser with Turnstile`
5. Click **OK**

### Step 3: Add Turnstile Execution

Your new flow should have this structure by default:
```
Browser with Turnstile
├── Cookie (ALTERNATIVE)
├── Kerberos (DISABLED)
├── Identity Provider Redirector (ALTERNATIVE)
└── Browser with Turnstile Forms (ALTERNATIVE)
    ├── Username Password Form (REQUIRED)
    └── Conditional OTP (CONDITIONAL)
```

**Add Turnstile BEFORE the forms:**

1. Click on "Browser with Turnstile" (the top level)
2. Click **Add execution**
3. Select **Cloudflare Turnstile** from dropdown
4. Click **Add**
5. Set requirement to **REQUIRED**

**Reorder if needed:**

1. Click the ⬆️ ⬇️ arrows to move Turnstile execution
2. Place it **before** "Browser with Turnstile Forms"

**Final structure:**
```
Browser with Turnstile
├── Cookie (ALTERNATIVE)
├── Kerberos (DISABLED)
├── Identity Provider Redirector (ALTERNATIVE)
├── Cloudflare Turnstile (REQUIRED) ← Added
└── Browser with Turnstile Forms (ALTERNATIVE)
    ├── Username Password Form (REQUIRED)
    └── Conditional OTP (CONDITIONAL)
```

This creates a **pre-authentication flow** - Turnstile verifies before username/password.

### Alternative: Post-Authentication Flow

To verify AFTER login (less common for Turnstile):

1. Add Turnstile execution INSIDE "Browser with Turnstile Forms" subflow
2. Place it AFTER "Username Password Form"
3. Place it BEFORE "Conditional OTP"

## Configuring the Authenticator

### Step 1: Open Configuration

1. In the flow editor, find your "Cloudflare Turnstile" execution
2. Click **⚙️ Settings** (gear icon) or **Actions** → **Config**
3. Configuration dialog opens

### Step 2: Required Settings

**Site Key**: Paste your Cloudflare site key
```
0x4AAAAAAABCDEFGHIJKLMNOPexample
```

**Secret Key**: Paste your Cloudflare secret key
```
0x4AAAAAAABCDEFGHIJKLMNOPsecretexample
```

### Step 3: Widget Configuration

**Widget Mode**:
- `managed` (recommended) - Balanced security and UX
- `non-interactive` - Minimal friction
- `invisible` - No visible widget (advanced)

**Widget Theme**:
- `auto` (recommended) - Matches user's OS dark mode
- `light` - Light theme only
- `dark` - Dark theme only

### Step 4: Fail Behavior

**Fail Action** (when verification fails):
- `BLOCK` (recommended) - Deny access
- `ALLOW` - Log but allow (testing only)
- `REQUIRE_MFA` - Trigger MFA requirement

**Fail Mode** (when Cloudflare API errors):
- `FAIL_CLOSED` (recommended) - Deny if API is down
- `FAIL_OPEN` - Allow if API is down (higher availability)

### Step 5: Optional Settings

**Record Verifications**: `true` (recommended)
- Stores all verification attempts in database
- Enables audit trail and analytics

**IP Allowlist**: (optional)
- Internal networks that skip Turnstile
- Format: `192.168.0.0/16,10.0.0.0/8`
- Leave empty if not needed

**IP Blocklist**: (optional)
- Networks to immediately block
- Format: `198.51.100.0/24`
- Leave empty if not needed

**Connect Timeout (ms)**: `5000` (default)
**Read Timeout (ms)**: `5000` (default)

### Step 6: Save Configuration

1. Click **Save**
2. Configuration is stored encrypted in Keycloak database

### Example Production Configuration

```
Site Key: 0x4AAAAAAA... (your key)
Secret Key: 0x4AAAAAAA... (your secret)
Widget Mode: managed
Widget Theme: auto
Record Verifications: true
IP Allowlist: (empty or your office network)
IP Blocklist: (empty)
Fail Action: BLOCK
Fail Mode: FAIL_CLOSED
Connect Timeout: 5000
Read Timeout: 5000
```

## Testing

### Step 1: Test Without Activating

**Important**: Don't bind the flow to your realm yet!

1. Keep your current browser flow active
2. Test the new flow using a direct URL

**Get the flow ID:**
1. In the flow editor, note the browser URL
2. Extract the flow ID from URL: `.../flows/{flow-id}/...`

**Test URL format:**
```
https://your-keycloak.com/realms/your-realm/protocol/openid-connect/auth?client_id=account&redirect_uri=...&kc_idp_hint=...&kc_action=...
```

Or use an incognito window and temporary realm for testing.

### Step 2: Verify Turnstile Widget Appears

1. Navigate to login page (via test method above)
2. You should see the Turnstile widget **before** username/password fields
3. Widget should match your theme configuration

**Expected appearance** (managed mode):
```
┌─────────────────────────────────────┐
│  Login to Your Realm                │
├─────────────────────────────────────┤
│                                     │
│  [Turnstile Widget Here]            │
│  ┌───────────────────────────────┐  │
│  │ ✓ Verification complete       │  │
│  └───────────────────────────────┘  │
│                                     │
│  [Continue Button]                  │
└─────────────────────────────────────┘
```

### Step 3: Test Successful Verification

1. Complete the Turnstile challenge
2. Click Continue
3. Should proceed to username/password form
4. Complete login normally

### Step 4: Check Database

Query the verification table:
```sql
SELECT * FROM cloudflare_turnstile_check
ORDER BY timestamp DESC
LIMIT 10;
```

You should see a record with:
- `success = true`
- Your IP address
- Timestamp
- Session ID

### Step 5: Check Events

1. In Keycloak Admin Console: Events → Login Events
2. Find your recent login
3. Click **Details**
4. Should show:
   - `cloudflare_turnstile_success: true`
   - `cloudflare_turnstile_hostname: your-domain`
   - `ip_address: your-ip`

### Step 6: Test Failure (Optional)

To test failure handling:

1. Temporarily change Fail Action to `ALLOW`
2. Use an invalid secret key
3. Try to log in
4. Should succeed (because ALLOW) but event logs error
5. Change back to correct secret key and `BLOCK`

## Activating the Flow

⚠️ **Warning**: Only activate after thorough testing!

### Step 1: Bind to Realm

1. In Keycloak Admin Console: **Authentication** → **Flows**
2. Find "Browser with Turnstile" flow
3. Click the **⋮** menu
4. Select **Bind flow**
5. Choose **Browser flow**
6. Click **Save**

OR via Realm Settings:

1. Navigate to **Realm Settings**
2. Click **Login** tab
3. Find **Browser Flow** dropdown
4. Select "Browser with Turnstile"
5. Click **Save**

### Step 2: Test Live

1. **Log out** of Keycloak
2. Navigate to login page in normal window
3. Turnstile widget should appear
4. Complete verification and login

### Step 3: Monitor

**Watch events** for the first few hours:
```sql
-- Check success rate
SELECT
    success,
    COUNT(*) as count
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '1 HOUR'
GROUP BY success;
```

**Check for errors**:
1. Events → Login Events
2. Filter by **Error** events
3. Look for Turnstile-related failures

### Step 4: Rollback Plan

If issues occur:

**Quick Rollback**:
1. Go to Realm Settings → Login tab
2. Change Browser Flow back to "Browser"
3. Click Save
4. Turnstile is now disabled

**Fix and Re-test**:
1. Check logs for errors
2. Verify Cloudflare keys
3. Test in duplicate flow
4. Re-activate when working

## Common Setup Issues

### Widget Not Appearing

**Check**:
- HTTPS enabled? (Turnstile requires HTTPS)
- **Content Security Policy configured?** - See [Configure CSP](#4-configure-content-security-policy) above
- Browser can reach `challenges.cloudflare.com`?
- JavaScript enabled in browser?
- Check browser console for errors (especially CSP violations)

### "Missing Input Response" Error

**Cause**: Token not submitted
**Fix**:
- Ensure FreeMarker template is correct
- Check form submission includes `cf-turnstile-response`
- Verify widget mode is compatible

### All Verifications Failing

**Check**:
- Secret key is correct
- Keycloak server can reach Cloudflare API
- No firewall blocking outbound HTTPS
- Check Keycloak logs for API errors

### Database Table Not Created

**Check**:
```sql
SELECT * FROM DATABASECHANGELOG
WHERE id = 'cloudflare-turnstile-1.0.0';
```

If missing:
- Verify JAR is in providers directory
- Check Keycloak logs for Liquibase errors
- Verify database permissions

For more troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Adding Turnstile to Registration

To protect user registration from bots, you can choose between four implementation approaches:

### Option 1: Separate Verification Page (Recommended)

1. **Navigate to Registration Flow**
   - Go to **Authentication** → **Flows**
   - Find **Registration** flow (or duplicate it)

2. **Add Turnstile Authenticator**
   - Click **Add execution**
   - Select **"Cloudflare Turnstile (Registration)"**

3. **Position Before Registration Form**
   - Drag Turnstile execution to be **before** "Registration Page Form"
   - This ensures verification happens before users see the form

4. **Set to REQUIRED**
   - Click Actions menu (⋮) → Select **REQUIRED**

5. **Configure Settings**
   - Click **⚙️ Settings** (gear icon)
   - Enter same configuration as login flow
   - Click **Save**

**User Experience:**
- User clicks "Register" link
- Turnstile verification page appears
- After verification, user proceeds to registration form

### Option 2: Inline Widget (Script Injection)

1. **Navigate to Registration Flow**
   - Go to **Authentication** → **Flows**
   - Find **Registration** flow
   - Expand **"Registration form"** subflow

2. **Add Script Injection FormAction**
   - Click **Add execution** within the subflow
   - Select **"Cloudflare Turnstile (Script Injection)"**

3. **Set to REQUIRED**
   - Click Actions menu (⋮) → Select **REQUIRED**

4. **Configure Settings**
   - Click **⚙️ Settings** (gear icon)
   - Enter same configuration as login flow
   - Click **Save**

**User Experience:**
- User clicks "Register" link
- Registration form appears with Turnstile widget embedded inline
- User completes form and Turnstile challenge together

**Note:** Option 2 requires JavaScript enabled and may have CSP compatibility issues.

### Option 3: Copied Template (Native Integration)

1. **Navigate to Registration Flow**
   - Go to **Authentication** → **Flows**
   - Find **Registration** flow
   - Expand **"Registration form"** subflow

2. **Add Copied Template FormAction**
   - Click **Add execution** within the subflow
   - Select **"Cloudflare Turnstile (Copied Template)"**

3. **Set to REQUIRED**
   - Click Actions menu (⋮) → Select **REQUIRED**

4. **Configure Settings**
   - Click **⚙️ Settings** (gear icon)
   - Enter same configuration as login flow
   - Click **Save**

**User Experience:**
- User clicks "Register" link
- Registration form appears with Turnstile widget natively embedded
- User completes form and Turnstile challenge together

**Note:** Option 3 uses a bundled template copy that may need updating when Keycloak updates.

### Option 4: Custom Theme (Standard Keycloak Approach)

1. **Select Turnstile Theme**
   - Go to **Realm Settings** → **Themes**
   - Under **Login Theme**, select **cloudflare-turnstile**
   - Click **Save**

2. **Navigate to Registration Flow**
   - Go to **Authentication** → **Flows**
   - Find **Registration** flow
   - Expand **"Registration form"** subflow

3. **Add Custom Theme FormAction**
   - Click **Add execution** within the subflow
   - Select **"Cloudflare Turnstile (Custom Theme)"**

4. **Set to REQUIRED**
   - Click Actions menu (⋮) → Select **REQUIRED**

5. **Configure Settings**
   - Click **⚙️ Settings** (gear icon)
   - Enter same configuration as login flow
   - Click **Save**

**User Experience:**
- User clicks "Register" link
- Registration form appears with Turnstile widget natively embedded (via theme)
- User completes form and Turnstile challenge together

**Note:** Option 4 requires selecting the "cloudflare-turnstile" theme in Realm Settings. Theme extends base theme.

### Detailed Guide

For complete registration setup instructions and troubleshooting, see [REGISTRATION.md](REGISTRATION.md).

---

## Next Steps

After successful setup:

1. **Add Registration Protection**: See [REGISTRATION.md](REGISTRATION.md)
2. **Review Configuration**: See [CONFIGURATION.md](CONFIGURATION.md) for advanced options
3. **Set Up Monitoring**: Query database regularly for failed attempts
4. **Configure IP Filtering**: Add office networks to allowlist if needed
5. **Enable Alerts**: Set up alerts for high failure rates
6. **Review Events**: Regularly check Keycloak events for anomalies

## Production Checklist

Before going to production:

- [ ] Tested in staging/test environment
- [ ] Verified Turnstile widget appears correctly
- [ ] Tested successful login flow
- [ ] Verified database recording works
- [ ] Checked Keycloak event logging
- [ ] Set Fail Mode to `FAIL_CLOSED`
- [ ] Set Fail Action to `BLOCK`
- [ ] Configured appropriate timeouts
- [ ] Tested from different networks/locations
- [ ] Documented Cloudflare keys location
- [ ] Set up monitoring queries
- [ ] Planned rollback procedure
- [ ] Informed users of new security feature (optional)
