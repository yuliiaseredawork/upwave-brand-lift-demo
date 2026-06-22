# upwave-brand-lift-demo

This is an independent demo based on public descriptions of the brand measurement domain; 
it is not affiliated with or endorsed by Upwave.

A small backend that models a simplified **brand lift measurement** pipeline: ingest
ad exposure events, ingest survey responses from exposed and control users, compute a
campaign **lift summary**, and turn that summary into a short, customer-facing
**insight**.

This is a focused engineering showcase, not a product. It is deliberately small enough
to read in one sitting and explain in an interview, while still exercising the backend
problems this domain actually has: idempotent high-volume ingestion, separating raw
data from computed results, recompute-ability, and a clean, AI-ready boundary for
generated insights. It is a simplified simulation intended to demonstrate backend
design tradeoffs — not real measurement accuracy.

## The problem it models

Brand lift measurement, like what [Upwave](https://www.upwave.com) works on, roughly
looks like this:

1. Ads are shown, producing a stream of **exposure events** — who saw which campaign,
   on which channel, and when.
2. Some users answer **survey questions** ("Have you heard of brand X?").
3. Comparing answers from users who *were* exposed against those who *were not*
   (control) estimates the **lift** the campaign produced.
4. Customers want that turned into clear, trustworthy **insights**.

The hard part is not the arithmetic. It is the plumbing: events arrive duplicated and
late, processing must retry safely, and raw event volume dwarfs the small summaries
customers actually read. This project is built around those concerns.

## Why synthetic data

Real exposure and survey datasets are proprietary, so the project generates its own
**synthetic-but-realistic** data. A small, dependency-free simulation layer
(`dev.sereda.brandlift.simulation`) produces deterministic campaigns, exposure events,
and survey responses from a configurable scenario — including the messy parts that
matter to a backend: exposed/control groups, duplicate exposure events, and
late-arriving responses. The numbers are invented on purpose; the engineering problems
they create are the real subject.

## What it demonstrates

* **Java 17 / Spring Boot 3** service with thin controllers over small services and
  explicit JDBC repositories — no hidden ORM mapping.
* **PostgreSQL + Flyway** schema with explicit constraints and indexes, modeled so
  data quality is enforced at the storage boundary.
* **Idempotent event ingestion**: duplicate delivery of an exposure event never
  creates a second row, with the database unique constraint as the final guard.
* **Raw events vs. computed summaries**: append-only raw tables feed a separately
  computed, persisted summary that is always rebuildable from source.
* **Survey response ingestion** capturing exposed/control group and late arrivals.
* **Lift calculation**: a single explicit SQL aggregation into a persisted summary.
* **Deterministic, AI-ready insights**: a natural-language insight generated behind an
  interface that a real LLM could later implement — with the deterministic generator
  as a built-in fallback.
* **Integration tests** that run against a real PostgreSQL via Testcontainers.

## Architecture

```text
                +------------------+
   HTTP (REST)  |  Campaign API    |  create / read campaigns
  ───────────►  |  Exposure API    |  ingest exposure events (idempotent)
                |  Survey API      |  ingest survey responses
                |  Lift API        |  recalculate / read lift summary
                |  Insights API    |  read generated insight
                +--------+---------+
                         |
            writes raw   |   reads raw, writes summary
                         v
        +-----------------------+        +---------------------------+
        |  raw event tables     |        |  computed summary table   |
        |  (append-only)        | ─────► |  campaign_lift_summaries  |
        |  ad_exposure_events   |  recalc|  (one row per campaign)   |
        |  survey_responses     |        +-------------+-------------+
        +-----------------------+                      |
                                                       v
                                          +---------------------------+
                                          |  CampaignInsightGenerator |
                                          |  (deterministic mock;     |
                                          |   LLM-ready interface)    |
                                          +---------------------------+
```

Ingestion only appends raw events. A separate, on-demand recalculation step rolls the
raw survey responses into `campaign_lift_summaries`. The insight generator reads that
persisted summary; it never recalculates. See [`docs/design-notes.md`](docs/design-notes.md)
for the reasoning behind each decision.

## Intentionally simplified

* No real ad-network or survey-provider integrations — data is synthetic.
* No real LLM provider — insights come from a deterministic mock behind an interface.
* No statistical significance, confidence intervals, or causal inference — lift is a
  plain exposed-minus-control average.
* No Kafka/Kinesis — ingestion is synchronous, but shaped so a queue could front it.
* No identity graph / attribution — exposures and responses share a `user_id_hash`.
* No frontend, no authentication, no multi-tenancy.

## Tech stack

Java 17 · Spring Boot 3 · Gradle (wrapper included) · PostgreSQL · Flyway · Docker
Compose · JUnit 5 · Testcontainers. No Lombok.

## Running locally

You need a JDK 17+ and Docker.

```bash
# 1. Start Postgres
docker compose up -d

# 2. Run the tests (use a real Postgres via Testcontainers; Docker must be running,
#    but they do not depend on the Compose instance)
./gradlew test

# 3. Run the app against the Compose Postgres, using the local profile
./gradlew bootRun --args='--spring.profiles.active=local'

# 4. Check health
curl http://localhost:8080/actuator/health   # {"status":"UP", ...}
```

Stop Postgres when done with `docker compose down` (add `-v` to drop the volume).

## Demo

With the app running, a small script walks the whole flow end to end (create campaign
→ ingest exposures → ingest exposed/control surveys → recalculate → summary →
insights):

```bash
chmod +x scripts/demo.sh
./scripts/demo.sh
```

## API walkthrough

The same flow by hand. `Content-Type: application/json` is omitted below for brevity
but required on the `POST`s with a body.

**1. Create a campaign** (capture the returned `id`)

```bash
curl -s -X POST http://localhost:8080/api/campaigns \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spring Awareness Push","brandName":"Acme",
       "startsAt":"2025-01-01T00:00:00Z","endsAt":"2025-01-15T00:00:00Z"}'
# 201 Created; 400 on blank name/brand, missing dates, or endsAt <= startsAt
```

**2. Ingest an exposure event** (idempotent: same `idempotencyKey` is a safe no-op)

```bash
curl -s -X POST http://localhost:8080/api/exposure-events \
  -H 'Content-Type: application/json' \
  -d '{"campaignId":"{id}","userIdHash":"u-0001","channel":"CTV",
       "creativeId":"creative-1","placementId":"placement-1",
       "impressionTimestamp":"2025-01-05T12:00:00Z","idempotencyKey":"evt-0001"}'
# First call: 201 with "duplicate": false. Same key again: 200 with "duplicate": true,
# returning the original event and no new row. channel must be one of CTV, SOCIAL,
# DISPLAY, STREAMING_AUDIO, RETAIL_MEDIA, LINEAR_TV. Unknown campaignId -> 404.
```

**3. Ingest survey responses** — at least one exposed and one control

```bash
# exposed respondent
curl -s -X POST http://localhost:8080/api/survey-responses \
  -H 'Content-Type: application/json' \
  -d '{"campaignId":"{id}","userIdHash":"u-0001","exposed":true,
       "awarenessScore":70,"considerationScore":40,"purchaseIntentScore":25,
       "responseTimestamp":"2025-01-06T09:00:00Z","late":false}'

# control respondent
curl -s -X POST http://localhost:8080/api/survey-responses \
  -H 'Content-Type: application/json' \
  -d '{"campaignId":"{id}","userIdHash":"u-9001","exposed":false,
       "awarenessScore":60,"considerationScore":30,"purchaseIntentScore":10,
       "responseTimestamp":"2025-01-06T09:00:00Z","late":false}'
# 201 each. Scores are bounded 0..100; out-of-range or missing required fields -> 400.
```

**4. Recalculate the lift summary**

```bash
curl -s -X POST http://localhost:8080/api/campaigns/{id}/lift-summary/recalculate
# 200 with counts, per-metric exposed/control averages, and lift.
# 404 if the campaign is unknown; 422 if it has no exposed or no control responses.
```

**5. Get the persisted lift summary**

```bash
curl -s http://localhost:8080/api/campaigns/{id}/lift-summary
# 200 with the summary; 404 if the campaign is unknown or no summary exists yet.
```

**6. Get the insight**

```bash
curl -s http://localhost:8080/api/campaigns/{id}/insights
# 200 with summary, keyFindings, recommendedNextSteps, caveats.
# 404 if the campaign is unknown or no summary exists yet.
```

Lift is a simple **exposed-average minus control-average** in score points, rounded to
two decimals. The insight text is produced by a deterministic mock generator (no API
keys, no network calls), behind a `CampaignInsightGenerator` interface that a real LLM
provider could later implement.

## Quality checks

```bash
./gradlew test                                  # full suite, incl. Testcontainers integration tests
docker compose up -d                            # local Postgres for running the app
./gradlew bootRun --args='--spring.profiles.active=local'
```
