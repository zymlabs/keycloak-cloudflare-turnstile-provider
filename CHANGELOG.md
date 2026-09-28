# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
