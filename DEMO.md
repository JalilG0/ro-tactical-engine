# Demo Scenario

A walkthrough for presenting Ro: four assets report in, one target intake
event arrives, and the engine returns a ranked, explained recommendation.
Section 3 shows the exact expected output, so this doc can be read and
understood **without running anything** — sections 0–2 are only needed if
you want to reproduce it live.

## 0. Start the stack

```bash
docker-compose up -d
```

Wait for `http://localhost:8080/swagger-ui.html` to respond, or watch
`docker-compose logs -f app`.

## 1. Fleet check-in (telemetry heartbeats)

Four assets report their status. `TS` is computed fresh each call because
the engine rejects any payload older than 10 seconds.

```bash
TS=$(( $(date +%s) * 1000 ))
curl -s -X POST http://localhost:8080/api/v1/telemetry/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "A-101",
    "model": "BOZOK",
    "location": { "lat": 39.9050, "lng": 32.8550, "alt": 1200 },
    "status": "FREE",
    "linkQuality": 92,
    "fuelPercentage": 85,
    "munition": { "currentType": "BOZOK", "capableTypes": ["BOZOK", "MAM_L"], "count": 4 },
    "timestamp": '"$TS"'
  }'
```

```bash
TS=$(( $(date +%s) * 1000 ))
curl -s -X POST http://localhost:8080/api/v1/telemetry/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "A-102",
    "model": "TOLUN",
    "location": { "lat": 39.8950, "lng": 32.8450, "alt": 1500 },
    "status": "FREE",
    "linkQuality": 95,
    "fuelPercentage": 90,
    "munition": { "currentType": "TOLUN", "capableTypes": ["TOLUN"], "count": 2 },
    "timestamp": '"$TS"'
  }'
```

```bash
TS=$(( $(date +%s) * 1000 ))
curl -s -X POST http://localhost:8080/api/v1/telemetry/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "A-103",
    "model": "MAM_C",
    "location": { "lat": 39.9100, "lng": 32.8600, "alt": 1100 },
    "status": "FREE",
    "linkQuality": 88,
    "fuelPercentage": 75,
    "munition": { "currentType": "MAM_C", "capableTypes": ["MAM_C", "MAM_L"], "count": 6 },
    "timestamp": '"$TS"'
  }'
```

A fourth asset is deliberately down for maintenance, to show the exclusion
engine at work:

```bash
TS=$(( $(date +%s) * 1000 ))
curl -s -X POST http://localhost:8080/api/v1/telemetry/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": "A-104",
    "model": "MAM_L",
    "location": { "lat": 39.9020, "lng": 32.8510, "alt": 1300 },
    "status": "MAINTENANCE_REQUIRED",
    "linkQuality": 60,
    "fuelPercentage": 40,
    "munition": { "currentType": "MAM_L", "capableTypes": ["MAM_L"], "count": 3 },
    "timestamp": '"$TS"'
  }'
```

## 2. Target intake

A moving target, threat level 6, under active EW jamming:

```bash
TS=$(( $(date +%s) * 1000 ))
curl -s -X POST http://localhost:8080/api/v1/recommendations/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-intake-alpha-001",
    "timestamp": '"$TS"',
    "target": {
      "targetId": "TARGET-ALPHA",
      "threatLevel": 6,
      "coordinates": { "lat": 39.9000, "lng": 32.8500 },
      "weatherContext": "CLEAR",
      "movementStatus": "MOVING"
    },
    "ewContext": {
      "activeEwThreat": true,
      "jammerPolygon": [
        { "lat": 39.9200, "lng": 32.8300 },
        { "lat": 39.9200, "lng": 32.8700 },
        { "lat": 39.8800, "lng": 32.8500 }
      ]
    },
    "allowPreemption": false
  }' | python3 -m json.tool
```

## 3. Expected output

Given the four heartbeats and the target intake above, the engine returns:

