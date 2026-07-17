# System Architecture

## Project Name
Ro (Tactical Decision Support & Triage Engine)

## Core Philosophy
This system is **NOT** a Command & Control (C2) or flight control system. It is a stateless, reactive microservice that calculates and returns Top-10 hierarchical tactical recommendation payloads for Ground Control Station (GCS) operators. It never issues a command to any asset — it only produces a ranked, explained recommendation. The human operator always makes the final call.

## Package Hierarchy
- `domain` — DTOs, Models, Enums
- `repository` — JPA (audit history) & Redis (live fleet cache)
- `engine` — Exclusion, Scoring, Geo, XAI Explanation
- `controller` — REST Gateway
- `service` — Orchestrates the end-to-end pipeline

## Request Flow

```mermaid
sequenceDiagram
    participant GCS as GCS Operator
    participant API as RecommendationController
    participant SVC as RecommendationService
    participant CACHE as FleetStatusCacheRepository (Redis)
    participant EXC as ExclusionEngine
    participant SCORE as TacticalScoringEngine
    participant XAI as XaiExplanationGenerator
    participant DB as RecommendationHistoryRepository (PostgreSQL)

    GCS->>API: POST /telemetry/heartbeat
    API->>CACHE: saveTelemetry(assetId, status, fuel, location...)

    GCS->>API: POST /recommendations/calculate (TargetIntakeDTO)
    API->>SVC: calculateRecommendation(intake)
    SVC->>SVC: reject if stale (>10s old) or duplicate eventId
    SVC->>CACHE: getActiveFreePool()
    CACHE-->>SVC: List<TelemetryHeartbeatDTO>
    SVC->>EXC: filterEligibleAssets(pool, intake)
    EXC-->>SVC: eligible assets + exclusion reasons
    SVC->>SCORE: generateRankedGroups(intake, eligible)
    SCORE-->>SVC: ranked model groups (Top-10) + overmatch/swarm flags
    SVC->>XAI: generate(intake, excluded, scoringOutcome)
    XAI-->>SVC: human-readable explanation string
    SVC->>DB: persist audit record
    SVC->>CACHE: applyShadowLock(assetIds in top group, TTL=30s)
    SVC-->>API: TacticalRecommendationDTO
    API-->>GCS: 200 OK (ranked recommendation + explanation)
```

## Pipeline Stages Explained

The core of the project is `RecommendationService#calculateRecommendation`, which
runs every incoming target through five deterministic stages:

**1. Validity gate.** Two guard checks run before any scoring happens:
   - *Stale-data rejection* — if the intake payload's timestamp is more than 10
     seconds old, it's rejected outright (`StaleDataException`). This models the
     reality that tactical data ages out fast and must never be acted on late.
   - *Idempotency* — each event carries an `eventId`; Redis (`SETNX` with a 10s
     TTL) guarantees the same event is never processed twice
     (`DuplicateEventException`).

**2. Exclusion (`ExclusionEngine`).** Every asset in the live fleet pool is
   checked against hard disqualifiers, in order:
   - Status `MAINTENANCE_REQUIRED` or `MANUAL_OVERRIDE` → excluded.
   - Zero munitions remaining → excluded.
   - Insufficient fuel for a round trip to the target plus a 15% safety
     margin (`bingoFuelThresholdPercent`, using haversine distance via
     `GeoUtils`) → excluded.

   Assets that pass all four checks move to scoring; everything else is
   recorded with a machine-readable reason code (surfaced later in the XAI
   explanation).

**3. Scoring (`TacticalScoringEngine`).** Each eligible asset gets a numeric
   score built from its currently loaded munition and the target context:
   - Base score = munition power × 10.
   - **Overmatch bonus** if munition power strictly exceeds the target's
     threat level.
   - **Weather adjustment** — dense fog penalizes laser-guided munitions
     (can't maintain a lock through fog) and rewards GPS/INS-guided ones.
   - **Movement adjustment** — a moving target favors laser-guided munitions
     (can re-track in flight); a stationary target favors GPS/INS (commits to
     a fixed coordinate at launch, which is fine if the target isn't moving).
   - **EW penalty** — an active jammer polygon forces a detour waypoint,
     applied uniformly to every asset.

   Assets are then grouped by model, and within each model group further
   clustered by location + status. A **Swarm Allocation** rule pairs up two
   medium-tier assets when no single asset alone achieves overmatch — i.e.
   the engine can recommend "send two" instead of "send one" when that's the
   only way to guarantee sufficient effect.

**4. Deterministic tie-breaking.** Any score tie is resolved, in strict
   order, by: (1) higher fuel percentage, (2) alphanumeric `assetId`. This
   guarantees the same inputs always produce the same output — a
   reproducibility requirement for an auditable recommendation system.

**5. Explanation (`XaiExplanationGenerator`) + audit trail.** A natural
   -language explanation is generated describing *why* the top group was
   chosen (weather, movement, EW, overmatch vs. swarm, tie-break) and *why*
   other assets were excluded. The full recommendation — including the
   ranked payload as JSON — is persisted to PostgreSQL for auditability, and
   the top-ranked assets receive a 30-second **Shadow Lock** in Redis so two
   concurrent requests can't be recommended the same asset.

## Example Scenario

A `TargetIntakeDTO` arrives for a moving target with threat level 6, under
active EW jamming. Three eligible assets survive exclusion:

| Asset | Munition | Power | Base | Overmatch | Moving+Laser | EW | Total |
|-------|----------|-------|------|-----------|--------------|-----|-------|
| A-101 | BOZOK    | 6     | 60   | +0 (6=6, not >6) | +25 | -5 | 80 |
| A-102 | TOLUN    | 8     | 80   | +50       | -20          | -5  | 105 |
| A-103 | MAM_C    | 5     | 50   | +0        | +25          | -5  | 70 |

`A-102` (TOLUN) ranks highest — its GPS/INS guidance takes a penalty for the
moving target, but its raw overmatch bonus (power 8 > threat 6) dominates.
The engine returns it as the top recommendation, applies a 30s shadow lock
to it, and the XAI explanation states the EW reroute, the movement-guidance
trade-off, and the overmatch justification in plain language.

## Test Coverage

| Test class | What it verifies |
|------------|-------------------|
| `ExclusionEngineTest` | Each exclusion reason (maintenance, manual override, zero munitions, bingo fuel) fires independently and correctly |
| `RecommendationServiceTest` | End-to-end orchestration: stale/duplicate rejection, pipeline wiring, audit persistence, shadow-lock application |
| `RecommendationControllerTest` | HTTP layer — request validation, status codes, error mapping |
| `TargetIntakeDTOValidationTest` | Bean Validation constraints on inbound target intake payloads |
| `TelemetryHeartbeatDTOValidationTest` | Bean Validation constraints on inbound telemetry payloads |
| `JsonNodeConverterTest` | JPA attribute converter that persists the ranked payload as JSONB |
| `RoApplicationTests` | Spring context loads successfully |

## Absolute Rules

a) Stale data with timestamps older than 10 seconds must be rejected immediately.
b) Redis must enforce a 30-second Time-To-Live (TTL) Shadow Lock on recommended assets.
c) Assets with `MAINTENANCE_REQUIRED` or `MANUAL_OVERRIDE` status must be strictly excluded from all scoring calculations.
d) Tie-breaking conditions in scoring must be 100% deterministic.
