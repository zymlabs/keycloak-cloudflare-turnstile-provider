# Example Configurations

This directory contains example configurations for the Cloudflare Turnstile Keycloak extension.

## Files

- **browser-flow-preauth.json** - Pre-authentication flow with Turnstile (recommended)
- **turnstile-authenticator-config.json** - Example authenticator configuration

## Pre-Authentication Mode (Recommended)

### browser-flow-preauth.json

Example authentication flow that includes Turnstile **before** username/password authentication.

**Structure**:
```
Browser with Turnstile
├── Cookie (ALTERNATIVE)
├── Kerberos (DISABLED)
├── Identity Provider Redirector (ALTERNATIVE)
├── Cloudflare Turnstile (REQUIRED)          ← Verifies before login
└── Browser Forms (ALTERNATIVE)
    ├── Username Password Form (REQUIRED)
    └── Conditional OTP (CONDITIONAL)
        ├── Condition - User Configured (REQUIRED)
        └── OTP Form (REQUIRED)
```

**User Experience**:
1. User navigates to login page
2. Turnstile widget appears **first**
3. User completes Turnstile challenge
4. Only then can they enter username/password
5. Optional: MFA if configured

**Benefits**:
- Blocks bots before they can attempt credentials
- Reduces password brute-force attempts
- Less database load from bot traffic
- Better security posture

**Import Instructions**:

⚠️ **Note**: Keycloak doesn't support direct JSON import for flows via Admin Console. You have two options:

**Option 1: Manual Creation (Recommended)**

1. Log into Keycloak Admin Console
2. Navigate to **Authentication** → **Flows**
3. Click "Browser" flow → **⋮** → **Duplicate**
4. Name it "Browser with Turnstile"
5. Click **Add execution** → Select "Cloudflare Turnstile"
6. Move Turnstile execution **before** "Browser Forms" subflow
7. Set Turnstile to **REQUIRED**
8. Configure Turnstile (see below)

**Option 2: REST API Import**

Use Keycloak REST API to import the flow:

```bash
# Get admin token
TOKEN=$(curl -X POST "https://your-keycloak/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

# Import flow
curl -X POST "https://your-keycloak/admin/realms/your-realm/authentication/flows" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @browser-flow-preauth.json
```

### turnstile-authenticator-config.json

Example authenticator configuration with recommended settings.

**Before Using**:
- Replace `YOUR_SITE_KEY` with your Cloudflare Turnstile site key
- Replace `YOUR_SECRET_KEY` with your Cloudflare Turnstile secret key

**Configuration Values Explained**:

| Setting | Example Value | Description |
|---------|---------------|-------------|
| siteKey | 0x4AAA...example | Your Cloudflare Turnstile site key (public) |
| secretKey | 0x4AAA...secret | Your Cloudflare Turnstile secret key (private) |
| widgetMode | managed | Widget mode: managed, non-interactive, invisible |
| widgetTheme | auto | Theme: light, dark, auto |
| recordVerifications | true | Store results in database for auditing |
| ipAllowlist | (empty) | IPs/CIDRs to skip verification |
| ipBlocklist | (empty) | IPs/CIDRs to immediately block |
| failAction | BLOCK | Action on failure: BLOCK, ALLOW, REQUIRE_MFA |
| failMode | FAIL_CLOSED | Error handling: FAIL_CLOSED, FAIL_OPEN |
| connectTimeout | 5000 | Connection timeout in milliseconds |
| readTimeout | 5000 | Read timeout in milliseconds |

**Manual Configuration**:

1. Create your authentication flow (see above)
2. Add Cloudflare Turnstile execution
3. Click **⚙️ Settings** → Config
4. Enter the values from this file (with your actual keys)
5. Click **Save**

## Configuration Scenarios

### Scenario 1: Production with High Security

**Goal**: Maximum bot protection, strict security

```json
{
  "siteKey": "YOUR_SITE_KEY",
  "secretKey": "YOUR_SECRET_KEY",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "ipAllowlist": "",
  "ipBlocklist": "",
  "failAction": "BLOCK",
  "failMode": "FAIL_CLOSED",
  "connectTimeout": "5000",
  "readTimeout": "5000"
}
```

**Behavior**:
- ✅ All users see Turnstile
- ✅ Failures block access
- ✅ API errors block access (secure)
- ✅ All attempts logged to database

**Best for**: Public-facing applications, high-value targets

### Scenario 2: Balanced Production

**Goal**: Security with user convenience

```json
{
  "siteKey": "YOUR_SITE_KEY",
  "secretKey": "YOUR_SECRET_KEY",
  "widgetMode": "non-interactive",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "ipAllowlist": "192.168.0.0/16,10.0.0.0/8",
  "ipBlocklist": "",
  "failAction": "BLOCK",
  "failMode": "FAIL_CLOSED",
  "connectTimeout": "5000",
  "readTimeout": "5000"
}
```

**Behavior**:
- ✅ Internal office users skip Turnstile
- ✅ External users see minimal Turnstile
- ✅ Failures block access
- ✅ API errors block access

**Best for**: Corporate applications with office network access

### Scenario 3: MFA Enforcement on Suspicious Traffic

**Goal**: Require MFA when Turnstile fails

