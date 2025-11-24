# Configuration Reference

Complete reference for all configuration options in the Cloudflare Turnstile Keycloak extension.

## Table of Contents

1. [Content Security Policy](#content-security-policy)
2. [Authenticator Configuration](#authenticator-configuration)
2. [Widget Configuration](#widget-configuration)
   - [Widget Mode](#widget-mode)
   - [Widget Theme](#widget-theme)
3. [IP Filtering](#ip-filtering)
   - [IP Allowlist](#ip-allowlist)
   - [IP Blocklist](#ip-blocklist)
4. [Fail Behavior](#fail-behavior)
   - [Fail Action](#fail-action)
   - [Fail Mode](#fail-mode)
5. [Database Recording](#database-recording)
6. [Network Configuration](#network-configuration)
   - [Connection Timeout](#connect-timeout-ms)
   - [Read Timeout](#read-timeout-ms)
7. [Advanced Scenarios](#advanced-scenarios)

## Content Security Policy

**REQUIRED**: Cloudflare Turnstile requires Content Security Policy (CSP) configuration to function properly.

### Overview

The Turnstile widget loads JavaScript and iframe content from Cloudflare's servers at `challenges.cloudflare.com`. Modern browsers will block these external resources unless explicitly allowed in your CSP configuration.

### Required CSP Directives

**Minimum (from Cloudflare)**:
```
script-src https://challenges.cloudflare.com
frame-src https://challenges.cloudflare.com
```

**Recommended for Keycloak**:
```
script-src 'self' https://challenges.cloudflare.com
frame-src 'self' https://challenges.cloudflare.com
connect-src 'self' https://challenges.cloudflare.com
```

The `'self'` directive allows Keycloak's own scripts to run, and `connect-src` enables AJAX requests that Turnstile may make.

### Configuration Locations

CSP can be configured at different layers. Choose the method that best fits your infrastructure:

#### 1. Keycloak Environment Variables

Set CSP via Keycloak's SPI configuration:

```bash
export KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC="'self' https://challenges.cloudflare.com"
export KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC="'self' https://challenges.cloudflare.com"
export KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC="'self' https://challenges.cloudflare.com"
```

**Pros**: Direct configuration, no additional infrastructure needed
**Cons**: Requires Keycloak restart to change

#### 2. Reverse Proxy

Configure CSP headers at your reverse proxy (nginx, Apache, Traefik):

**nginx**:
```nginx
add_header Content-Security-Policy "script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;" always;
```

**Apache**:
```apache
Header always set Content-Security-Policy "script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;"
```

**Pros**: Can update without Keycloak restart, centralized control
**Cons**: Requires reverse proxy configuration access

#### 3. Container Orchestration

**Docker Compose**:
```yaml
environment:
  KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC: "'self' https://challenges.cloudflare.com"
  KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC: "'self' https://challenges.cloudflare.com"
  KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC: "'self' https://challenges.cloudflare.com"
```

**Kubernetes ConfigMap**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-csp
data:
  KC_SPI_CONTENT_SECURITY_POLICY_SCRIPT_SRC: "'self' https://challenges.cloudflare.com"
  KC_SPI_CONTENT_SECURITY_POLICY_FRAME_SRC: "'self' https://challenges.cloudflare.com"
  KC_SPI_CONTENT_SECURITY_POLICY_CONNECT_SRC: "'self' https://challenges.cloudflare.com"
```

**Pros**: Infrastructure as code, version controlled
**Cons**: Requires container restart to change

### Verifying CSP Configuration

After configuring, verify CSP is active:

**1. Check HTTP Response Headers**:

```bash
curl -I https://your-keycloak.com/realms/myrealm/protocol/openid-connect/auth | grep -i content-security-policy
```

Expected output:
```
content-security-policy: script-src 'self' https://challenges.cloudflare.com; frame-src 'self' https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com;
```

**2. Browser DevTools**:

1. Open Keycloak login page
2. Open DevTools (F12) → Console tab
3. Look for CSP violations

**Should NOT see**:
```
Refused to load the script 'https://challenges.cloudflare.com/...' because it violates the following Content Security Policy directive: "script-src 'self'"
```

**3. Network Tab**:

1. Open DevTools (F12) → Network tab
2. Reload login page
3. Filter for `challenges.cloudflare.com`
4. All requests should show status 200 (not blocked)

### Common CSP Issues

**Issue**: CSP headers not appearing

**Causes**:
- Environment variables not set correctly
- Keycloak not restarted after config change
- Reverse proxy overriding headers

**Fix**:
```bash
# Verify environment variables
printenv | grep KC_SPI_CONTENT_SECURITY_POLICY

# Restart Keycloak
systemctl restart keycloak
# Or for Docker
docker-compose restart keycloak
```

**Issue**: Widget blocked despite CSP configured

**Causes**:
- Multiple CSP headers conflicting
- Strict CSP from reverse proxy
- Browser extensions blocking

**Fix**:
- Check for multiple `Content-Security-Policy` headers
- Ensure only one layer sets CSP (either Keycloak OR reverse proxy)
- Test in incognito mode without extensions

### Security Considerations

**Nonce-based CSP (Advanced)**:

For enhanced security, Cloudflare recommends using nonce-based CSP with `strict-dynamic`:

```html
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" nonce="RANDOM_NONCE"></script>
```

With CSP:
```
script-src 'nonce-RANDOM_NONCE' 'strict-dynamic'
```

This approach eliminates the need to allowlist specific domains. However, it requires:
- Generating a unique nonce per request
- Injecting the nonce into FreeMarker templates
- More complex Keycloak customization

For most deployments, domain-based CSP (as shown above) provides sufficient security.

### Full Example CSP

For a production deployment with strict security:

```
Content-Security-Policy:
  default-src 'self';
  script-src 'self' https://challenges.cloudflare.com;
  style-src 'self' 'unsafe-inline';
  img-src 'self' data:;
  font-src 'self' data:;
  frame-src 'self' https://challenges.cloudflare.com;
  connect-src 'self' https://challenges.cloudflare.com;
  frame-ancestors 'self';
  base-uri 'self';
  form-action 'self';
```

**Notes**:
- `'unsafe-inline'` in `style-src` may be required for Keycloak themes
- `data:` in `img-src` and `font-src` allows embedded images/fonts
- `frame-ancestors 'self'` prevents clickjacking
- Adjust based on your specific Keycloak theme requirements

For detailed setup instructions, see [SETUP.md](SETUP.md#4-configure-content-security-policy).

## Authenticator Configuration

All configuration is done through the Keycloak Admin Console under Authentication → Flows → [Your Flow] → Cloudflare Turnstile → Config.

### Site Key

**Type**: String
**Required**: Yes
**Example**: `0x4AAAAAAA...`

Your Cloudflare Turnstile site key (client-side key). This is the public key displayed on the login page and used to render the Turnstile widget.

**Where to find it**:
1. Go to https://dash.cloudflare.com
2. Navigate to Turnstile section
3. Select your site
4. Copy the "Site Key"

**Security Note**: This key is public and visible in browser HTML. It's safe to commit to version control.

### Secret Key

**Type**: Password (secret)
**Required**: Yes
**Example**: `0x4AAAAAAA...`

Your Cloudflare Turnstile secret key (server-side key). This is used to verify the Turnstile response token with Cloudflare's API.

**Where to find it**:
1. Go to https://dash.cloudflare.com
2. Navigate to Turnstile section
3. Select your site
4. Copy the "Secret Key"

**Security Note**: This key is stored encrypted in the Keycloak database. Never commit this to version control or share publicly.

## Widget Configuration

### Widget Mode

**Type**: Dropdown
**Required**: Yes
**Options**: managed, non-interactive, invisible
**Default**: managed

Determines how the Turnstile widget behaves and appears to users.

| Mode | Behavior | User Experience | Use Case |
|------|----------|-----------------|----------|
| **managed** | Interactive when needed | Automatic for low-risk, CAPTCHA for suspicious | Recommended for most sites - best balance |
| **non-interactive** | Non-intrusive | Very minimal user interaction | Sites prioritizing user experience |
| **invisible** | Runs in background | No visible widget | Advanced integrations, custom UI |

#### managed (Default)

```
┌─────────────────────────────┐
│  ✓ Verification complete    │
└─────────────────────────────┘
```

- Shows automatic verification for low-risk users
- Presents interactive challenge for suspicious traffic
- Most similar to traditional CAPTCHAs when needed
- **Recommended for most deployments**

**Best for**:
- General-purpose login protection
- Unknown/mixed user bases
- Balancing security and UX

#### non-interactive

```
┌─────────────────────────────┐
│  🔄 Verifying...            │
└─────────────────────────────┘
```

- Minimal user interaction required
- Runs verification check automatically
- Very low friction for users
- May show brief "verifying" message

**Best for**:
- Trusted user bases
- Internal applications
- UX-first deployments

#### invisible

```
(No visible widget)
```

- Completely invisible to users
- Verification happens in background
- Requires custom frontend integration
- Advanced use case

**Best for**:
- Custom login UI implementations
- Mobile applications
- Seamless user experiences

**Configuration Example**:
```json
{
  "widgetMode": "managed"
}
```

### Widget Theme

**Type**: Dropdown
**Required**: Yes
**Options**: light, dark, auto
**Default**: auto

Visual theme of the Turnstile widget to match your login page design.

| Theme | Appearance | Use Case |
|-------|------------|----------|
| **light** | Light background, dark text | Light-themed login pages |
| **dark** | Dark background, light text | Dark-themed login pages |
| **auto** | Matches user's OS preference | Supports both light and dark modes |

#### Theme Examples

**Light Theme**:
```
┌─────────────────────────────┐
│  ☀️ ✓ Verification complete │  <- White background
└─────────────────────────────┘
```

**Dark Theme**:
```
┌─────────────────────────────┐
│  🌙 ✓ Verification complete │  <- Dark background
└─────────────────────────────┘
```

**Auto Theme**:
- Automatically detects user's OS dark mode setting
- Switches between light and dark themes
- Best for modern responsive applications

**Recommendation**: Use `auto` for best user experience across different user preferences.

## IP Filtering

### IP Allowlist

**Type**: Multi-valued String
**Required**: No
**Default**: `10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7,fe80::/10` (private/internal ranges)
**Format**: Comma-separated IPs or CIDR ranges

List of IP addresses or CIDR ranges that should be **handled specially** during Turnstile verification. The exact behavior is controlled by the **Allowlist Behavior** setting.

**Supported Formats**:
- Single IPv4: `192.168.1.100`
- Single IPv6: `2001:db8::1`
- IPv4 CIDR: `192.168.1.0/24`
- IPv6 CIDR: `2001:db8::/32`
- Mixed: `192.168.1.0/24,10.0.0.1,2001:db8::/32`

**Examples**:

Internal office network:
```
192.168.0.0/16
```

Multiple office locations:
```
192.168.0.0/16,10.0.0.0/8,172.16.0.0/12
```

Specific trusted IPs:
```
203.0.113.10,203.0.113.11,203.0.113.12
```

Corporate VPN + office:
```
10.8.0.0/16,192.168.100.0/24
```

**How it works**:
1. User connects from IP address
2. Authenticator checks if IP matches allowlist
3. **Turnstile widget is ALWAYS shown** (for UX consistency)
4. Verification behavior is determined by **Allowlist Behavior** setting:
   - **VERIFY_BUT_ALLOW** (default): Make Cloudflare API call, log result, but always allow access (audit mode)
   - **SKIP_VERIFICATION**: Skip Cloudflare API call entirely (faster, saves API quota)
5. If no match: Normal Turnstile verification

**Use Cases**:
- Audit verification attempts from internal networks (VERIFY_BUT_ALLOW)
- Skip verification for trusted office networks (SKIP_VERIFICATION)
- Trust known VPN ranges while maintaining logs
- Allow testing environments without enforcing Turnstile
- Monitor security posture of privileged IPs

**Security Considerations**:
- ✅ Widget shown to all users maintains consistent UX
- ✅ VERIFY_BUT_ALLOW mode provides audit trail while allowing access
- ⚠️ SKIP_VERIFICATION mode bypasses all Cloudflare verification
- Ensure IP ranges are under your control
- Regularly audit allowlist entries
- Consider using MFA for additional security on allowlisted IPs

### Allowlist Behavior

**Type**: Dropdown
**Required**: Yes
**Options**: SKIP_VERIFICATION, VERIFY_BUT_ALLOW
**Default**: VERIFY_BUT_ALLOW

Controls how allowlisted IP addresses are handled during Turnstile verification. This setting works in conjunction with the **IP Allowlist** configuration.

| Mode | Cloudflare API Call | Always Allow | Audit Trail | API Quota | Use Case |
|------|-------------------|--------------|-------------|-----------|----------|
| **VERIFY_BUT_ALLOW** ⭐ | ✅ Yes | ✅ Yes | ✅ Yes | Uses quota | Audit mode - track verification attempts |
| **SKIP_VERIFICATION** | ❌ No | ✅ Yes | ⚠️ Limited | Saves quota | Performance mode - trust completely |

#### VERIFY_BUT_ALLOW (Default - Recommended)

**Behavior**:
1. User from allowlisted IP sees Turnstile widget
2. User completes challenge
3. Server makes Cloudflare API verification call
4. Server logs the verification result (success or failure)
5. **User is allowed to proceed regardless of verification result**

**Benefits**:
- ✅ **Audit trail**: See if allowlisted IPs would pass/fail verification
- ✅ **Security monitoring**: Detect compromised internal systems
- ✅ **Compliance**: Maintain logs of all authentication attempts
- ✅ **Gradual rollout**: Test verification before enforcing

**Use Cases**:
- Monitor security posture of internal networks
- Detect compromised employee devices
- Compliance requirements for audit logging
- Testing Turnstile configuration before enforcing

**Example Scenario**:
```
Office network: 192.168.1.0/24
Behavior: VERIFY_BUT_ALLOW

Employee at 192.168.1.100:
1. Sees Turnstile widget (normal UX)
2. Completes challenge
3. Cloudflare returns: "FAIL - bot detected"
4. Server logs: "192.168.1.100 - FAILED verification (but allowed due to allowlist)"
5. User logs in successfully
6. Security team reviews logs, discovers compromised device
```

**Logging**:
```
event_detail: cloudflare_turnstile_action=ip_allowlisted_verify_but_allow
event_detail: cloudflare_turnstile_result=success (or failure details)
event_detail: ip_address=192.168.1.100
```

#### SKIP_VERIFICATION

**Behavior**:
1. User from allowlisted IP sees Turnstile widget
2. User completes challenge
3. **Server skips Cloudflare API call entirely**
4. User is allowed to proceed immediately

**Benefits**:
- ✅ **Faster authentication**: No API latency
- ✅ **Saves API quota**: Reduces Cloudflare API calls
- ✅ **Offline resilience**: Works even if Cloudflare is unreachable
- ✅ **Predictable performance**: No dependency on external API

**Use Cases**:
- Fully trusted internal networks
- Testing/staging environments
- API quota conservation
- Latency-sensitive applications

**Example Scenario**:
```
Office network: 192.168.1.0/24
Behavior: SKIP_VERIFICATION

Employee at 192.168.1.100:
1. Sees Turnstile widget (normal UX)
2. Completes challenge
3. Server immediately allows login (no API call)
4. Minimal logging: "192.168.1.100 - allowlisted, skipped verification"
```

**Logging**:
```
event_detail: cloudflare_turnstile_result=ip_allowlisted_skip_verification
event_detail: ip_address=192.168.1.100
```

**Trade-offs**:
- ⚠️ No verification audit trail
- ⚠️ Can't detect compromised allowlisted IPs
- ⚠️ Fully trusts the network

**Recommendation**: Use **VERIFY_BUT_ALLOW** for most deployments to maintain audit trails. Only use **SKIP_VERIFICATION** for fully trusted, isolated networks where audit logging is not required.

### IP Blocklist

**Type**: Multi-valued String
**Required**: No
**Default**: Empty
**Format**: Comma-separated IPs or CIDR ranges

List of IP addresses or CIDR ranges that should be **immediately blocked** before Turnstile verification.

**Supported Formats**: Same as IP Allowlist (IPv4, IPv6, CIDR)

**Examples**:

Block specific malicious IPs:
```
203.0.113.50,203.0.113.51
```

Block entire ranges:
```
198.51.100.0/24
```

**How it works**:
1. User connects from IP address
2. Authenticator checks if IP matches blocklist
3. If match found: **Immediately deny**, no Turnstile shown
4. If no match: Proceed to Turnstile verification

**Use Cases**:
- Block known attack sources
- Prevent access from sanctioned countries (via CIDR)
- Blacklist abusive IPs
- Block Tor exit nodes

**Priority**: Blocklist is checked **before** allowlist. A blocked IP will not authenticate even if it's in the allowlist.

**Example Combined Configuration**:
```json
{
  "ipAllowlist": "192.168.0.0/16,10.0.0.0/8",
  "ipBlocklist": "198.51.100.0/24,203.0.113.50"
}
```

## Fail Behavior

### Fail Action

**Type**: Dropdown
**Required**: Yes
**Options**: BLOCK, ALLOW, REQUIRE_MFA
**Default**: BLOCK

Action to take when Turnstile verification **fails** (user fails the challenge or provides invalid token).

| Action | Behavior | Use Case |
|--------|----------|----------|
| **BLOCK** | Deny authentication | Production environments, security-first |
| **ALLOW** | Allow with warning log | Testing, monitoring mode |
| **REQUIRE_MFA** | Trigger MFA requirement | Balanced security approach |

#### BLOCK (Recommended for Production)

```
Turnstile Failed → ❌ Access Denied
```

- User cannot proceed with authentication
- Error message shown: "Security verification failed"
- Event logged with failure details
- **Most secure option**

**Use when**:
- Production environments
- Security is paramount
- Bot protection is critical
- Compliance requires verification

#### ALLOW (Testing/Monitoring Only)

```
Turnstile Failed → ⚠️ Warning Logged → ✓ Access Allowed
```

- User authentication proceeds normally
- Failure logged to events and database
- **Not recommended for production**

**Use when**:
- Testing Turnstile integration
- Monitoring false positive rates
- Gradual rollout/testing phase
- Analytics/data collection

**Warning**: This mode does NOT provide bot protection. Use only for testing.

#### REQUIRE_MFA

```
Turnstile Failed → 🔐 MFA Required → Continue Authentication
```

- User must complete additional MFA step
- Sets auth note: `turnstile_failed=true`
- Subsequent authenticators can read this note
- Allows conditional MFA enforcement

**Use when**:
- Balanced security approach needed
- Want to allow legitimate users while adding friction for suspicious ones
- Already have MFA configured
- Prefer defense in depth

**Setup Required**:
1. Add Turnstile authenticator to flow
2. Add MFA authenticator(s) after Turnstile
3. Configure MFA to check `turnstile_failed` auth note (optional)
4. Set Fail Action to `REQUIRE_MFA`

**Example Flow**:
```
Browser Flow:
├── Cookie (ALTERNATIVE)
├── Cloudflare Turnstile (REQUIRED) [failAction=REQUIRE_MFA]
├── Username/Password Form (REQUIRED)
└── OTP Form (CONDITIONAL) [only if turnstile_failed=true]
```

### Fail Mode

**Type**: Dropdown
**Required**: Yes
**Options**: FAIL_CLOSED, FAIL_OPEN
**Default**: FAIL_CLOSED

How to handle **errors** connecting to Cloudflare API (network errors, timeouts, API unavailable).

| Mode | Behavior | Priority |
|------|----------|----------|
| **FAIL_CLOSED** | Deny authentication on errors | Security > Availability |
| **FAIL_OPEN** | Allow authentication on errors | Availability > Security |

#### FAIL_CLOSED (Recommended)

```
Cloudflare API Error → ❌ Access Denied
```

- If Cloudflare API is unreachable: deny authentication
- If API times out: deny authentication
- If network error occurs: deny authentication
- **Most secure option**

**Use when**:
- Security is critical
- Can tolerate some downtime
- Have Cloudflare SLA in contract
- Production environments

**Advantages**:
- ✓ Cannot bypass Turnstile by causing errors
- ✓ Attackers can't DoS Cloudflare to bypass verification
- ✓ Maintains security posture during outages

**Disadvantages**:
- ✗ Users locked out if Cloudflare is down
- ✗ Network issues can prevent logins
- ✗ Requires Cloudflare to be highly available

#### FAIL_OPEN

```
Cloudflare API Error → ⚠️ Warning Logged → ✓ Access Allowed
```

- If Cloudflare API is unreachable: allow authentication with warning
- API errors logged but don't block users
- **Less secure but higher availability**

**Use when**:
- Availability is critical (e.g., emergency access)
- Cloudflare is "nice to have" not "must have"
- Cannot accept any auth service downtime
- Have other security layers

**Advantages**:
- ✓ Users never locked out by Cloudflare issues
- ✓ Cloudflare outages don't affect availability
- ✓ Network issues don't prevent logins

**Disadvantages**:
- ✗ Attackers could bypass by causing API errors
- ✗ DoS Cloudflare = bypass verification
- ✗ Reduced security posture

**Recommendation**: Use FAIL_CLOSED in production unless availability requirements absolutely demand FAIL_OPEN.

## Database Recording

### Record Verifications

**Type**: Boolean
**Required**: No
**Default**: true

Whether to store Turnstile verification results in the database for auditing and analytics.

**When Enabled** (default):
- Every verification attempt is stored in `cloudflare_turnstile_check` table
- Includes: IP, timestamp, success/failure, error codes, session info
- Available for SQL queries and reporting
- Enables historical analysis

**When Disabled**:
- No database records created
- Verifications only logged to Keycloak events
- Reduced database storage
- Less audit trail

**Storage Impact**:

Average record size: ~500 bytes

| Logins/Day | Records/Month | Storage/Month |
|------------|---------------|---------------|
| 1,000 | 30,000 | ~15 MB |
| 10,000 | 300,000 | ~150 MB |
| 100,000 | 3,000,000 | ~1.5 GB |

**Recommendation**: Keep enabled (true) for production to maintain audit trail.

**Use Cases for Disabling**:
- Extreme high-volume deployments (>1M logins/day)
- Storage constraints
- Privacy requirements (no PII storage)
- GDPR compliance with strict data minimization

**Events vs. Database**:

Both enabled (default):
```
Verification → Database Record + Keycloak Event
```

Only events (recordVerifications=false):
```
Verification → Keycloak Event only
```

## Network Configuration

### Connect Timeout (ms)

**Type**: String (numeric)
**Required**: No
**Default**: 5000 (5 seconds)
**Range**: 1000 - 30000 recommended

Maximum time to wait for **connection establishment** to Cloudflare API.

**What it controls**:
- TCP connection setup time
- DNS resolution time
- Time to first byte from Cloudflare

**Examples**:

Very fast (aggressive):
```
connectTimeout: 2000  (2 seconds)
```

Default (balanced):
```
connectTimeout: 5000  (5 seconds)
```

Patient (slow networks):
```
connectTimeout: 10000  (10 seconds)
```

**Recommendations by Network**:

| Network Type | Recommended Timeout |
|--------------|-------------------|
| Data center / Cloud | 2000-3000 ms |
| Corporate network | 5000 ms (default) |
| VPN / Slow network | 8000-10000 ms |
| Satellite / Poor network | 15000-30000 ms |

**Tuning Guide**:
1. Start with default (5000)
2. Monitor timeout errors in logs
3. If seeing frequent timeouts: increase by 2000ms
4. If no timeouts after 1 week: can decrease by 1000ms
5. Never go below 2000ms

### Read Timeout (ms)

**Type**: String (numeric)
**Required**: No
**Default**: 5000 (5 seconds)
**Range**: 2000 - 30000 recommended

Maximum time to wait for **API response** after connection is established.

**What it controls**:
- Time for Cloudflare to verify token
- Time to receive complete HTTP response
- Maximum request duration

**Examples**:

Very fast:
```
readTimeout: 3000  (3 seconds)
```

Default:
```
readTimeout: 5000  (5 seconds)
```

Patient:
```
readTimeout: 10000  (10 seconds)
```

**Typical Cloudflare Response Times**:
- P50 (median): 100-300 ms
- P95: 500-1000 ms
- P99: 1000-2000 ms

**Recommendations**:
- **Default 5000ms** is sufficient for 99.9% of requests
- Only increase if seeing timeout errors in logs
- Lower timeouts = faster failure detection
- Higher timeouts = fewer false failures

**Combined Timeout Example**:
```json
{
  "connectTimeout": "5000",
  "readTimeout": "5000"
}
```

Total maximum time: 10000ms (10 seconds) if both timeouts hit.

**When Timeouts Occur**:

FAIL_CLOSED mode:
```
Timeout → ❌ Access Denied → Event logged
```

FAIL_OPEN mode:
```
Timeout → ⚠️ Warning → ✓ Access Allowed → Event logged
```

## Advanced Scenarios

### Scenario 1: Internal Network + External Access

**Goal**: Skip Turnstile for office network, require for external access.

**Configuration**:
```json
{
  "siteKey": "your-site-key",
  "secretKey": "your-secret-key",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "ipAllowlist": "192.168.0.0/16,10.0.0.0/8",
  "failAction": "BLOCK",
  "failMode": "FAIL_CLOSED",
  "recordVerifications": "true"
}
```

**Result**:
- Office IPs (192.168.x.x, 10.x.x.x): No Turnstile
- External IPs: Turnstile required

### Scenario 2: High-Security with MFA Fallback

**Goal**: Block suspicious traffic, require MFA for Turnstile failures.

**Configuration**:
```json
{
  "siteKey": "your-site-key",
  "secretKey": "your-secret-key",
  "widgetMode": "managed",
  "failAction": "REQUIRE_MFA",
  "failMode": "FAIL_CLOSED",
  "recordVerifications": "true"
}
```

**Flow**:
```
1. User hits login page
2. Turnstile shown (managed mode)
3. If success → Continue to username/password
4. If fail → Require MFA after username/password
```

### Scenario 3: Invisible Mode for Custom UI

**Goal**: No visible Turnstile widget, custom integration.

**Configuration**:
```json
{
  "siteKey": "your-site-key",
  "secretKey": "your-secret-key",
  "widgetMode": "invisible",
  "widgetTheme": "auto",
  "failAction": "BLOCK",
  "failMode": "FAIL_CLOSED"
}
```

**Requirements**:
- Custom JavaScript to trigger invisible verification
- Must pass `cf-turnstile-response` token with form
- See Cloudflare docs for invisible mode integration

### Scenario 4: Monitoring Mode (Testing)

**Goal**: Collect data without blocking users.

**Configuration**:
```json
{
  "siteKey": "your-site-key",
  "secretKey": "your-secret-key",
  "widgetMode": "managed",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN",
  "recordVerifications": "true"
}
```

**Result**:
- All verifications recorded to database
- Failures logged but don't block
- Can analyze data before enforcing
- **Switch to BLOCK after testing phase**

### Scenario 5: Block Known Bad Networks

**Goal**: Block Tor, VPNs, known attack sources.

**Configuration**:
```json
{
  "siteKey": "your-site-key",
  "secretKey": "your-secret-key",
  "widgetMode": "managed",
  "ipBlocklist": "198.51.100.0/24,203.0.113.0/24",
  "failAction": "BLOCK",
  "failMode": "FAIL_CLOSED"
}
```

**Tor Exit Node Blocklists**:
- Obtain from https://check.torproject.org/exit-addresses
- Update blocklist periodically
- Or use Cloudflare's built-in Tor detection (automatic)

## Configuration Best Practices

### Production Checklist

✅ **Do**:
- Use `FAIL_CLOSED` mode
- Enable `recordVerifications`
- Use `managed` widget mode (balanced UX/security)
- Set reasonable timeouts (5000ms default is good)
- Use `BLOCK` fail action
- Review logs regularly
- Test allowlist/blocklist rules before deploying

❌ **Don't**:
- Use `FAIL_OPEN` unless absolutely necessary
- Use `ALLOW` fail action in production
- Add overly broad IP ranges to allowlist
- Set timeouts below 2000ms
- Disable database recording without good reason
- Forget to test configuration before going live

### Performance Tips

1. **Allowlist Internal IPs**: Reduce Cloudflare API calls
2. **Use Appropriate Timeouts**: Too low = false failures, too high = slow failures
3. **Monitor Database Size**: Clean old records if storage is concern
4. **Choose Right Widget Mode**: `invisible` is fastest, `managed` is most secure

### Security Tips

1. **Rotate Secret Keys**: Periodically regenerate in Cloudflare dashboard
2. **Audit Allowlist**: Remove stale entries monthly
3. **Monitor Failed Attempts**: High failure rates = possible attack
4. **Use FAIL_CLOSED**: Don't allow bypass via API errors
5. **Enable Recording**: Maintain audit trail for compliance

### Audit and Compliance

When **Record Verifications** is enabled, the provider maintains comprehensive audit trails in both the database and Keycloak events.

#### What Gets Audited

Every verification attempt records:
- **Configuration Snapshot** - Active fail_mode, fail_action, allowlist_behavior, and implementation_method settings
- **Decision Trail** - Whether IP was allowlisted/blocklisted, whether verification was skipped
- **Final Outcome** - Whether authentication was allowed and why
- **Full Context** - User, IP, timestamp, flow type (login vs registration)

#### Configuration Context Tracking

Each audit record captures the configuration active at the time:

| Audit Field | Configuration Setting | Values |
|-------------|----------------------|--------|
| `fail_mode` | Error Handling Mode | FAIL_OPEN, FAIL_CLOSED |
| `fail_action` | Verification Failure Action | BLOCK, ALLOW, REQUIRE_MFA |
| `allowlist_behavior` | Allowlist Behavior | SKIP_VERIFICATION, VERIFY_BUT_ALLOW |
| `implementation_method` | Implementation Method | SEPARATE_PAGE, SCRIPT_INJECTION, CUSTOM_THEME |

This enables **historical analysis** - you can see how configuration changes affected outcomes over time.

#### Audit Use Cases

**Security Monitoring**:
- Detect brute force attacks
- Identify credential stuffing attempts
- Monitor blocked access attempts
- Track allowlist/blocklist effectiveness

**Compliance Reporting**:
- SOC 2 / ISO 27001 access control evidence
- PCI DSS failed authentication tracking
- GDPR data access requests
- Configuration change audit trail

**Configuration Analysis**:
- Compare FAIL_OPEN vs FAIL_CLOSED impact during API errors
- Evaluate BLOCK vs ALLOW vs REQUIRE_MFA effectiveness
- Analyze SKIP_VERIFICATION vs VERIFY_BUT_ALLOW for allowlisted IPs
- Measure verification success rates by flow type (login vs registration)

#### Example Audit Queries

**Find configuration changes over time**:
```sql
SELECT DISTINCT
    DATE(timestamp) as date,
    fail_mode,
    fail_action,
    allowlist_behavior,
    COUNT(*) as checks_with_config
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY DATE(timestamp), fail_mode, fail_action, allowlist_behavior
ORDER BY date DESC;
```

**Analyze decision outcomes**:
```sql
SELECT
    action_reason,
    authentication_allowed,
    COUNT(*) as count
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '7 DAYS'
GROUP BY action_reason, authentication_allowed
ORDER BY count DESC;
```

**Allowlist effectiveness**:
```sql
SELECT
    allowlist_behavior,
    COUNT(*) as total_checks,
    SUM(CASE WHEN verification_skipped THEN 1 ELSE 0 END) as skipped,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as would_have_failed
FROM cloudflare_turnstile_check
WHERE ip_allowlisted = true
  AND timestamp > NOW() - INTERVAL '7 DAYS'
GROUP BY allowlist_behavior;
```

For comprehensive audit guidance, query examples, and SIEM integration, see [AUDIT.md](AUDIT.md).

### Troubleshooting Tips

If users report issues:
1. Check event logs for error details
2. Verify secret key is correct
3. Test from same network as user
4. Check timeouts aren't too aggressive
5. Verify Cloudflare API is accessible from Keycloak server

For more troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
