# Audit and Compliance Guide

Complete guide to audit tracking, security monitoring, and compliance reporting for the Cloudflare Turnstile Keycloak Provider.

## Table of Contents

1. [Overview](#overview)
2. [Audit Capabilities](#audit-capabilities)
3. [Database Audit Trail](#database-audit-trail)
4. [Event Audit Trail](#event-audit-trail)
5. [Security Analysis Queries](#security-analysis-queries)
6. [Compliance Reporting](#compliance-reporting)
7. [SIEM Integration](#siem-integration)
8. [Data Retention](#data-retention)

## Overview

The Cloudflare Turnstile provider includes comprehensive audit tracking capabilities designed for:

- **Security Monitoring** - Detect suspicious patterns, attacks, and anomalies
- **Compliance Reporting** - Meet regulatory requirements (SOC 2, ISO 27001, GDPR, etc.)
- **Configuration Analysis** - Understand how settings affect authentication outcomes
- **Incident Response** - Investigate security events with complete context
- **Performance Optimization** - Balance security and user experience

### Dual Audit Trail

The provider maintains TWO parallel audit trails:

1. **Keycloak Events** (built-in) - Lightweight, real-time, integrated with Keycloak's event system
2. **Database Records** (optional) - Comprehensive, queryable, long-term storage with full configuration context

## Audit Capabilities

### What Gets Tracked

Every Turnstile verification attempt (login and registration) records:

✅ **Verification Outcome** - Whether Cloudflare verification passed or failed
✅ **Final Authentication Decision** - Whether user was ultimately allowed or blocked
✅ **Decision Reason** - Human-readable explanation (e.g., "Success", "Failed - BLOCK", "Allowlisted - SKIP_VERIFICATION")
✅ **Configuration Snapshot** - What settings were active at the time
✅ **IP Processing Status** - Whether IP was allowlisted/blocklisted, whether verification was skipped
✅ **Flow Context** - Login vs registration
✅ **Error Details** - Cloudflare error codes, exception messages
✅ **Correlation IDs** - Link to Keycloak events and sessions

### Configuration Snapshot

Each audit record captures the active configuration at verification time:

- **Error Handling Mode** (fail_mode) - FAIL_OPEN or FAIL_CLOSED
- **Verification Failure Action** (fail_action) - BLOCK, ALLOW, or REQUIRE_MFA
- **Allowlist Behavior** (allowlist_behavior) - SKIP_VERIFICATION or VERIFY_BUT_ALLOW
- **Implementation Method** (implementation_method) - SEPARATE_PAGE, SCRIPT_INJECTION, or CUSTOM_THEME

This enables **historical analysis** - you can see how configuration changes affected outcomes over time.

### Decision Context

Each audit record includes flags showing how the decision was made:

- **ip_allowlisted** - Was IP on the allowlist?
- **ip_blocklisted** - Was IP on the blocklist?
- **verification_skipped** - Did we skip calling Cloudflare API?
- **authentication_allowed** - Final authentication outcome (allowed/blocked)
- **action_reason** - Human-readable reason for the decision

## Database Audit Trail

When **Record Verifications** is enabled in the authenticator configuration, all verification attempts are stored in the `cloudflare_turnstile_check` table.

### Database Schema

```sql
CREATE TABLE cloudflare_turnstile_check (
    -- Identity
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- User Context
    user_id VARCHAR(36),
    username VARCHAR(255),
    email VARCHAR(255),
    realm_id VARCHAR(36) NOT NULL,

    -- Request Data
    ip_address VARCHAR(45) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    flow_type VARCHAR(50),

    -- Verification Result
    success BOOLEAN NOT NULL,
    error_codes VARCHAR(500),
    challenge_ts VARCHAR(50),
    hostname VARCHAR(255),
    raw_response TEXT,

    -- Configuration Context (v1.1)
    fail_mode VARCHAR(20),
    fail_action VARCHAR(20),
    allowlist_behavior VARCHAR(30),
    implementation_method VARCHAR(30),

    -- IP Processing Status (v1.1)
    ip_allowlisted BOOLEAN NOT NULL DEFAULT FALSE,
    ip_blocklisted BOOLEAN NOT NULL DEFAULT FALSE,
    verification_skipped BOOLEAN NOT NULL DEFAULT FALSE,

    -- Final Outcome (v1.1)
    authentication_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    action_reason VARCHAR(100),

    -- Correlation
    event_id VARCHAR(36),
    session_id VARCHAR(36)
);
```

### Audit Column Reference

| Column | Purpose | Example Values |
|--------|---------|----------------|
| `fail_mode` | Configuration snapshot: error handling mode | `FAIL_OPEN`, `FAIL_CLOSED` |
| `fail_action` | Configuration snapshot: verification failure action | `BLOCK`, `ALLOW`, `REQUIRE_MFA` |
| `allowlist_behavior` | Configuration snapshot: allowlist behavior | `SKIP_VERIFICATION`, `VERIFY_BUT_ALLOW` |
| `implementation_method` | Configuration snapshot: implementation method | `SEPARATE_PAGE`, `SCRIPT_INJECTION`, `CUSTOM_THEME` |
| `ip_allowlisted` | Was IP on allowlist at time of verification? | `true`, `false` |
| `ip_blocklisted` | Was IP on blocklist at time of verification? | `true`, `false` |
| `verification_skipped` | Was Cloudflare API call skipped? | `true`, `false` |
| `authentication_allowed` | Final authentication outcome | `true` (allowed), `false` (blocked) |
| `action_reason` | Human-readable reason for decision | See [Action Reasons](#action-reasons) |

### Action Reasons

Standard `action_reason` values:

| Reason | Meaning | authentication_allowed |
|--------|---------|------------------------|
| `Success` | Verification passed | `true` |
| `Failed - BLOCK` | Verification failed, user blocked | `false` |
| `Failed - ALLOW` | Verification failed, user allowed (fail action = ALLOW) | `true` |
| `Failed - MFA` | Verification failed, MFA required | `true` |
| `Error - FAIL_OPEN` | API error, user allowed (fail mode = FAIL_OPEN) | `true` |
| `Error - FAIL_CLOSED` | API error, user blocked (fail mode = FAIL_CLOSED) | `false` |
| `Blocked - IP blocklisted` | IP was on blocklist | `false` |
| `Allowlisted - SKIP_VERIFICATION` | IP allowlisted, verification skipped | `true` |
| `Allowlisted - VERIFY_BUT_ALLOW` | IP allowlisted, verified for audit but always allowed | `true` |

## Event Audit Trail

All verification attempts log detailed information to Keycloak's event system, visible in:
- **Keycloak Admin Console** → Events → Login Events
- **External Event Listeners** (SIEM, log aggregators, analytics platforms)

### Event Detail Fields

Every verification attempt logs 15+ event detail fields:

#### Basic Fields
- `cloudflare_turnstile_success` - "true" or "false"
- `cloudflare_turnstile_hostname` - Hostname from Cloudflare
- `cloudflare_turnstile_errors` - Comma-separated error codes
- `cloudflare_turnstile_flow_type` - "login" or "registration"
- `ip_address` - User's IP address

#### Configuration Context
- `cloudflare_turnstile_fail_mode` - "FAIL_OPEN" or "FAIL_CLOSED"
- `cloudflare_turnstile_fail_action` - "ALLOW", "BLOCK", or "REQUIRE_MFA"
- `cloudflare_turnstile_allowlist_behavior` - "SKIP_VERIFICATION" or "VERIFY_BUT_ALLOW"
- `cloudflare_turnstile_implementation_method` - "SEPARATE_PAGE", "SCRIPT_INJECTION", "CUSTOM_THEME"

#### IP Processing Status
- `cloudflare_turnstile_ip_allowlisted` - "true" or "false"
- `cloudflare_turnstile_ip_blocklisted` - "true" or "false"
- `cloudflare_turnstile_verification_skipped` - "true" or "false"

#### Final Outcome
- `cloudflare_turnstile_authentication_allowed` - "true" or "false"
- `cloudflare_turnstile_action_reason` - Human-readable reason

### Events vs Database

| Feature | Keycloak Events | Database Records |
|---------|----------------|------------------|
| **Performance** | Extremely fast | Fast (indexed) |
| **Storage** | Configurable retention | Persistent |
| **Querying** | Limited | SQL queries |
| **Real-time** | Yes | Yes |
| **SIEM Integration** | Native via event listeners | Custom export |
| **Historical Analysis** | Limited by retention | Full history |
| **Compliance** | Good | Excellent |

**Recommendation**: Enable both for comprehensive audit coverage.

## Security Analysis Queries

### Detecting Attack Patterns

#### 1. Brute Force Detection
```sql
-- IPs with excessive failed attempts
SELECT
    ip_address,
    COUNT(*) as failed_attempts,
    COUNT(DISTINCT user_id) as users_targeted,
    MIN(timestamp) as first_attempt,
    MAX(timestamp) as last_attempt,
    ROUND((MAX(timestamp) - MIN(timestamp)) / 60, 2) as duration_minutes
FROM cloudflare_turnstile_check
WHERE authentication_allowed = false
  AND timestamp > NOW() - INTERVAL '1 HOUR'
GROUP BY ip_address
HAVING COUNT(*) > 10
ORDER BY failed_attempts DESC;
```

#### 2. Credential Stuffing Detection
```sql
-- Same IP attempting multiple usernames
SELECT
    ip_address,
    COUNT(DISTINCT username) as unique_usernames,
    COUNT(*) as total_attempts,
    SUM(CASE WHEN authentication_allowed = false THEN 1 ELSE 0 END) as failed_attempts
FROM cloudflare_turnstile_check
WHERE username IS NOT NULL
  AND timestamp > NOW() - INTERVAL '6 HOURS'
GROUP BY ip_address
HAVING COUNT(DISTINCT username) > 5
ORDER BY unique_usernames DESC;
```

#### 3. Distributed Attack Detection
```sql
-- Multiple IPs targeting same user
SELECT
    username,
    COUNT(DISTINCT ip_address) as unique_ips,
    COUNT(*) as total_attempts,
    SUM(CASE WHEN authentication_allowed = false THEN 1 ELSE 0 END) as failed_attempts
FROM cloudflare_turnstile_check
WHERE username IS NOT NULL
  AND timestamp > NOW() - INTERVAL '1 HOUR'
GROUP BY username
HAVING COUNT(DISTINCT ip_address) > 5
ORDER BY unique_ips DESC;
```

#### 4. Anomaly Detection - Sudden Spike in Failures
```sql
-- Compare hourly failure rates
SELECT
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as total_checks,
    SUM(CASE WHEN authentication_allowed = false THEN 1 ELSE 0 END) as failures,
    ROUND(100.0 * SUM(CASE WHEN authentication_allowed = false THEN 1 ELSE 0 END) / COUNT(*), 2) as failure_rate_pct
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '24 HOURS'
GROUP BY DATE_TRUNC('hour', timestamp)
ORDER BY hour DESC;
```

### Configuration Effectiveness Analysis

#### 5. Allowlist Behavior Effectiveness
```sql
-- Compare SKIP_VERIFICATION vs VERIFY_BUT_ALLOW outcomes
SELECT
    allowlist_behavior,
    COUNT(*) as total_checks,
    SUM(CASE WHEN verification_skipped THEN 1 ELSE 0 END) as verifications_skipped,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as would_have_failed,
    ROUND(100.0 * SUM(CASE WHEN success = false THEN 1 ELSE 0 END) / COUNT(*), 2) as potential_block_rate_pct
FROM cloudflare_turnstile_check
WHERE ip_allowlisted = true
  AND timestamp > NOW() - INTERVAL '7 DAYS'
GROUP BY allowlist_behavior;
```

**Interpretation**: If `would_have_failed` is high with SKIP_VERIFICATION, you may have allowlisted compromised IPs.

#### 6. Error Handling Mode Impact
```sql
-- Compare FAIL_OPEN vs FAIL_CLOSED during errors
SELECT
    fail_mode,
    COUNT(*) as total_checks,
    SUM(CASE WHEN action_reason LIKE 'Error%' THEN 1 ELSE 0 END) as error_count,
    SUM(CASE WHEN action_reason LIKE 'Error%' AND authentication_allowed THEN 1 ELSE 0 END) as errors_allowed,
    SUM(CASE WHEN action_reason LIKE 'Error%' AND NOT authentication_allowed THEN 1 ELSE 0 END) as errors_blocked
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY fail_mode;
```

**Use Case**: Understand how often API errors occur and their impact on authentication.

#### 7. Verification Failure Action Analysis
```sql
-- Impact of BLOCK vs ALLOW vs REQUIRE_MFA on failed verifications
SELECT
    fail_action,
    COUNT(*) as total_checks,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failed_verifications,
    SUM(CASE WHEN success = false AND authentication_allowed THEN 1 ELSE 0 END) as failures_allowed,
    SUM(CASE WHEN success = false AND NOT authentication_allowed THEN 1 ELSE 0 END) as failures_blocked,
    ROUND(100.0 * SUM(CASE WHEN success = false AND NOT authentication_allowed THEN 1 ELSE 0 END) / NULLIF(SUM(CASE WHEN success = false THEN 1 ELSE 0 END), 0), 2) as block_rate_pct
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY fail_action;
```

## Compliance Reporting

### SOC 2 / ISO 27001 Compliance

#### Access Control Monitoring
```sql
-- Blocked access attempts (security control evidence)
SELECT
    DATE(timestamp) as date,
    COUNT(*) as blocked_attempts,
    COUNT(DISTINCT ip_address) as unique_blocked_ips,
    COUNT(DISTINCT username) as unique_blocked_users
FROM cloudflare_turnstile_check
WHERE authentication_allowed = false
  AND timestamp > NOW() - INTERVAL '90 DAYS'
GROUP BY DATE(timestamp)
ORDER BY date DESC;
```

#### Configuration Audit Trail
```sql
-- Configuration changes over time (change management evidence)
SELECT DISTINCT
    DATE(timestamp) as date,
    fail_mode,
    fail_action,
    allowlist_behavior,
    implementation_method,
    COUNT(*) as checks_with_config
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '90 DAYS'
GROUP BY DATE(timestamp), fail_mode, fail_action, allowlist_behavior, implementation_method
ORDER BY date DESC;
```

### GDPR Compliance

#### User Data Access
```sql
-- All verification attempts for a specific user (GDPR data access request)
SELECT
    timestamp,
    flow_type,
    ip_address,
    success,
    authentication_allowed,
    action_reason,
    error_codes
FROM cloudflare_turnstile_check
WHERE user_id = '<user-uuid>'
ORDER BY timestamp DESC;
```

#### Data Retention Report
```sql
-- Records older than retention policy (for cleanup)
SELECT
    DATE(timestamp) as date,
    COUNT(*) as record_count
FROM cloudflare_turnstile_check
WHERE timestamp < NOW() - INTERVAL '90 DAYS'
GROUP BY DATE(timestamp)
ORDER BY date;
```

### PCI DSS Compliance

#### Failed Authentication Monitoring
```sql
-- Track failed authentication attempts (PCI DSS Requirement 8.1.6)
SELECT
    DATE(timestamp) as date,
    COUNT(*) as total_attempts,
    SUM(CASE WHEN authentication_allowed = false THEN 1 ELSE 0 END) as failed_attempts,
    ROUND(100.0 * SUM(CASE WHEN authentication_allowed = false THEN 1 ELSE 0 END) / COUNT(*), 2) as failure_rate_pct
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY DATE(timestamp)
ORDER BY date DESC;
```

## SIEM Integration

### Splunk Integration

#### Event Forwarding
Configure Keycloak event listener to forward events to Splunk:

```bash
# In Keycloak standalone.xml / keycloak.conf
spi-events-listener-jboss-logging-success-level=info
spi-events-listener-jboss-logging-error-level=warn
```

#### Splunk Search Queries

**Failed Verifications**:
```spl
index=keycloak cloudflare_turnstile_authentication_allowed="false"
| stats count by cloudflare_turnstile_action_reason, ip_address
| sort -count
```

**Attack Pattern Detection**:
```spl
index=keycloak cloudflare_turnstile_authentication_allowed="false"
| bucket _time span=5m
| stats count by _time, ip_address
| where count > 10
```

### ELK Stack Integration

#### Elasticsearch Query

```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "cloudflare_turnstile_authentication_allowed": "false" }},
        { "range": { "@timestamp": { "gte": "now-1h" }}}
      ]
    }
  },
  "aggs": {
    "top_blocked_ips": {
      "terms": { "field": "ip_address", "size": 20 }
    }
  }
}
```

### Custom Event Processor

Example Java event listener for custom SIEM integration:

```java
@Override
public void onEvent(Event event) {
    if (event.getDetails().containsKey("cloudflare_turnstile_authentication_allowed")) {
        String allowed = event.getDetails().get("cloudflare_turnstile_authentication_allowed");
        String actionReason = event.getDetails().get("cloudflare_turnstile_action_reason");
        String ipAddress = event.getIpAddress();

        // Send to your SIEM
        siemClient.sendAlert(
            "Turnstile Authentication Event",
            Map.of(
                "allowed", allowed,
                "reason", actionReason,
                "ip", ipAddress
            )
        );
    }
}
```

## Data Retention

### Retention Policy Recommendations

| Use Case | Retention Period | Rationale |
|----------|------------------|-----------|
| **Security Incident Investigation** | 90 days | Most incidents detected within 30-90 days |
| **Compliance (SOC 2)** | 1 year | Annual audit requirements |
| **Compliance (PCI DSS)** | 90 days minimum | PCI DSS Requirement 10.7 |
| **Compliance (GDPR)** | Minimal necessary | Data minimization principle |
| **Long-term Analytics** | Aggregated data only | Summarize monthly, delete details |

### Automated Cleanup

#### PostgreSQL Cleanup Job
```sql
-- Create cleanup function
CREATE OR REPLACE FUNCTION cleanup_turnstile_old_records()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM cloudflare_turnstile_check
    WHERE timestamp < NOW() - INTERVAL '90 DAYS';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Schedule daily cleanup at 2 AM
SELECT cron.schedule('cleanup-turnstile', '0 2 * * *',
    'SELECT cleanup_turnstile_old_records()');
```

#### Archive Before Delete
```sql
-- Archive to separate table before cleanup
INSERT INTO cloudflare_turnstile_check_archive
SELECT * FROM cloudflare_turnstile_check
WHERE timestamp < NOW() - INTERVAL '90 DAYS';

-- Then delete from main table
DELETE FROM cloudflare_turnstile_check
WHERE timestamp < NOW() - INTERVAL '90 DAYS';
```

### Anonymization for Long-term Storage

```sql
-- Anonymize PII while keeping analytics data
UPDATE cloudflare_turnstile_check
SET
    username = CONCAT('user_', MD5(username)),
    email = CONCAT('user_', MD5(email), '@anonymized.local'),
    user_id = MD5(user_id)
WHERE timestamp < NOW() - INTERVAL '180 DAYS'
  AND timestamp > NOW() - INTERVAL '365 DAYS';
```

## Best Practices

### 1. Enable Both Audit Trails
✅ Enable **Record Verifications** in authenticator config for database audit trail
✅ Configure Keycloak event listeners for real-time SIEM integration

### 2. Monitor Regularly
- Set up daily automated reports for failed authentications
- Alert on unusual spike in verification failures
- Review allowlist/blocklist effectiveness weekly

### 3. Correlation
- Use `event_id` and `session_id` to correlate database records with Keycloak events
- Cross-reference with application logs for complete incident timeline

### 4. Compliance
- Document retention policies based on regulatory requirements
- Implement automated cleanup with audit logging
- Maintain configuration change history

### 5. Performance
- Database audit adds ~5-10ms per verification (negligible)
- Consider partitioning table by timestamp for >10M records
- Archive old records to separate table/database

## Troubleshooting

### No Audit Records in Database

Check:
1. **Record Verifications** setting enabled in authenticator config
2. Database connectivity (check Keycloak logs)
3. Liquibase migrations applied (check `DATABASECHANGELOG` table)

### Missing Event Details

Check:
1. Event logging enabled in Keycloak (Realm Settings → Events)
2. "Save Events" turned on
3. Event details not being truncated (check Keycloak event storage limits)

### Query Performance

- Ensure indexes exist (15 indexes should be created by Liquibase)
- Use `EXPLAIN ANALYZE` to check query plans
- Consider partitioning table by timestamp for large datasets

## Additional Resources

- [DATABASE.md](DATABASE.md) - Complete database schema reference
- [CONFIGURATION.md](CONFIGURATION.md) - Configuration options
- [Keycloak Event Documentation](https://www.keycloak.org/docs/latest/server_admin/#auditing-and-events)
