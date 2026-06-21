# upwave-brand-lift-demo

A small backend project that models a simplified **brand lift measurement**
pipeline: ingest a lot of ad exposure events, combine them with survey answers,
calculate campaign **lift summaries**, and turn those summaries into readable
marketing insights.

This is not trying to be a full product. It’s more of a focused engineering
showcase: data modeling, safe high-volume ingestion, reliability thinking, and
observability - in code that is still simple enough to explain in an interview.

## What problem this models

Brand lift measurement, like what [Upwave](https://www.upwave.com) works on,
roughly looks like this:

1. Ads are shown, which creates a stream of **exposure events** - who saw which
   campaign and when.
2. Some users answer **survey questions**, like "Have you heard of brand X?"
3. By comparing answers from people who *were* exposed with people who *were not*
   exposed, you can estimate the **lift** caused by the campaign.
4. Customers need that data turned into clear and trustworthy **insights**.

The tricky part is not really the formula. The harder part is the backend plumbing:
events can arrive duplicated, late, or out of order; processing can fail and needs
to retry safely; and the raw amount of events is much bigger than the summaries
customers actually care about.

This project is built around those practical problems.

## Why it exists

I built this to show how I would approach this kind of backend system as a senior
engineer:

* Keep **raw events separate from computed summaries**, so ingestion can stay
  simple and append-only, while reads stay fast.
* Make ingestion **idempotent**, so retries and at-least-once delivery do not mess
  up the counts.
* Keep the design **async-friendly**, even if the first version is synchronous,
  so moving to a queue or stream later would not require reworking the whole domain.
* Treat **reliability and observability** as part of the design, not something to
  bolt on at the end.

## Why synthetic data?

Real ad exposure and survey datasets are proprietary, so this project generates its
own **synthetic-but-realistic** data instead. A small simulation layer
(`dev.sereda.brandlift.simulation`) produces deterministic campaigns, exposure
events, and survey responses from a configurable scenario — including the messy
parts that matter to a backend: exposed/control groups, duplicate exposure events,
and late-arriving survey responses.

The point is to model the **production backend concerns** of a brand measurement
platform — idempotent ingestion, data quality, recompute-ability — not to claim real
measurement accuracy. The numbers are made up on purpose; the engineering problems
they create are the real subject.

## Tech stack

* Java 17, Spring Boot 3.x
* Gradle, with wrapper included
* PostgreSQL
* Flyway for database migrations
* Docker Compose for local Postgres
* JUnit 5 + Testcontainers for integration tests

## Planned architecture

```text
                +------------------+
   HTTP (REST)  |  Campaign API    |  create/read campaigns
  ───────────►  |  Exposure API    |  ingest exposure events (idempotent)
                |  Survey API      |  ingest survey responses
                |  Lift API        |  read computed lift summaries
                +--------+---------+
                         |
                         v
              +----------------------+        +-------------------------+
              |  raw event tables    |        |  computed summary tables |
              |  append-only         | ─────► |  campaign_lift_summary  |
              |  ad_exposure_events  |        |                         |
              |  survey_responses    |        |                         |
              +----------------------+        +-----------+-------------+
                                                          |
                                                          v
                                              +-----------------------+
                                              |  Insight generator    |
                                              |  mock AI, no API key  |
                                              +-----------------------+
```

Ingestion writes into append-only raw tables. A computation step, synchronous at
first and queue-driven later, rolls those raw events into `campaign_lift_summary`.

A mock insight generator takes a summary and produces a natural-language marketing
insight. It sits behind an interface, so a real LLM integration could be added
later without changing the rest of the app too much.

The project now has the initial persistence schema (Flyway): `campaigns`, the raw
`ad_exposure_events` and `survey_responses` tables, and the computed
`campaign_lift_summaries`, with small explicit JDBC repositories for the raw
entities. REST APIs and the lift calculation come in later steps.

More details are in [`docs/design-notes.md`](docs/design-notes.md).

## Planned commit roadmap

1. **Project skeleton + docs** - Gradle/Spring Boot setup, Docker Compose
   Postgres, Flyway, Actuator health, context-load test, README and design notes.
   *(this step)*
2. **Campaign domain + schema** - `campaigns` table and basic create/read REST API.
3. **Ad exposure ingestion** - `ad_exposure_events` table, idempotency key, and
   deduplication on ingest.
4. **Survey response ingestion** - `survey_responses` table and REST API.
5. **Lift computation + summary table** - separate `campaign_lift_summary` table
   and exposed-vs-control lift calculation.
6. **Lift summary retrieval API** - endpoint for reading computed summaries.
7. **Mock AI insight generator** - interface plus deterministic mock implementation.
8. **Reliability + observability pass** - structured logs, metrics-friendly hooks,
   and retry/DLQ notes connected back to the code.

The idea is that each step is one clean, reviewable commit.

## Out of scope, on purpose

* Real statistical significance or confidence intervals. Lift here is simplified.
* Real authentication, authorization, and multi-tenancy.
* A real streaming pipeline like Kafka. The design is ready for it, but does not
  pull it in yet.
* A real LLM integration. The insight generator is a deterministic mock behind an
  interface.
* A UI. This is backend-only.

## Running it locally

You need JDK 17+ and Docker.

**1. Start Postgres**

```bash
docker compose up -d
```

**2. Run the tests**

The tests use Testcontainers, so Docker needs to be running. They do not depend on
the Compose Postgres instance.

```bash
./gradlew test
```

**3. Run the app**

This runs the app against the Compose Postgres instance using the `local` profile.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

**4. Check health**

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

When you are done, stop Postgres:

```bash
docker compose down          # keep data
docker compose down -v       # also remove the volume
```
