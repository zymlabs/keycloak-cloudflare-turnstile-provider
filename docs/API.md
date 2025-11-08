# Cloudflare Turnstile API Reference

Technical reference for the Cloudflare Turnstile API used by this extension.

## API Endpoint

### Siteverify Endpoint

**URL**: `https://challenges.cloudflare.com/turnstile/v0/siteverify`

**Method**: POST

**Content-Type**: `application/x-www-form-urlencoded` or `application/json`

**Purpose**: Server-side verification of Turnstile challenge responses

## Request Format

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `secret` | string | Yes | Your Cloudflare Turnstile secret key |
| `response` | string | Yes | The `cf-turnstile-response` token from client |
| `remoteip` | string | No | User's IP address (recommended for analytics) |

### Example Request (Form-Encoded)

```http
POST /turnstile/v0/siteverify HTTP/1.1
Host: challenges.cloudflare.com
Content-Type: application/x-www-form-urlencoded

secret=0x4AAAAAAA...secretkey&response=TOKEN_FROM_WIDGET&remoteip=203.0.113.1
```

### Example Request (JSON)

```http
POST /turnstile/v0/siteverify HTTP/1.1
Host: challenges.cloudflare.com
Content-Type: application/json

{
  "secret": "0x4AAAAAAA...secretkey",
  "response": "TOKEN_FROM_WIDGET",
  "remoteip": "203.0.113.1"
}
```

### How This Extension Makes Requests

The extension uses Apache HttpClient with form-encoded requests:

```java
List<NameValuePair> params = new ArrayList<>();
params.add(new BasicNameValuePair("secret", secretKey));
params.add(new BasicNameValuePair("response", token));
params.add(new BasicNameValuePair("remoteip", ipAddress));

HttpPost post = new HttpPost(SITEVERIFY_URL);
post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
```

## Response Format

### Success Response

```json
{
  "success": true,
  "challenge_ts": "2024-01-15T12:34:56.789Z",
  "hostname": "auth.example.com",
  "error-codes": [],
  "action": "login",
  "cdata": "session-data-if-provided"
}
```

### Failure Response

```json
{
  "success": false,
  "error-codes": [
    "timeout-or-duplicate"
  ],
  "challenge_ts": null,
  "hostname": "auth.example.com"
}
```

### Response Fields

| Field | Type | Always Present | Description |
|-------|------|----------------|-------------|
| `success` | boolean | Yes | `true` if verification passed, `false` otherwise |
| `challenge_ts` | string | When success=true | ISO 8601 timestamp of challenge completion |
| `hostname` | string | Usually | Hostname where challenge was completed |
| `error-codes` | array | When success=false | List of error codes (see below) |
| `action` | string | Optional | Action name if configured in widget |
| `cdata` | string | Optional | Custom data if passed from widget |

## Error Codes

### Common Error Codes

| Error Code | Description | Cause | Solution |
|------------|-------------|-------|----------|
| `missing-input-secret` | Secret key not provided | Server configuration error | Verify secret key is set |
| `invalid-input-secret` | Secret key is invalid | Wrong key or typo | Check secret key in Cloudflare dashboard |
| `missing-input-response` | Response token not provided | Client didn't submit token | Ensure widget submission works |
| `invalid-input-response` | Response token is invalid | Token expired, tampered, or already used | User may need to retry |
| `timeout-or-duplicate` | Token expired or reused | Token >5 min old or submitted twice | User should refresh page |
| `internal-error` | Cloudflare internal error | Temporary Cloudflare issue | Retry request, check status page |
| `bad-request` | Malformed request | Invalid request format | Check request format |

### Error Code Handling in Extension

```java
if (jsonResponse.has("error-codes") && jsonResponse.get("error-codes").isArray()) {
    List<String> errors = new ArrayList<>();
    jsonResponse.get("error-codes").forEach(node -> errors.add(node.asText()));
    errorCodes = String.join(",", errors);
}
```

Stored as comma-separated string in database: `"timeout-or-duplicate,invalid-input-response"`

## Rate Limits

**Official Limits**: Cloudflare doesn't publish specific rate limits

**Observed Behavior**:
- Normal traffic: No limits observed
- Extreme volumes: May be rate-limited per IP
- Best practice: Implement backoff on repeated failures

**Extension Timeouts**:
- Connect timeout: 5000ms (default)
- Read timeout: 5000ms (default)
- Total max time: 10 seconds

## Token Lifecycle

### 1. Widget Renders
```javascript
// Cloudflare script loads
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>

// Widget renders
<div class="cf-turnstile" data-sitekey="0x4AAAA..."></div>
```

### 2. User Completes Challenge
- Managed: May auto-complete or show challenge
- Non-interactive: Minimal interaction
- Invisible: Background verification

### 3. Token Generated
```javascript
// Token placed in hidden field
<input type="hidden" name="cf-turnstile-response" value="TOKEN_HERE">
```

**Token characteristics**:
- Single-use only (verifying twice fails)
- Expires after ~5 minutes
- Tied to client IP (optional validation)
- Bound to specific hostname

### 4. Form Submission
```http
POST /auth/realms/myrealm/login-actions/authenticate
...
cf-turnstile-response=TOKEN_HERE
```

### 5. Server Verification
Extension extracts token and verifies with Cloudflare.

