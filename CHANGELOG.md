# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial release of Cloudflare Turnstile provider for Keycloak
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

[Unreleased]: https://github.com/zymlabs/keycloak-cloudflare-turnstile/compare/v1.0.0...HEAD
