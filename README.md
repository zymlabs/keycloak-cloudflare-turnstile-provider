# Keycloak Cloudflare Turnstile Provider

A Keycloak authentication provider that integrates Cloudflare Turnstile CAPTCHA verification into the authentication flow. This extension adds bot protection and security verification to your Keycloak login process.

## Features

### Login Protection

**Four implementation options** with different trade-offs:

| Option | Approach | Theme Required | JavaScript | Maintenance | When to Use |
|--------|----------|----------------|------------|-------------|-------------|
| **1. Separate Page** ⭐ | Standalone verification page | ❌ No | ❌ No | ✅ Low | **Recommended** - Maximum security, works everywhere |
| **2. Script Injection** | JavaScript DOM manipulation | ❌ No | ✅ Yes | ✅ Low | Modern browsers, seamless inline UX |
| **3. Copied Template** | Bundled template in JAR | ❌ No | ❌ No | ⚠️ Medium | Native integration, comfortable with template maintenance |
| **4. Custom Theme** | Standard Keycloak theme | ✅ Yes | ❌ No | ⚠️ Medium | Standard theme approach, single-realm deployments |

**Key Features:**
- **Flexible deployment** - Options 1-3 work without custom themes; Option 4 uses standard theme approach
- **Same configuration** - All options reuse the same settings
- **Server-side validation** - All options enforce verification on the server

### Registration Protection

**Four implementation options** with different trade-offs:

| Option | Approach | Theme Required | JavaScript | Maintenance | When to Use |
|--------|----------|----------------|------------|-------------|-------------|
| **1. Separate Page** ⭐ | Standalone verification page | ❌ No | ❌ No | ✅ Low | **Recommended** - Maximum security, works everywhere |
| **2. Script Injection** | JavaScript DOM manipulation | ❌ No | ✅ Yes | ✅ Low | Modern browsers, seamless inline UX |
| **3. Copied Template** | Bundled template in JAR | ❌ No | ❌ No | ⚠️ Medium | Native integration, comfortable with template maintenance |
| **4. Custom Theme** | Standard Keycloak theme | ✅ Yes | ❌ No | ⚠️ Medium | Standard theme approach, single-realm deployments |

**Key Features:**
- **Flexible deployment** - Options 1-3 work without custom themes; Option 4 uses standard theme approach
- **Same configuration** - All options reuse login flow settings
- **Server-side validation** - All options enforce verification on the server

### Widget Customization
- **Multiple widget modes** - Managed, non-interactive, or invisible challenges
- **Configurable themes** - Light, dark, or auto theme matching

### Network & Security
- **IP allowlist/blocklist** - Skip or block verification based on IP/CIDR ranges (IPv4 and IPv6 support)
- **Flexible failure handling** - Block, allow, or require MFA when verification fails
- **Fail-safe modes** - Configure FAIL_OPEN or FAIL_CLOSED behavior for API errors

### Monitoring & Compliance
- **Audit logging** - Optional database storage of all verification attempts
- **Event integration** - Logs to Keycloak events for monitoring and analytics
- **Analytics queries** - Pre-built SQL queries for security analysis
- **Customizable timeouts** - Configure connection and read timeouts for Cloudflare API calls

## Requirements

