# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Realm-wide settings:** a "Cloudflare Turnstile" tab in Realm settings (component
  `cloudflare-turnstile-settings`, needs Keycloak's `declarative-ui` feature, or set it through realm
  JSON) with the keys and policies. The secret key may be a vault reference, on the tab or on a step.
- **Reset-password protection:** the authenticator works in the reset-credentials ("Forgot your
  password?") flow. `SEPARATE_PAGE` shows a Turnstile page, worded for the reset, before Keycloak's
  reset page. `SCRIPT_INJECTION` puts the widget on Keycloak's own reset page and takes the place of
  the Choose User step, whose logic it reuses (`CUSTOM_THEME` uses script injection there). The
  emailed reset link needs no new check. `REQUIRE_MFA` blocks there: nothing could ask for a second
  factor before the reset email is sent. Events and records have the flow type `reset-credentials`
  (`RESET_CREDENTIALS` in the database).
- `CloudflareTurnstileAuthenticator.FAILED_NOTE`: the authentication-session note `REQUIRE_MFA` sets.
- E2E tests for both reset options, the registration separate page, allowlisted addresses, realm
  settings, `login_hint`, a preset user, passkeys, token-less posts and brute-force counting; the
  compose file now includes Mailpit for Keycloak's emails and turns on `declarative-ui`.

### Changed
- **With the widget inline, the sign-in page is Keycloak's own username/password form**, built and
  checked by Keycloak's step with the widget added. `login_hint` and remembered usernames are filled
  in, a user identified by an earlier step is asked only for the password, and on Keycloak 26 (with
  `SCRIPT_INJECTION`) passkeys work, including autofill; Keycloak 26 also records the password as the
  credential used. A passkey submission (WebAuthn fields, no username or password) posts without a
  Turnstile token and goes to Keycloak's form unchecked: a passkey can't be guessed. The bundled
  `cloudflare-turnstile` theme offers no passkeys (it supports Keycloak 26.0, which has no passkey
  templates).
- Flow steps have a **Use Realm Settings** switch, on for steps created in the admin console: the
  step then takes everything but its implementation method from the realm tab. With it off (or
  absent, as in existing configurations), the step's own values apply, and an empty value now means
  "use the realm's setting" rather than "empty".
- In a registration flow the authenticator always shows its separate page; inline widgets there come
  from the registration form action.
- Failed checks that block, and Cloudflare errors with FAIL_CLOSED, are recorded with the event errors
  `turnstile_verification_failed` and `turnstile_verification_error` instead of
  `invalid_user_credentials` (or `invalid_registration`), so they aren't taken for wrong passwords
  (for example by IP-throttling listeners, which would otherwise block busy shared addresses during a
  Cloudflare outage).
- Turnstile's refusals (failed check, Cloudflare error, blocklisted IP, missing configuration), and
  anything Keycloak refuses on a passkey submission, no longer count toward Keycloak's brute-force
  lockout of the account named so far: otherwise anyone could lock accounts without solving a check.
- The step's reference category is now `password` (was `captcha`), as it stands in for the
  username/password form.

### Fixed
- `FAIL_OPEN` and `FAIL_CLOSED` never applied to Cloudflare outages: network errors, timeouts and
  unreadable answers were reported as failed checks (`network-error`), so the verification failure
  action applied instead (BLOCK by default). They are now verification errors, handled by the error
  handling mode.
- On Keycloak 26.5 and later, wrong passwords on the inline sign-in page didn't count toward
  brute-force lockout (Keycloak counts only steps of the password, OTP and recovery-code categories),
  and a successful inline sign-in didn't reset the count.
- The bundled `cloudflare-turnstile` theme showed no password field when an earlier step had already
  identified the user (re-authentication, username-first flows).
- The authenticator never recognized the registration flow (it read a session note Keycloak doesn't
  set), so its page there had the sign-in wording and events said `login`. It now uses Keycloak's
  flow path.
- With the widget inline on the sign-in page, allowlisted addresses (`SKIP_VERIFICATION`) and
  Cloudflare outages with `FAIL_OPEN` skipped the username and password check, so those sign-ins
  failed even with the right password. The credentials are now checked on every path, before the
  attempt is audited as allowed.
- A timeout setting that isn't a positive number of milliseconds made every verification error out,
  which with `FAIL_OPEN` let every attempt through unchecked. Such values now fall back to 5000 ms
  (with a warning), and the realm tab refuses them.

### Security
- **IP allowlist/blocklist no longer resolves hostnames through DNS.** `InetAddress.getByName` was called on list entries and on the client address, so a hostname entry was silently resolved on every login. That let whoever controls (or spoofs) the DNS record decide who is allowlisted (bypassing verification with `SKIP_VERIFICATION`), and put blocking DNS lookups on the login path. Only IPv4/IPv6 literals are now accepted: hostname entries never match and are skipped with a warning in the log, while the other entries in the list still apply. Bracketed IPv6 entries such as `[::1]` are no longer accepted; write them without brackets.

### Added
- **CI upgrade check**: imports the example realms on Keycloak 24.0.0 and upgrades the same database to 26.7.4
- **Playwright end-to-end tests** (`e2e/`) covering script injection, separate page, custom theme, server-side rejection, IP blocklist and registration flows
- **CI compatibility matrix**: unit tests compiled against Keycloak 24.0.0, 25.0.6 and 26.7.4, and e2e tests run the released JAR on each version; releases now require both to pass
- **CloudflareTurnstileHelper** utility class with shared event logging and database methods
- **CloudflareTurnstileValidator** for centralized token validation logic
- **CloudflareTurnstileFormAction** for unified registration flow protection
- **Resource Providers** for theme integration (ThemeResourceProvider, RealmResourceProvider)
- **Legacy theme variant** (cloudflare-turnstile-legacy) for Keycloak 24.x compatibility
- **9 new audit trail fields** to database schema:
  - `flow_type`: Track login vs registration
  - `fail_mode`, `fail_action`: Configuration context at verification time
  - `allowlist_behavior`, `implementation_method`: Operational settings
  - `ip_allowlisted`, `ip_blocklisted`, `verification_skipped`: IP processing status
  - `authentication_allowed`, `action_reason`: Final outcome tracking
- **15 database indexes** for efficient querying and analytics
- **Named query** `findByFlowType` for flow-specific analysis
- **Comprehensive event logging** with 15+ audit fields for security monitoring
- **Configuration option**: `allowlistBehavior` (SKIP_VERIFICATION | VERIFY_BUT_ALLOW)
- **Configuration option**: `enableDebugLogging` for JavaScript console logging
- **Configuration option**: `implementationMethod` (SEPARATE_PAGE | SCRIPT_INJECTION | CUSTOM_THEME)
- **New documentation**:
  - docs/AUDIT.md - Complete audit trail and compliance guide
  - docs/THEME-SELECTION.md - Theme variant selection guide
  - examples/FLOW_EXAMPLES.md - Authentication flow examples
- **CSP configuration guide** in README for Cloudflare Turnstile domains
- **Internationalization support** with messages/ directory in themes
- **JavaScript utilities** for widget interaction (turnstile-injector.js)
- **Automated initialization script** (docker/init-keycloak.sh) for development
- **Healthcheck configuration** in Docker Compose for reliable startup
- Pre-authentication Turnstile CAPTCHA verification
- Support for managed, non-interactive, and invisible widget modes
- Configurable widget themes (light, dark, auto)
- IP allowlist and blocklist with IPv4/IPv6 CIDR support
- Configurable fail actions (BLOCK, ALLOW, REQUIRE_MFA)
- Fail-safe modes (FAIL_OPEN, FAIL_CLOSED)
- Optional database persistence of verification results
- Keycloak event integration for monitoring
- Configurable connection and read timeouts
- Comprehensive documentation and examples

### Changed
- Build against Keycloak 26.7.4 (was 24.0.0); Keycloak 24.0.0 remains the minimum supported version
- Jackson is no longer bundled in the JAR; the provider uses the Jackson that ships with Keycloak
- Docker Compose uses the official `quay.io/keycloak/keycloak` image (was `bitnamilegacy/keycloak`), selectable with `KEYCLOAK_VERSION`
- Updated test dependencies, Maven plugins and GitHub Actions
- Migrated GitVersion from 5.x to 6.8 (`gittools/actions@v4`). Release versions are unchanged; develop pre-release numbers now increase by one per commit instead of jumping
- **Simplified architecture** from 4 to 3 implementation methods
- **Refactored authenticator** to use shared CloudflareTurnstileHelper utilities
- **Enhanced database schema** with comprehensive audit capabilities
- **Upgraded Docker Compose** from Keycloak 24.0.5 to 26.0.6
- **Improved theme templates** with better Turnstile integration and resource organization
- **Updated all documentation** for 3-method architecture and audit features
- **Expanded README** with detailed event logging documentation and examples
- **Enhanced CLAUDE.md** with helper utilities documentation and audit field details
- **Updated configuration guides** with new audit settings and CSP requirements
- **Improved development workflow** with auto-approval settings and build step detection

### Removed
- The unused `turnstile-register.ftl` template (no implementation method renders it) and the
  "Copied Template" registration option documented for it.
- **Copied Template implementation** (consolidated into other methods)
- **14 legacy implementation classes**:
  - CloudflareTurnstileCopiedTemplateAction
  - CloudflareTurnstileCopiedTemplateActionFactory
  - CloudflareTurnstileCustomThemeAction
  - CloudflareTurnstileCustomThemeActionFactory
  - CloudflareTurnstileLoginCopiedTemplateAction
  - CloudflareTurnstileLoginCopiedTemplateActionFactory
  - CloudflareTurnstileLoginCustomThemeAction
  - CloudflareTurnstileLoginCustomThemeActionFactory
  - CloudflareTurnstileLoginScriptInjectionAction
  - CloudflareTurnstileLoginScriptInjectionActionFactory
  - CloudflareTurnstileRegistrationAuthenticator
  - CloudflareTurnstileRegistrationAuthenticatorFactory
  - CloudflareTurnstileScriptInjectionAction
  - CloudflareTurnstileScriptInjectionActionFactory

### Fixed
- Example realm (`examples/realms/turnstile-demo-realm.json`) now includes Keycloak's built-in `browser`, `direct grant`, `registration` and `saml ecp` flows. A realm imported from the old file on Keycloak 24.0.0 or 24.0.1 has no `browser` flow, and Keycloak 26.6.1+ then fails to start when upgrading it (`MigrateTo26_6_1` NullPointerException). Those realms need a `browser` flow added before upgrading; see [Troubleshooting](docs/TROUBLESHOOTING.md#keycloak-fails-to-start-after-upgrading-to-2661-or-later)
- Documented the `cloudflare-turnstile` theme as Keycloak 26+ (it failed with `Template not found for name "field.ftl"` on 25); use `cloudflare-turnstile-legacy` on 24 and 25
- Eliminated code duplication between login and registration flows
- Improved event logging consistency across all verification paths
- Better error handling for database operations (non-blocking)

[Unreleased]: https://github.com/zymlabs/keycloak-cloudflare-turnstile/compare/HEAD