### 6. Token Consumed
Token can never be used again. User must complete new challenge.

## Performance Characteristics

### Typical Response Times

Based on production usage:

| Percentile | Response Time |
|------------|---------------|
| P50 (median) | 100-300ms |
| P95 | 500-1000ms |
| P99 | 1000-2000ms |
| P99.9 | 2000-3000ms |

**Recommendation**: Set read timeout to 5000ms (default) to handle P99.9+.

### Network Path

```
Keycloak Server
    ↓ (HTTPS POST)
challenges.cloudflare.com
    ↓ (Processing)
Cloudflare Edge Network
    ↓ (Response)
Keycloak Server
```

**Latency factors**:
- Geographic distance to Cloudflare edge
- Network quality
- Cloudflare load
- DNS resolution time

## API Availability

**Cloudflare SLA**:
- Free tier: No SLA
- Pro+: 99.95% uptime SLA (if Cloudflare is on your domain)

**Status Page**: https://www.cloudflarestatus.com/

**Extension Behavior on API Unavailable**:
- `FAIL_CLOSED`: Deny authentication
- `FAIL_OPEN`: Allow authentication with warning log

## Security Considerations

### Token Security

✅ **Secure Practices**:
- Tokens are single-use (prevents replay attacks)
- Short expiration (5 minutes)
- Tied to hostname
- HTTPS-only transmission

❌ **Insecure Practices to Avoid**:
- Don't cache tokens
- Don't reuse tokens
- Don't share tokens across sessions
- Don't extend token lifetime

### Secret Key Security

⚠️ **Critical**:
- Never expose secret key client-side
- Never commit to version control
- Rotate periodically (every 90-180 days)
- Store encrypted (Keycloak does this automatically)

### API Request Security

**This extension uses**:
- HTTPS only
- Configurable timeouts
- Error handling
- Request/response validation

## Debugging API Calls

### Enable Debug Logging

Add to Keycloak configuration:
```bash
KC_LOG_LEVEL_COM_ZYMLABS_KEYCLOAK_CLOUDFLARE_TURNSTILEPROVIDER=DEBUG
```

### Log Output Example

```
DEBUG [com.zymlabs.keycloak.cloudflare.turnstileprovider.CloudflareTurnstileService]
Verifying Turnstile token for IP: 203.0.113.1

DEBUG [com.zymlabs.keycloak.cloudflare.turnstileprovider.CloudflareTurnstileService]
Turnstile API response: {"success":true,"challenge_ts":"2024-01-15T12:34:56Z",...}

INFO [com.zymlabs.keycloak.cloudflare.turnstileprovider.CloudflareTurnstileService]
Turnstile verification successful for IP: 203.0.113.1, hostname: auth.example.com
```

### Manual API Testing

Test API directly with curl:

```bash
curl -X POST https://challenges.cloudflare.com/turnstile/v0/siteverify \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "secret=YOUR_SECRET_KEY" \
  -d "response=TEST_TOKEN" \
  -d "remoteip=203.0.113.1"
```

**Test with invalid token**:
```bash
curl -X POST https://challenges.cloudflare.com/turnstile/v0/siteverify \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "secret=YOUR_SECRET_KEY" \
  -d "response=invalid_token_12345"
```

Expected response:
```json
{
  "success": false,
  "error-codes": ["invalid-input-response"]
}
```

## API Monitoring

### Health Check Query

```sql
-- Check API success rate in last hour
SELECT
    COUNT(*) as total_calls,
    SUM(CASE WHEN success THEN 1 ELSE 0 END) as successful,
    SUM(CASE WHEN error_codes LIKE '%internal-error%' THEN 1 ELSE 0 END) as api_errors,
    SUM(CASE WHEN error_codes LIKE '%timeout%' THEN 1 ELSE 0 END) as timeouts
FROM cloudflare_turnstile_check
WHERE timestamp > NOW() - INTERVAL '1 HOUR';
```

### Alert Triggers

Set up alerts for:
- API error rate > 5%
- Timeout rate > 10%
- No successful verifications in 10 minutes (might indicate API down)

## API Changes and Versioning

**Current Version**: `/turnstile/v0/siteverify`

**Change Policy**: Cloudflare maintains backward compatibility

**Monitoring Changes**:
- Subscribe to Cloudflare blog
- Monitor https://developers.cloudflare.com/turnstile/
- Watch for deprecation notices

**Extension Updates**:
- Extension will be updated for API changes
- Check GitHub releases for updates
- Subscribe to release notifications

## Additional Resources

- **Official Docs**: https://developers.cloudflare.com/turnstile/
- **API Reference**: https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- **Status Page**: https://www.cloudflarestatus.com/
- **Support**: Cloudflare dashboard → Support

## Differences from reCAPTCHA

For teams migrating from reCAPTCHA:

| Feature | reCAPTCHA | Turnstile |
|---------|-----------|-----------|
| Endpoint | google.com/recaptcha/api/siteverify | challenges.cloudflare.com/turnstile/v0/siteverify |
| Param name | `g-recaptcha-response` | `cf-turnstile-response` |
| Score | Yes (v3) | No (pass/fail only) |
| Privacy | Tracks users | Privacy-first, no tracking |
| Widget | Multiple types | Managed/Non-interactive/Invisible |

**Migration**: Configuration changes only, core logic similar.
