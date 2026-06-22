# Design notes

This is a living document. The point is to capture the reasoning behind the
structure, so the *why* can be reviewed together with the code. I’ll update it as
each step lands.

## Domain assumptions

These are intentional simplifications. In places where I would normally push back
or ask the Upwave team for the real production model, I called that out.

* **Campaign** - the main unit we measure. It has an advertiser/brand, a name, and
  a measurement window. A campaign owns its exposure events, survey responses, and
  computed lift results.
* **Ad exposure event** - basically, "user U was exposed to campaign C at time T".
  These events come from ad-serving systems and can arrive in very high volume.
  I treat them as immutable facts. I also assume **at-least-once delivery**, so
  duplicate events are expected, not some weird edge case.
* **Survey response** - "user U answered question Q with value V for campaign C".
  Each respondent belongs either to the **exposed** group or the **control** group.
  In real life, this is more complicated - matching, weighting, etc. For this demo,
  group membership is just stored as an attribute.
* **Lift** - the difference between the positive-response rate in the exposed group
  and the positive-response rate in the control group. Here it is modeled as a
  simple rate difference. Real significance testing, confidence intervals, and
  weighting are out of scope, as mentioned in the README.
* **Insight** - a short human-readable summary of a lift result, something that
  could show up in a customer-facing report.

**Questions I would ask the Upwave team** - also related to the scaling section:

* Is a "user" a stable identifier that can be joined across exposure and survey
  data, or is the join based on some probabilistic / household-level identifier?
* How is control-group membership actually decided? Does that happen upstream, or
  would this service be responsible for it?
* Is lift calculated per question, per audience segment, or both?

## Synthetic data

Because real exposure and survey data is proprietary, the project generates its own
via `SyntheticBrandLiftGenerator`. A few deliberate assumptions:

* Each user is independently exposed or not (Bernoulli draw on `exposureRate`); the
  rest are control. Exposed users get a small number of impressions across channels.
* Survey scores are `baseline (+ lift if exposed) + small noise`, clamped to 0..100.
  Lift is injected directly into exposed users rather than emerging from any model.
* Duplicates are exact redeliveries (same idempotency key); late responses are simply
  flagged and timestamped after the campaign window.
* Generation is fully deterministic: one seeded `Random` plus hashed ids, so the same
  scenario always yields an equal dataset.

**What this is useful for:** exercising the backend problems — idempotent ingestion
and dedup, handling late-arriving data, recompute-ability, data-quality counts — with
data shaped like the real thing, reproducibly and without proprietary inputs.

**What it does not prove:** anything about real-world measurement accuracy. There is
no causal model, no audience matching, no weighting, and no sampling realism. The lift
is *assumed*, not *measured*.

**Why final lift / statistical correctness is deferred:** computing a trustworthy lift
number (significance, confidence intervals, weighting) is a separate concern from the
data plumbing this project is about. Modeling it now would add statistical complexity
that obscures the backend design, so the lift summary is computed in a later step and
kept intentionally simple even then.

## Raw events vs. computed summaries

The most important modeling decision in this project is **separating append-only
raw events from computed summaries**.

* **Raw event tables** - `ad_exposure_events` and `survey_responses` - are
  write-optimized and append-only. Ingestion should do as little as possible:
  validate, deduplicate, insert. No aggregation on the hot path.
* **Computed summary tables** - like `campaign_lift_summary` - are read-optimized.
  They store the small derived numbers that customers actually look at, and they
  are created by a separate computation step.

Why keep them separate:

* **Different scale and access patterns.** Raw events are large and written all the
  time. Summaries are small and read more often. If we mix these concerns into the
  same table/model, it usually ends up bad at both.
* **Recompute-ability.** Since raw events are kept, summaries can be rebuilt from
  source data. This matters for late-arriving data and also for fixing bugs in the
  computation logic without losing history.
* **Auditability.** Customers may ask, "why is this number what it is?" Keeping the
  raw events gives us a way to answer that.

