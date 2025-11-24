# Docker Development Environment

## Quick Start

```bash
# Build the extension first
mvn clean package

# Start Keycloak and PostgreSQL
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Clean restart (removes all data)
docker-compose down -v && docker-compose up -d
```

## Automatic Initialization

The `docker-compose.yml` includes an automatic initialization service that:

1. Waits for Keycloak to be fully started and healthy
2. Automatically disables SSL requirement for both **master** and **maxmind-demo** realms
3. Configures Keycloak for local HTTP development (no HTTPS required)

This means **new developers can start working immediately** without manual configuration!

## Services

### Keycloak
- URL: http://localhost:8080
- Admin Console: http://localhost:8080/admin
- Admin credentials: `admin` / `admin`
- Debug port: 5005 (for remote debugging)

### PostgreSQL
- Host: localhost:5432
- Database: `keycloak`
- Username: `keycloak`
- Password: `keycloak`

### MaxMind Demo Realm
- Account Console: http://localhost:8080/realms/maxmind-demo/account
- Test user: `testuser` / `password` (configured in realm import)

## How It Works

The initialization is handled by the `init-keycloak.sh` script which:

1. Waits for Keycloak's healthcheck to pass
2. Obtains an admin access token
3. Updates SSL requirements for both realms via REST API
4. Exits automatically when complete

The `keycloak-init` container runs once on startup and exits after configuration is complete.

## Troubleshooting

### Init container didn't run
Check logs: `docker-compose logs keycloak-init`

### Keycloak not starting
Check logs: `docker-compose logs keycloak`

### Still getting SSL errors
Restart with clean slate:
```bash
docker-compose down -v
docker-compose up -d
```

### Need to rebuild JAR
```bash
mvn clean package
docker-compose restart keycloak
```

## Development Workflow

1. Make code changes
2. Run `mvn clean package`
3. Restart Keycloak: `docker-compose restart keycloak`
4. Test changes at http://localhost:8080

The JAR is mounted as a volume, so Keycloak picks up changes on restart.
