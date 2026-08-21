# SomnGuard API

Backend API for the SomnGuard drowsiness detection system.

## Tech Stack

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.x
- **Build Tool**: Maven
- **Database**: PostgreSQL 16
- **Migrations**: Liquibase
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
| Platform | `com.somnguard.platform` | Transversal: errors, logging, observability |

## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- Docker + Docker Compose
- PostgreSQL 16 (via Docker Compose)

## Quick Start

### 1. Configure Environment

```bash
cp .env.example .env.develop
# Edit .env.develop with your values
```

### 2. Start Database

```bash
docker compose --env-file .env.develop up postgres -d
```

### 3. Run Application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=develop
```

### 4. Verify

- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/api-docs`

## Project Structure

```
somnguard-api/
├── src/
│   ├── main/
│   │   ├── java/com/somnguard/
│   │   │   ├── platform/                 # Transversal code
│   │   │   ├── security/                 # Security module
│   │   │   ├── parameterization/         # Parameterization module
│   │   │   ├── device_management/        # Device management module
│   │   │   ├── telemetry_service/        # Telemetry service module
│   │   │   ├── monitoring/               # Monitoring module
│   │   │   └── analytics/                # Analytics module
│   │   └── resources/
│   │       ├── application.yml           # Main configuration
│   │       ├── liquibase.properties      # Liquibase config
│   │       ├── db/changelog/             # Liquibase master changelog
│   │       └── db/migration/             # SQL migrations (organized by type)
│   └── test/                             # Test structure mirrors main
├── docker/
├── docs/
├── .github/workflows/
├── scripts/
├── pom.xml
├── docker-compose.yml
├── Dockerfile
├── .env.example
└── README.md
```

### Hexagonal Structure (per module)

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

## Database Migrations

Migrations are organized following the structure from the architecture document:

```
src/main/resources/db/migration/
├── 01_ddl/
│   ├── 00_extensions/
│   ├── 01_schemas/
│   ├── 02_types/
│   ├── 03_tables/
│   ├── 04_alter/
│   ├── 05_views/
│   ├── 06_functions/
│   ├── 07_procedures/
│   ├── 08_triggers/
│   └── 10_indexes/
├── 02_dml/
│   ├── 00_inserts/
│   ├── 01_updates/
│   ├── 02_deletes/
│   ├── 03_upserts/
│   └── 04_patches/
├── 03_dcl/
│   ├── 00_roles/
│   ├── 01_grants/
│   └── 02_policies/
├── 04_tcl/
│   ├── 00_transaction_blocks/
│   ├── 01_manual_recoveries/
│   └── 02_release_tags/
└── 05_rollbacks/
    ├── 01_ddl/
    ├── 02_dml/
    ├── 03_dcl/
    └── 04_tcl/
```

### Running Migrations

```bash
# Auto-run on startup (default)
mvn spring-boot:run

# Manual via Maven
mvn liquibase:update -Dspring-boot.run.profiles=develop

# Rollback
mvn liquibase:rollback -Dliquibase.rollbackCount=1 -Dspring-boot.run.profiles=develop
```

## Environments

| Environment | Profile | Branch | Migrations |
|-------------|---------|--------|------------|
| Develop | `develop` | `develop` | Forward + rollback |
| QA | `qa` | `qa` | Forward-only |
| Production | `main` | `main` | Forward-only |

Configuration files:
- `.env.develop` - Development
- `.env.qa` - QA
- `.env.main` - Production

## Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -Pintegration-tests
```

## API Documentation

- **OpenAPI/Swagger**: `http://localhost:8080/swagger-ui.html`
- **API Design**: See [`docs/07-api-design/api-design.md`](../somnguard-docs/docs/07-api-design/api-design.md)
- **Authentication**: See [`docs/07-api-design/authentication.md`](../somnguard-docs/docs/07-api-design/authentication.md)

## Architecture Decisions

- [ADR-001: Backend in Java Spring Boot](../somnguard-docs/docs/05-architecture/decisions/records/ADR-001-backend-java-spring-boot.md)
- [ADR-002: Hexagonal Architecture](../somnguard-docs/docs/05-architecture/decisions/records/ADR-002-hexagonal-architecture.md)
- [ADR-003: Analytics Module](../somnguard-docs/docs/05-architecture/decisions/records/ADR-003-analytics-module.md)

## CI/CD

GitHub Actions workflows in `.github/workflows/`:
- CI: Build, test, validate
- CD: Deploy to environments (develop → qa → main)

## License

See [LICENSE](../somnguard-docs/LICENSE)