- Keycloak 24.0.0 or later
- Java 17 or later
- Cloudflare Turnstile site key and secret key (get them at https://dash.cloudflare.com/?to=/:account/turnstile)

## Installation

### 1. Download the Provider

Download the latest `zymlabs-cloudflare-turnstile-provider.jar` from the [releases page](https://github.com/zymlabs/keycloak-cloudflare-turnstile/releases).

### 2. Deploy to Keycloak

#### Keycloak Quarkus (v17+)

```bash
# Copy the JAR to the providers directory
cp zymlabs-cloudflare-turnstile-provider.jar /opt/keycloak/providers/

# Rebuild Keycloak
/opt/keycloak/bin/kc.sh build

# Restart Keycloak
/opt/keycloak/bin/kc.sh start
```

#### Docker

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:24.0.0
    volumes:
      - ./zymlabs-cloudflare-turnstile-provider.jar:/opt/keycloak/providers/zymlabs-cloudflare-turnstile-provider.jar:rw
    command:
      - start-dev
```

### 3. Verify Installation

Check the Keycloak logs for:

```
INFO  [com.zymlabs.keycloak.cloudflare.turnstileprovider.CloudflareTurnstileJpaEntityProviderFactory]
Initializing CloudflareTurnstileJpaEntityProviderFactory
```

The database table `cloudflare_turnstile_check` will be created automatically via Liquibase.

## Configuration

### 1. Get Cloudflare Turnstile Keys

1. Go to https://dash.cloudflare.com
2. Navigate to Turnstile section
3. Create a new site
4. Copy the **Site Key** and **Secret Key**

### 2. Configure Authentication Flow

1. In Keycloak Admin Console, go to **Authentication** → **Flows**
2. Create a new flow or copy the existing **Browser** flow
3. Add the **Cloudflare Turnstile** execution
4. Click **Actions** → **Config** to configure the authenticator

### 3. Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| **Site Key** | String | Required | Your Cloudflare Turnstile site key (client-side) |
| **Secret Key** | Password | Required | Your Cloudflare Turnstile secret key (server-side) |
| **Widget Mode** | List | `managed` | Widget interaction mode: `managed`, `non-interactive`, or `invisible` |
| **Widget Theme** | List | `auto` | Visual theme: `light`, `dark`, or `auto` |
| **Record Verifications** | Boolean | `true` | Store verification results in database for auditing |
| **IP Allowlist** | Multi-valued | Empty | IPs/CIDRs to skip verification (e.g., `192.168.1.0/24,10.0.0.1`) |
| **IP Blocklist** | Multi-valued | Empty | IPs/CIDRs to immediately block (e.g., `1.2.3.4/32`) |
| **Fail Action** | List | `BLOCK` | Action on verification failure: `BLOCK`, `ALLOW`, or `REQUIRE_MFA` |
| **Fail Mode** | List | `FAIL_CLOSED` | Behavior on API errors: `FAIL_CLOSED` (block) or `FAIL_OPEN` (allow) |
| **Connect Timeout** | String | `5000` | Connection timeout in milliseconds |
| **Read Timeout** | String | `5000` | Read timeout in milliseconds |

### Widget Modes

- **managed** (default) - Shows interactive challenge when needed, automatic verification for low-risk traffic
- **non-interactive** - Non-intrusive challenge, best for most sites
- **invisible** - No visible widget, runs in background (requires custom integration)

### Fail Actions

- **BLOCK** (default) - Deny authentication when Turnstile verification fails
- **ALLOW** - Log the failure but allow authentication to proceed (monitoring mode)
- **REQUIRE_MFA** - Trigger additional MFA requirements when verification fails

### Fail Modes

- **FAIL_CLOSED** (default) - Block authentication if Cloudflare API is unreachable (secure)
- **FAIL_OPEN** - Allow authentication if Cloudflare API is unreachable (availability over security)

## Usage Examples

### Example 1: Login Protection

You can choose between four implementation approaches:

**Option 1: Separate Verification Page (Recommended)**

1. Copy the "Browser" flow
2. Add **"Cloudflare Turnstile"** execution at the beginning
3. Configure with your site key and secret key
4. Set as REQUIRED
5. Bind the flow to your realm's "Browser Flow"

**User Experience:**
- User visits login page
- Turnstile verification page appears
- After verification, user sees username/password form

**Option 2: Inline Widget on Login Form (Script Injection)**

1. Copy the "Browser" flow
2. Expand **"Username Password Form"** or **"Browser - Conditional OTP"** subflow
3. Click **Add execution** within the subflow
4. Select **"Cloudflare Turnstile - Login (Script Injection)"**
5. Set as REQUIRED
6. Configure with same settings

**User Experience:**
- User sees login form with Turnstile widget embedded inline via JavaScript
- Widget appears before the login button
- User completes widget and submits credentials

**Option 3: Inline Widget (Copied Template)**

1. Copy the "Browser" flow
2. Expand **"Username Password Form"** or **"Browser - Conditional OTP"** subflow
3. Click **Add execution** within the subflow
4. Select **"Cloudflare Turnstile - Login (Copied Template)"**
5. Set as REQUIRED
6. Configure with same settings

**User Experience:**
- User sees login form with Turnstile widget natively embedded
- Widget appears before the login button
- User completes widget and submits credentials

**Option 4: Inline Widget (Custom Theme)**

1. Go to **Realm Settings** → **Themes**
2. Under **Login Theme**, select **cloudflare-turnstile**
3. Click **Save**
4. Copy the "Browser" flow
5. Expand **"Username Password Form"** or **"Browser - Conditional OTP"** subflow
6. Click **Add execution** within the subflow
7. Select **"Cloudflare Turnstile - Login (Custom Theme)"**
8. Set as REQUIRED
9. Configure with same settings

**User Experience:**
- User sees login form with Turnstile widget natively embedded (via theme)
- Widget appears before the login button
- User completes widget and submits credentials

**See [docs/SETUP.md](docs/SETUP.md) for detailed setup instructions.**

### Example 2: Registration Protection

You can choose between four implementation approaches:

**Option 1: Separate Verification Page (Recommended)**

1. Go to **Authentication** → **Flows** → **Registration**
2. Click **Add execution**
3. Select **"Cloudflare Turnstile (Registration)"**
4. Position it **before** "Registration Page Form"
5. Set as REQUIRED
6. Configure with same settings as login flow

**User Experience:**
- User clicks "Register" link
- Turnstile verification page appears
- After verification, user proceeds to registration form

**Option 2: Inline Widget (Script Injection)**

1. Go to **Authentication** → **Flows** → **Registration**
2. Expand **"Registration form"** subflow
3. Click **Add execution** within the subflow
4. Select **"Cloudflare Turnstile (Script Injection)"**
5. Set as REQUIRED
6. Configure with same settings as login flow

**User Experience:**
- User clicks "Register" link
- Registration form appears with Turnstile widget embedded inline via JavaScript
- User completes form and Turnstile challenge
- User submits registration

**Option 3: Copied Template (Native Integration)**

1. Go to **Authentication** → **Flows** → **Registration**
2. Expand **"Registration form"** subflow
3. Click **Add execution** within the subflow
4. Select **"Cloudflare Turnstile (Copied Template)"**
5. Set as REQUIRED
6. Configure with same settings as login flow

**User Experience:**
- User clicks "Register" link
- Registration form appears with Turnstile widget natively embedded
- User completes form and Turnstile challenge
- User submits registration

**Option 4: Custom Theme (Standard Keycloak Approach)**

1. Go to **Realm Settings** → **Themes**
2. Under **Login Theme**, select **cloudflare-turnstile**
3. Click **Save**
4. Go to **Authentication** → **Flows** → **Registration**
5. Expand **"Registration form"** subflow
6. Click **Add execution** within the subflow
7. Select **"Cloudflare Turnstile (Custom Theme)"**
8. Set as REQUIRED
9. Configure with same settings as login flow

**User Experience:**
- User clicks "Register" link
- Registration form appears with Turnstile widget natively embedded (via theme)
- User completes form and Turnstile challenge
- User submits registration

**See [docs/REGISTRATION.md](docs/REGISTRATION.md) for complete registration guide.**

### Example 3: IP Allowlist for Internal Networks

Configure IP Allowlist:
```
192.168.0.0/16,10.0.0.0/8,172.16.0.0/12
```

Users from these internal networks will skip Turnstile verification entirely.

### Example 4: Suspicious IP Blocklist

Configure IP Blocklist:
```
1.2.3.4,5.6.7.0/24
```

These IPs will be immediately blocked before any verification attempt.

### Example 5: MFA Enforcement on Failure

Set **Fail Action** to `REQUIRE_MFA` and add an MFA execution after the Turnstile step. When Turnstile verification fails, users will be required to complete MFA.

## Database Schema

When **Record Verifications** is enabled, verification attempts are stored in the `cloudflare_turnstile_check` table:

```sql
CREATE TABLE cloudflare_turnstile_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(36),
    realm_id VARCHAR(36) NOT NULL,
    username VARCHAR(255),
    email VARCHAR(255),
    ip_address VARCHAR(45) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL,
    error_codes VARCHAR(500),
    challenge_ts VARCHAR(50),
    hostname VARCHAR(255),
    event_id VARCHAR(36),
    session_id VARCHAR(36),
    raw_response TEXT
);
```

### Useful Queries

Find failed verifications in the last 24 hours:
```sql
SELECT * FROM cloudflare_turnstile_check
WHERE success = false
  AND timestamp > NOW() - INTERVAL 24 HOUR
ORDER BY timestamp DESC;
```

Count verifications by user:
```sql
SELECT user_id, username, COUNT(*) as verification_count,
       SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as success_count
FROM cloudflare_turnstile_check
WHERE user_id IS NOT NULL
GROUP BY user_id, username
ORDER BY verification_count DESC;
```

Find most blocked IPs:
```sql
SELECT ip_address, COUNT(*) as failed_count
FROM cloudflare_turnstile_check
WHERE success = false
GROUP BY ip_address
ORDER BY failed_count DESC
LIMIT 10;
```

## Event Logging

The authenticator logs the following event details:

- `cloudflare_turnstile_success` - Whether verification succeeded (true/false)
- `cloudflare_turnstile_hostname` - Hostname from Cloudflare response
- `cloudflare_turnstile_errors` - Error codes if verification failed
- `cloudflare_turnstile_action` - Action taken (blocked, allowed, mfa_required)
- `cloudflare_turnstile_result` - Result descriptor (e.g., blocked_ip, verification_error)
- `ip_address` - User's IP address

These events can be viewed in:
- Keycloak Admin Console → Events → Login Events
- External event listeners (e.g., sent to SIEM systems)

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/zymlabs/keycloak-cloudflare-turnstile.git
cd keycloak-cloudflare-turnstile

# Build with Maven
mvn clean package

# The JAR will be in target/zymlabs-cloudflare-turnstile-provider.jar
```

### Running Tests

```bash
mvn test
```

### Local Development with Docker

```bash
# Start Keycloak with PostgreSQL
docker-compose up -d

# Keycloak will be available at http://localhost:8080
# Admin credentials: admin / admin
```

## Troubleshooting

### Turnstile widget not appearing

- Check browser console for JavaScript errors
- Verify the site key is correct
- Ensure `https://challenges.cloudflare.com` is accessible

### Verification always failing

- Verify the secret key is correct
- Check Keycloak logs for error messages
- Ensure Keycloak can reach `https://challenges.cloudflare.com`
- Check firewall rules and proxy settings

### Database table not created

- Check Keycloak logs for Liquibase errors
- Verify database permissions
- Ensure the JPA entity provider is registered correctly

### API timeouts

- Increase **Connect Timeout** and **Read Timeout** values
- Check network connectivity to Cloudflare
- Consider using **FAIL_OPEN** mode for critical systems

## Security Considerations

- **Never expose your Secret Key** - It's encrypted in Keycloak's database
- **Use FAIL_CLOSED in production** - Prevents bypassing Turnstile during outages
- **Review IP allowlists carefully** - Ensure they don't create security holes
- **Monitor failed verifications** - High failure rates may indicate attacks
- **Use HTTPS** - Required for Turnstile to function properly

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

- **Issues**: https://github.com/zymlabs/keycloak-cloudflare-turnstile/issues
- **Documentation**: See the `docs/` directory for detailed guides

## Credits

Developed by ZymLabs. Cloudflare Turnstile is a trademark of Cloudflare, Inc.