```json
{
  "recommendationId": "<generated-uuid>",
  "targetId": "TARGET-ALPHA",
  "timestamp": 1752739200000,
  "xaiExplanation": "Hedef TARGET-ALPHA (Tehdit Seviyesi: 6/10) için taktik değerlendirme tamamlandı. Aktif Elektronik Harp (EW) tehdidi tespit edildi; rota, tespit edilen karıştırma (jammer) bölgesini bypass edecek şekilde uzatıldı. Hedefin hareketli (MOVING) olması sebebiyle anlık takip yapabilen lazer güdümlü mühimmatlar (MAM-L, MAM-C, BOZOK) önceliklendirilmiş; sabit koordinata taarruz eden GPS/INS güdümlü sistemlere (TOLUN, SOM) düşük öncelik verilmiştir. En yüksek öncelikli öneri: TOLUN modeli (1 adet müsait varlık). Overmatch Doktrini gereği, seçilen mühimmat gücü hedef tehdit seviyesini tek başına aşmaktadır. 1 varlık aşağıdaki nedenlerle değerlendirme dışı bırakıldı: A-104 (Bakım Gerekiyor).",
  "rankedModelGroups": [
    {
      "modelName": "TOLUN",
      "totalAvailableCount": 1,
      "subClusters": [
        {
          "location": { "lat": 39.895, "lng": 32.845, "alt": 1500.0 },
          "status": "FREE",
          "assetIds": ["A-102"],
          "maxSelectable": 1,
          "isBatchSelectable": false
        }
      ],
      "tieBreakerApplied": false
    },
    {
      "modelName": "BOZOK",
      "totalAvailableCount": 1,
      "subClusters": [
        {
          "location": { "lat": 39.905, "lng": 32.855, "alt": 1200.0 },
          "status": "FREE",
          "assetIds": ["A-101"],
          "maxSelectable": 1,
          "isBatchSelectable": false
        }
      ],
      "tieBreakerApplied": false
    },
    {
      "modelName": "MAM_C",
      "totalAvailableCount": 1,
      "subClusters": [
        {
          "location": { "lat": 39.91, "lng": 32.86, "alt": 1100.0 },
          "status": "FREE",
          "assetIds": ["A-103"],
          "maxSelectable": 1,
          "isBatchSelectable": false
        }
      ],
      "tieBreakerApplied": false
    }
  ]
}
```

(`recommendationId` and `timestamp` are generated at call time, so those two
fields will differ on every real run — everything else is deterministic and
reproducible for these inputs, which is the point of the tie-breaking rule
in [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md).)

Note what's *absent*: `A-104` doesn't appear in `rankedModelGroups` at all —
it was filtered out before scoring even ran. Its exclusion is only visible
in the `xaiExplanation` text, which is intentional: the exclusion engine and
the explanation generator are decoupled (see the pipeline diagram).

## 4. What to expect — and what to say

The response ranks three model groups. Walk through it in this order:

1. **`A-104` never appears** — it was excluded before scoring even started
   (`MAINTENANCE_REQUIRED`). Point out the `xaiExplanation` field mentions it
   by name with the reason code translated into Turkish.
2. **`TOLUN` (A-102) is ranked first**, not the highest raw-power weapon in
   isolation — explain the scoring: base score 80 (power 8 × 10) + 50
   overmatch bonus (8 > threat 6) − 20 moving-target penalty (GPS/INS
   commits to a fixed point, can't re-track a moving target) − 5 EW-reroute
   penalty = **105**.
3. **`BOZOK` (A-101) is second at 80** — no overmatch (power 6 does not
   exceed threat 6), but it gets a +25 bonus for being laser-guided against
   a moving target (can re-track in flight) minus the 5-point EW penalty.
4. **`MAM_C` (A-103) is third at 70`** — same laser/moving bonus as BOZOK,
   but lower base power.
5. Read the `xaiExplanation` string out loud — it independently states the
   EW reroute, the movement/guidance trade-off, and the overmatch
   justification, which is the "explainability" contribution of the
   project: every ranking decision is traceable to a stated reason, not a
   black box.
6. Optionally, re-run step 2 within 30 seconds and point out the response
   is identical — then explain the **Shadow Lock**: `A-102` was reserved in
   Redis for 30 seconds after the first call precisely so a second,
   concurrent operator request can't be handed the same asset.
7. Re-run step 2 a third time with the *same* `timestamp` you used
   originally (i.e. reuse an old request) to trigger the **stale-data
   rejection** (`409`/error response after 10s) — a good way to show the
   validity-gate rule from `SYSTEM_ARCHITECTURE.md` live.

## 5. Cross-reference

- Full pipeline explanation: [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)
- Interactive API exploration: `http://localhost:8080/swagger-ui.html`
