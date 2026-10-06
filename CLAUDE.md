# Keycloak Cloudflare Turnstile Provider - AI Assistant Context

This file provides context for AI assistants working with this codebase.

## Project Overview

A Keycloak authentication provider that integrates Cloudflare Turnstile CAPTCHA verification. The extension adds bot protection to Keycloak login flows by displaying a Turnstile widget before authentication and verifying the response with Cloudflare's API.

## Architecture

### Package Structure

```
com.zymlabs.keycloak.cloudflare.turnstileprovider/
├── CloudflareTurnstileAuthenticator.java           - Main login authenticator logic
├── CloudflareTurnstileAuthenticatorFactory.java    - Factory + configuration UI
├── CloudflareTurnstileFormAction.java              - Registration form action
├── CloudflareTurnstileFormActionFactory.java       - Registration factory + config
├── CloudflareTurnstileHelper.java                  - Shared event/database utilities
├── CloudflareTurnstileService.java                 - Cloudflare API client
├── CloudflareTurnstileValidator.java               - Token validation logic
├── CloudflareTurnstileCheckEntity.java             - JPA entity for audit logs
├── CloudflareTurnstileJpaEntityProvider.java       - JPA provider
├── CloudflareTurnstileJpaEntityProviderFactory.java - JPA factory
├── CloudflareTurnstileRealmSettings.java           - Realm-wide settings + effective settings of a step
├── CloudflareTurnstileRealmSettingsTab.java        - "Cloudflare Turnstile" tab in Realm settings (declarative UI)
└── IpAddressUtils.java                             - IP/CIDR matching utilities
```

### Key Design Patterns

1. **Singleton Authenticator**: Factory returns a singleton authenticator instance (stateless)
2. **SPI Integration**: Uses Keycloak SPI for `AuthenticatorFactory` and `JpaEntityProviderFactory`
3. **Configuration via Factory**: All config properties defined in `AuthenticatorFactory.getConfigProperties()`
4. **No user required**: `requiresUser()` returns false - runs before credentials (separate page), or is the credentials form itself (inline)
5. **Optional Database Persistence**: Configurable via `recordVerifications` setting

### Authentication Flow

1. User accesses login page
2. Authenticator checks IP blocklist (immediate block if matched)
3. Authenticator checks IP allowlist (behavior determined by `allowlistBehavior` config)
4. Displays Turnstile widget (via FreeMarker template) - shown to all users including allowlisted IPs for UX consistency
5. User completes challenge, form submits with `cf-turnstile-response` token
6. `action()` method processes based on IP allowlist status:
   - If allowlisted + SKIP_VERIFICATION: Bypass Cloudflare API call, allow immediately
   - If allowlisted + VERIFY_BUT_ALLOW: Make Cloudflare API call (for auditing), but allow regardless of result
   - Otherwise: Verify token with Cloudflare API
