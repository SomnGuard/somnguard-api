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

> `com.somnguard.platform` is transversal (out of the modules): errors, logging, observability — see [ADR-002](../somnguard-docs/docs/05-architecture/decisions/records/ADR-002-hexagonal-architecture.md).

## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+



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

> Module names use kebab-case in the catalog and snake_case in Java packages — see [architecture-document.md](../somnguard-docs/docs/05-architecture/architecture-document.md#8-backend-clean-architecture-con-puertos-y-adaptadores-hexagonal).