This is basically a command/query split at the data layer, but kept practical and
concrete instead of turning it into a big framework.

## Schema and persistence (current state)

The schema (Flyway `V2__brand_lift_domain.sql`) has four tables: `campaigns`, the
two raw-event tables `ad_exposure_events` and `survey_responses`, and the computed
`campaign_lift_summaries`.

* **Raw events vs. computed summaries, in the schema.** The raw tables are
  append-only and write-heavy; `campaign_lift_summaries` holds one small row per
  campaign and is fully derivable from the raw events. Keeping them in separate
  tables means the read-heavy summary can be recomputed at any time without touching
  the raw history (important for late-arriving data and for fixing computation bugs).
* **Why `idempotency_key` is unique.** Exposure events arrive under at-least-once
  delivery, so the same logical event can be delivered more than once. A unique
  constraint on `idempotency_key` makes the database the single source of truth for
  deduplication — it holds across restarts and across multiple app instances, which
  an in-memory cache could not.
* **Indexes and why.** On both raw tables we index `campaign_id` (compute/read per
  campaign), `user_id_hash` (join exposures to survey responses by user), and the
  event/response timestamp (windowing and late-data handling). `idempotency_key` is
  already covered by its unique constraint. We index the actual read paths rather
  than guessing.
* **Data-quality constraints at the storage boundary.** Survey scores are bounded
  `0..100` by check constraints, `channel` is restricted to the known set (matching
  the `Channel` enum), and `campaigns` requires `ends_at > starts_at`. Bad data is
  rejected by the database, not only by application code.
* **Server-assigned timestamps.** `created_at` / `updated_at` / `received_at` default
  to `now()` in the database; the application does not set them. Domain timestamps
  (`impression_timestamp`, `response_timestamp`) are supplied by the caller.

**Intentionally deferred:** REST APIs, the lift calculation that populates
`campaign_lift_summaries`, dedup-on-conflict ingestion (the repository insert is a
plain insert that surfaces the unique-constraint violation for now), and any
`updated_at` trigger. The persistence layer currently exposes only `insert` and
`findById` for the three raw entities — enough to prove the schema and boundaries.

## Why campaign management comes first

The first API exposed is plain CRUD-ish campaign management, before any event
ingestion or lift calculation. That ordering is deliberate:

* A campaign is the parent of every other record (exposures, responses, summaries
  all reference `campaign_id`), so it has to exist before anything can be ingested.
* It lets us establish the API conventions — validation, error shape (`ApiError`),
  DTO-vs-persistence separation, controller/service/repository boundaries — on the
  simplest possible resource, so the higher-volume ingestion endpoints can follow
  the same patterns without re-litigating them.

Event ingestion and lift calculation are intentionally deferred to keep each API
layer small and independently reviewable. The campaign endpoints use a plain insert
plus read-back (no idempotency yet); idempotent exposure ingestion is its own step,
where the unique `idempotency_key` constraint becomes dedup-on-conflict.

## Idempotent exposure ingestion (implemented)

`POST /api/exposure-events` ingests one raw exposure event idempotently.

* **Why duplicate delivery is expected.** Upstream ad-serving and event-streaming
  systems are practically at-least-once: producers retry on timeouts, brokers
  redeliver on consumer restarts, and network hiccups cause re-sends. So the same
  logical event arriving twice is normal traffic, not an error — ingestion has to be
  safe under it.
* **How it behaves.** Unknown campaign → 404. New `idempotencyKey` → insert and
  `201 Created` with `duplicate=false`. Already-seen key → no second row, `200 OK`
  with the existing event and `duplicate=true`. A duplicate is a successful no-op, so
  a retrying producer converges instead of failing.
* **Why the unique constraint is the final guard.** The service first checks for an
  existing key, but that check and the insert are not atomic — two concurrent
  deliveries can both pass the check. The unique constraint on `idempotency_key`
  makes the database reject the loser; the service catches that violation and returns
  the row that won. The pre-check is only an optimization; correctness lives in the
  constraint. This is also why we did not reach for an application-level lock.

