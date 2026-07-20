# Ro — Tactical Decision Support & Triage Engine

[![CI](https://github.com/JalilG0/ro-tactical-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/JalilG0/ro-tactical-engine/actions/workflows/ci.yml)

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

### Option A — Docker only (recommended, no Java/Maven needed)

The only prerequisite is Docker & Docker Compose. This builds the app image
and starts it alongside PostgreSQL and Redis in one step:

```bash
docker-compose up -d --build
```

That's it — the service is running at `http://localhost:8080`. Check
progress with `docker-compose logs -f app`, stop everything with
`docker-compose down`.

### Option B — Local dev loop (app on host, only DB/Redis in Docker)

Use this if you're actively editing code and want fast restarts.

**Prerequisites:** Java 21+, the included `mvnw` wrapper (no separate Maven
install needed), Docker & Docker Compose.

```bash
# 1. Start only PostgreSQL and Redis
docker-compose up -d postgres redis

# 2. Run the application on the host
./mvnw spring-boot:run
```

Don't run both options at once — each binds port 8080 and they'll conflict.

Database and Redis connection details can be overridden via environment
variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`) — see [application.yml](src/main/resources/application.yml).

### Authentication

Every `/api/v1/**` endpoint except `/api/v1/auth/login` requires a JWT
Bearer token. Log in as the seeded GCS operator to get one:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "operator", "password": "changeme"}'
```

Then pass the returned `accessToken` on every subsequent call:

```bash
curl -s -X POST http://localhost:8080/api/v1/telemetry/heartbeat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ ... }'
```

The operator credentials and JWT signing secret are dev-only defaults —
override them via `OPERATOR_USERNAME`, `OPERATOR_PASSWORD`, and `JWT_SECRET`
in any shared or deployed environment. There is no user-management
subsystem: this is a single seeded account, not an identity provider.

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

| Method | Path                          | Auth required | Description                                   |
|--------|--------------------------------|:---:|-----------------------------------------------|
| POST   | `/api/v1/auth/login`                | No  | Exchange operator credentials for a JWT       |
| POST   | `/api/v1/recommendations/calculate` | Yes | Submit target intake data, receive a ranked recommendation payload |
| POST   | `/api/v1/telemetry/heartbeat`       | Yes | Ingest a telemetry heartbeat for an asset      |

## Demo

A ready-to-run walkthrough (fleet check-in → target intake → ranked,
explained recommendation) with copy-pasteable `curl` commands lives in
[DEMO.md](DEMO.md).

## Status

This project is under active development as part of an academic capstone.
It has a minimal JWT authentication layer (a single seeded operator account,
no user management, no refresh tokens) and is intended for local/dev use
only — do not deploy it to a public-facing environment as-is.

## License

Academic project — no license granted for reuse without permission.
