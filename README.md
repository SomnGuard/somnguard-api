# SomnGuard API

Backend API for the SomnGuard drowsiness detection system.

## Tech Stack

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 4.1.1
- **Build Tool**: Maven
- **Database**: PostgreSQL 16
- **Authentication**: JWT RS256 + API Keys (devices)
- **Architecture**: Hexagonal (Ports & Adapters) within a Modular Monolith

## Modules

| Module | Package | Responsibility |
|--------|---------|----------------|
| Security | `com.somnguard.security` | Authentication, authorization, audit |
| Parameterization | `com.somnguard.parameterization` | Configurable catalogs |
| Device Management | `com.somnguard.device_management` | Devices, assignments, config |
| Telemetry Service | `com.somnguard.telemetry_service` | Events, evidence, alert logs |
| Monitoring | `com.somnguard.monitoring` | Notifications |
| Analytics | `com.somnguard.analytics` | Timeline, metrics, reports |


## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- PostgreSQL 16 (for dev/qa/prod profiles)
- Docker (optional, for containerized run)

## Configuration

### Environment Variables (Required)

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile: `dev`, `qa`, `prod`, `test` | `dev` |
| `SERVER_PORT` | HTTP server port (default: 8080) | `8081` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/somnguard` |
| `POSTGRES_USER` | Database username | `somnguard_user` |
| `POSTGRES_PASSWORD` | Database password | `changeme` |
| `JWT_PUBLIC_KEY_PATH` | Path to RSA public key | `classpath:keys/dev/public.pem` |
| `JWT_PRIVATE_KEY_PATH` | Path to RSA private key | `classpath:keys/dev/private.pem` |
| `LIQUIBASE_CONTEXTS` | Liquibase contexts to run | `dev` |

### Profile-specific overrides

- **dev**: `application-dev.yml` - `ddl-auto: update`, debug logging, H2-compatible keys
- **qa**: `application-qa.yml` - `ddl-auto: validate`, reduced logging
- **prod**: `application-prod.yml` - `ddl-auto: validate`, probes enabled, secrets from filesystem
- **test**: `src/test/resources/application.yml` - H2 in-memory, Liquibase disabled

## Ejecución Rápida (Docker Compose)

**Requisito:** PostgreSQL corriendo en host (puerto 5432).

```bash
# 1. Generar claves JWT RS256 (primera vez / dev)
docker run --rm -v "${PWD}\src\main\resources\keys\dev:/keys" alpine/openssl genrsa -out /keys/private.pem 2048
docker run --rm -v "${PWD}\src\main\resources\keys\dev:/keys" alpine/openssl rsa -in /keys/private.pem -pubout -out /keys/public.pem

# 2. Configurar variables (una vez)
cp .env.example .env
# Editar .env → cambiar POSTGRES_PASSWORD como mínimo

# 3. Levantar API en Docker
docker compose --env-file .env up -d --build

# 4. Verificar
curl http://localhost:8080/actuator/health
# Swagger: http://localhost:8080/swagger-ui.html
```

### Tras cambios en el código
```bash
# Mismo comando recompila y reinicia
docker compose --env-file .env up -d --build
```

### Comandos útiles
```bash
# Ver logs
docker compose logs -f api

# Parar
docker compose down

# Cambiar puerto
SERVER_PORT=8081 docker compose --env-file .env up -d --build
```

## Endpoints

| Endpoint | Description | Auth |
|----------|-------------|------|
| `GET /` | Service info (version, links) | No |
| `GET /actuator/health` | Health check (liveness/readiness) | No* |
| `GET /actuator/info` | Build/app info | No* |
| `GET /actuator/prometheus` | Prometheus metrics | No* |
| `GET /actuator/metrics` | Micrometer metrics | No* |
| `GET /swagger-ui.html` | Swagger UI (OpenAPI docs) | No |
| `GET /v3/api-docs` | OpenAPI 3 JSON spec | No |
| `GET /.well-known/jwks.json` | JWKS endpoint for JWT verification | No |

> *In `qa`/`prod` profiles, actuator endpoints require authorization (`management.endpoint.health.show-details: when-authorized`)

### Authentication Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/auth/login` | POST | User login (returns JWT + refresh token) |
| `/auth/refresh` | POST | Refresh access token |
| `/auth/logout` | POST | Logout (revokes refresh token) |
| `/auth/register` | POST | Register new user (admin only) |

### Device Endpoints (API Key auth)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/telemetry/events` | POST | Ingest telemetry event |
| `/devices/{id}/config` | GET | Get device configuration |

Headers required: `X-Device-ID: <uuid>` + `X-API-Key: <key>`

## Testing

```bash
# Unit + integration tests (uses H2 in-memory, profile 'test')
mvn test -Ptest

# With coverage
mvn verify -Ptest
```

## CI/CD

GitHub Actions workflow: `.github/workflows/ci.yml`

Runs on push/PR to `main`/`develop`:
1. Build (`mvn clean compile`)
2. Test (`mvn test -Ptest`)
3. Checkstyle (`mvn checkstyle:check`)
4. Dependency Check (`mvn dependency-check:check`)
5. Docker build (`docker build`)

## Project Structure (Hexagonal per module)

```
com.somnguard.<module>/
├── application/
│   ├── port/
│   │   ├── in/          # Use case interfaces (input ports)
│   │   └── out/         # Repository/service interfaces (output ports)
│   └── usecase/         # Use case implementations
├── domain/
│   ├── model/           # Business entities
│   └── service/         # Domain services
└── adapter/
    ├── in/
    │   ├── web/         # REST controllers
    │   └── amqp/        # Message consumers (if applicable)
    └── out/
        ├── persistence/ # JPA/PostgreSQL adapters
        └── storage/     # Multimedia storage adapters (MinIO/S3)
```

## Port Configuration

Default port: **8080** (configurable via `SERVER_PORT` env var)

```bash
# Change port to avoid conflicts
SERVER_PORT=8081 mvn spring-boot:run

# Or in Docker
docker run -p 8081:8081 -e SERVER_PORT=8081 somnguard-api:latest
```

## Useful Commands

```bash
# Clean build
mvn clean install

# Run with specific profile
SPRING_PROFILES_ACTIVE=qa mvn spring-boot:run

# Skip tests during build
mvn clean install -DskipTests

# Generate OpenAPI spec
mvn spring-boot:run && curl http://localhost:8080/v3/api-docs > openapi.json
```