**Intentionally deferred:**

* An async queue / Kafka / Kinesis in front of ingestion. The endpoint is shaped
  like a producer write (append a raw event, no computation), so a queue can be added
  later without reshaping the domain.
* Dead-letter handling for events that repeatedly fail validation or processing.
* Aggregation / lift calculation over the ingested events.
* Late-event handling beyond simply storing the event with its timestamps; deciding
  whether a late event triggers recompute is a later concern.

## Survey response ingestion (implemented)

`POST /api/survey-responses` stores one survey response. It is deliberately simpler
than exposure ingestion: campaign must exist, scores are validated `0..100`, and the
response is inserted as a raw record.

* **Why responses are stored raw.** Like exposures, survey responses are immutable
  facts ("user U answered with these scores at time T"). Keeping them raw and
  append-only means lift can be computed — and recomputed — from source data, and we
  can audit any summary back to the underlying responses. No aggregation happens on
  the ingest path.
* **Why exposed/control is captured on the response.** Lift is the difference between
  the exposed group and the control group, so each response must carry which group it
  belongs to. We record `exposed` as a stored attribute rather than deriving it later
  from exposure events: the group assignment is an input to measurement (in reality
  decided by the study design / upstream), and pinning it on the response keeps the
  comparison stable even as exposure data arrives or is corrected. `late` is likewise
  captured per response so late arrivals are visible without recomputing.
* **No idempotency key, on purpose.** Unlike exposure events, survey responses have
  no natural idempotency key in this model, so ingestion is a plain insert and we do
  not dedup. This is called out rather than hidden.

**Intentionally deferred:**

* Lift calculation over the stored responses.
* Statistical confidence / significance of any computed lift.
* Survey deduplication (would require a real response identity from the survey
  provider).
* Attribution / identity-graph logic for joining responses to exposures beyond the
  shared `user_id_hash`.

## Campaign lift calculation (implemented)

`POST /api/campaigns/{id}/lift-summary/recalculate` computes a summary from the raw
survey responses and upserts it into `campaign_lift_summaries`; `GET .../lift-summary`
returns the persisted summary.

* **The calculation.** Responses are split by `exposed`. For each metric we take the
  exposed-group average and the control-group average, and define lift as
  `exposed_avg - control_avg` in score points. Averages and lift are rounded to two
  decimals, and lift is computed from the rounded averages so the persisted numbers
  are internally consistent. The aggregation is a single explicit SQL query using
  Postgres `FILTER`, which keeps the per-group math in one readable place.
* **Why calculation is separated from ingestion.** Ingestion stays a cheap,
  append-only write on the hot path; computation is a separate, on-demand step over
  the accumulated data. This is the command/query split made concrete: it lets the
  two scale and fail independently, and means a recompute never blocks or slows
  ingestion. Today recalculation is synchronous and triggered by an endpoint; the
  same boundary is where a queue or scheduled batch job would later sit.
* **Why raw responses remain the source of truth.** The summary is a pure function of
  the raw responses, so it can always be rebuilt. That is what makes late-arriving
  data and computation-bug fixes safe to handle by re-running the calculation rather
  than patching numbers in place, and it keeps every summary auditable back to its
  inputs.
* **Why summaries are persisted.** Customers read lift far more often than it changes,
  and the underlying aggregation gets more expensive as response volume grows. Storing
  one small row per campaign makes reads cheap and stable, and records `calculated_at`
  so staleness is visible. The summary is a cache of a computation, not a second
  source of truth.
* **Insufficient data is an explicit error.** Lift is a comparison, so if either group
  has zero responses we refuse to invent a number and return 422 rather than producing
  a misleading zero or null.

**Intentionally deferred:** statistical significance, confidence intervals, causal
inference, identity-graph / attribution logic for joining responses to exposures, and
moving recalculation to an async queue or scheduled batch job.

