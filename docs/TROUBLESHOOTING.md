# Troubleshooting Guide

Common issues and solutions for the Cloudflare Turnstile Keycloak extension.

## Quick Diagnostics

Run these checks first:

```bash
# 1. Check Keycloak logs
tail -f /opt/keycloak/data/log/keycloak.log | grep -i turnstile

# 2. Check database table exists
psql -d keycloak -c "SELECT COUNT(*) FROM cloudflare_turnstile_check;"

# 3. Test Cloudflare API connectivity
curl -v https://challenges.cloudflare.com/turnstile/v0/siteverify

# 4. Check recent verifications
psql -d keycloak -c "SELECT success, error_codes, timestamp FROM cloudflare_turnstile_check ORDER BY timestamp DESC LIMIT 5;"
```

## Widget Issues

### Widget Not Appearing

**Symptoms**: Login page shows but no Turnstile widget

**Causes & Solutions**:

1. **HTTPS Not Enabled**
   - Turnstile requires HTTPS
   - Check: Browser URL starts with `https://`
   - Fix: Enable HTTPS on Keycloak or use reverse proxy

2. **JavaScript Blocked**
   - Browser has JavaScript disabled
   - Ad blockers blocking Cloudflare scripts
   - Fix: Enable JavaScript, whitelist `challenges.cloudflare.com`

3. **Network Blocked**
   - Client can't reach `challenges.cloudflare.com`
   - Check: Browser console for network errors
   - Fix: Allow HTTPS to Cloudflare domains

4. **Wrong Flow Active**
   - Turnstile flow not bound to realm
   - Check: Realm Settings → Login → Browser Flow
   - Fix: Select "Browser with Turnstile" flow

**Debug Steps**:
```javascript
// Open browser console (F12)
// Check for errors like:
// "Failed to load resource: https://challenges.cloudflare.com/..."
// "Refused to connect to 'https://challenges.cloudflare.com' because it violates CSP"
```

### Widget Shows But Doesn't Load

**Symptoms**: Widget container appears but stays blank or shows loading forever

**Causes**:

1. **Invalid Site Key**
   ```
   Check Keycloak logs for:
   "Turnstile widget failed to initialize"
   ```
   - Fix: Verify site key in authenticator config matches Cloudflare dashboard

2. **Domain Mismatch**
   - Widget configured for `auth.example.com`
   - Accessing via `localhost` or different domain
   - Fix: Add all domains to Turnstile site configuration in Cloudflare

3. **CSP (Content Security Policy) Blocking**
   - Fix: Add to CSP headers:
     ```
     script-src 'self' https://challenges.cloudflare.com;
     frame-src 'self' https://challenges.cloudflare.com;
     ```

### Widget Appears Multiple Times

**Symptoms**: Two or more widgets on same page

**Cause**: Template included multiple times or JavaScript re-initializing

**Fix**:
1. Check custom theme for duplicate includes
2. Review FreeMarker template for loops
3. Clear Keycloak cache:
   ```bash
   rm -rf /opt/keycloak/data/tmp/*
   /opt/keycloak/bin/kc.sh build
   ```

## Verification Failures

### All Verifications Failing

**Symptoms**: Every login fails with "Security verification failed"

**Causes & Solutions**:

1. **Wrong Secret Key**
   ```sql
   -- Check recent attempts
   SELECT error_codes FROM cloudflare_turnstile_check
   WHERE timestamp > NOW() - INTERVAL '1 HOUR';

   -- Look for: "invalid-input-secret"
   ```
   - Fix: Update secret key in authenticator config

2. **Keycloak Can't Reach Cloudflare API**
   ```bash
   # Test from Keycloak server
   curl -v https://challenges.cloudflare.com/turnstile/v0/siteverify
   ```
   - Check firewall rules
   - Check proxy settings
   - Verify DNS resolution

3. **Timeout Issues**
   - Logs show: "SocketTimeoutException" or "ConnectTimeoutException"
   - Fix: Increase timeouts in config (default 5000ms)
   - Or check network latency to Cloudflare

4. **Fail Mode Set to FAIL_CLOSED with API Issues**
   - If Cloudflare API is down, all logins blocked
   - Temporary fix: Change to FAIL_OPEN
   - Permanent fix: Resolve API connectivity

### Intermittent Failures

**Symptoms**: Some verifications succeed, others fail randomly

**Debug Query**:
```sql
SELECT
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as total,
    SUM(CASE WHEN success THEN 1 ELSE 0 END) as successful,
    SUM(CASE WHEN NOT success THEN 1 ELSE 0 END) as failed
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '24 HOURS'
GROUP BY hour
ORDER BY hour DESC;
```

**Causes**:

1. **Token Expiration**
   - Error: `timeout-or-duplicate`
   - User waited >5 minutes between challenge and submission
   - Fix: Inform users to complete login promptly

