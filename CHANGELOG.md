# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
- Eliminated code duplication between login and registration flows
- Improved event logging consistency across all verification paths
- Better error handling for database operations (non-blocking)

[Unreleased]: https://github.com/zymlabs/keycloak-cloudflare-turnstile/compare/HEAD
