# Setup Guide

Step-by-step guide for installing and configuring the Cloudflare Turnstile Keycloak extension.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Getting Cloudflare Turnstile Keys](#getting-cloudflare-turnstile-keys)
3. [Installing the Extension](#installing-the-extension)
4. [Creating an Authentication Flow](#creating-an-authentication-flow)
5. [Configuring the Authenticator](#configuring-the-authenticator)
6. [Testing](#testing)
7. [Activating the Flow](#activating-the-flow)
8. [Adding Turnstile to Registration](#adding-turnstile-to-registration)
9. [Next Steps](#next-steps)

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
- Browser can reach `challenges.cloudflare.com`?
- JavaScript enabled in browser?
- Check browser console for errors

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
