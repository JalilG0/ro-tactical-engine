# Ro — Tactical Decision Support & Triage Engine

Stateless, reactive REST service that computes ranked tactical recommendation
payloads from real-time telemetry and target intake events. Built as a
university graduation project.

> **Scope note:** Ro is a decision-*support* engine only. It is not a
> Command & Control (C2) or flight-control system, and it does not issue
> commands to any asset. It produces ranked recommendations; all final
> engagement decisions remain with the human operator.

## Overview

Ground Control Station (GCS) operators send telemetry heartbeats and target
intake events to Ro. The engine runs each candidate asset through a pipeline
of scoring stages — exclusion filtering, tactical scoring, weather/EW
adjustment, and deterministic tie-breaking — and returns a ranked Top-10
recommendation list with an accompanying explanation for each result.

## Architecture

```
com.mil.trdss.ro
├── controller   REST gateway (recommendation + telemetry endpoints)
├── service      Orchestrates the recommendation pipeline
├── engine       Exclusion, scoring, geo, and explanation (XAI) logic
├── domain       DTOs and enums
└── repository   JPA persistence + Redis-backed asset cache
```

See [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) for the core design
rules the engine enforces (stale-data rejection, shadow-lock TTL, exclusion
criteria, deterministic tie-breaking).

## Tech Stack

| Layer          | Technology                    |
|----------------|--------------------------------|
| Language       | Java 21                        |
| Framework      | Spring Boot 4.1                |
| Persistence    | PostgreSQL (JPA/Hibernate)     |
| Cache          | Redis                          |
| API docs       | springdoc-openapi (Swagger UI) |
| Build          | Maven                          |

## Getting Started

### Prerequisites
- Java 21+
- Maven (or use the included `mvnw` wrapper)
- Docker & Docker Compose (for PostgreSQL and Redis)

### Run locally

```bash
# 1. Start PostgreSQL and Redis
docker-compose up -d

# 2. Run the application
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080` by default. Database and
Redis connection details can be overridden via environment variables
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`) — see
[application.yml](src/main/resources/application.yml).

### API Documentation

Once running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

### Run tests

```bash
./mvnw test
```

## API Endpoints

| Method | Path                          | Description                                   |
|--------|--------------------------------|-----------------------------------------------|
| POST   | `/api/v1/recommendations/calculate` | Submit target intake data, receive a ranked recommendation payload |
| POST   | `/api/v1/telemetry/heartbeat`       | Ingest a telemetry heartbeat for an asset      |

## Status

This project is under active development as part of an academic capstone.
It currently has no authentication/authorization layer and is intended for
local/dev use only — do not deploy it to a public-facing environment as-is.

## License

Academic project — no license granted for reuse without permission.