## Idempotency approach

Ingestion needs to be safe when producers retry, especially with at-least-once
delivery. Duplicate events should not inflate counts.

* Each exposure event has a client-provided **idempotency key**, for example a
  stable event id from the producer. The key is unique per campaign.
* The database enforces that with a **unique constraint**. Ingestion does an
  idempotent insert - insert the row, and ignore it on conflict.
* The database is the source of truth for deduplication. Application memory would
  not survive restarts, and it would not work cleanly across multiple app instances.
* A duplicate event is treated as a **successful no-op**, not as an error. A
  retrying producer should converge to the same result instead of causing failures.

Why DB-enforced instead of app-level dedup:

* It survives restarts.
* It works across horizontally-scaled instances.
* There is one correctness boundary, which makes it easier to reason about and test.

## Reliability concerns

These are the main failure modes I care about for this kind of pipeline, and how
the project is shaped to handle them:

* **Duplicate events** - expected with at-least-once delivery. Handled using the
  idempotency key and database unique constraint.
* **Late-arriving data** - exposures or survey responses may arrive after a summary
  has already been computed. Since summaries are recomputed from retained raw data,
  late data can be handled by rerunning the computation rather than patching numbers
  in place. Summaries should have a "computed at" timestamp so staleness is visible.
* **Failed processing** - ingestion is small and transactional. The computation
  step is designed to be **retryable and idempotent**, so a failed computation can
  simply be run again.
* **Retries** - safe by design, because both ingestion and computation are
  idempotent. This is one of the main payoffs of the raw-event + recompute model.
* **Dead-letter strategy** - in the current synchronous version, malformed input is
  rejected at the API boundary with a clear error. If ingestion later moves to a
  queue, the equivalent would be a **dead-letter queue** for events that repeatedly
  fail validation or processing. Those messages should include enough context to
  inspect and replay them. The domain model is shaped so this becomes an
  infrastructure change, not a redesign.

## Async-friendly, but synchronous first

The first version computes lift synchronously, but the boundaries are drawn so an
async version can be added later without changing the domain too much.

* Ingestion only appends raw events. It does not compute anything. Thats already
  close to the shape of a producer writing into a log.
* Computation reads raw events and writes a summary, which is basically a consumer’s
  job.
* Moving to Kafka, SQS, or something similar would mostly mean putting a queue
  between those two parts and running computation as a consumer. The domain model
  does not need to be rebuilt for that.

## Observability

This is kept simple and metrics-friendly rather than trying to add too much too
early.

* **Health** through Spring Boot Actuator, especially `/actuator/health`, including
  the datasource.
* **Structured contextual logs** around ingestion and computation boundaries:
  campaign id, idempotency key, counts, and similar fields. This makes it easier to
  trace what happened to a specific event or campaign.
* **Counter-shaped operations** - events ingested, duplicates ignored, summaries
  computed. These are designed so Micrometer counters can be added later without
  reshaping the code.

## Scaling questions I would ask the Upwave team

These are the questions where the answers would actually change the architecture:

* **Volume and retention.** What is the peak exposure-event rate, and how long do
  raw events need to be stored? This affects partitioning and whether raw storage
  should stay in Postgres or move to a columnar store / data lake, with Postgres
  keeping only summaries.
* **Freshness SLA.** How fresh do lift numbers need to be - minutes, hours, or daily
  batch? This decides whether synchronous computation is enough, or whether we need
  streaming or batch processing.
* **Identity and joins.** How do exposure records and survey records join back to a
  person, and how stable is that identifier? This is the foundation for correctness.
* **Cardinality of summaries.** Are summaries per campaign, per question, per
  segment, or some combination of those? This affects both table design and compute
  cost.
* **Reprocessing expectations.** How often does historical data get corrected, and
  what is the acceptable cost of recomputing a full campaign?
* **Multi-tenancy and isolation.** Do advertisers need noisy-neighbor isolation at
  the storage level, or is logical separation enough for this system?
