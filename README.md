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
| Platform | `com.somnguard.platform` | Transversal: errors, logging, observability |

## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+



### Hexagonal Structure (per module)

```
com.somnguard.api<module>/
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
    │   └── web/         # REST controllers
    └── out/
        ├── persistence/ # JPA/PostgreSQL adapters
        └── storage/     # Multimedia storage adapters (MinIO/S3)
```
