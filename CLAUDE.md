# Keycloak Cloudflare Turnstile Provider - AI Assistant Context

This file provides context for AI assistants working with this codebase.

## Project Overview

A Keycloak authentication provider that integrates Cloudflare Turnstile CAPTCHA verification. The extension adds bot protection to Keycloak login flows by displaying a Turnstile widget before authentication and verifying the response with Cloudflare's API.

## Architecture

### Package Structure

```
com.zymlabs.keycloak.cloudflare.turnstileprovider/
├── CloudflareTurnstileAuthenticator.java           - Main authenticator logic
├── CloudflareTurnstileAuthenticatorFactory.java    - Factory + configuration UI
├── CloudflareTurnstileService.java                 - Cloudflare API client
├── CloudflareTurnstileCheckEntity.java             - JPA entity for audit logs
├── CloudflareTurnstileJpaEntityProvider.java       - JPA provider
├── CloudflareTurnstileJpaEntityProviderFactory.java - JPA factory
└── IpAddressUtils.java                             - IP/CIDR matching utilities
```

### Key Design Patterns

1. **Singleton Authenticator**: Factory returns a singleton authenticator instance (stateless)
2. **SPI Integration**: Uses Keycloak SPI for `AuthenticatorFactory` and `JpaEntityProviderFactory`
3. **Configuration via Factory**: All config properties defined in `AuthenticatorFactory.getConfigProperties()`
4. **Pre-authentication Mode**: `requiresUser()` returns false - runs before credentials
5. **Optional Database Persistence**: Configurable via `recordVerifications` setting

### Authentication Flow

1. User accesses login page
2. Authenticator checks IP allowlist/blocklist
3. If not bypassed, displays Turnstile widget (via FreeMarker template)
4. User completes challenge, form submits with `cf-turnstile-response` token
5. `action()` method verifies token with Cloudflare API
6. Based on result and configuration, either:
   - Success: `context.success()`
   - Failure + BLOCK: `context.failure()`
   - Failure + ALLOW: `context.success()` with warning
   - Failure + REQUIRE_MFA: `context.success()` with auth note

### Configuration Keys

All configuration is stored in Keycloak's database and accessed via `AuthenticatorConfigModel`:

- `siteKey` - Cloudflare site key (client-side)
- `secretKey` - Cloudflare secret key (server-side, encrypted)
- `widgetMode` - managed | non-interactive | invisible
- `widgetTheme` - light | dark | auto
- `recordVerifications` - boolean, store in database
- `ipAllowlist` - comma-separated IPs/CIDRs to skip verification
- `ipBlocklist` - comma-separated IPs/CIDRs to block immediately
- `failAction` - BLOCK | ALLOW | REQUIRE_MFA
- `failMode` - FAIL_CLOSED | FAIL_OPEN
- `connectTimeout` - milliseconds
- `readTimeout` - milliseconds

### Database Schema

Table: `cloudflare_turnstile_check`

Stores verification attempts when `recordVerifications` is enabled. Includes:
- User context (user_id, username, email, realm_id)
- Request data (ip_address, timestamp)
- Verification result (success, error_codes, challenge_ts, hostname)
- Correlation (event_id, session_id)

Indexes on: user_id, realm_id, timestamp, (user_id, timestamp), event_id, session_id

### Cloudflare API Integration

**Endpoint**: POST https://challenges.cloudflare.com/turnstile/v0/siteverify

**Request**:
```
secret=<secret_key>
response=<token_from_widget>
remoteip=<user_ip> (optional)
```

**Response**:
```json
{
  "success": true/false,
  "error-codes": ["timeout-or-duplicate", ...],
  "challenge_ts": "2024-01-01T00:00:00Z",
  "hostname": "example.com"
}
```

### IP Address Utilities

`IpAddressUtils` supports:
- IPv4 and IPv6 addresses
- CIDR notation (e.g., `192.168.1.0/24`, `2001:db8::/32`)
- Comma-separated lists
- Validation with helpful error messages

## Common Tasks

### Adding a New Configuration Option

1. Add constant in `CloudflareTurnstileAuthenticator`:
   ```java
   public static final String CONFIG_NEW_OPTION = "newOption";
   ```

2. Add to `CloudflareTurnstileAuthenticatorFactory.getConfigProperties()`:
   ```java
   .property()
       .name(CloudflareTurnstileAuthenticator.CONFIG_NEW_OPTION)
       .label("New Option")
       .helpText("Description")
       .type(ProviderConfigProperty.STRING_TYPE)
       .defaultValue("default")
       .add()
   ```

3. Use in authenticator:
   ```java
   String value = configMap.getOrDefault(CONFIG_NEW_OPTION, "default");
   ```

### Modifying the Widget

Edit `src/main/resources/theme-resources/templates/turnstile-form.ftl`

Template variables available:
- `${turnstileSiteKey}` - Site key from config
- `${turnstileMode}` - Widget mode
- `${turnstileTheme}` - Theme
- `${url.loginAction}` - Form submission URL
- `${message}` - Error/info messages

### Adding Event Details

In authenticator:
```java
context.getEvent()
    .detail("custom_key", "value")
    .success(); // or .error(Errors.SOME_ERROR)
```

### Database Queries

Named queries defined in `@NamedQueries` on entity:
- `findByUserId`
- `findByRealmId`
- `findByUserIdAndDateRange`
- `findFailedByRealm`

Access via EntityManager:
```java
EntityManager em = context.getSession()
    .getProvider(JpaConnectionProvider.class)
    .getEntityManager();

List<CloudflareTurnstileCheckEntity> results = em
    .createNamedQuery("findByUserId", CloudflareTurnstileCheckEntity.class)
    .setParameter("userId", userId)
    .getResultList();
```

## Build System

**Maven**: Uses shade plugin to create fat JAR with dependencies

**Included dependencies** (bundled in JAR):
- httpclient
- jackson-databind

**Provided dependencies** (from Keycloak):
- keycloak-core
- keycloak-server-spi
- keycloak-services
- hibernate-core

**Output**: `target/zymlabs-cloudflare-turnstile-provider.jar`

## Testing Approach

- Unit tests for logic (IpAddressUtils, Service, Entity)
- Mock-based tests for authenticator (future)
- Integration tests with Docker Compose

Test framework: JUnit 5 + AssertJ + Mockito

## Deployment

1. Copy JAR to `/opt/keycloak/providers/`
2. Run `kc.sh build` (Quarkus Keycloak)
3. Restart Keycloak
4. Database table created automatically via Liquibase

## Security Notes

- Secret key stored encrypted in Keycloak database
- IP blocklist checked before any processing
- FAIL_CLOSED prevents bypass during outages
- All verification attempts logged to events
- Optional database audit trail

## Future Enhancements

Potential improvements:
- Admin REST API for querying verification history
- Grafana dashboard integration
- Rate limiting per IP
- Custom widget styling options
- Support for Turnstile callbacks
- MFA enforcer authenticator (separate component)
