# Design notes

A short technical memo on the reasoning behind this service, so the *why* can be
reviewed alongside the code. It is a simplified simulation intended to demonstrate
backend design tradeoffs, not real measurement accuracy.

## Domain assumptions

Deliberate simplifications; where I would normally ask the Upwave team for the real
production model, it is flagged below and in the final section.

* **Campaign** — the unit we measure: a brand, a name, and a measurement window. It
  owns its exposure events, survey responses, and computed lift.
* **Ad exposure event** — "user U was exposed to campaign C, on channel X, at time T".
  These come from ad-serving systems in high volume. Treated as immutable facts, and
  assumed to arrive under **at-least-once delivery**, so duplicates are normal traffic.
* **Survey response** — "user U answered with these scores at time T", tagged as
  **exposed** or **control**. Real group assignment (matching, weighting) is more
  involved; here it is a stored attribute.
* **Lift** — exposed-group average minus control-group average, per metric, in score
  points. A plain rate difference; no significance testing or weighting.
* **Insight** — a short, human-readable description of a lift result, suitable for a
  customer-facing report.

## Synthetic data

Real data is proprietary, so `SyntheticBrandLiftGenerator` produces its own:

* Each user is independently exposed or not (a draw on `exposureRate`); the rest are
  control. Exposed users get a few impressions across channels.
* Survey scores are `baseline (+ lift if exposed) + small noise`, clamped to 0..100.
  Lift is injected into exposed users rather than emerging from a model.
* Duplicates are exact redeliveries (same idempotency key); late responses are flagged
  and timestamped after the campaign window.
* Generation is fully deterministic — one seeded `Random` plus hashed ids — so a
  scenario always yields an equal dataset.

It is useful for exercising the backend problems (idempotent ingestion, late data,
recompute-ability, data-quality counts) with realistically shaped, reproducible data.
It proves nothing about measurement accuracy: there is no causal model, audience
matching, weighting, or sampling realism. The lift is *assumed*, not *measured*.

## Raw events vs. computed summaries

The central modeling decision is keeping append-only raw events separate from computed
summaries.

* **Raw event tables** (`ad_exposure_events`, `survey_responses`) are write-optimized
  and append-only. Ingestion does the minimum: validate, dedup, insert — no
  aggregation on the hot path.
* **Computed summary table** (`campaign_lift_summaries`) is read-optimized: one small
  row per campaign, produced by a separate step.