7. Based on result and configuration, either:
   - Success: `context.success()`
   - Failure + BLOCK: `context.failure()`
   - Failure + ALLOW: continue with a warning (inline, Keycloak's form continues once the password is right)
   - Failure + REQUIRE_MFA: `context.success()` with auth note `turnstile_failed` (`FAILED_NOTE`); BLOCK in the reset flow
   - Event errors for failed checks and Cloudflare errors are `turnstile_verification_failed` and
     `turnstile_verification_error`, kept apart from wrong passwords

### Flows

The authenticator knows its flow from `context.getFlowPath()` (`authenticate`, `registration`,
`reset-credentials`):

- **Sign-in:** inline, this step stands in for Keycloak's username/password form and delegates to
  Keycloak's own step (`keycloakStep()`, provider `auth-username-password-form`): it builds the page
  (login_hint, remembered username, preset user, passkeys) and, once Turnstile lets an attempt
  through, takes the submission (`submittedToKeycloakForm()` returns whether Keycloak accepted it;
  only then is the attempt audited as allowed). Passkey submissions (`isPasskeySubmission()`:
  WebAuthn fields, empty username and password) skip the Turnstile check, through a context whose
  refusals don't count toward brute-force lockout (`withoutLockoutCounting()`). The class still
  extends `AbstractUsernameFormAuthenticator`: inline, that marks it as a form keeping to an account
  set earlier in the flow, which other extensions (e.g. an IP throttle's known-device path) rely on.
  Its inherited form helpers are deliberately unused (they would skip the widget).
- **Refusals** (`refuse()`) use `forceChallenge`, never `failure`/`failureChallenge`: Keycloak counts
  those toward brute-force lockout, and a Turnstile refusal is no wrong password. The step's
  reference category is `password`, because Keycloak 26.5+ counts failed sign-ins only for steps of
  the password, OTP and recovery-code categories.
- **Registration:** always the separate page; inline widgets come from `CloudflareTurnstileFormAction`.
- **Reset password:** the separate page goes before Keycloak's Choose User step. Inline, this step
  replaces Choose User and delegates to Keycloak's provider for it (so its account lookup,
  anti-enumeration and page-skipping stay Keycloak's). Both delegations go through a context whose
  `form()` adds the widget to the page Keycloak's step builds (`withWidgetOnItsPage()`). Scripts must never be added to the request's shared form
  provider up front: Keycloak renders the next flow's page in the same response (e.g. the sign-in
  page after the reset email), which would get them too. The separate page is skipped when an
  emailed reset link identified the user.

### Realm settings

`CloudflareTurnstileRealmSettings.effective()` merges the realm tab with a step's configuration. A
step with `useRealmSettings=true` (the admin console's default for new steps, because the console
saves every field's default) takes everything but its implementation method from the realm. Without
it (older configurations, realm JSON), the step's non-empty values win.

### Configuration Keys

All configuration is stored in Keycloak's database and accessed via `AuthenticatorConfigModel`:

- `siteKey` - Cloudflare site key (client-side)
- `secretKey` - Cloudflare secret key (server-side; stored as entered unless it is a vault reference)
- `implementationMethod` - SEPARATE_PAGE | SCRIPT_INJECTION | CUSTOM_THEME (UI label: "Implementation Method")
- `widgetMode` - managed | non-interactive | invisible
- `widgetTheme` - light | dark | auto
- `recordVerifications` - boolean, store in database
- `ipAllowlist` - comma-separated IPs/CIDRs to handle specially (see `allowlistBehavior`)
- `allowlistBehavior` - SKIP_VERIFICATION | VERIFY_BUT_ALLOW (default) - Controls how allowlisted IPs are processed
  - SKIP_VERIFICATION: Bypass Cloudflare API call entirely (faster, saves API quota)
  - VERIFY_BUT_ALLOW: Make API call for auditing, but always allow access (audit mode)
- `ipBlocklist` - comma-separated IPs/CIDRs to block immediately
- `failAction` - BLOCK | ALLOW | REQUIRE_MFA (UI label: "Verification Failure Action")
- `failMode` - FAIL_CLOSED | FAIL_OPEN (UI label: "Error Handling Mode")
- `connectTimeout` - milliseconds
- `readTimeout` - milliseconds
- `enableDebugLogging` - boolean, enable JavaScript console logging

### Database Schema

Table: `cloudflare_turnstile_check`

Stores verification attempts when `recordVerifications` is enabled. Includes:

**User Context:**
- user_id, username, email, realm_id

**Request Data:**
- ip_address, timestamp, flow_type

**Verification Result:**
- success, error_codes, challenge_ts, hostname, raw_response

**Configuration Context** (added v1.1):
- fail_mode, fail_action, allowlist_behavior, implementation_method

**IP Processing Status** (added v1.1):
- ip_allowlisted, ip_blocklisted, verification_skipped

**Final Outcome** (added v1.1):
- authentication_allowed, action_reason

**Correlation:**
- event_id, session_id

**Indexes:** 15 indexes optimize queries on user_id, realm_id, timestamp, event_id, session_id, flow_type, authentication_allowed, fail_mode, fail_action, ip_allowlisted, verification_skipped, action_reason, and composite outcome analysis.

**Named Queries:**
- `findByUserId` - User verification history
- `findByRealmId` - Realm-wide verifications
- `findByUserIdAndDateRange` - Time-bounded user history
- `findFailedByRealm` - Failed verifications by realm
- `findByFlowType` - Verifications by flow (login vs registration)

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

### CloudflareTurnstileHelper Utilities

The `CloudflareTurnstileHelper` class contains shared logic for both authenticator and form action to eliminate code duplication.

#### Event Logging Methods

- `logIpBlockedEvent(event, ipAddress)` - Log IP blocklist events
- `logSkipVerificationEvent(event, ipAddress)` - Log allowlist skip events
- `logVerifyButAllowEvent(event, result, ipAddress, isRegistration)` - Log allowlist audit events with full verification details
- `logVerificationResult(event, result, ipAddress, isRegistration)` - Log standard verification outcomes
- `logVerificationError(event, errorMessage, failOpen, isRegistration)` - Log API errors and exceptions
- `logFailAction(event, action)` - Log fail action decisions
- `addAuditContextToEvent(event, failMode, failAction, allowlistBehavior, implementationMethod, ipAllowlisted, ipBlocklisted, verificationSkipped, authenticationAllowed, actionReason)` - Add comprehensive audit context to events

**IMPORTANT**: Always call `addAuditContextToEvent()` BEFORE calling `context.getEvent().error()` or `context.getEvent().success()`, as these methods finalize the event and prevent subsequent `.detail()` calls from being recorded.

#### Database Storage Methods

- `createVerificationEntity(result, ipAddress, realm, user, sessionId, eventId, flowType, failMode, failAction, allowlistBehavior, implementationMethod, ipAllowlisted, ipBlocklisted, verificationSkipped, authenticationAllowed, actionReason)` - Create fully populated entity with all audit columns
- `storeVerificationResult(session, ...)` - Persist verification result with error handling (non-blocking)

#### Utility Methods

- `urlEncode(value)` - URL-encode string values
- `getConnectTimeout(config)` - Extract connect timeout from config (default: 5000ms)
- `getReadTimeout(config)` - Extract read timeout from config (default: 5000ms)

**Usage Example**:
```java
// Log verification result
CloudflareTurnstileHelper.logVerificationResult(context.getEvent(), result, ipAddress, false);

// Add comprehensive audit context BEFORE finalizing event
CloudflareTurnstileHelper.addAuditContextToEvent(
    context.getEvent(),
    failMode, failAction, allowlistBehavior, implementationMethod,
    ipAllowlisted, ipBlocklisted, verificationSkipped,
    authenticationAllowed, actionReason
);

// Then finalize event
context.getEvent().success(); // or .error()

// Store in database (if recordVerifications enabled)
CloudflareTurnstileHelper.storeVerificationResult(
    context.getSession(), result, ipAddress, realm, user,
    sessionId, eventId, "login",
    failMode, failAction, allowlistBehavior, implementationMethod,
    ipAllowlisted, ipBlocklisted, verificationSkipped,
    authenticationAllowed, actionReason
);
```

### Event Logging

All verification attempts log comprehensive event details with 15+ fields:

**Basic Fields:**
- `cloudflare_turnstile_success` - "true" or "false"
- `cloudflare_turnstile_hostname` - Hostname from Cloudflare
- `cloudflare_turnstile_errors` - Comma-separated error codes
- `cloudflare_turnstile_flow_type` - "login" or "registration"
- `ip_address` - IP address

**Action/Result Fields:**
- `cloudflare_turnstile_action` - "allowed", "mfa_required", "blocked", "ip_allowlisted_verify_but_allow"
- `cloudflare_turnstile_result` - "blocked_ip", "ip_allowlisted_skip_verification", "verification_error", "verification_error_fail_open"
- `error_message` - Error message for verification errors

**Configuration Context:**
- `cloudflare_turnstile_fail_mode` - "FAIL_OPEN" or "FAIL_CLOSED"
- `cloudflare_turnstile_fail_action` - "ALLOW", "BLOCK", or "REQUIRE_MFA"
- `cloudflare_turnstile_allowlist_behavior` - "SKIP_VERIFICATION" or "VERIFY_BUT_ALLOW"
- `cloudflare_turnstile_implementation_method` - "SEPARATE_PAGE", "SCRIPT_INJECTION", or "CUSTOM_THEME"

**IP Processing Status:**
- `cloudflare_turnstile_ip_allowlisted` - "true" or "false"
- `cloudflare_turnstile_ip_blocklisted` - "true" or "false"
- `cloudflare_turnstile_verification_skipped` - "true" or "false"

**Final Outcome:**
- `cloudflare_turnstile_authentication_allowed` - "true" or "false"
- `cloudflare_turnstile_action_reason` - Human-readable reason (e.g., "Success", "Failed - BLOCK", "Allowlisted - SKIP_VERIFICATION")

See `CloudflareTurnstileHelper` methods for event logging patterns.

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
- `CloudflareTurnstileCheck.findByUserId`
- `CloudflareTurnstileCheck.findByRealmId`
- `CloudflareTurnstileCheck.findByUserIdAndDateRange`
- `CloudflareTurnstileCheck.findFailedByRealm`
- `CloudflareTurnstileCheck.findByFlowType`

Access via EntityManager:
```java
EntityManager em = context.getSession()
    .getProvider(JpaConnectionProvider.class)
    .getEntityManager();

List<CloudflareTurnstileCheckEntity> results = em
    .createNamedQuery("CloudflareTurnstileCheck.findByUserId", CloudflareTurnstileCheckEntity.class)
    .setParameter("userId", userId)
    .getResultList();

// Find by flow type (login vs registration)
List<CloudflareTurnstileCheckEntity> loginChecks = em
    .createNamedQuery("CloudflareTurnstileCheck.findByFlowType", CloudflareTurnstileCheckEntity.class)
    .setParameter("realmId", realmId)
    .setParameter("flowType", "login")
    .getResultList();
```

## Build System

**Maven**: Uses shade plugin to create fat JAR with dependencies

**Included dependencies** (bundled in JAR):
- httpclient

**Provided dependencies** (from Keycloak):
- keycloak-core
- keycloak-server-spi
- keycloak-services
- hibernate-core
- jackson-databind (transitive from keycloak-core; deliberately not bundled so each Keycloak version uses its own Jackson)

**Keycloak compatibility**: compiled against the latest Keycloak (`keycloak.version`), but must keep running on 24.0.0+.
- Don't call Keycloak APIs added after 24; the CI `compatibility` matrix compiles against 24/25/26 to catch this
- Keep `jandex-maven-plugin` at 3.1.x: newer versions write index version 13, which Keycloak 24 can't read
- Keep Java `release` at 17

**Output**: `target/zymlabs-cloudflare-turnstile-provider.jar`

## Testing Approach

- Unit tests for logic (IpAddressUtils, Service, Entity)
- Mock-based tests for authenticator (future)
- Playwright e2e tests in `e2e/` against the Docker Compose stack (`KEYCLOAK_VERSION` selects the image); CI runs them on Keycloak 24.0.0, 25.0.6 and 26.7.4
  - The `turnstile-e2e` realm (`e2e/realms/turnstile-e2e-realm.json`) is re-imported by `global-setup.ts`, which binds one client per scenario to its browser flow
  - Uses Cloudflare test keys (`1x…AA` passes, secret `2x…AA` always fails server-side); needs internet access
  - The realm must allow `https://challenges.cloudflare.com` in its CSP (`frame-src`, `script-src`) or the widget renders blank

Test framework: JUnit 5 + AssertJ + Mockito; Playwright for e2e

## Deployment

1. Copy JAR to `/opt/keycloak/providers/`
2. Run `kc.sh build` (Quarkus Keycloak)
3. Restart Keycloak
4. Database table created automatically via Liquibase

## Security Notes

- Secret key stored as entered in Keycloak's database unless it is a vault reference (`${vault.…}`)
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