```json
{
  "siteKey": "YOUR_SITE_KEY",
  "secretKey": "YOUR_SECRET_KEY",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "ipAllowlist": "",
  "ipBlocklist": "",
  "failAction": "REQUIRE_MFA",
  "failMode": "FAIL_CLOSED",
  "connectTimeout": "5000",
  "readTimeout": "5000"
}
```

**Behavior**:
- ✅ Turnstile failures don't block, but trigger MFA
- ✅ Adds friction for suspicious users without blocking
- ✅ Legitimate users who fail can still authenticate with MFA

**Best for**: Applications with MFA already configured, risk-based access

**Required Setup**:
- MFA must be configured in authentication flow
- Add MFA execution after Turnstile
- Configure MFA to read `turnstile_failed` auth note (optional)

### Scenario 4: Testing/Development

**Goal**: Test integration without blocking users

```json
{
  "siteKey": "YOUR_SITE_KEY",
  "secretKey": "YOUR_SECRET_KEY",
  "widgetMode": "managed",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "ipAllowlist": "",
  "ipBlocklist": "",
  "failAction": "ALLOW",
  "failMode": "FAIL_OPEN",
  "connectTimeout": "5000",
  "readTimeout": "5000"
}
```

**Behavior**:
- ✅ All verifications logged
- ⚠️ Failures logged but **don't block**
- ⚠️ API errors allow access
- ⚠️ **NOT SECURE** - for testing only!

**Use when**:
- Initial setup and testing
- Collecting baseline data
- Measuring false positive rates
- **NEVER use in production!**

**After Testing**:
Change to `failAction: BLOCK` and `failMode: FAIL_CLOSED`

### Scenario 5: Internal Application (Minimal Friction)

**Goal**: Very low user friction, trust internal network

```json
{
  "siteKey": "YOUR_SITE_KEY",
  "secretKey": "YOUR_SECRET_KEY",
  "widgetMode": "invisible",
  "widgetTheme": "auto",
  "recordVerifications": "true",
  "ipAllowlist": "10.0.0.0/8",
  "ipBlocklist": "",
  "failAction": "BLOCK",
  "failMode": "FAIL_OPEN",
  "connectTimeout": "5000",
  "readTimeout": "5000"
}
```

**Behavior**:
- ✅ Office users skip completely
- ✅ External users see invisible verification
- ✅ High availability (FAIL_OPEN)
- ⚠️ Slightly less secure

**Best for**: Internal tools, low-sensitivity applications

## Network-Specific Configurations

### High Latency Networks

For users on slow networks (VPN, satellite, etc.):

```json
{
  "connectTimeout": "10000",
  "readTimeout": "10000",
  "failMode": "FAIL_OPEN"
}
```

Increases timeouts and uses FAIL_OPEN to prevent lockouts from network issues.

### Multiple Office Locations

```json
{
  "ipAllowlist": "203.0.113.0/24,198.51.100.0/24,2001:db8::/32"
}
```

Add all office networks to skip Turnstile for internal users.

### Blocking Known Bad Networks

```json
{
  "ipBlocklist": "198.51.100.50/32,203.0.113.0/24"
}
```

Block specific IPs or ranges known for abuse.

## Migrating Configurations

### From Testing to Production

1. **Change fail behavior**:
   ```
   failAction: ALLOW → BLOCK
   failMode: FAIL_OPEN → FAIL_CLOSED
   ```

2. **Enable stricter widget mode** (optional):
   ```
   widgetMode: non-interactive → managed
   ```

3. **Keep recording enabled**:
   ```
   recordVerifications: true
   ```

4. **Review allowlists**:
   - Remove test IPs
   - Add only trusted production networks

### Adding IP Filtering

Start without, then add based on analytics:

1. **Collect data** (2-4 weeks)
2. **Analyze** office IPs:
   ```sql
   SELECT ip_address, COUNT(*) as login_count
   FROM cloudflare_turnstile_check
   WHERE success = true
   GROUP BY ip_address
   ORDER BY login_count DESC
   LIMIT 20;
   ```

3. **Add trusted ranges** to allowlist
4. **Monitor** for issues

## Troubleshooting Configurations

### All Verifications Failing

**Check**:
1. Secret key is correct (common mistake)
2. Keycloak can reach `challenges.cloudflare.com`
3. Check logs for specific error codes

### Widget Not Appearing

**Check**:
1. Site key is correct
2. HTTPS enabled (Turnstile requires it)
3. Browser can reach Cloudflare
4. Flow is active (bound to realm)

### Users Getting Blocked Incorrectly

**Quick Fix**:
1. Temporarily set `failAction: ALLOW`
2. Investigate via database/events
3. Fix root cause (wrong keys, network issues, etc.)
4. Set back to `BLOCK`

## Additional Resources

- **Full Documentation**: See `/docs` directory
- **Configuration Reference**: [CONFIGURATION.md](../docs/CONFIGURATION.md)
- **Setup Guide**: [SETUP.md](../docs/SETUP.md)
- **Troubleshooting**: [TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md)
- **Database Queries**: [DATABASE.md](../docs/DATABASE.md)

## Support

For issues or questions:
- **GitHub Issues**: https://github.com/zymlabs/keycloak-cloudflare-turnstile/issues
- **Documentation**: `/docs` directory
- **Cloudflare Docs**: https://developers.cloudflare.com/turnstile/
