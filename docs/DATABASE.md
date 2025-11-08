# Database Reference

Complete database schema and query reference for the Cloudflare Turnstile Keycloak extension.

## Table of Contents

1. [Database Schema](#database-schema)
2. [Table Structure](#table-structure)
3. [Indexes](#indexes)
4. [Named Queries](#named-queries)
5. [Common SQL Queries](#common-sql-queries)
6. [Data Lifecycle](#data-lifecycle)
7. [Performance Considerations](#performance-considerations)

## Database Schema

The extension creates a single table: `cloudflare_turnstile_check`

This table stores all Turnstile verification attempts when `recordVerifications` is enabled.

### Automatic Creation

The table is created automatically via Liquibase when Keycloak starts:

1. Keycloak detects the JPA entity provider
2. Runs `META-INF/cloudflare-turnstile-changelog.xml`
3. Creates table and indexes
4. No manual intervention required

### Database Compatibility

Tested and compatible with:
- ✅ PostgreSQL 12+
- ✅ MySQL 8.0+
- ✅ MariaDB 10.5+
- ✅ Oracle 19c+
- ✅ Microsoft SQL Server 2019+
- ✅ H2 (for development/testing)

## Table Structure

### cloudflare_turnstile_check

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

### Column Descriptions

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGINT | No | Primary key, auto-increment |
| `user_id` | VARCHAR(36) | Yes | Keycloak user UUID (null for pre-auth checks) |
| `realm_id` | VARCHAR(36) | No | Keycloak realm UUID |
| `username` | VARCHAR(255) | Yes | Username (null for pre-auth) |
| `email` | VARCHAR(255) | Yes | User email (null for pre-auth) |
| `ip_address` | VARCHAR(45) | No | IPv4 or IPv6 address (45 chars for full IPv6) |
| `timestamp` | TIMESTAMP | No | When verification occurred (UTC) |
| `success` | BOOLEAN | No | true = passed, false = failed |
| `error_codes` | VARCHAR(500) | Yes | Comma-separated Cloudflare error codes |
| `challenge_ts` | VARCHAR(50) | Yes | Cloudflare challenge timestamp (ISO 8601) |
| `hostname` | VARCHAR(255) | Yes | Hostname from Cloudflare response |
| `event_id` | VARCHAR(36) | Yes | Keycloak event ID for correlation |
| `session_id` | VARCHAR(36) | Yes | Keycloak session ID |
| `raw_response` | TEXT | Yes | Complete JSON response from Cloudflare |

### Pre-Authentication Records

When Turnstile runs before credentials are entered:
- `user_id` = NULL
- `username` = NULL
- `email` = NULL
- Other fields populated normally

## Indexes

Six indexes optimize common query patterns:

### 1. idx_turnstile_user_id
```sql
CREATE INDEX idx_turnstile_user_id ON cloudflare_turnstile_check(user_id);
```
**Purpose**: Fast lookups by user
**Used by**: User history queries, user-specific reports

### 2. idx_turnstile_realm_id
```sql
CREATE INDEX idx_turnstile_realm_id ON cloudflare_turnstile_check(realm_id);
```
**Purpose**: Fast lookups by realm
**Used by**: Realm-wide statistics, multi-tenant queries

### 3. idx_turnstile_timestamp
```sql
CREATE INDEX idx_turnstile_timestamp ON cloudflare_turnstile_check(timestamp);
```
**Purpose**: Time-range queries, data cleanup
**Used by**: Recent activity, daily/weekly reports, retention policies

### 4. idx_turnstile_user_timestamp
```sql
CREATE INDEX idx_turnstile_user_timestamp ON cloudflare_turnstile_check(user_id, timestamp);
```
**Purpose**: User activity over time
**Used by**: User behavior analysis, repeat failure detection

### 5. idx_turnstile_event_id
```sql
CREATE INDEX idx_turnstile_event_id ON cloudflare_turnstile_check(event_id);
```
**Purpose**: Correlate with Keycloak events
**Used by**: Event debugging, audit trail correlation

### 6. idx_turnstile_session_id
```sql
CREATE INDEX idx_turnstile_session_id ON cloudflare_turnstile_check(session_id);
```
**Purpose**: Session-based queries
**Used by**: Session analysis, multi-step flow tracking

## Named Queries

Four JPA named queries are defined on the entity:

### findByUserId
```java
@NamedQuery(name = "findByUserId",
    query = "SELECT c FROM CloudflareTurnstileCheckEntity c
             WHERE c.userId = :userId
             ORDER BY c.timestamp DESC")
```

**Usage**:
```java
List<CloudflareTurnstileCheckEntity> checks = em
    .createNamedQuery("findByUserId", CloudflareTurnstileCheckEntity.class)
    .setParameter("userId", userId)
    .getResultList();
```

### findByRealmId
```java
@NamedQuery(name = "findByRealmId",
    query = "SELECT c FROM CloudflareTurnstileCheckEntity c
             WHERE c.realmId = :realmId
             ORDER BY c.timestamp DESC")
```

### findByUserIdAndDateRange
```java
@NamedQuery(name = "findByUserIdAndDateRange",
    query = "SELECT c FROM CloudflareTurnstileCheckEntity c
             WHERE c.userId = :userId
             AND c.timestamp BETWEEN :startDate AND :endDate
             ORDER BY c.timestamp DESC")
```

### findFailedByRealm
```java
@NamedQuery(name = "findFailedByRealm",
    query = "SELECT c FROM CloudflareTurnstileCheckEntity c
             WHERE c.realmId = :realmId
             AND c.success = false
             ORDER BY c.timestamp DESC")
```

## Common SQL Queries

### Security & Monitoring

#### Failed Verifications in Last 24 Hours
```sql
SELECT * FROM cloudflare_turnstile_check
WHERE success = false
  AND timestamp > NOW() - INTERVAL '24 HOURS'
ORDER BY timestamp DESC;
```

#### Top Failed IPs (Potential Attacks)
```sql
SELECT
    ip_address,
    COUNT(*) as failure_count,
    MAX(timestamp) as last_failure
FROM cloudflare_turnstile_check
WHERE success = false
  AND timestamp > NOW() - INTERVAL '7 DAYS'
GROUP BY ip_address
HAVING COUNT(*) > 10
ORDER BY failure_count DESC
LIMIT 20;
```

#### Suspicious Repeat Failures (Same IP, Multiple Users)
```sql
SELECT
    ip_address,
    COUNT(DISTINCT username) as user_count,
    COUNT(*) as attempt_count
FROM cloudflare_turnstile_check
WHERE success = false
  AND timestamp > NOW() - INTERVAL '1 DAY'
  AND username IS NOT NULL
GROUP BY ip_address
HAVING COUNT(DISTINCT username) > 5
ORDER BY user_count DESC;
```

### User Analytics

#### User Verification History
```sql
SELECT
    timestamp,
    success,
    ip_address,
    error_codes
FROM cloudflare_turnstile_check
WHERE user_id = '<user-uuid>'
ORDER BY timestamp DESC
LIMIT 50;
```

#### Users with Most Failed Verifications
```sql
SELECT
    username,
    email,
    COUNT(*) as failure_count,
    MAX(timestamp) as last_failure
FROM cloudflare_turnstile_check
WHERE success = false
  AND username IS NOT NULL
  AND timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY username, email
ORDER BY failure_count DESC
LIMIT 20;
```

### Realm Statistics

#### Realm Success Rate (Last 30 Days)
```sql
SELECT
    realm_id,
    COUNT(*) as total_checks,
    SUM(CASE WHEN success THEN 1 ELSE 0 END) as successful,
    SUM(CASE WHEN NOT success THEN 1 ELSE 0 END) as failed,
    ROUND(100.0 * SUM(CASE WHEN success THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate_pct
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY realm_id;
```

#### Daily Verification Volume
```sql
SELECT
    DATE(timestamp) as date,
    COUNT(*) as total_verifications,
    SUM(CASE WHEN success THEN 1 ELSE 0 END) as successful,
    SUM(CASE WHEN NOT success THEN 1 ELSE 0 END) as failed
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY DATE(timestamp)
ORDER BY date DESC;
```

### Error Analysis

#### Most Common Error Codes
```sql
SELECT
    error_codes,
    COUNT(*) as occurrence_count
FROM cloudflare_turnstile_check
WHERE error_codes IS NOT NULL
  AND timestamp > NOW() - INTERVAL '7 DAYS'
GROUP BY error_codes
ORDER BY occurrence_count DESC
LIMIT 10;
```

#### Timeout Errors
```sql
SELECT
    DATE(timestamp) as date,
    COUNT(*) as timeout_count
FROM cloudflare_turnstile_check
WHERE error_codes LIKE '%timeout%'
  OR error_codes LIKE '%network-error%'
GROUP BY DATE(timestamp)
ORDER BY date DESC;
```

### Geographic Analysis (via IP)

#### Unique IPs Per Day
```sql
SELECT
    DATE(timestamp) as date,
    COUNT(DISTINCT ip_address) as unique_ips,
    COUNT(*) as total_checks
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY DATE(timestamp)
ORDER BY date DESC;
```

## Data Lifecycle

### Data Retention

**Considerations**:
- Each record: ~500 bytes average
- 1M verifications = ~500 MB storage
- Indexes add ~30% overhead

**Recommended Retention**:
- **Security/Audit**: 90-365 days
- **Analytics**: 30-90 days
- **Compliance**: Per regulations (e.g., GDPR: minimal necessary)
- **High Volume**: 7-30 days

### Cleanup Strategies

#### Manual Cleanup (Old Records)
```sql
-- Delete records older than 90 days
DELETE FROM cloudflare_turnstile_check
WHERE timestamp < NOW() - INTERVAL '90 DAYS';
```

#### Archive Before Delete
```sql
-- Archive to separate table
CREATE TABLE cloudflare_turnstile_check_archive AS
SELECT * FROM cloudflare_turnstile_check
WHERE timestamp < NOW() - INTERVAL '90 DAYS';

-- Then delete from main table
DELETE FROM cloudflare_turnstile_check
WHERE timestamp < NOW() - INTERVAL '90 DAYS';
```

#### Scheduled Cleanup Job (PostgreSQL)
```sql
-- Create cleanup function
CREATE OR REPLACE FUNCTION cleanup_turnstile_checks()
RETURNS void AS $$
BEGIN
    DELETE FROM cloudflare_turnstile_check
    WHERE timestamp < NOW() - INTERVAL '90 DAYS';
END;
$$ LANGUAGE plpgsql;

-- Schedule daily at 2 AM
-- (Requires pg_cron extension)
SELECT cron.schedule('cleanup-turnstile', '0 2 * * *',
    'SELECT cleanup_turnstile_checks()');
```

### Partitioning (High Volume)

For very high volumes (>10M records), consider partitioning by timestamp:

```sql
-- PostgreSQL partitioning example
CREATE TABLE cloudflare_turnstile_check (
    -- columns as before
) PARTITION BY RANGE (timestamp);

CREATE TABLE cloudflare_turnstile_check_2024_01
    PARTITION OF cloudflare_turnstile_check
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE cloudflare_turnstile_check_2024_02
    PARTITION OF cloudflare_turnstile_check
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Continue for each month...
```

**Benefits**:
- Faster queries (partition pruning)
- Easier data management (drop old partitions)
- Better index performance

## Performance Considerations

### Query Optimization

**✅ Indexed Queries** (Fast):
```sql
-- Uses idx_turnstile_user_id
SELECT * FROM cloudflare_turnstile_check WHERE user_id = '...';

-- Uses idx_turnstile_timestamp
SELECT * FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '7 DAYS';

-- Uses idx_turnstile_user_timestamp
SELECT * FROM cloudflare_turnstile_check
WHERE user_id = '...' AND timestamp > NOW() - INTERVAL '30 DAYS';
```

**❌ Non-Indexed Queries** (Slow):
```sql
-- No index on username
SELECT * FROM cloudflare_turnstile_check WHERE username = 'john';

-- No index on hostname
SELECT * FROM cloudflare_turnstile_check WHERE hostname = 'example.com';

-- Full text search on raw_response
SELECT * FROM cloudflare_turnstile_check WHERE raw_response LIKE '%something%';
```

### Additional Index Recommendations

For specific use cases, consider adding:

#### Index on success + timestamp (failure analysis)
```sql
CREATE INDEX idx_turnstile_success_timestamp
ON cloudflare_turnstile_check(success, timestamp);
```

#### Index on realm + timestamp (realm analytics)
```sql
CREATE INDEX idx_turnstile_realm_timestamp
ON cloudflare_turnstile_check(realm_id, timestamp);
```

#### Index on IP + timestamp (IP tracking)
```sql
CREATE INDEX idx_turnstile_ip_timestamp
ON cloudflare_turnstile_check(ip_address, timestamp);
```

**Trade-off**: Each index adds ~15-30% storage overhead and slows inserts.

### Write Performance

Typical insert rates:
- **Single-threaded**: 1,000-5,000 inserts/sec
- **With indexes**: 500-2,000 inserts/sec
- **Bottleneck**: Usually network/API latency, not database

### Read Performance

With proper indexes:
- **Point queries** (by ID): <1ms
- **User history** (by user_id): 1-10ms
- **Recent failures** (last 24h): 10-100ms
- **Complex aggregations**: 100ms-1s

## Monitoring Queries

### Database Health

#### Table Size
```sql
-- PostgreSQL
SELECT
    pg_size_pretty(pg_total_relation_size('cloudflare_turnstile_check')) as total_size,
    pg_size_pretty(pg_relation_size('cloudflare_turnstile_check')) as table_size,
    pg_size_pretty(pg_indexes_size('cloudflare_turnstile_check')) as indexes_size;

-- MySQL
SELECT
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) as size_mb
FROM information_schema.TABLES
WHERE table_name = 'cloudflare_turnstile_check';
```

#### Row Count
```sql
SELECT COUNT(*) FROM cloudflare_turnstile_check;

-- Approximate (faster for large tables)
SELECT reltuples::bigint FROM pg_class
WHERE relname = 'cloudflare_turnstile_check';
```

#### Growth Rate
```sql
SELECT
    DATE(timestamp) as date,
    COUNT(*) as records_created
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '30 DAYS'
GROUP BY DATE(timestamp)
ORDER BY date;
```

## Backup Recommendations

### What to Backup
- ✅ Table data
- ✅ Indexes (recreate from schema)
- ⚠️ Raw responses (optional, can be large)

### Backup Strategies

**Full Backup** (includes all data):
```bash
# PostgreSQL
pg_dump -t cloudflare_turnstile_check keycloak > turnstile_backup.sql

# MySQL
mysqldump keycloak cloudflare_turnstile_check > turnstile_backup.sql
```

**Selective Backup** (last 90 days only):
```sql
-- PostgreSQL COPY
COPY (
    SELECT * FROM cloudflare_turnstile_check
    WHERE timestamp > NOW() - INTERVAL '90 DAYS'
) TO '/tmp/turnstile_recent.csv' WITH CSV HEADER;
```

**Backup Frequency**:
- **Audit/Compliance**: Daily
- **Analytics Only**: Weekly
- **Can Recreate from Events**: Optional

## Troubleshooting

### Table Not Created
```sql
-- Check if Liquibase ran
SELECT * FROM DATABASECHANGELOG
WHERE filename LIKE '%turnstile%';

-- Expected: Entry with id "cloudflare-turnstile-1.0.0"
```

### Slow Queries
```sql
-- Check missing indexes
EXPLAIN ANALYZE
SELECT * FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '7 DAYS';

-- Look for "Seq Scan" (bad) vs "Index Scan" (good)
```

### Lock Contention
```sql
-- PostgreSQL: Check for locks
SELECT * FROM pg_locks
WHERE relation = 'cloudflare_turnstile_check'::regclass;
```

For more help, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
