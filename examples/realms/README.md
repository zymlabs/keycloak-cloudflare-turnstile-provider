# Example Realms

This directory is intended for example Keycloak realm exports that demonstrate Cloudflare Turnstile integration.

## Creating Your Own Example Realm

1. Configure a realm with Turnstile authentication flow
2. Export the realm:
   ```bash
   /opt/keycloak/bin/kc.sh export --realm your-realm --file your-realm.json
   ```
3. **Sanitize the export** - Remove:
   - Secret keys
   - User data
   - Internal IPs
   - Any sensitive configuration

## Importing Example Realms

```bash
# Via CLI
/opt/keycloak/bin/kc.sh import --file examples/realms/your-realm.json

# Via Docker
docker-compose exec keycloak /opt/bitnami/keycloak/bin/kc.sh import --file /opt/bitnami/keycloak/data/import/your-realm.json
```

## Note

Example realms will be added in future releases. For now, follow the [SETUP.md](../../docs/SETUP.md) guide to configure manually.
