# System Architecture

## Project Name
Ro (Tactical Decision Support & Triage Engine)

## Core Philosophy
This system is **NOT** a Command & Control (C2) or flight control system. It is a stateless, reactive microservice that calculates and returns Top-10 hierarchical tactical recommendation payloads for Ground Control Station (GCS) operators.

## Package Hierarchy
- `domain` — DTOs, Models, Enums
- `repository` — JPA & Redis
- `engine` — Triage, Overmatch, EW, Weather, XAI, Tie-Breaker
- `controller` — REST Gateway

## Absolute Rules
a) Stale data with timestamps older than 10 seconds must be rejected immediately.
b) Redis must enforce a 30-second Time-To-Live (TTL) Shadow Lock on recommended assets.
c) Assets with `MAINTENANCE_REQUIRED` or `MANUAL_OVERRIDE` status must be strictly excluded from all scoring calculations.
d) Tie-breaking conditions in scoring must be 100% deterministic.