2. **Token Reuse**
   - Same token submitted twice
   - Fix: Ensure form doesn't allow double-submission

3. **Network Issues**
   - Intermittent connectivity to Cloudflare
   - Check network stability
   - Monitor with:
     ```bash
     ping -c 100 challenges.cloudflare.com
     ```

### Specific Users Always Failing

**Symptoms**: Same user/IP consistently fails

**Debug**:
```sql
SELECT
    username,
    ip_address,
    COUNT(*) as attempts,
    SUM(CASE WHEN success THEN 1 ELSE 0 END) as successful
FROM cloudflare_turnstile_check
WHERE username = 'problematic-user'
GROUP BY username, ip_address;
```

**Causes**:

1. **User on Blocklist**
   - Check IP blocklist configuration
   - Verify user IP isn't in blocklist range

2. **Bot Detection**
   - Cloudflare detecting automated behavior
   - User may be using automation tools
   - Fix: User should try different browser, disable extensions

3. **Old Browser**
   - Very old browsers may not support Turnstile
   - Fix: User should update browser

## Database Issues

### Table Not Created

**Symptoms**: Queries fail with "table does not exist"

**Check Liquibase**:
```sql
SELECT * FROM DATABASECHANGELOG
WHERE id = 'cloudflare-turnstile-1.0.0';
```

**If empty**:

1. **JAR Not Loaded**
   - Check: `/opt/keycloak/providers/zymlabs-cloudflare-turnstile-provider.jar` exists
   - Fix: Copy JAR and rebuild Keycloak

2. **Liquibase Error**
   - Check logs for:
     ```
     ERROR [org.keycloak.connections.jpa.updater.liquibase.LiquibaseJpaUpdaterProvider]
     ```
   - Fix: Check database permissions, review error details

3. **Wrong Database Selected**
   - Keycloak using different database than expected
   - Check: `KC_DB_URL` environment variable

**Manual Creation** (emergency only):
```sql
-- Run the SQL from cloudflare-turnstile-changelog.xml
CREATE TABLE cloudflare_turnstile_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- ... rest of schema
);
```

### Records Not Being Saved

**Symptoms**: `recordVerifications=true` but table stays empty

**Causes**:

1. **recordVerifications Disabled**
   - Check authenticator config
   - Fix: Enable "Record Verifications"

2. **Transaction Rollback**
   - Check logs for database errors
   - Fix: Resolve database connection issues

3. **Permissions Issue**
   - Keycloak user can't INSERT
   - Fix: Grant INSERT permission

**Verify**:
```sql
-- Try manual insert
INSERT INTO cloudflare_turnstile_check
    (realm_id, ip_address, timestamp, success)
VALUES
    ('test-realm', '127.0.0.1', NOW(), true);

-- If this fails, it's a permissions issue
```

### Database Growing Too Large

**Symptoms**: Table consuming too much disk space

**Check Size**:
```sql
-- PostgreSQL
SELECT pg_size_pretty(pg_total_relation_size('cloudflare_turnstile_check'));

-- MySQL
SELECT
    ROUND(((data_length + index_length) / 1024 / 1024), 2) as size_mb
FROM information_schema.TABLES
WHERE table_name = 'cloudflare_turnstile_check';
```

**Solutions**:

1. **Delete Old Records**
   ```sql
   DELETE FROM cloudflare_turnstile_check
   WHERE timestamp < NOW() - INTERVAL '90 DAYS';
   ```

2. **Disable Recording**
   - Set `recordVerifications=false`
   - Only events will be logged

3. **Implement Partitioning**
   - See DATABASE.md for partition strategy

## Configuration Issues

### Changes Not Taking Effect

**Symptoms**: Updated configuration but behavior unchanged

**Fixes**:

1. **Cache Not Cleared**
   ```bash
   # Restart Keycloak
   systemctl restart keycloak
   # Or for Docker
   docker-compose restart keycloak
   ```

2. **Wrong Flow Being Used**
   - Check which flow is actually bound to realm
   - Realm Settings → Login → Browser Flow

3. **Multiple Configs Exist**
   - Deleted and recreated flow/execution
   - Old config may still be referenced
   - Fix: Delete all copies, create fresh

### Can't Access Configuration

**Symptoms**: "Config" button missing or greyed out

**Causes**:

1. **Not Set as REQUIRED**
   - Set execution to REQUIRED first
   - Then config button appears

2. **Permissions Issue**
   - User doesn't have admin rights
   - Fix: Use admin account

### Secret Key Not Saving

**Symptoms**: Secret key field empty after save

**This is NORMAL**:
- Password fields don't display saved values for security
- Value IS saved (encrypted in database)
- To verify: Try authentication, check logs

## Performance Issues

### Slow Login Page

**Symptoms**: Login page takes >3 seconds to load