Why separate them: raw events are large and constantly written while summaries are
small and read often, so one table cannot be good at both; retained raw events make
summaries **rebuildable** (key for late data and bug fixes) and **auditable** ("why is
this number what it is?"). This is a command/query split at the data layer, kept
concrete rather than dressed up as a framework.

The schema (Flyway `V2__brand_lift_domain.sql`) enforces data quality at the storage
boundary: survey scores are checked `0..100`, `channel` is restricted to the known set
(matching the `Channel` enum), `campaigns` requires `ends_at > starts_at`, and
`idempotency_key` is unique. Server-assigned timestamps (`created_at`, `updated_at`,
`received_at`) default to `now()` in the database; domain timestamps
(`impression_timestamp`, `response_timestamp`) are supplied by the caller. Indexes
cover the real read paths: `campaign_id`, `user_id_hash`, and the event/response
timestamp on each raw table.

## Idempotency

Exposure ingestion must be safe under producer retries and at-least-once delivery, so
duplicate delivery must never inflate counts.

* Each event carries a client-supplied **idempotency key**. New key → insert and
  `201`; already-seen key → no new row, `200` with the existing event and
  `duplicate=true`. A duplicate is a successful no-op, so a retrying producer
  converges instead of failing.
* A **unique constraint** on `idempotency_key` is the final guard. The service does a
  fast pre-check for an existing key, but that check and the insert are not atomic —
  two concurrent deliveries can both pass it. The constraint rejects the loser, and
  the service catches that violation and returns the row that won. The pre-check is an
  optimization; correctness lives in the database, which is why there is no
  application-level lock.

DB-enforced dedup survives restarts and works across horizontally-scaled instances,
with a single correctness boundary that is easy to reason about and test.

## Survey response modeling

Survey ingestion is intentionally simpler than exposure ingestion.

* **Stored raw.** Responses are immutable facts, kept append-only so lift can be
  computed and recomputed from source and audited back to its inputs.
* **Exposed/control on the response.** Lift is a comparison between the two groups, so
  each response carries its group. Group membership is an *input* to measurement
  (decided by study design / upstream), so pinning it on the response keeps the
  comparison stable even as exposure data arrives or is corrected. `late` is captured
  the same way, so late arrivals are visible without recomputing.
* **No idempotency key, on purpose.** Survey responses have no natural idempotency key
  in this model, so ingestion is a plain insert with no dedup — stated rather than
  hidden. Real dedup would need a response identity from the survey provider.

## Lift calculation

`POST /api/campaigns/{id}/lift-summary/recalculate` aggregates raw responses and
upserts `campaign_lift_summaries`; `GET .../lift-summary` returns the persisted row.

* **The calculation.** Responses are split by `exposed`; for each metric, lift is
  `exposed_avg - control_avg`. Averages and lift are rounded to two decimals, and lift
  is computed from the rounded averages so the stored numbers are internally
  consistent. The aggregation is a single explicit SQL query using Postgres `FILTER`,
  keeping the per-group math in one readable place.
* **Separated from ingestion.** Ingestion stays a cheap append; computation is a
  separate, on-demand step over accumulated data, so the two scale and fail
  independently and a recompute never slows ingestion. Recalculation is synchronous
  today; this boundary is exactly where a queue or scheduled batch job would sit.
* **Summary is a cache, not a second source of truth.** It is a pure function of the
  raw responses, persisted because customers read lift far more often than it changes
  and the aggregation grows with volume. `calculated_at` records staleness.
* **Insufficient data is explicit.** If either group has zero responses, the service
  returns `422` rather than inventing a misleading zero or null.

## AI-ready insight generation

`GET /api/campaigns/{id}/insights` turns a persisted summary into a customer-facing
insight. It reads the summary and never recalculates, so it is side-effect free.

* **Why a deterministic mock, not a real LLM.** The point is the *integration
  boundary*, not a provider. A real call adds network dependency, latency, cost,
  non-determinism, and an external failure mode to what is otherwise a pure read.
  Modeling the seam deterministically lets the API shape, error handling, and tests be
  built and trusted now, with the provider as a later, isolated change.
* **The boundary.** `CampaignInsightGenerator` is a one-method interface
  (`CampaignLiftSummary -> CampaignInsight`). The current implementation classifies
  each metric's lift into strong/modest/neutral/negative and phrases plain,
  non-exaggerated text, taking every number straight from the summary. A future
  LLM-backed implementation would, behind the same interface, shape the summary's
  structured numbers into a prompt (never raw end-user text), expect a structured
  response matching `CampaignInsight`, and **fall back to the deterministic generator**
  on timeout/error/unusable output.
* **Why this helps.** Deterministic output is unit-testable without mocking a model
  and stable across runs; the interface makes the LLM swap a wiring decision; the
  deterministic path doubles as the always-available fallback; and generating from a
  stored summary keeps inputs grounded in real numbers, limiting the blast radius of
  any future model.

## Reliability and observability

The failure modes that matter for this kind of pipeline, and how the design handles
them:

* **Duplicate events** — expected under at-least-once delivery; handled by the
  idempotency key and unique constraint.
* **Late-arriving data** — because summaries are recomputed from retained raw data,
  late data is handled by re-running the calculation rather than patching numbers in
  place. `calculated_at` makes staleness visible.
* **Failed processing** — ingestion is small and transactional; recalculation is
  idempotent (it upserts), so a failed run can simply be re-run.
* **Retries** — safe by construction, since both ingestion and recalculation are
  idempotent.
* **Dead-letter strategy** — today, malformed input is rejected at the API boundary
  with a consistent `ApiError`. If ingestion moves to a queue, the equivalent is a
  dead-letter queue for messages that repeatedly fail, carrying enough context to
  inspect and replay. The domain is shaped so this is an infrastructure change, not a
  redesign.
* **Async-friendly** — ingestion appends raw events (a producer's shape) and
  recalculation reads raw and writes a summary (a consumer's shape). Introducing
  Kafka/SQS means putting a queue between the two, not reshaping the domain.

Observability is kept simple and metrics-friendly: Actuator `/actuator/health`
(including the datasource), structured logging at the ingestion and computation
boundaries (campaign id, idempotency key, counts), and counter-shaped operations
(events ingested, duplicates ignored, summaries recalculated) that a Micrometer
counter could attach to without reshaping code.

## Questions I would ask the Upwave team

The answers that would actually change this architecture:

* **Late data.** How do you handle late-arriving exposure and survey data today — is
  there a defined cutoff, or is everything continuously reprocessed?
* **Real-time vs. batch.** Which parts of lift calculation are real-time vs. batch, and
  what freshness SLA do customers actually need (minutes, hours, daily)?
* **Evaluating AI insights.** How are AI-generated customer insights evaluated and
  guard-railed — human review, automated grounding/quality checks, or both?
* **Correctness risks.** What are the most important correctness risks in campaign
  measurement (identity resolution, control-group construction, dedup, weighting), and
  where do they tend to break?
* **Where the time goes.** Where do engineers spend the most time today — ingestion,
  data modeling, analytics, or customer-facing workflows — and what is the current
  biggest source of operational pain?
* **Identity and joins.** Is a "user" a stable identifier joinable across exposure and
  survey data, or a probabilistic / household-level one? This is the foundation for
  correctness.
* **Volume, retention, and cardinality.** What is the peak exposure-event rate, how
  long must raw events be retained, and are summaries per campaign, per question, per
  segment, or a combination? These drive storage choices and compute cost.
