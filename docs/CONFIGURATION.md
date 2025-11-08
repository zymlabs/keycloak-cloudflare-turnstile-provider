# Configuration Reference

Complete reference for all configuration options in the Cloudflare Turnstile Keycloak extension.

## Table of Contents

1. [Authenticator Configuration](#authenticator-configuration)
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
**Default**: Empty
**Format**: Comma-separated IPs or CIDR ranges

List of IP addresses or CIDR ranges that should **skip Turnstile verification entirely**.

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
3. If match found: Skip Turnstile, allow authentication
4. If no match: Show Turnstile widget

**Use Cases**:
- Skip verification for internal office networks
- Trust known VPN ranges
- Allow testing environments without Turnstile
- Whitelist specific admin IPs

**Security Considerations**:
- ⚠️ Allowlisted IPs bypass ALL Turnstile verification
- Ensure IP ranges are under your control
- Regularly audit allowlist entries
- Consider using MFA for allowlisted IPs instead

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

### Troubleshooting Tips

If users report issues:
1. Check event logs for error details
2. Verify secret key is correct
3. Test from same network as user
4. Check timeouts aren't too aggressive
5. Verify Cloudflare API is accessible from Keycloak server

For more troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