**Causes**:

1. **Widget Loading Slow**
   - Cloudflare CDN slow from user location
   - Check browser network tab
   - Usually <500ms, if >2s investigate

2. **Reverse Proxy Issues**
   - Proxy adding latency
   - Check proxy logs

3. **Database Query Slow**
   - Too many records in table
   - Fix: Add indexes or clean old data

### Timeout Errors

**Symptoms**: "Read timeout" or "Connect timeout" in logs

**Solutions**:

1. **Increase Timeouts**
   - Default: 5000ms
   - Try: 10000ms for slow networks
   - Config: Connect Timeout and Read Timeout

2. **Network Issues**
   ```bash
   # Test latency
   curl -w "@-" -o /dev/null -s https://challenges.cloudflare.com/turnstile/v0/siteverify <<'EOF'
   time_namelookup:  %{time_namelookup}\n
   time_connect:  %{time_connect}\n
   time_total:  %{time_total}\n
   EOF
   ```

3. **Proxy Timeouts**
   - Check reverse proxy timeout settings
   - Ensure proxy timeout > Keycloak timeout

## Error Messages

### "Security verification failed"

**User sees**: Generic error on login page

**Causes**:
- Failed Turnstile challenge
- Invalid token
- API verification failed

**Debug**:
1. Check events for user's login attempt
2. Check database for error_codes
3. Review troubleshooting for specific error code

### "Access denied. Your IP address is blocked"

**Cause**: User IP in blocklist

**Fix**:
1. Remove IP from blocklist if added by mistake
2. Or inform user they're blocked

### "An error occurred during security verification"

**Cause**: Cloudflare API error or network issue

**Debug**:
1. Check Cloudflare status: https://www.cloudflarestatus.com/
2. Check Keycloak logs for stack trace
3. Verify API connectivity

## Common Deployment Issues

### Docker Container Issues

**Logs not showing Turnstile initialization**:

1. **Volume mount incorrect**
   ```yaml
   # Correct
   - ./zymlabs-cloudflare-turnstile-provider.jar:/opt/bitnami/keycloak/providers/zymlabs-cloudflare-turnstile-provider.jar:rw
   ```

2. **Container not rebuilt**
   ```bash
   docker-compose down
   docker-compose up --build
   ```

### Kubernetes Deployment

**Pod restarts after installing**:

- Memory limit too low
- Increase memory limit in deployment.yaml

**ConfigMap not updating**:
- Delete pod to force recreation
- Or use rolling restart

## Advanced Debugging

### Enable Detailed Logging

```bash
# Keycloak Quarkus
export KC_LOG_LEVEL_COM_ZYMLABS_KEYCLOAK_CLOUDFLARE_TURNSTILEPROVIDER=DEBUG
/opt/keycloak/bin/kc.sh start

# Docker
environment:
  KC_LOG_LEVEL_COM_ZYMLABS_KEYCLOAK_CLOUDFLARE_TURNSTILEPROVIDER: DEBUG
```

### Network Debugging

```bash
# Packet capture on Keycloak server
tcpdump -i any -w turnstile.pcap host challenges.cloudflare.com

# Analyze in Wireshark
wireshark turnstile.pcap
```

### Database Debugging

```sql
-- Check all failures in last hour
SELECT * FROM cloudflare_turnstile_check
WHERE success = false
  AND timestamp > NOW() - INTERVAL '1 HOUR'
ORDER BY timestamp DESC;

-- Group by error code
SELECT error_codes, COUNT(*) as count
FROM cloudflare_turnstile_check
WHERE success = false
  AND timestamp > NOW() - INTERVAL '24 HOURS'
GROUP BY error_codes
ORDER BY count DESC;
```

## Getting Help

### Before Asking for Help

Gather this information:

1. **Keycloak version**: `cat /opt/keycloak/version.txt`
2. **Extension version**: Check JAR filename
3. **Database**: PostgreSQL/MySQL/etc + version
4. **Error logs**: Last 50 lines with errors
5. **Configuration**: Sanitized config (NO secret key!)
6. **Recent events**: Database query results

### Where to Get Help

1. **GitHub Issues**: https://github.com/zymlabs/keycloak-cloudflare-turnstile/issues
2. **Documentation**: docs/ directory
3. **Cloudflare Docs**: https://developers.cloudflare.com/turnstile/
4. **Keycloak Docs**: https://www.keycloak.org/documentation

### What NOT to Share

- ❌ Secret keys
- ❌ Database passwords
- ❌ User emails or PII
- ❌ Internal IP addresses (sanitize logs)

### Useful Debug Info to Share

- ✅ Error messages
- ✅ Keycloak version
- ✅ Extension version
- ✅ Error codes from database
- ✅ Sanitized configuration
- ✅ Stack traces (without sensitive data)
